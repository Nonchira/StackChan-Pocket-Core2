package jp.stackchan.pocket

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.media.*
import android.net.*
import android.net.wifi.WifiManager
import android.os.*
import android.speech.tts.*
import com.k2fsa.sherpa.onnx.*
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicLong

class RobotService : Service() {
    companion object {
        @Volatile var status="停止中"
        @Volatile var audioDiagnostics="Core2スピーカーで再生すると診断を取得します。"
        @Volatile var asrTiming="音声認識：未計測"
        @Volatile var activeBackend="未読み込み"
        @Volatile var llmTiming="まだ計測していません"
        @Volatile var running=false
        @Volatile var ready=false
        @Volatile var core2Volume=-1
        @Volatile var volumeStatus="接続して本体の音量を取得してください"
    }
    private val playbackAudit=PlaybackAudit()
    private val settings by lazy { getSharedPreferences("settings",MODE_PRIVATE) }
    private val work=ArrayBlockingQueue<PendingCommand>(16)
    private data class Frame(val generation:Long,val samples:FloatArray,val reset:Boolean=false,val audioGeneration:Long=0)
    private val input=ArrayBlockingQueue<Frame>(32)
    private val epoch=AtomicLong()
    private val audioEpoch=AtomicLong()
    private val captureLock=Any()
    private val modelLock=Any()
    private val asrLock=Any()
    private val modelOwner=Any()
    @Volatile private var releaseRequested=false
    @Volatile private var modelLoading=false
    private fun requestRelease() {
        synchronized(captureLock) {
            releaseRequested=true
            ready=false
            ModelRuntime.update(modelOwner,ModelState("releasing","処理終了後に解放します"))
        }
        cancel(true)
    }
    // Called only by the LLM worker, after its current generation has joined.
    private fun releaseModels(error:String?=null)=synchronized(modelLock) {
        if(!ModelRuntime.owns(modelOwner)) { releaseRequested=false;return@synchronized }
        ready=false
        cachedValid=false
        cachedConversation?.close();cachedConversation=null;conversation=null
        engine?.close();engine=null
        synchronized(captureLock) { vad?.release();vad=null }
        synchronized(asrLock) { asr?.release();asr=null }
        memory.clear();activeBackend="未読み込み"
        ModelRuntime.release(modelOwner,error)
        releaseRequested=false
    }
    private val speechInbox=SpeechInbox()
    private data class Recognition(val generation:Long,val samples:FloatArray,val single:Boolean,val registration:Boolean,val heardAt:Long)
    private val recognitionJobs=ArrayBlockingQueue<Recognition>(2)
    @Volatile private var outputActive=false
    private var speechActive=false // guarded by captureLock
    private var processedAudioEpoch=-1L
    private fun acceptsAudio()=alive && ready && connected && listening && !outputActive && (!oneShot || !busy)
    private fun resetCapture()=synchronized(captureLock) {
        audioEpoch.incrementAndGet();input.clear();micPrimed=false;speechActive=false
    }
    private fun pauseForOutput(turn:Long) {
        val deadline=SystemClock.elapsedRealtime()+20000
        while(true) {
            checkTurn(turn)
            val paused=synchronized(captureLock) {
                if(outputActive)true
                else if(!speechActive || SystemClock.elapsedRealtime()>=deadline) {
                    outputActive=true;resetCapture();true
                } else false
            }
            if(paused)break
            Thread.sleep(20)
        }
        send("compat.ready","listening" to false)
    }
    private fun resumeAfterOutput(turn:Long) {
        synchronized(captureLock) {
            if(turn!=epoch.get())return
            if(outputActive) { resetCapture();outputActive=false }
            if(connected)state(if(listening) "listening" else "idle")
        }
    }
    private val ttsReady=CompletableFuture<Unit>()
    private var selectedOfflineVoice:Voice?=null
    private val utterances=ConcurrentHashMap<String,CompletableFuture<Unit>>()
    private var tts:TextToSpeech?=null
    private var vad:Vad?=null
    private var asr:OfflineRecognizer?=null
    private var engine:LocalLlm?=null
    private val memory=ConversationMemory()
    @Volatile private var conversation:LocalSession?=null
    private var cachedConversation:LocalSession?=null
    @Volatile private var cachedValid=false
    private var cachedHistory:List<ConversationMemory.Turn> = emptyList()
    private var cachedMode=-1
    @Volatile private var transport:RobotTransport?=null
    private val linkEpoch=AtomicLong()
    @Volatile private var connected=false
    @Volatile private var alive=true
    @Volatile private var listening=false
    @Volatile private var touchStartPending=false
    @Volatile private var busy=false
    @Volatile private var recorder:AudioRecord?=null
    private val phoneMicRunning=java.util.concurrent.atomic.AtomicBoolean(false)
    private val phoneMicStopToken=AtomicLong()
    private var lastSequence:Long?=null
    @Volatile private var registering=false
    private val wakeWindow=WakeWindow()
    @Volatile private var oneShot=false
    @Volatile private var micPrimed=false
    private val capture=UtteranceCapture()
    @Volatile private var listenDeadline=0L
    @Volatile private var monologue=false
    private var nextMonologue=0L
    private var lastReply=""
    private val controlReplies=ConcurrentHashMap<String,CompletableFuture<JSONObject>>()
    private fun robotControl(op:String,value:String,turn:Long):JSONObject {
        checkTurn(turn);check(connected) { "Core2に接続してください" }
        val id="ctrl-${turn}-${System.nanoTime()}";val future=CompletableFuture<JSONObject>();controlReplies[id]=future
        try {
            send("compat.control","requestId" to id,"op" to op,"value" to value)
            val response=future.get(15,TimeUnit.SECONDS);checkTurn(turn)
            check(response.optBoolean("ok")) { response.optString("error","本体操作を完了できませんでした") }
            return response
        } catch(e:TimeoutException) { send("compat.stop");throw IllegalStateException("本体の応答がありません。Core2ファームの更新と接続を確認してください",e) }
        finally { controlReplies.remove(id) }
    }
    private fun executeIntent(i:RobotIntent,turn:Long):String {
        checkTurn(turn)
        return when(i.op) {
            "motion" -> { state("idle");robotControl(i.op,i.value,turn);when(i.value) {
                "tilt" -> "横に傾く軸はないので、少し斜めを向きました。"
                "nod" -> "うなずきました。";"dance" -> "踊りました。";"center" -> "正面に戻しました。"
                else -> mapOf("right" to "右","left" to "左","up" to "上","down" to "下").getValue(i.value)+"を向きました。"
            } }
            "volume.set","volume.delta" -> {
                val n=i.value.toInt();if(i.op=="volume.set" && n !in 0..100) "音量は0から100パーセントで指定してください。"
                else if(phoneOutput()) { val percent=setPhoneVolume(if(i.op=="volume.delta") phoneVolume()+n else n);"スマホの音量を${percent}パーセントにしました。" }
                else { val result=robotControl(i.op,i.value,turn);val percent=result.getInt("value");core2Volume=percent;volumeStatus="本体保存済み：${percent}%";"音量を${percent}パーセントにしました。" }
            }
            "battery" -> { val n=robotControl("battery","",turn).getInt("value");"Core2の電池残量は${n}パーセントです。" }
            "region" -> {
                robotControl("region",i.value,turn)
                check(settings.edit().putString("region",i.value).putBoolean("weather_wide",false).commit()) { "本体の地点は変更できましたが、スマホ側の保存に失敗しました" }
                "天気の地点を${ForecastParser.names.getValue(i.value)}にしました。"
            }
            "monologue" -> { monologue=i.value=="on";scheduleMonologue();if(monologue) "独り言を始めます。" else "独り言をやめました。" }
            "repeat" -> lastReply.ifBlank { "まだ読み上げた返答がありません。" }
            "repeat.last" -> lastReply.split(Regex("(?<=[。！？])")).lastOrNull { it.isNotBlank() } ?: "まだ読み上げた返答がありません。"
            "timer" -> {
                val seconds=i.value.toLong()
                if(seconds !in 1..3600) "タイマーは1秒から60分までです。"
                else { val replaced=PhoneTimer.start(this,seconds);"${if(replaced) "前のタイマーを置き換え、" else ""}${seconds/60}分${seconds%60}秒のタイマーを開始しました。" }
            }
            "timer.cancel" -> { PhoneTimer.cancel(this);"タイマーを取り消しました。" }
            "timer.remaining" -> PhoneTimer.remaining(this)?.let { "タイマーはあと${it}秒です。" } ?: "タイマーは動いていません。"
            else -> error("未対応の操作です")
        }
    }
    private fun scheduleMonologue() { nextMonologue=SystemClock.elapsedRealtime()+40000+java.util.concurrent.ThreadLocalRandom.current().nextInt(30000) }
    private fun idleActions() {
        if(connected && !busy && PhoneTimer.takeAnnouncement(this)) {
            say("タイマーの時間になりました。",epoch.get())
        }
        if(oneShot && listening && !busy && SystemClock.elapsedRealtime()>listenDeadline) {
            synchronized(captureLock) {
                if(oneShot && listening && !busy) { cancel(true);report("聞き取り待機を終了しました") }
            }
        }
        if(monologue && ready && connected && !listening && !busy && SystemClock.elapsedRealtime()>=nextMonologue) {
            scheduleMonologue()
            answer("短い独り言を一つ話してください。話題は"+listOf("楽しいこと","ジョーク","季節","俳句","好きなもの").random()+"です。",epoch.get(),true)
        }
    }
    @Volatile private var playback:CompletableFuture<Unit>?=null
    @Volatile private var playbackId=""
    @Volatile private var phonePlayback:AudioTrack?=null
    private var pending=FloatArray(0)
    private var vadEpoch=-1L
    @Volatile private var feedbackUntil=0L
    private val noiseEpoch=AtomicLong()
    private var processedNoiseEpoch=0L
    private lateinit var power:PowerManager.WakeLock
    private lateinit var wifi:WifiManager.WifiLock
    private val http=OkHttpClient.Builder().pingInterval(15,TimeUnit.SECONDS).build()
    private val weather by lazy { Weather(this) }
    private val news by lazy { News(this) }
    private fun report(text:String) { status=text }
    private fun send(type:String, vararg values:Pair<String,Any>) {
        val j=JSONObject().put("type",type); values.forEach { j.put(it.first,it.second) }; transport?.sendText(j.toString())
    }
    private fun state(value:String) {
        send("compat.expression","value" to if(value=="speaking") 1 else 0)
        send("state","value" to value)
        send("compat.ready","listening" to (value=="listening" && ready && connected && listening && micPrimed && acceptsAudio()))
    }
    override fun onBind(intent:Intent?)=null
    override fun onCreate() {
        super.onCreate(); running=true;ready=false
        val nm=getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("conversation","音声会話",NotificationManager.IMPORTANCE_LOW))
        val stop=PendingIntent.getService(this,0,Intent(this,RobotService::class.java).setAction("shutdown"),PendingIntent.FLAG_IMMUTABLE)
        val open=PendingIntent.getActivity(this,1,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        val note=Notification.Builder(this,"conversation").setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("スタックチャン Pocket").setContentText("ローカル音声処理を使用中").setContentIntent(open)
            .addAction(Notification.Action.Builder(null,"終了",stop).build()).setOngoing(true).build()
        startForeground(1,note,ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        power=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"StackChan:conversation")
        wifi=(applicationContext.getSystemService(WIFI_SERVICE) as WifiManager).createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,"StackChan")
        power.acquire(); wifi.acquire()
        tts=TextToSpeech(this) { result ->
            if(result==TextToSpeech.SUCCESS) ttsReady.complete(Unit)
            else ttsReady.completeExceptionally(IllegalStateException("日本語の端末内音声エンジンを設定してください"))
        }
        tts!!.setOnUtteranceProgressListener(object:UtteranceProgressListener(){
            override fun onStart(id:String?) {}
            override fun onDone(id:String?) { utterances.remove(id)?.complete(Unit) }
            override fun onStop(id:String?,interrupted:Boolean) { utterances.remove(id)?.completeExceptionally(IllegalStateException("Androidの音声合成が停止されました")) }
            @Deprecated("platform callback") override fun onError(id:String?) { utterances.remove(id)?.completeExceptionally(IllegalStateException("音声合成失敗")) }
        })
        Thread({
            try {
                while(alive) {
                    if(releaseRequested) { releaseModels();report("モデルを解放しました（接続維持）") }
                    val taskEpoch=epoch.get()
                    val command=work.poll()
                    try {
                        if(command!=null) command.run(epoch.get(),alive) else {
                            val spoken=speechInbox.poll()
                            if(spoken!=null)handleRecognized(spoken) else { idleActions();Thread.sleep(20) }
                        }
                    } catch(e:Exception) {
                        if(taskEpoch==epoch.get())busy=false
                        val message=TaskFailure.message(e,taskEpoch!=epoch.get() || !alive)
                        if(message!=null) {
                            android.util.Log.e("StackChanPocket","Local task failed",e)
                            report("エラー: $message")
                            if(connected) { state(if(listening) "listening" else "idle");send("compat.expression","value" to 4) }
                        }
                    }
                }
            } finally { releaseModels() }
        },"local-ai").start()
        Thread({
            try {
                while(alive) {
                    val frame=input.poll(100,TimeUnit.MILLISECONDS) ?: continue
                    try { process(frame) } catch(e:Exception) {
                        val message=TaskFailure.message(e,frame.generation!=epoch.get() || !alive)
                        if(message!=null) {
                            android.util.Log.e("StackChanPocket","Recognition failed",e)
                            report("音声認識: $message");resetCapture()
                            if(oneShot) { listening=false;oneShot=false;busy=false;startPhoneMic();state("idle") }
                        }
                    }
                }
            } finally { /* Models are released by the LLM worker under captureLock/asrLock. */ }
        },"local-vad").start()
        Thread({
            try {
                while(alive) {
                    val job=recognitionJobs.poll(100,TimeUnit.MILLISECONDS) ?: continue
                    try { recognize(job) } catch(e:Exception) {
                        speechInbox.discard(job.generation)
                        val message=TaskFailure.message(e,job.generation!=epoch.get() || !alive)
                        if(message!=null) {
                            android.util.Log.e("StackChanPocket","ASR failed",e)
                            report("音声認識: $message")
                            if(job.single || job.registration) { busy=false;oneShot=false }
                        }
                    }
                }
            } finally { /* See releaseModels. */ }
        },"local-asr").start()
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        when(intent?.action) {
            "volume.get" -> { if(phoneOutput())refreshPhoneVolume() else if(connected)send("compat.volume.get") }
            "volume.set" -> {
                val n=intent.getStringExtra("text")?.toIntOrNull()
                if(phoneOutput() && n!=null && n in 0..100) { setPhoneVolume(n) }
                else if(connected && n!=null && n in 0..100) { volumeStatus="本体へ送信中…";send("compat.volume.set","percent" to n) }
                else volumeStatus="Core2に接続してから調整してください"
            }
            "shutdown" -> stopSelf()
            "stop" -> if(modelLoading)requestRelease() else cancel(true)
            "forget" -> { cancel(true); enqueue { memory.clear();report("会話の記憶をリセットしました。会話開始で再開できます") } }
            "connect" -> connect()
            "disconnect" -> { cancel(true);linkEpoch.incrementAndGet();transport?.close();transport=null;connected=false;core2Volume=-1;report("通信方式を変更しました。接続してください") }
            "load" -> if(ModelRuntime.available() && !releaseRequested) enqueue { loadModels() } else report("モデルの使用・取り込み・解放中です")
            "unload" -> requestRelease()
            "listen" -> { cancel(true);val turn=epoch.get();enqueue { checkTurn(turn);send("compat.click");beginListening(settings.getString("talk_mode","once")!="continuous") } }
            "tts" -> enqueue { turn -> say("こんにちは。スタックチャンです。",turn) }
            "news" -> { cancel(true);val turn=epoch.get();enqueue { report("ニュースを取得中…");say(news.speech(),turn) } }
            "weather" -> { val turn=epoch.get(); enqueue { say(weather.selectedSpeech(check={checkTurn(turn)}),turn) } }
            "text" -> { val text=intent.getStringExtra("text") ?: ""; enqueue { turn -> answer(text,turn) } }
            "sync" -> { cancel(true); startPhoneMic(); sync() }
        }
        return START_NOT_STICKY
    }
    private fun enqueue(block:(Long)->Unit) { if(!work.offer(PendingCommand(epoch.get(),block))) report("処理待ちです。停止してから再試行してください") }
    private fun cancel(stopListening:Boolean) {
        playbackAudit.cancel();audioDiagnostics=playbackAudit.summary()
        touchStartPending=false
        controlReplies.values.forEach { it.completeExceptionally(CancellationException("中断")) };controlReplies.clear()
        synchronized(captureLock) {
            if(stopListening) { listening=false;oneShot=false;monologue=false;registering=false }
            busy=false
            val generation=epoch.incrementAndGet();speechInbox.reset(generation);recognitionJobs.clear();resetCapture();outputActive=false
        }
        work.clear()
        if(stopListening)startPhoneMic()
        phonePlayback?.let { runCatching { it.pause();it.flush() } }
        conversation?.let { cachedValid=false;runCatching { it.cancelProcess() } }
        tts?.stop(); utterances.values.forEach { it.completeExceptionally(CancellationException("ユーザー操作で中断")) }; utterances.clear()
        playback?.completeExceptionally(CancellationException("ユーザー操作で中断")); playback=null
        transport?.discardAudio();send("compat.stop"); state(if(listening) "listening" else "idle")
        report(if(stopListening) "会話停止（接続維持）" else "処理を中断しました")
    }
    private fun checkTurn(turn:Long) { if(!alive || turn!=epoch.get()) throw CancellationException("中断") }
    private fun beginListening(single:Boolean=true) {
        if(!ready) { report("先にモデルを読み込んでください"); return }
        if(!connected) { report("先にCore2に接続してください"); return }
        micPrimed=false;oneShot=single;listenDeadline=SystemClock.elapsedRealtime()+15000
        synchronized(captureLock) {
            listening=true;speechInbox.reset(epoch.incrementAndGet());recognitionJobs.clear();resetCapture();outputActive=false
        }
        state("listening"); startPhoneMic(); report("マイク準備中…マイク表示が点いたら話してください")
    }
    private fun connect() {
        cancel(true);val link=linkEpoch.incrementAndGet();transport?.close();transport=null;connected=false;core2Volume=-1;volumeStatus="音量は未取得"
        val usb=settings.getString("transport","wifi")=="usb"
        val listener=object:RobotTransport.Listener {
            override fun opened() {
                if(link!=linkEpoch.get())return
                connected=true;send("compat.stop");send("compat.ready","listening" to false);lastSequence=null;send("device.info.get");send("compat.volume.get");if(phoneOutput())refreshPhoneVolume();sync();report(if(usb) "Core2 USB接続済み" else "Core2 Wi-Fi接続済み")
            }
            override fun text(value:String){if(link!=linkEpoch.get())return;runCatching { event(JSONObject(value)) }.onFailure { report("受信エラー: ${it.message}") }}
            override fun audio(value:ByteArray) {
                if(link!=linkEpoch.get() || !acceptsAudio() || settings.getString("mic","core2")!="core2")return
                val generation=epoch.get();val audioGeneration=audioEpoch.get()
                runCatching {
                    val packet=AudioData.mic(value)
                    val gap=packet.start || lastSequence?.let { ((it+1) and 0xffffffffL)!=packet.sequence }==true
                    lastSequence=packet.sequence
                    if(generation==epoch.get() && audioGeneration==audioEpoch.get() && acceptsAudio()) {
                        val frame=Frame(generation,packet.samples,gap,audioGeneration)
                        if(!input.offer(frame)){input.clear();input.offer(frame.copy(reset=true))}
                    }
                }.onFailure { input.clear();lastSequence=null }
            }
            override fun failed(message:String){if(link!=linkEpoch.get())return;connected=false;core2Volume=-1;volumeStatus="Core2未接続";cancel(true);report(message)}
        }
        try {
            transport=if(usb)UsbRobotTransport(this,listener) else {
                val host=settings.getString("host","192.168.4.1")!!
                require(host.matches(Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")) && host.split('.').all { it.toInt() in 0..255 })
                val cm=getSystemService(ConnectivityManager::class.java)
                val network=cm.allNetworks.firstOrNull { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true }
                WifiRobotTransport(if(network!=null)http.newBuilder().socketFactory(network.socketFactory).build() else http,host,listener)
            }
            report(if(usb) "USB接続中…使用許可と本体のUSB設定を確認してください" else "Wi-Fi接続中…")
            transport?.start()
        }catch(e:Exception){listener.failed("接続失敗：${e.message}")}
    }
    private fun sync()=send("compat.sync","region" to (settings.getString("region","osaka")!!.takeIf { it in ForecastParser.names } ?: "osaka"),
        "wake" to settings.getBoolean("wake",false),"mic" to settings.getString("mic","core2")!!)
    private fun event(j:JSONObject) {
        if(j.optString("type")=="audio.playback_diag") { if(playbackAudit.accept(j,epoch.get()))audioDiagnostics=playbackAudit.summary();return }
        if(j.optString("type")=="compat.control.result") { controlReplies[j.optString("requestId")]?.complete(j);return }
        if(j.optString("type")=="compat.volume") {
            val n=j.optInt("percent",-1)
            if(n in 0..100 && !phoneOutput()) { core2Volume=n;volumeStatus=if(n==0) "本体保存済み：消音" else "本体保存済み：${n}%" }
            return
        }
        if(j.optString("type")=="compat.feedback") { feedbackUntil=SystemClock.elapsedRealtime()+350;noiseEpoch.incrementAndGet();input.clear();return }
        if(j.optString("type")=="compat.playback.done") {
            if(j.optString("requestId")==playbackId) playback?.complete(Unit)
            return
        }
        if(j.optString("type")!="compat.action") return
        when(j.optString("action")) {
            "news" -> { cancel(true);val turn=epoch.get();enqueue { report("ニュースを取得中…");say(news.speech(),turn) } }
            "weather" -> { val turn=epoch.get(); enqueue { say(weather.selectedSpeech(check={checkTurn(turn)}),turn) } }
            "region" -> { val region=j.optString("region"); if(region in ForecastParser.names) { settings.edit().putString("region",region).putBoolean("weather_wide",false).apply(); sync() } }
            "wake.toggle" -> { settings.edit().putBoolean("wake",!settings.getBoolean("wake",false)).apply(); sync() }
            "wake.register" -> { val turn=epoch.get(); enqueue { check(ready) { "先にモデルを読み込んでください" }; say("登録するウェイクワードを一つ話してください。",turn); checkTurn(turn); registering=true; beginListening(false) } }
            "listen" -> { cancel(true);val turn=epoch.get();enqueue { checkTurn(turn);beginListening(settings.getString("talk_mode","once")!="continuous") } }
            "monologue.toggle" -> {
                if(!ready || !connected) { report("先にモデルを読み込み、Core2へ接続してください");return }
                val enabled=!monologue;cancel(true);monologue=enabled;scheduleMonologue()
                val turn=epoch.get();enqueue { say(if(enabled) "独り言始めます。" else "独り言やめます。",turn) }
            }
            "touch.listen" -> {
                if(!ready || !connected) { report("顔タッチ：先にモデルを読み込み、Core2へ接続してください");return }
                // Listening remains true during recognition/reply; another tap stops the whole session.
                // A tap during queued startup must also cancel, rather than queue another start.
                if(listening || busy || touchStartPending) { registering=false;cancel(true);return }
                cancel(true);registering=false;touchStartPending=true
                val turn=epoch.get()
                enqueue {
                    try { checkTurn(turn);wakeWindow.open(SystemClock.elapsedRealtime());beginListening(settings.getString("talk_mode","once")!="continuous") }
                    finally { if(turn==epoch.get() || listening)touchStartPending=false }
                }
            }
            "stop" -> if(modelLoading)requestRelease() else cancel(true)
            "forget" -> { cancel(true); enqueue { memory.clear();report("会話の記憶をリセットしました。会話開始で再開できます") } }
            "mic" -> { settings.edit().putString("mic",j.optString("mic","core2")).apply(); cancel(false); startPhoneMic(); sync() }
            "battery" -> { val n=j.optInt("percent",-1); val turn=epoch.get(); enqueue { say(if(n in 0..100) "バッテリー残量は${n}パーセントです。" else "バッテリー残量を取得できません。",turn) } }
        }
    }
    private fun loadModels()=synchronized(modelLock) {
        check(!ready && ModelRuntime.beginLoad(modelOwner)) { "先にモデルを解放し、取り込み完了を待ってください" }
        modelLoading=true
        val turn=epoch.get()
        fun ensureLoading() { checkTurn(turn);if(releaseRequested)throw CancellationException("読み込み中止") }
        fun stage(text:String,percent:Int?=null) {
            synchronized(captureLock) {
                ensureLoading();ModelRuntime.update(modelOwner,ModelState("loading",text,percent=percent));report(text.substringBefore("（"))
            }
        }
        fun model(name:String):String=File(filesDir,name).also { check(it.isFile && it.length()>0) { "$name を取り込んでください" } }.path
        try {
            val gguf=settings.getString("llm_engine","litert")=="gguf"
            val gpu=settings.getString(if(gguf)"gguf_backend" else "llm_backend","cpu")=="gpu"
            val a=model("sensevoice.onnx");val tokens=model("tokens.txt");val v=model("silero_vad.onnx")
            val llm=model(if(gguf)"model.gguf" else "model.litertlm")
            stage("音声認識を読み込み中")
            synchronized(asrLock) { asr=OfflineRecognizer(config=OfflineRecognizerConfig(modelConfig=OfflineModelConfig(
                senseVoice=OfflineSenseVoiceModelConfig(model=a,language="ja"),tokens=tokens,numThreads=2))) }
            stage("VADを読み込み中")
            synchronized(captureLock) { vad=Vad(config=VadModelConfig(sileroVadModelConfig=SileroVadModelConfig(model=v,minSilenceDuration=settings.getFloat("vad_silence",1f).coerceIn(.65f,1.5f),maxSpeechDuration=15f))) }
            stage(if(gguf)"GGUFファイルをロード中" else "LiteRT-LMを読み込み中")
            engine=if(gguf)GgufLlm(llm,settings.getInt("gguf_threads",4),applicationInfo.nativeLibraryDir,gpu,
                settings.getBoolean("gguf_reuse",true),onLoad={ progress ->
                    if(progress>=0)stage("GGUFファイルをロード中（RAM全体の割合ではありません）",(progress*100).toInt().coerceIn(0,100))
                    else stage("GGUFコンテキストを確保中")
                },cancelLoad={releaseRequested || !alive || turn!=epoch.get()})
                else LiteRtLlm(llm,gpu,cacheDir.path)
            synchronized(captureLock) {
            ensureLoading()
            activeBackend=if(gguf) "GGUF / ${if(gpu)"Vulkan GPU" else "CPU"}" else "LiteRT-LM / ${if(gpu)"GPU" else "CPU"}"
            ready=true
            ModelRuntime.update(modelOwner,ModelState("ready","使用中：${settings.getString("model_name_${if(gguf) "model.gguf" else "model.litertlm"}",if(gguf) "model.gguf" else "model.litertlm")}",activeBackend))
            report("モデル準備完了（$activeBackend）。会話開始を押してください")
            }
        } catch(e:Exception) {
            val message=TaskFailure.message(e,releaseRequested || !alive || turn!=epoch.get())
            releaseModels(message)
            if(message!=null)throw e else report("読み込みを中止し解放しました")
        } finally { modelLoading=false }
    }
    private fun process(frame:Frame) {
        var single=false
        var registration=false
        val samples=synchronized(captureLock) {
            if(!acceptsAudio() || frame.generation!=epoch.get() || frame.audioGeneration!=audioEpoch.get())return
            val v=vad ?: return
            val noise=noiseEpoch.get()
            if(frame.reset || vadEpoch!=frame.generation || processedAudioEpoch!=frame.audioGeneration || noise!=processedNoiseEpoch) {
                v.reset();pending=FloatArray(0);capture.reset();speechActive=false
                vadEpoch=frame.generation;processedAudioEpoch=frame.audioGeneration;processedNoiseEpoch=noise
            }
            if(oneShot)capture.append(frame.samples)
            if(SystemClock.elapsedRealtime()<feedbackUntil)return
            if(!micPrimed) {
                micPrimed=true;send("compat.ready","listening" to true)
                if(!busy)report(if(oneShot) "一回会話：話し終わると自動終了します" else "連続会話：聞き取り中（タッチで停止）")
            }
            pending+=frame.samples
            while(pending.size>=512) { v.acceptWaveform(pending.copyOfRange(0,512));pending=pending.copyOfRange(512,pending.size) }
            speechActive=v.isSpeechDetected()
            if(v.empty())return
            single=oneShot;registration=registering
            val result=if(single)capture.samples() else v.front().samples
            v.pop();v.reset();pending=FloatArray(0);capture.reset();speechActive=false
            if(single || registration) { listening=false;busy=true;startPhoneMic();state("idle") }
            result
        }
        synchronized(captureLock) {
            if(frame.generation!=epoch.get())return
            if(!speechInbox.reserve(frame.generation)) {
                report("次の発言は2件までです。返答を待ってからもう一度話してください")
                return
            }
            if(!recognitionJobs.offer(Recognition(frame.generation,samples,single,registration,SystemClock.elapsedRealtime()))) {
                speechInbox.discard(frame.generation)
                return
            }
        }
        if(!busy || single || registration)report("録音終了・音声認識中…")
    }
    private fun recognize(job:Recognition) {
        checkTurn(job.generation)
        val asrStarted=SystemClock.elapsedRealtime()
        val text=synchronized(asrLock) {
        checkTurn(job.generation)
        val recognizer=asr ?: error("音声認識モデルが未準備です")
        val stream=recognizer.createStream()
        try {
            stream.acceptWaveform(job.samples,16000);recognizer.decode(stream)
            recognizer.getResult(stream).text.replace(Regex("<\\|.*?\\|>"),"").trim()
        } finally { stream.release() }
        }
        checkTurn(job.generation)
        asrTiming="直近の音声認識：${SystemClock.elapsedRealtime()-asrStarted}ms / 入力音声${job.samples.size*1000L/16000}ms（VADの無音待ち・順番待ちを除く）"
        synchronized(captureLock) {
            checkTurn(job.generation)
            if(text.isBlank()) {
                speechInbox.discard(job.generation)
                if(job.single || job.registration) { busy=false;oneShot=false;report("聞き取れませんでした。もう一度会話開始を押してください") }
                return
            }
            if(speechInbox.complete(SpeechInbox.Item(job.generation,text,job.single,job.registration,job.heardAt)) && busy && !job.single && !job.registration)
                report("次の発言を受け付けました（順番待ち${speechInbox.size()}件）")
        }
    }
    private fun handleRecognized(item:SpeechInbox.Item) {
        checkTurn(item.generation)
        try {
            val text=item.text
            if(item.registration) {
                registering=false;listening=false;startPhoneMic()
                settings.edit().putString("wakeword",text).putBoolean("wake",true).apply();sync()
                say("ウェイクワードを、${text}、に設定しました。",item.generation);return
            }
            var query=text
            if(!item.single && settings.getBoolean("wake",false) && !wakeWindow.accepts(item.heardAt)) {
                val word=settings.getString("wakeword","スタックチャン")!!
                if(!text.contains(word))return
                query=text.substringAfter(word).trim('。','、',' ','！','？')
                wakeWindow.open(SystemClock.elapsedRealtime())
                if(query.isBlank()) { say("はい、どうぞ。",item.generation);return }
            }
            answer(query,item.generation)
        } finally {
            if(item.generation==epoch.get() && item.single) {
                oneShot=false;listening=false;startPhoneMic();report("一回の会話が終了しました")
            }
            if(item.generation==epoch.get()) { busy=false;resumeAfterOutput(item.generation) }
        }
    }
    private fun answer(text:String,turn:Long,monologueTurn:Boolean=false) {
        checkTurn(turn); if(text.isBlank())return
        require(text.length<=1000) { "一度の発言は1000文字以内にしてください" }
        busy=true
        if(!acceptsAudio())send("compat.ready","listening" to false)
        try {
            if(!monologueTurn)ChatLog.history.add("user",text)
            report("返答を考えています…");send("compat.expression","value" to 3)
            val intent=if(monologueTurn) null else RobotIntent.parse(text)
            val clockReply=if(monologueTurn) null else ClockReply.answer(text,java.time.ZonedDateTime.now())
            val result=if(intent!=null) {
                try { executeIntent(intent,turn) } catch(e:Exception) { checkTurn(turn);"操作を完了できませんでした。"+(TaskFailure.message(e,false) ?: "もう一度お試しください。") }
            } else if(clockReply!=null) clockReply else if(text.contains("ニュース")) { news.speech() } else if(listOf("天気","天候","降水確率").any { text.contains(it) }) {
                val day=if(text.contains("明日") && !text.contains("今日")) 1 else if(text.contains("今日") && !text.contains("明日")) 0 else -1
                weather.selectedSpeech(day){checkTurn(turn)}
            } else {
                val e=engine ?: error("先にモデルを読み込んでください")
                // Recreate native context with bounded, role-separated completed turns.
                val previous=memory.snapshot()
                val replyMode=if(monologueTurn) 0 else settings.getInt("llm_reply",1)
                val started=SystemClock.elapsedRealtime()
                llmTiming="LLM処理中（$activeBackend）"
                run {
                    val reuse=ContextReuse.allowed(e.supportsReuse && settings.getBoolean("reuse_context",false),monologueTurn,cachedValid,cachedHistory,memory.snapshot(),cachedMode,replyMode)
                    val reuseReason=if(!e.supportsReuse) "GGUFのトークン一致判定（詳細は末尾）" else ContextReuse.reason(settings.getBoolean("reuse_context",false),monologueTurn,cachedValid,cachedHistory,memory.snapshot(),cachedMode,replyMode)
                    val c=if(reuse) cachedConversation!! else {
                        cachedConversation?.close();cachedConversation=null
                        e.session(ReplyPolicy.prompt(replyMode),previous,ReplyPolicy.profile(replyMode).tokens).also { cachedConversation=it }
                    }
                    cachedValid=false
                    val contextReady=SystemClock.elapsedRealtime()
                    var completed=false
                    conversation=c
                    try {
                        val stream=SentenceStream(earlyClause=settings.getBoolean("early_clause",false))
                        val generated=AtomicLong(0)
                        val firstSent=AtomicLong(0)
                        val firstToken=AtomicLong(0)
                        val firstText=AtomicLong(0)
                        val firstSynthesis=AtomicLong(-1)
                        var firstPrep=""
                        var firstBoundary=""
                        val rtf=TtsRtf()
                        var logId=0L
                        c.generate(text,onDelta={ delta -> if(turn==epoch.get()) { if(delta.isNotBlank())firstToken.compareAndSet(0,SystemClock.elapsedRealtime());stream.append(delta) } },
                            onDone={generated.set(SystemClock.elapsedRealtime());stream.finish()},onError={stream.fail(it)})
                        try {
                            while(true) {
                                checkTurn(turn)
                                check(SystemClock.elapsedRealtime()-started<180000) { "LLMの応答が時間内に完了しませんでした" }
                                val sentence=stream.awaitNext()
                                if(sentence!=null && sentence.isNotBlank()) {
                                    if(firstText.compareAndSet(0,SystemClock.elapsedRealtime())) {
                                        val kind=when(sentence.lastOrNull()) { '、' -> "読点"; '。','！','？','!','?' -> "文末"; '\n' -> "改行"; else -> "生成完了" }
                                        firstBoundary="最初の読み上げ区間：${sentence.length}文字 / 区切り：$kind"
                                    }
                                    if(logId==0L)logId=ChatLog.history.add("assistant",sentence) else ChatLog.history.append(logId,sentence)
                                    say(sentence,turn,keepBusy=true,log=false,onFirstAudio={ firstSent.compareAndSet(0,SystemClock.elapsedRealtime()) },onSynthesis={ firstSynthesis.compareAndSet(-1,it) },onPreparation={ if(firstPrep.isEmpty())firstPrep=it },onRtf={ ms,bytes -> rtf.add(ms,bytes) })
                                } else if(stream.complete())break

                            }
                        } finally { if(!stream.complete())runCatching { c.cancelProcess() } }
                        checkTurn(turn)
                        val answer=stream.text()
                        completed=true
                        cachedHistory=memory.snapshot()+ConversationMemory.Turn(text,answer)
                        cachedMode=replyMode
                        cachedValid=e.supportsReuse && !monologueTurn && settings.getBoolean("reuse_context",false)
                        val seconds=(generated.get()-started)/1000.0
                        val first=if(firstSent.get()>0) "${firstSent.get()-started}ms" else "なし"
                        llmTiming=String.format(Locale.JAPAN,"全文生成：%.1f秒 / 出力%d文字 / 履歴%d往復 / $activeBackend\n最初の音声送信：%s\n生成と読み上げを並行。音声送信は実際の発音開始とは異なります。",seconds,answer.length,previous.size,first) +
                            "\n会話準備：${contextReady-started}ms / 履歴再利用：${if(reuse) "あり" else "なし"}\n再利用判定：$reuseReason\n最初の生成通知：${if(firstToken.get()>0) firstToken.get()-started else -1}ms / 最初の読み上げ用文章：${if(firstText.get()>0) firstText.get()-started else -1}ms\n生成通知から文章区切りまで：${if(firstText.get()>0 && firstToken.get()>0) firstText.get()-firstToken.get() else -1}ms"+
                            "\n$firstBoundary\n最初の区間の音声合成：${firstSynthesis.get()}ms\n$firstPrep\n${rtf.summary()}\n生成通知・文章・送信は会話コンテキスト作成前から計測。無音待ちとASRを除く。音声合成は単独の所要時間。"
                        if(e is GgufLlm)llmTiming+="\n"+e.lastStats
                        Thread.sleep(250);checkTurn(turn) // Quiet tail only after the complete reply.
                        answer
                    } catch(e:Exception) { cachedValid=false;llmTiming="前回のLLM処理は中断または失敗しました";throw e }
                    finally { conversation=null;if(!completed || !cachedValid) { cachedValid=false;cachedConversation?.close();cachedConversation=null } }
                }
            }
            checkTurn(turn); if(intent!=null || clockReply!=null || text.contains("ニュース") || listOf("天気","天候","降水確率").any { text.contains(it) })say(result,turn)
            checkTurn(turn);lastReply=result;if(!monologueTurn)memory.remember(text,result,compactOnOverflow=settings.getBoolean("reuse_context",false))
        } finally { if(turn==epoch.get()) { busy=false;resumeAfterOutput(turn) } }
    }
    private fun say(text:String,turn:Long,keepBusy:Boolean=false,log:Boolean=true,onFirstAudio:()->Unit={},onSynthesis:(Long)->Unit={},onPreparation:(String)->Unit={},onRtf:(Long,Int)->Unit={_,_->}) {
        checkTurn(turn); check(connected) { "Core2を接続してください" }; busy=true
        val prepareStarted=SystemClock.elapsedRealtime()
        pauseForOutput(turn)
        val capturePausedAt=SystemClock.elapsedRealtime()
        if(log)ChatLog.history.add("assistant",text)
        try {
            StageWait.await(ttsReady,"音声エンジンの準備",15000,{checkTurn(turn)})
            val t=tts ?: error("音声エンジン未準備")
            val voice=selectedOfflineVoice ?: run {
                val installed=t.voices.orEmpty().filter { it.locale.language==Locale.JAPANESE.language && !it.isNetworkConnectionRequired && !(it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) ?: false) }
                val preferred=t.defaultVoice
                val chosen=installed.firstOrNull { it.name==preferred?.name }
                ?: installed.sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.latency }.thenBy { it.name }).firstOrNull()
                ?: error("オフライン日本語音声をAndroidの読み上げ設定でダウンロードしてください")
                selectedOfflineVoice=chosen
                chosen
            }
            check(t.setVoice(voice)==TextToSpeech.SUCCESS)
            check(t.setSpeechRate(settings.getFloat("tts_rate",1.3f).coerceIn(.6f,2f))==TextToSpeech.SUCCESS)
            check(t.setPitch(settings.getFloat("tts_pitch",1.5f).coerceIn(.7f,2f))==TextToSpeech.SUCCESS)
            val voiceReadyAt=SystemClock.elapsedRealtime()
            report("スタックチャンが話しています")
            // Sentence chunks bound WAV memory and make weather announcements start earlier.
            require(text.length<=20000) { "読み上げが長すぎます。広域の地点数を減らしてください" }
            val chunks=text.split(Regex("(?<=[。！？])")).filter { it.isNotBlank() }.flatMap { it.chunked(200) }
            for((chunkIndex,chunk) in chunks.withIndex()) {
                val position="読み上げ区間 ${chunkIndex+1}/${chunks.size}"
                checkTurn(turn)
                val id="${turn}-${System.nanoTime()}"; val done=CompletableFuture<Unit>(); utterances[id]=done
                val file=File(cacheDir,"$id.wav")
                try {
                    // Start Core2 preparation while TTS runs; retain the full settling interval.
                    val stateStarted=SystemClock.elapsedRealtime()
                    state("speaking")
                    val stateSentAt=SystemClock.elapsedRealtime()
                    val synthesisStarted=SystemClock.elapsedRealtime()
                    check(t.synthesizeToFile(chunk,Bundle(),file,id)==TextToSpeech.SUCCESS)
                    StageWait.await(done,"スマホの音声合成（$position）",30000,{checkTurn(turn)})
                    checkTurn(turn)
                    val synthesisElapsed=SystemClock.elapsedRealtime()-synthesisStarted
                    onSynthesis(synthesisElapsed)
                    check(file.length() in 1..(8*1024*1024))
                    val pcm=AudioData.wavToPcm16k(file.readBytes())
                    val convertedAt=SystemClock.elapsedRealtime()
                    onRtf(synthesisElapsed,pcm.size)
                    // Synthesis and conversion overlap the existing 80 ms settling interval.
                    val settle=(80L-(convertedAt-stateSentAt)).coerceAtLeast(0L)
                    if(settle>0)Thread.sleep(settle)
                    checkTurn(turn)
                    if(chunkIndex==0)onPreparation("送信前内訳：入力休止 ${capturePausedAt-prepareStarted}ms / 音声設定 ${voiceReadyAt-capturePausedAt}ms / 状態送信 ${stateSentAt-stateStarted}ms / PCM変換 ${convertedAt-synthesisStarted-synthesisElapsed}ms / 準備待ち ${SystemClock.elapsedRealtime()-convertedAt}ms（合成・変換と80ms準備は並行）")
                    if(phoneOutput()) { playPhone(pcm,turn,onFirstAudio);state("idle") } else {
                    var offset=0
                    val sendStarted=SystemClock.elapsedRealtime()
                    var lastSent=0L;var maxGap=0L;var maxQueued=0L
                    val sendClock=if(settings.getString("transport","wifi")=="usb")PcmSendClock(System.nanoTime()) else null
                    while(offset<pcm.size) {
                        checkTurn(turn); check(connected)
                        check((transport?.queuedBytes() ?: Long.MAX_VALUE)<128*1024) { "音声通信が遅れています" }
                        val size=minOf(1280,pcm.size-offset)
                        check(transport?.sendAudio(pcm.copyOfRange(offset,offset+size))==true)
                        val sentAt=SystemClock.elapsedRealtime()
                        if(lastSent!=0L)maxGap=maxOf(maxGap,sentAt-lastSent)
                        lastSent=sentAt;maxQueued=maxOf(maxQueued,transport?.queuedBytes() ?: 0)
                        onFirstAudio();offset+=size
                        if(sendClock==null)Thread.sleep((size*1000L/32000).coerceAtLeast(1)) else {
                            val waitNanos=sendClock.afterFrame(size,System.nanoTime())
                            val deadline=System.nanoTime()+waitNanos
                            while(true) {
                                checkTurn(turn)
                                val remaining=deadline-System.nanoTime()
                                if(remaining<=0)break
                                TimeUnit.NANOSECONDS.sleep(minOf(remaining,10_000_000L))
                            }
                        }
                    }
                    val sendElapsed=SystemClock.elapsedRealtime()-sendStarted
                    val drained=CompletableFuture<Unit>(); playbackId=id; playback=drained
                    state("idle"); send("compat.drain","requestId" to id)
                    try {
                        StageWait.await(drained,"Core2の再生完了確認（$position）",12000,{checkTurn(turn)},retry={
                            check(connected) { "Core2との接続が切れました" }
                            // Reassert end-of-stream, then query the same ID. Never replay PCM.
                            send("state","value" to "idle")
                            send("compat.drain","requestId" to id)
                        })
                    } finally { playback=null }
                    checkTurn(turn)
                    playbackAudit.expect(id,PlaybackAudit.Sent(turn,pcm.size,sendElapsed,maxGap,maxQueued))
                    audioDiagnostics=playbackAudit.summary()
                    send("audio.playback_diag","requestId" to id)
                    }
                } finally { utterances.remove(id); file.delete() }
            }
            checkTurn(turn)
            if(!keepBusy)lastReply=text
            if(!keepBusy)Thread.sleep(250);checkTurn(turn)
            report(if(keepBusy) "返答を続けています（聞き取り休止）" else if(listening) "マイク準備中…" else if(monologue) "独り言モード（40～70秒間隔）" else "会話停止（接続維持）")
        } catch(e:Exception) {
            if(turn==epoch.get()) {
                tts?.stop();transport?.discardAudio();send("compat.stop");send("compat.mouth","envelope" to 0)
                state("idle")
            }
            throw e
        } finally { if(turn==epoch.get() && !keepBusy) { busy=false;resumeAfterOutput(turn) } }
    }
    private fun phoneOutput()=settings.getString("speaker","core2")=="phone"
    private fun phoneVolume():Int {
        val a=getSystemService(AudioManager::class.java)
        return kotlin.math.round(a.getStreamVolume(AudioManager.STREAM_MUSIC)*100f/a.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)).toInt()
    }
    private fun refreshPhoneVolume() { core2Volume=phoneVolume();volumeStatus="スマホのメディア音量：${core2Volume}%" }
    private fun setPhoneVolume(percent:Int):Int {
        val a=getSystemService(AudioManager::class.java)
        val max=a.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        a.setStreamVolume(AudioManager.STREAM_MUSIC,kotlin.math.round(percent.coerceIn(0,100)*max/100f).toInt(),0)
        refreshPhoneVolume();return core2Volume
    }
    private fun playPhone(pcm:ByteArray,turn:Long,onFirstAudio:()->Unit) {
        val mouthTimeline=MouthTimeline.prepare(pcm)
        checkTurn(turn)
        val manager=getSystemService(AudioManager::class.java)
        val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        val focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes).setOnAudioFocusChangeListener({ change ->
                if(change<0 && turn==epoch.get())cancel(true)
            },Handler(Looper.getMainLooper())).build()
        check(manager.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "スマホの音声出力を使用できません" }
        try {
        val track=AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(16000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(6400,AudioTrack.getMinBufferSize(16000,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT)))
            .build()
        try {
            check(track.state==AudioTrack.STATE_INITIALIZED) { "スマホの音声出力を開始できません" }
            val speaker=getSystemService(AudioManager::class.java).getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type==AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            check(speaker!=null && track.setPreferredDevice(speaker)) { "スマホ本体のスピーカーを選択できません" }
            phonePlayback=track;checkTurn(turn);track.play()
            var offset=0;val deadline=SystemClock.elapsedRealtime()+pcm.size*1000L/32000+15000
            var nextMouthAt=0L
            fun updateMouth() {
                val now=SystemClock.elapsedRealtime()
                if(now<nextMouthAt)return
                nextMouthAt=now+50
                // Drop animation updates under congestion rather than delaying audio.
                if((transport?.queuedBytes() ?: Long.MAX_VALUE)<2048) {
                    val played=track.playbackHeadPosition.toLong() and 0xffffffffL
                    send("compat.mouth","envelope" to mouthTimeline.at(played))
                }
            }
            while(offset<pcm.size) {
                checkTurn(turn);check(SystemClock.elapsedRealtime()<deadline) { "スマホ音声の再生がタイムアウトしました" }
                updateMouth()
                val n=track.write(pcm,offset,minOf(1280,pcm.size-offset),AudioTrack.WRITE_NON_BLOCKING)
                check(n>=0) { "スマホ音声の再生に失敗しました：$n" }
                if(n>0) { if(offset==0)onFirstAudio();offset+=n } else Thread.sleep(10)
            }
            while((track.playbackHeadPosition.toLong() and 0xffffffffL)<pcm.size/2) {
                checkTurn(turn);check(SystemClock.elapsedRealtime()<deadline) { "スマホ音声の再生完了を確認できません" };updateMouth();Thread.sleep(10)
            }
        } finally { if(turn==epoch.get())send("compat.mouth","envelope" to 0);phonePlayback=null;runCatching { track.stop() };track.release() }
        } finally { manager.abandonAudioFocusRequest(focus) }
    }
    private fun startPhoneMic() {
        if(settings.getString("mic","core2")!="phone" || !listening) { phoneMicStopToken.incrementAndGet();recorder?.let { runCatching { it.stop() } }; return }
        if(!phoneMicRunning.compareAndSet(false,true))return
        val micToken=phoneMicStopToken.get()
        Thread({
            var r:AudioRecord?=null
            try {
                if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    report("マイクの権限が必要です"); return@Thread
                }
                r=AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(6400,AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)))
                check(r.state==AudioRecord.STATE_INITIALIZED); recorder=r; r.startRecording()
                val shorts=ShortArray(640)
                while(alive && listening && micToken==phoneMicStopToken.get() && settings.getString("mic","core2")=="phone") {
                    val generation=epoch.get();val audioGeneration=audioEpoch.get();val allowed=acceptsAudio()
                    val n=r.read(shorts,0,shorts.size); if(n<0)break
                    if(n>0 && allowed && acceptsAudio() && generation==epoch.get() && audioGeneration==audioEpoch.get()) {
                        val frame=Frame(generation,FloatArray(n){shorts[it]/32768f},false,audioGeneration)
                        if(!input.offer(frame)) { input.clear();input.offer(frame.copy(reset=true)) }
                    }
                }
            } catch(e:Exception) { report("スマホマイク: ${e.message}") }
            finally {
                runCatching { r?.stop() };r?.release();recorder=null;phoneMicRunning.set(false)
                // A new start can arrive before the old blocking read has returned.
                if(alive && listening && micToken!=phoneMicStopToken.get() && settings.getString("mic","core2")=="phone")startPhoneMic()
            }
        },"phone-mic").start()
    }
    override fun onDestroy() {
        core2Volume=-1;volumeStatus="Core2未接続"
        requestRelease(); alive=false; connected=false; linkEpoch.incrementAndGet();transport?.close(); recorder?.let { runCatching { it.stop() } }
        tts?.shutdown(); power.release(); wifi.release(); running=false; ready=false
        http.dispatcher.executorService.shutdown(); status="終了しました"
        super.onDestroy()
    }
}


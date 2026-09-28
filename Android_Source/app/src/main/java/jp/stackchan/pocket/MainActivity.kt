package jp.stackchan.pocket

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.text.TextUtils
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity:Activity() {
    private val palette by lazy { AppPalettes.selected(settings) }
    private val bg get()=palette.bg
    private val surface get()=palette.surface
    private val ink get()=palette.ink
    private val muted get()=palette.muted
    private val mint get()=palette.accent
    private val lilac get()=if(palette.id=="classic")Color.rgb(207,166,232) else palette.accent
    private val blue get()=palette.control
    private val chatBackground get()=if(palette.id=="classic")Color.rgb(17,22,29) else palette.bg
    private val userBackground get()=palette.userBg
    private val userInk get()=palette.ink
    private val userCaption get()=palette.userCaption
    private val robotBackground get()=if(palette.id=="classic")Color.rgb(221,232,224) else palette.accent
    private val robotInk get()=if(palette.id=="classic")Color.rgb(29,48,39) else palette.bg
    private val robotCaption get()=robotInk
    private lateinit var modelStatus:TextView
    private lateinit var modelProgress:ProgressBar
    private lateinit var loadButton:Button
    private lateinit var unloadButton:Button
    private val importButtons=mutableListOf<Button>()
    private var importsOpen=false
    private lateinit var root:LinearLayout
    private lateinit var status:TextView
    private lateinit var log:LinearLayout
    private lateinit var logScroll:ScrollView
    private lateinit var latest:Button
    private lateinit var weatherStatus:TextView
    private lateinit var volumeLabel:TextView
    private lateinit var volumeSlider:SeekBar
    private var volumeDragging=false
    private lateinit var input:EditText
    private lateinit var back:Button
    private lateinit var toolsButton:Button
    private lateinit var settingsButton:Button
    private lateinit var toolsPage:View
    private val backCallback=android.window.OnBackInvokedCallback { showPage(if(selected==1)3 else 0) }
    private lateinit var settingsPage:View
    private lateinit var chatPage:View
    private lateinit var weatherPage:View
    private var selected=0
    private var importName=""
    private var seenRevision=-1L
    private val handler=Handler(Looper.getMainLooper())
    private val settings by lazy { getSharedPreferences("settings",MODE_PRIVATE) }
    private val refresh=object:Runnable { override fun run(){
        status.text=RobotService.status;weatherStatus.text=Weather.lastStatus;
        if(!volumeDragging) {
            volumeLabel.text=RobotService.volumeStatus
            volumeSlider.isEnabled=RobotService.core2Volume>=0
            if(RobotService.core2Volume>=0)volumeSlider.progress=RobotService.core2Volume
        }
        updateLog();updateModelState();handler.postDelayed(this,400)
    } }
    private fun dp(n:Int)=(resources.displayMetrics.density*n+.5f).toInt()
    private fun rounded(color:Int,radius:Int=0)=GradientDrawable().apply { setColor(color);cornerRadius=0f;setStroke(dp(1),blue) }
    private fun terminalFrame(color:Int=surface,rule:Int=blue)=TerminalFrame(color,rule,resources.displayMetrics.density)
    private fun control(active:Boolean)=android.graphics.drawable.RippleDrawable(
        android.content.res.ColorStateList.valueOf(Color.argb(45,Color.red(mint),Color.green(mint),Color.blue(mint))),
        rounded(surface).apply { setStroke(dp(1),if(active)mint else blue) },null)
    private fun column()=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
    private fun text(value:String,size:Float=16f,color:Int=ink)=TextView(this).apply {
        text=value;textSize=size;setTextColor(color);typeface=Typeface.MONOSPACE;setLineSpacing(dp(3).toFloat(),1f)
    }
    private fun label(parent:LinearLayout,value:String,size:Float=16f,color:Int=ink)=text(value,size,color).also {
        it.setPadding(0,dp(8),0,dp(8));parent.addView(it)
    }
    private fun action(title:String,primary:Boolean=false,run:()->Unit)=Button(this).apply {
        text="[$title]";contentDescription=title;textSize=13f;isAllCaps=false;minHeight=dp(48);minimumWidth=0
        typeface=Typeface.MONOSPACE;stateListAnimator=null
        setTextColor(if(primary)mint else ink);background=control(primary)
        setPadding(dp(8),dp(8),dp(8),dp(8));setOnClickListener { run() }
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(6);bottomMargin=dp(6) }
    }
    private fun button(parent:LinearLayout,title:String,primary:Boolean=false,run:()->Unit)=action(title,primary,run).also { parent.addView(it) }
    private fun row(parent:LinearLayout,vararg buttons:Button) {
        val row=LinearLayout(this)
        buttons.forEachIndexed { i,b -> row.addView(b,LinearLayout.LayoutParams(0,-2,1f).apply { if(i>0)leftMargin=dp(8);topMargin=dp(4);bottomMargin=dp(4) }) }
        parent.addView(row)
    }
    private fun field(value:String,hintText:String)=EditText(this).apply {
        setText(value);hint=hintText;setTextColor(ink);setHintTextColor(muted);textSize=16f
        background=rounded(surface);setPadding(dp(12),dp(12),dp(12),dp(12));setSingleLine()
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(8);topMargin=dp(6) }
    }
    private fun section(parent:LinearLayout,title:String):LinearLayout {
        val card=column().apply { background=terminalFrame();setPadding(dp(16),dp(10),dp(16),dp(14)) }
        parent.addView(card,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(14) })
        label(card,"┤ $title ├",15f,lilac).setTypeface(Typeface.MONOSPACE,Typeface.BOLD);return card
    }
    private fun scroll(content:View)=ScrollView(this).apply { isFillViewport=true;addView(content);clipToPadding=false }
    private fun command(action:String,value:String?=null) {
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO,Manifest.permission.POST_NOTIFICATIONS),1)
            Toast.makeText(this,"マイクの許可後、もう一度押してください",Toast.LENGTH_LONG).show();return
        }
        startForegroundService(Intent(this,RobotService::class.java).setAction(action).putExtra("text",value))
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        setTheme(palette.style)
        super.onCreate(savedInstanceState)
        importsOpen=savedInstanceState?.getBoolean("imports",false) ?: false
        selected=savedInstanceState?.getInt("tab",0) ?: 0;importName=savedInstanceState?.getString("import","") ?: ""
        window.setDecorFitsSystemWindows(false)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        window.statusBarColor=bg;window.navigationBarColor=bg
        window.insetsController?.setSystemBarsAppearance(0,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
        root=column().apply { setBackgroundColor(bg) };setContentView(root)
        root.setOnApplyWindowInsetsListener { view,insets ->
            val safe=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val keyboard=insets.getInsets(WindowInsets.Type.ime())
            val extra=(20*1.5f*resources.displayMetrics.scaledDensity).toInt()
            view.setPadding(safe.left+dp(16),safe.top+extra,safe.right+dp(16),maxOf(safe.bottom,keyboard.bottom)+dp(8))
            insets
        };root.requestApplyInsets()
        val header=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        header.addView(text("スタックチャン",18f).apply { setTypeface(Typeface.MONOSPACE,Typeface.BOLD) },LinearLayout.LayoutParams(0,-2,1f))
        header.addView(text("[pocket]",12f,lilac));root.addView(header)
        val statusPanel=column().apply { background=terminalFrame(rule=mint);setPadding(dp(12),dp(8),dp(12),dp(8)) }
        root.addView(statusPanel,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(12);bottomMargin=dp(12) })
        statusPanel.addView(text("┤ 現在の状態 ├  タップで詳細",11f,lilac))
        status=text(RobotService.status,18f,mint).apply {
            setTypeface(Typeface.MONOSPACE,Typeface.BOLD)
            maxLines=2;ellipsize=TextUtils.TruncateAt.END;minHeight=dp(48);gravity=Gravity.CENTER_VERTICAL;setPadding(0,dp(4),0,0)
            contentDescription="動作状態。タップして詳細表示"
            setOnClickListener { AlertDialog.Builder(this@MainActivity).setTitle("動作状況").setMessage(RobotService.status+"\n\n"+Weather.lastStatus).setPositiveButton("閉じる",null).show() }
        };statusPanel.addView(status)
        statusPanel.setOnClickListener { status.performClick() }
        val toolbar=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        back=action("戻る"){showPage(if(selected==1)3 else 0)}.apply { textSize=12f;contentDescription="前の画面へ戻る" }
        toolbar.addView(back,LinearLayout.LayoutParams(dp(60),dp(48)).apply { rightMargin=dp(6) })
        toolsButton=action("天気・他"){showPage(3)}
        settingsButton=action("設定"){showPage(2)}
        val reconnect=action("再接続"){command("connect")}.apply { contentDescription="選択した通信方式でCore2に再接続" }
        listOf(toolsButton,settingsButton,reconnect).forEachIndexed { i,button ->
            button.textSize=13f
            toolbar.addView(button,LinearLayout.LayoutParams(0,dp(48),1f).apply { if(i>0)leftMargin=dp(6) })
        }
        root.addView(toolbar,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(8) })
        val pages=FrameLayout(this);root.addView(pages,LinearLayout.LayoutParams(-1,0,1f))
        chatPage=buildChat(savedInstanceState?.getString("draft","") ?: "")
        weatherPage=buildWeather();settingsPage=buildSettings();toolsPage=buildTools()
        pages.addView(chatPage);pages.addView(weatherPage);pages.addView(settingsPage);pages.addView(toolsPage)
        showPage(selected)
    }
    private fun buildTools():View {
        val content=column();val menu=section(content,"機能を選択")
        button(menu,"天気予報",true){showPage(1)}
        label(menu,"今日・明日の読み上げ、地点の選択、予報の保存",13f,muted)
        val newsCard=section(content,"ニュース")
        val categories=NewsOptions.categories.keys.toList()
        select(newsCard,NewsOptions.categories.values.toTypedArray(),categories.indexOf(settings.getString("news_category","top-picks")).coerceAtLeast(0),"ニュースカテゴリー") { i -> settings.edit().putString("news_category",categories[i]).apply() }
        select(newsCard,(1..10).map { "${it}件" }.toTypedArray(),settings.getInt("news_count",3).coerceIn(1,10)-1,"ニュース件数") { i -> settings.edit().putInt("news_count",i+1).apply() }
        button(newsCard,"ニュースを読み上げ",true){command("news")}
        label(newsCard,"Yahoo!ニュースの見出し。指定件数に満たない場合は取得分のみ。オフラインでは同じ設定の6時間以内の保存分を日時付きで案内します。",13f,muted)
        button(menu,"声でできる操作") {
            AlertDialog.Builder(this).setTitle("声でできる操作").setMessage("今何時／今日は何日／令和何年\n右・左・上・下を向いて／正面に戻って\nうなずいて／首をかしげて／踊って\n音量を下げて／音量を50パーセントにして\n電池はあとどのくらい\n独り言を始めて／独り言をやめて\n天気の場所を京都市にして\nもう一度言って／最後の部分だけ\n3分たったら教えて／タイマーを取り消して\n\nタイマーは1件・最大60分。会話や通信復帰を待って通知します。アプリの処理終了で解除されます。首かしげは2軸のため斜め向きです。")
                .setPositiveButton("閉じる",null).show()
        }
        return scroll(content)
    }
    private fun buildChat(draft:String):View {
        val page=column()
        log=column().apply { setPadding(dp(8),dp(8),dp(8),dp(12)) };logScroll=scroll(log).apply {
            setBackgroundColor(chatBackground)
        }
        page.addView(logScroll,LinearLayout.LayoutParams(-1,0,1f))
        latest=action("新しい発言 ↓"){logScroll.post{logScroll.fullScroll(View.FOCUS_DOWN)};latest.visibility=View.GONE}
        latest.visibility=View.GONE;page.addView(latest)
        logScroll.setOnScrollChangeListener { _,_,_,_,_->if(atBottom())latest.visibility=View.GONE }
        val compose=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        input=field(draft,"> メッセージを入力").apply { background=rounded(blue).apply { setStroke(dp(1),muted) };setSingleLine(false);maxLines=3;imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEND }
        compose.addView(input,LinearLayout.LayoutParams(0,-2,1f).apply { rightMargin=dp(8) })
        compose.addView(action("送信",true){sendText()},LinearLayout.LayoutParams(dp(76),dp(48)))
        input.setOnEditorActionListener { _,id,_->if(id==android.view.inputmethod.EditorInfo.IME_ACTION_SEND){sendText();true}else false }
        page.addView(compose)
        row(page,action("会話開始",true){command("listen")},action("停止"){if(RobotService.running)command("stop")})
        return page
    }
    private fun sendText() {
        val value=input.text.toString().trim();if(value.isEmpty())return
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)input.setText("")
        command("text",value)
    }
    private fun atBottom()=!logScroll.canScrollVertically(1)
    private fun updateLog() {
        if(selected!=0)return
        val snapshot=ChatLog.history.snapshot();if(snapshot.revision==seenRevision)return
        val follow=seenRevision<0 || atBottom();val oldY=logScroll.scrollY;seenRevision=snapshot.revision;log.removeAllViews()
        if(snapshot.entries.isEmpty()) {
            label(log,"会話ログはここに表示されます",16f,muted)
            label(log,"声でも、文字でも。\n準備は「設定」から行えます。\n画面を消しても会話を続けられます。",16f,muted)
        }
        val time=SimpleDateFormat("HH:mm",Locale.JAPAN)
        snapshot.entries.forEach { entry ->
            val user=entry.speaker=="user"
            val outer=LinearLayout(this).apply { gravity=if(user)Gravity.END else Gravity.START;setPadding(if(user)dp(30) else 0,dp(5),if(user)0 else dp(30),dp(5)) }
            val bubble=column().apply {
                background=terminalFrame(if(user)userBackground else robotBackground,if(user)userCaption else robotCaption)
                setPadding(dp(12),dp(4),dp(12),dp(8))
            }
            label(bubble,(if(user)"あなた >" else "スタックチャン >")+"  "+time.format(Date(entry.time)),12f,if(user)userCaption else robotCaption)
            label(bubble,if(user)entry.text else entry.text.replace(Regex("[\\r\\n]+"),""),16f,if(user)userInk else robotInk).apply {
                typeface=Typeface.MONOSPACE
                if(user)setTextIsSelectable(true) else setOnClickListener { bubble.performClick() }
            }
            if(!user)bubble.setOnClickListener {
                val form=column().apply { setPadding(dp(16),dp(12),dp(16),dp(12));setBackgroundColor(bg) }
                label(form,"読み上げへ渡した文章の区切り",15f,mint)
                label(form,entry.parts.mapIndexed { i,part -> "${i+1}. $part" }.joinToString("\n\n"),14f,ink).setTextIsSelectable(true)
                val dialog=AlertDialog.Builder(this).setView(scroll(form)).create()
                button(form,"閉じる"){dialog.dismiss()};dialog.show()
            }
            outer.addView(bubble,LinearLayout.LayoutParams(-2,-2));log.addView(outer)
        }
        logScroll.post{if(follow)logScroll.fullScroll(View.FOCUS_DOWN) else logScroll.scrollTo(0,oldY)}
        latest.visibility=if(follow || selected!=0)View.GONE else View.VISIBLE
    }
    private fun reloadWeather() {
        val parent=weatherPage.parent as ViewGroup;val index=parent.indexOfChild(weatherPage)
        parent.removeView(weatherPage);weatherPage=buildWeather();parent.addView(weatherPage,index);showPage(1)
    }
    private fun buildWeather():View {
        val places=WeatherPlaces(this);val all=places.all()
        val content=column();val card=section(content,"今日・明日の天気")
        select(card,arrayOf("1地点を読み上げ","広域：選んだ地点を順に読み上げ"),if(settings.getBoolean("weather_wide",false))1 else 0,"読み上げ範囲") { i -> settings.edit().putBoolean("weather_wide",i==1).apply() }
        select(card,all.map { it.name }.toTypedArray(),all.indexOfFirst { it.id==settings.getString("region","osaka") }.coerceAtLeast(0),"天気予報地点") { i ->
            settings.edit().putString("region",all[i].id).apply()
            if(RobotService.running)command("sync")
        }
        button(card,"広域の地点を選択 ▼") {
            val chosen=places.broad().toMutableSet()
            val form=column().apply { setPadding(dp(16),dp(12),dp(16),dp(12));setBackgroundColor(bg) }
            label(form,"広域で読む地点（表示順）",16f,mint)
            val dialog=AlertDialog.Builder(this).setView(scroll(form)).create()
            all.forEach { place ->
                val option=button(form,(if(place.id in chosen)"■ " else "□ ")+place.name) {}
                option.setOnClickListener {
                    if(!chosen.remove(place.id))chosen.add(place.id)
                    option.text="["+(if(place.id in chosen)"■ " else "□ ")+place.name+"]"
                }
            }
            button(form,"保存",true) {
                if(chosen.isEmpty())Toast.makeText(this,"1地点以上選んでください",Toast.LENGTH_SHORT).show()
                else { settings.edit().putStringSet("weather_broad",chosen.toSet()).apply();dialog.dismiss();reloadWeather() }
            }
            button(form,"戻る"){dialog.dismiss()};dialog.show()
        }
        label(card,"広域："+all.filter { it.id in places.broad() }.joinToString(" → ") { it.name },13f,muted)
        button(card,"天気を読み上げる",true){command("weather")}
        val registration=section(content,"地点を登録")
        label(registration,"国内の地名を検索して候補を確認・登録。最大20件。見つからない場合は市名の一部やローマ字で検索してください。",13f,muted)
        val query=field("","例：札幌、横浜、Sapporo");registration.addView(query)
        val result=label(registration,"",13f,muted)
        val results=column();registration.addView(results)
        val search=button(registration,"地名を検索 ▼") {}
        search.setOnClickListener {
            search.isEnabled=false;result.text="検索中…";results.removeAllViews()
            val value=query.text.toString()
            Thread({
                val found=runCatching { places.search(value) }
                runOnUiThread {
                    if(isFinishing || isDestroyed)return@runOnUiThread
                    search.isEnabled=true
                    found.fold(onSuccess={ candidates ->
                        result.text=if(candidates.isEmpty())"候補がありません。地名を変えてください。" else "登録する候補を選んでください。"
                        candidates.forEach { place ->
                            button(results,"${place.name}（${place.latitude}, ${place.longitude}）を登録") {
                                runCatching { places.add(place) }.onSuccess { reloadWeather() }.onFailure { result.text=it.message }
                            }
                        }
                    },onFailure={ result.text="検索できません：${TaskFailure.message(it,false)}" })
                }
            },"weather-search").start()
        }
        all.filter { it.id.startsWith("geo_") }.forEach { place ->
            button(registration,"${place.name} を登録から削除") {
                val form=column().apply { setPadding(dp(16),dp(12),dp(16),dp(12));setBackgroundColor(bg) }
                label(form,"${place.name} を削除しますか？",16f)
                val dialog=AlertDialog.Builder(this).setView(form).create()
                button(form,"削除する"){places.remove(place.id);dialog.dismiss();reloadWeather()}
                button(form,"戻る"){dialog.dismiss()};dialog.show()
            }
        }
        val save=section(content,"オフラインの準備")
        label(save,"外出前に登録地点の予報を保存できます。Core2未接続でも使えます。",14f,muted)
        button(save,"登録地点の天気を保存"){
            val appContext=applicationContext
            Thread({runCatching{Weather(appContext).prefetch()}.onFailure{Weather.lastStatus="天気保存失敗：${TaskFailure.message(it,false)}"}},"weather-save").start()
        }
        weatherStatus=label(save,Weather.lastStatus,14f)
        label(content,"オフライン時は保存日時付きで案内。\n追加地点は地点予報です。都道府県全体の予報ではありません。\n本体の地点メニューは大阪府・東京都。追加地点・広域はここで設定し、本体Bボタン・音声から読み上げできます。\n出典：気象庁・Open-Meteo / GeoNames",12f,muted)
        return scroll(content)
    }
    private fun buildSettings():View {
        val content=column();val connection=section(content,"Core2への接続")
        select(connection,arrayOf("Wi-Fi接続","USBシリアル接続"),if(settings.getString("transport","wifi")=="usb")1 else 0,"通信方式"){i->
            settings.edit().putString("transport",if(i==0)"wifi" else "usb").apply()
            if(RobotService.running)command("disconnect")
        }
        label(connection,"本体のB長押し→通信方式も合わせてください。USBはPixelをホストにしてデータ対応ケーブルで接続し、使用許可を選びます。Wi-Fi：StackChan-Direct",14f,muted)
        button(connection,"Wi-Fi設定を開く"){startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))}
        val host=field(settings.getString("host","192.168.4.1")!!,"Core2のIPアドレス");connection.addView(host)
        button(connection,"接続 / 再接続",true){
            if(settings.getString("transport","wifi")=="usb"){command("connect");return@button}
            val value=host.text.toString().trim()
            if(!value.matches(Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")) || !value.split('.').all{it.toInt() in 0..255})Toast.makeText(this,"IPアドレスを確認してください",Toast.LENGTH_LONG).show()
            else{settings.edit().putString("host",value).apply();command("connect")}
        }
        val voice=section(content,"マイク・ウェイクワード")
        select(voice,arrayOf("Core2のマイク","スマホのマイク"),if(settings.getString("mic","core2")=="phone")1 else 0,"マイク"){i->
            val value=if(i==0)"core2" else "phone"
            if(settings.getString("mic","core2")!=value){settings.edit().putString("mic",value).apply();if(RobotService.running)command("sync")}
        }
        select(voice,arrayOf("一回：返答したら待機オフ","連続：考え中も聞き取り"),if(settings.getString("talk_mode","once")=="continuous")1 else 0,"会話モード"){i->
            settings.edit().putString("talk_mode",if(i==0)"once" else "continuous").apply()
            if(RobotService.running)command("stop")
        }
        label(voice,"話し終わりの無音で区切ります。連続では考え中も聞き取り、次の発言を2件まで順番に受け付けます。読み上げ中は聞き取りを休止。変更後は会話開始を押してください。",13f,muted)
        select(voice,arrayOf("Core2のスピーカー","スマホ本体のスピーカー"),if(settings.getString("speaker","core2")=="phone")1 else 0,"スピーカー"){i->
            settings.edit().putString("speaker",if(i==0)"core2" else "phone").apply()
            RobotService.core2Volume=-1;RobotService.volumeStatus="音量を取得してください"
            if(RobotService.running){command("stop");command("volume.get")}
        }
        val wake=field(settings.getString("wakeword","スタックチャン")!!,"ウェイクワード");voice.addView(wake)
        val enabled=Switch(this).apply{text="ウェイクワードを使う";setTextColor(ink);minHeight=dp(48);isChecked=settings.getBoolean("wake",false)};voice.addView(enabled)
        button(voice,"ウェイクワードを保存"){
            val value=wake.text.toString().trim()
            if(value.isNotEmpty()){settings.edit().putString("wakeword",value).putBoolean("wake",enabled.isChecked).apply();if(RobotService.running)command("sync");Toast.makeText(this,"保存しました",Toast.LENGTH_SHORT).show()}
        }
        val llm=section(content,"会話の返答")
        button(llm,"返答の長さ") {
            AlertDialog.Builder(this).setTitle("返答の長さ")
                .setSingleChoiceItems(arrayOf("短め：テンポ優先","標準：普段の会話","詳しく：説明向け"),settings.getInt("llm_reply",1).coerceIn(0,2)){dialog,index ->
                    settings.edit().putInt("llm_reply",index).apply();dialog.dismiss()
                    Toast.makeText(this,"次の返答から反映します",Toast.LENGTH_SHORT).show()
                }.setNegativeButton("戻る",null).show()
        }
        button(llm,"Core2音声転送の診断") {
            val form=column().apply { setPadding(dp(16),dp(12),dp(16),dp(12));setBackgroundColor(bg) }
            val snapshot=RobotService.audioDiagnostics
            label(form,snapshot,13f).setTextIsSelectable(true)
            val dialog=AlertDialog.Builder(this).setView(scroll(form)).create()
            button(form,"診断をコピー") {
                getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("音声転送診断",snapshot))
                Toast.makeText(this,"コピーしました",Toast.LENGTH_SHORT).show()
            }
            button(form,"閉じる"){dialog.dismiss()};dialog.show()
        }
        button(llm,"直近のLLM処理時間") {
            val snapshot="LLM処理時間（Android 0.3.47 移植検証版）\n"+RobotService.llmTiming+"\n\n"+RobotService.asrTiming+
                "\nASRは独立した直近値です。連続会話では上の返答と異なる発言の場合があります。\n会話本文は含みません。"
            val form=column().apply { setPadding(dp(16),dp(12),dp(16),dp(12));setBackgroundColor(bg) }
            label(form,snapshot,13f).setTextIsSelectable(true)
            val dialog=AlertDialog.Builder(this).setTitle("LLM処理時間").setView(scroll(form)).create()
            button(form,"計測結果をコピー") {
                getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("LLM処理時間",snapshot))
                Toast.makeText(this,"計測結果をコピーしました",Toast.LENGTH_SHORT).show()
            }
            button(form,"閉じる"){dialog.dismiss()}
            dialog.show()
        }
        val models=section(content,"LLMエンジンの選択")
        val reuse=Switch(this).apply { text="会話の処理結果を再利用（上限時に履歴をまとめて整理）";setTextColor(ink);minHeight=dp(48);isChecked=settings.getBoolean("reuse_context",false) }
        reuse.setOnCheckedChangeListener { _,checked -> settings.edit().putBoolean("reuse_context",checked).apply() };models.addView(reuse)
        label(models,"次の会話から反映。完了済み履歴が一致する場合のみ再利用。停止・履歴の変更時は作り直します。",12f,muted)
        val appearance=section(content,"UIカラー")
        select(appearance,AppPalettes.all.map { it.label }.toTypedArray(),AppPalettes.all.indexOf(palette),"UIカラー"){ i ->
            if(AppPalettes.all[i].id!=palette.id) {
                settings.edit().putString("ui_palette",AppPalettes.all[i].id).apply();recreate()
            }
        }
        select(models,arrayOf("LiteRT-LM (.litertlm)","llama.cpp (.gguf)"),if(settings.getString("llm_engine","litert")=="gguf")1 else 0,"LLMエンジン（LiteRT / llama.cpp）"){ i ->
            settings.edit().putString("llm_engine",if(i==1)"gguf" else "litert").apply();updateModelState()
        }
        select(models,arrayOf("LiteRT CPU","LiteRT GPU"),if(settings.getString("llm_backend","cpu")=="gpu")1 else 0,"LiteRT選択時の処理（CPU / GPU）"){i->
            settings.edit().putString("llm_backend",if(i==1)"gpu" else "cpu").apply();updateModelState()
        }
        select(models,arrayOf("GGUF CPU","GGUF Vulkan GPU"),if(settings.getString("gguf_backend","cpu")=="gpu")1 else 0,"llama.cpp選択時の処理（CPU / Vulkan GPU）"){i->
            settings.edit().putString("gguf_backend",if(i==1)"gpu" else "cpu").apply();updateModelState()
        }
        select(models,(1..8).map { "CPUスレッド $it" }.toTypedArray(),settings.getInt("gguf_threads",4).coerceIn(1,8)-1,"llama.cppのCPUスレッド数"){i->settings.edit().putInt("gguf_threads",i+1).apply()}
        val ggufReuse=Switch(this).apply {text="GGUFの一致した入力トークンを再利用";setTextColor(ink);isChecked=settings.getBoolean("gguf_reuse",true)}
        ggufReuse.setOnCheckedChangeListener{_,value->settings.edit().putBoolean("gguf_reuse",value).apply()};models.addView(ggufReuse)
        label(models,"変更は解放→読み込み後に反映。保存は両形式、RAMに読み込むLLMは一つです。Vulkan非対応時は明示エラーとなります。GGUFは単一ファイルのテキスト会話用です。",12f,muted)
        modelStatus=label(models,"",13f)
        modelProgress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {max=100;progressTintList=android.content.res.ColorStateList.valueOf(mint)}
        models.addView(modelProgress)
        val early=Switch(this).apply { text="最初の読点から話す（比較用）";setTextColor(ink);minHeight=dp(48);isChecked=settings.getBoolean("early_clause",false) }
        early.setOnCheckedChangeListener { _,checked -> settings.edit().putBoolean("early_clause",checked).apply() }
        models.addView(early)
        label(models,"最初の文章は5文字以上の読点から開始。相づちだけ・引用や括弧の中の読点は除外します。次の返答から反映。短い区間は抑揚や間が変わる場合があります。",12f,muted)
        button(models,"聞き取りの区切り") {
            val values=floatArrayOf(.65f,1f,1.5f)
            val labels=arrayOf("短め：0.65秒","標準：1秒","長め：1.5秒（ゆっくり話すとき）")
            val current=settings.getFloat("vad_silence",1f).coerceIn(.65f,1.5f)
            AlertDialog.Builder(this).setTitle("発話が終わったと判断する無音時間")
                .setSingleChoiceItems(labels,values.indexOfFirst { it == current }.coerceAtLeast(0)){dialog,index->
                    settings.edit().putFloat("vad_silence",values[index]).apply();dialog.dismiss()
                    Toast.makeText(this,"次のモデル読み込みから反映します",Toast.LENGTH_LONG).show()
                }.setNegativeButton("戻る",null).show()
        }
        loadButton=button(models,"モデルを読み込む",true){command("load")}
        unloadButton=button(models,"モデルを解放 / 読み込み中止"){command("unload")}
        val imports=column().apply{visibility=if(importsOpen)View.VISIBLE else View.GONE}
        button(models,"モデルの取り込みを開く / 閉じる"){importsOpen=!importsOpen;imports.visibility=if(importsOpen)View.VISIBLE else View.GONE}
        models.addView(imports)
        label(imports,"モデルを解放してから取り込んでください。接続は維持します。コピー完了後に検証・保存を行います。",13f,muted)
        for((name,title) in listOf("sensevoice.onnx" to "音声認識 ONNX","tokens.txt" to "音声認識 tokens.txt","silero_vad.onnx" to "Silero VAD ONNX","model.litertlm" to "LLM .litertlm","model.gguf" to "LLM .gguf")) {
            importButtons+=button(imports,title){
                if(!ModelRuntime.available())Toast.makeText(this,"先にモデルを解放し、処理完了を待ってください",Toast.LENGTH_LONG).show()
                else{importName=name;startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),20)}
            }
        }
        val history=section(content,"会話ログ・記憶")
        label(history,"直近最大6往復を、合計1800文字の範囲で会話に使います。古い会話から外れます。アプリの処理終了で記憶は消えます。",13f,muted)
        button(history,"会話の記憶をリセット"){
            AlertDialog.Builder(this).setMessage("これまでの会話の記憶をリセットしますか？表示ログは残ります。").setNegativeButton("戻る",null)
                .setPositiveButton("リセット"){_,_->if(RobotService.running)command("forget")}.show()
        }
        button(history,"会話ログを消去"){
            AlertDialog.Builder(this).setMessage("画面の会話ログを消去しますか？").setNegativeButton("戻る",null)
                .setPositiveButton("消去"){_,_->ChatLog.history.clear();Toast.makeText(this,"会話ログを消去しました",Toast.LENGTH_SHORT).show()}.show()
        }
        val timer=section(content,"スマホのタイマー")
        label(timer,"声で「3分たったら教えて」「タイマーを取消」「タイマーの残り時間」。画面消灯・会話中・Core2切断中もスマホへ通知します。最初に下の2つの許可を確認してください。",13f,muted)
        button(timer,"アラームとリマインダーの許可") {
            startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,android.net.Uri.parse("package:$packageName")))
        }
        button(timer,"タイマーの通知・音・振動設定") {
            PhoneTimer.channel(this)
            if(!getSystemService(NotificationManager::class.java).areNotificationsEnabled()) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),31)
                startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,packageName))
            } else startActivity(Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,packageName).putExtra(android.provider.Settings.EXTRA_CHANNEL_ID,"timer_finished"))
        }
        button(timer,"10秒タイマーを試す") {
            runCatching { PhoneTimer.start(this,10) }.onSuccess { Toast.makeText(this,"10秒後に通知します（既存タイマーを置換）",Toast.LENGTH_LONG).show() }
                .onFailure { Toast.makeText(this,it.message,Toast.LENGTH_LONG).show() }
        }
        button(timer,"タイマーの残り時間・許可を確認") {
            val seconds=PhoneTimer.remaining(this)
            Toast.makeText(this,(PhoneTimer.problem(this) ?: "通知・アラーム許可済み")+"\n"+(seconds?.let { "残り${it}秒" } ?: "タイマー停止中"),Toast.LENGTH_LONG).show()
        }
        button(timer,"タイマーを取り消す") { PhoneTimer.cancel(this);Toast.makeText(this,"取り消しました",Toast.LENGTH_SHORT).show() }
        label(timer,"音・振動はAndroidの通知設定に従います。スマホ再起動・アプリの強制停止後はタイマーを設定し直してください。通常のアプリ処理終了ではタイマーを維持します。",12f,muted)
        val sound=section(content,"声・動作確認")
        volumeLabel=label(sound,RobotService.volumeStatus,14f,muted)
        volumeSlider=SeekBar(this).apply {
            max=100;progress=RobotService.core2Volume.coerceAtLeast(0);isEnabled=RobotService.core2Volume>=0
            minimumHeight=dp(48);contentDescription="選択したスピーカー音量、0から100パーセント"
            progressTintList=android.content.res.ColorStateList.valueOf(mint)
            thumbTintList=android.content.res.ColorStateList.valueOf(mint)
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(s:SeekBar){volumeDragging=true}
                override fun onProgressChanged(s:SeekBar,n:Int,user:Boolean){
                    if(user) { volumeLabel.text="音量：${n}%";if(!volumeDragging)command("volume.set",n.toString()) }
                }
                override fun onStopTrackingTouch(s:SeekBar){volumeDragging=false;command("volume.set",s.progress.toString())}
            })
        }
        val volumeTrack=LinearLayout(this)
        volumeTrack.addView(volumeSlider,LinearLayout.LayoutParams(0,dp(48),1f))
        volumeTrack.addView(View(this),LinearLayout.LayoutParams(0,dp(48),1f))
        sound.addView(volumeTrack)
        val volumeSteps=LinearLayout(this)
        volumeSteps.addView(action("－") { if(RobotService.core2Volume>=0)command("volume.set",(RobotService.core2Volume-10).coerceAtLeast(0).toString()) },LinearLayout.LayoutParams(dp(64),dp(48)))
        volumeSteps.addView(action("＋") { if(RobotService.core2Volume>=0)command("volume.set",(RobotService.core2Volume+10).coerceAtMost(100).toString()) },LinearLayout.LayoutParams(dp(64),dp(48)))
        sound.addView(volumeSteps)
        label(sound,"選択した出力先の音量です。スマホはメディア音量を変更します。Core2の操作音は本体の音量に従います。",13f,muted)
        button(sound,"選択した出力先の音量を取得"){command("volume.get")}
        button(sound,"声の速さ・高さ") {
            val form=column().apply { setPadding(dp(20),dp(8),dp(20),dp(8));setBackgroundColor(bg) }
            var rate=settings.getFloat("tts_rate",1.3f).coerceIn(.6f,2f)
            var pitch=settings.getFloat("tts_pitch",1.5f).coerceIn(.7f,2f)
            fun control(title:String,min:Float,initial:Float,standard:Float,changed:(Float)->Unit):()->Unit {
                val caption=label(form,"",14f,ink)
                fun display(value:Float) { caption.text=String.format(java.util.Locale.JAPAN,"%s：%.2f（標準 %.2f）",title,value,standard) }
                display(initial)
                val slider=SeekBar(this).apply {
                    max=kotlin.math.round((2f-min)/.05f).toInt();progress=kotlin.math.round((initial-min)/.05f).toInt().coerceIn(0,max)
                    minimumHeight=dp(48);contentDescription=title
                    progressTintList=android.content.res.ColorStateList.valueOf(mint)
                    thumbTintList=android.content.res.ColorStateList.valueOf(mint)
                    setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                        override fun onStartTrackingTouch(s:SeekBar) {}
                        override fun onStopTrackingTouch(s:SeekBar) {}
                        override fun onProgressChanged(s:SeekBar,n:Int,user:Boolean) { val value=min+n*.05f;display(value);changed(value) }
                    })
                }
                // The entire range is reachable without touching the screen's right half.
                val track=LinearLayout(this)
                track.addView(slider,LinearLayout.LayoutParams(0,dp(48),1f))
                track.addView(View(this),LinearLayout.LayoutParams(0,dp(48),1f))
                form.addView(track,LinearLayout.LayoutParams(-1,dp(48)))
                val steps=LinearLayout(this)
                val minus=action("－") { slider.progress=(slider.progress-1).coerceAtLeast(0) }.apply { contentDescription="$title を0.05下げる" }
                val plus=action("＋") { slider.progress=(slider.progress+1).coerceAtMost(slider.max) }.apply { contentDescription="$title を0.05上げる" }
                steps.addView(minus,LinearLayout.LayoutParams(dp(64),dp(48)))
                steps.addView(plus,LinearLayout.LayoutParams(dp(64),dp(48)).apply { leftMargin=dp(8) })
                form.addView(steps,LinearLayout.LayoutParams(-1,dp(48)))
                return { slider.progress=kotlin.math.round((standard-min)/.05f).toInt() }
            }
            val resetRate=control("速さ",.6f,rate,1.3f){rate=it}
            val resetPitch=control("高さ",.7f,pitch,1.5f){pitch=it}
            button(form,"標準に戻す（速さ1.30・高さ1.50）") { resetRate();resetPitch() }
            label(form,"速さ：0.60～2.00 / 高さ：0.70～2.00\nどちらも0.05刻み。声質そのものの変更ではありません。",12f,muted)
            fun saveVoice() { settings.edit().putFloat("tts_rate",rate).putFloat("tts_pitch",pitch).apply() }
            button(form,"保存して試聴") { saveVoice();command("tts") }
            val dialog=AlertDialog.Builder(this).setTitle("声の速さ・高さ").setView(scroll(form)).create()
            button(form,"保存して閉じる") { saveVoice();dialog.dismiss();Toast.makeText(this,"次の読み上げから反映します",Toast.LENGTH_SHORT).show() }
            button(form,"閉じる") { dialog.dismiss() }
            dialog.show()
        }
        label(sound,"速さ・高さはこのアプリの設定を使用します。",12f,muted)
        val engineOptions=column().apply { visibility=View.GONE }
        button(sound,"音声エンジンの補助設定を開く / 閉じる") { engineOptions.visibility=if(engineOptions.visibility==View.VISIBLE)View.GONE else View.VISIBLE }
        sound.addView(engineOptions)
        label(engineOptions,"日本語音声の追加・エンジンの選択用です。Android側の速さ・高さは会話に適用しません。エンジン変更後はアプリの処理を終了して再開してください。",12f,muted)
        button(engineOptions,"Androidの音声エンジン設定") { runCatching{startActivity(Intent("com.android.settings.TTS_SETTINGS"))}.onFailure{startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))} }
        button(sound,"読み上げテスト"){command("tts")}
        button(content,"アプリの処理を終了"){if(RobotService.running)stopService(Intent(this,RobotService::class.java))}
        label(content,"本体操作\n画面上側：選択した会話モードで開始／再タッチで停止\n左側：独り言切替 / 右側：電池案内\n中央下：サーボ切替\nA：ウェイクワード切替\nB短押し：天気 / 長押し：メニュー\nC：電池案内\n\n表示ログは直近100件。アプリのプロセス終了時に消えます。表示ログ消去と会話の記憶リセットは別です。\nPocket 0.3.50",13f,muted)
        return scroll(content)
    }
    private fun select(parent:LinearLayout,items:Array<String>,initial:Int,title:String="項目を選択",change:(Int)->Unit) {
        label(parent,title,14f,muted)
        var current=initial.coerceIn(items.indices)
        val selector=Button(this).apply {
            text="▼  ${items[current]}";isAllCaps=false;textSize=16f;typeface=Typeface.MONOSPACE
            gravity=Gravity.START or Gravity.CENTER_VERTICAL;minHeight=dp(56);minimumHeight=dp(56)
            setPadding(dp(14),dp(10),dp(14),dp(10));setTextColor(ink);stateListAnimator=null
            background=android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(75,Color.red(mint),Color.green(mint),Color.blue(mint))),
                GradientDrawable().apply { setColor(surface);setStroke(dp(1),mint) },null)
            contentDescription="$title：${items[current]}。押して選択を変更"
        }
        parent.addView(selector,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(4);bottomMargin=dp(6) })
        selector.setOnClickListener {
            val form=column().apply {setPadding(dp(16),dp(12),dp(16),dp(12));setBackgroundColor(bg)}
            label(form,title,18f,mint)
            val dialog=AlertDialog.Builder(this).setView(scroll(form)).create()
            items.forEachIndexed { index,value ->
                val option=action((if(index==current)"● " else "○ ")+value,index==current) {
                    if(index!=current){
                        current=index;selector.text="▼  ${items[current]}"
                        selector.contentDescription="$title：${items[current]}。押して選択を変更"
                        change(current)
                    }
                    dialog.dismiss()
                }.apply {gravity=Gravity.START or Gravity.CENTER_VERTICAL;textSize=16f;isSelected=index==current;contentDescription=value+if(index==current)"、選択中" else "、選択する"}
                form.addView(option)
            }
            button(form,"戻る"){dialog.dismiss()}
            dialog.show()
        }
    }
    private fun showPage(index:Int) {
        selected=index.coerceIn(0,3)
        chatPage.visibility=if(selected==0)View.VISIBLE else View.GONE
        weatherPage.visibility=if(selected==1)View.VISIBLE else View.GONE
        settingsPage.visibility=if(selected==2)View.VISIBLE else View.GONE
        toolsPage.visibility=if(selected==3)View.VISIBLE else View.GONE
        back.visibility=if(selected==0)View.GONE else View.VISIBLE
        listOf(toolsButton to (selected==1 || selected==3),settingsButton to (selected==2)).forEach { (button,active) ->
            button.background=control(active);button.setTextColor(if(active)mint else ink);button.isSelected=active
        }
        onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backCallback)
        if(selected!=0)onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,backCallback)
        if(selected!=0)(getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken,0)
        else updateLog()
    }
    override fun onSaveInstanceState(out:Bundle){out.putBoolean("imports",importsOpen);out.putInt("tab",selected);out.putString("import",importName);out.putString("draft",input.text.toString());super.onSaveInstanceState(out)}
    @Deprecated("Legacy activity result") override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=20 || resultCode!=RESULT_OK)return
        val uri=data?.data ?: return;val name=importName
        if(!ModelImporter.start(applicationContext,uri,name))Toast.makeText(this,"モデル使用中または取り込み中です。解放後に再試行してください",Toast.LENGTH_LONG).show()
    }
    private fun updateModelState() {
        if(!::modelStatus.isInitialized)return
        val m=ModelRuntime.state;val imp=ModelImporter.state
        val gguf=settings.getString("llm_engine","litert")=="gguf"
        val needed=listOf("sensevoice.onnx","tokens.txt","silero_vad.onnx",if(gguf)"model.gguf" else "model.litertlm")
        val missing=needed.filter { !File(filesDir,it).isFile || File(filesDir,it).length()==0L }
        val stored=ModelImporter.names.joinToString("\n") { name ->
            val f=File(filesDir,name)
            if(f.isFile && f.length()>0)"保存済み：${settings.getString("model_name_$name",name)} (${if(f.length()<1048576) "${f.length()/1024} KiB" else "${f.length()/1048576} MiB"})" else "未取り込み：$name"
        }
        val importText=if(imp.name.isNotEmpty()) {
            val pct=ImportProgress.percent(imp.copied,imp.total)
            "\n取り込み：${imp.name}\n${imp.phase} / コピー済み ${imp.copied/1024} KiB"+(if(pct!=null)" / コピー $pct%" else " / 総量不明")
        } else ""
        val next="${if(gguf)"GGUF" else "LiteRT-LM"} / ${settings.getString(if(gguf)"gguf_backend" else "llm_backend","cpu") }"
        modelStatus.text="$stored\n\n次回設定：$next\nメモリ状態：${m.detail}"+
            (if(m.backend.isNotBlank())" / ${m.backend}" else "")+(m.percent?.let {" / ファイルロード $it%"} ?: "")+importText+
            (if(missing.isNotEmpty())"\n次回の不足：${missing.joinToString()}" else "")
        val importing=ModelRuntime.isImporting()
        val pct=if(importing && imp.phase=="コピー中")ImportProgress.percent(imp.copied,imp.total) else if(m.phase=="loading")m.percent else null
        modelProgress.visibility=if(importing || m.phase in listOf("loading","releasing"))View.VISIBLE else View.GONE
        modelProgress.isIndeterminate=pct==null
        if(pct!=null)modelProgress.progress=pct
        loadButton.isEnabled=ModelRuntime.available() && missing.isEmpty()
        unloadButton.isEnabled=RobotService.running && m.phase in listOf("loading","ready")
        importButtons.forEach { it.isEnabled=ModelRuntime.available() }
        (importButtons+listOf(loadButton,unloadButton)).forEach { it.alpha=if(it.isEnabled)1f else .45f }
    }
    override fun onResume(){super.onResume();handler.post(refresh)}
    override fun onPause(){handler.removeCallbacks(refresh);super.onPause()}
}

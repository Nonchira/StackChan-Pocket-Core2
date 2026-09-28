package jp.stackchan.pocket

import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

/** One worker owns the model. Cancellation never frees memory used by native decode. */
class GgufLlm(path:String,threads:Int,nativeLibraryDir:String,gpu:Boolean=false,private val reusePrompt:Boolean=true,private val seed:Int=-1,onLoad:(Float)->Unit={},cancelLoad:()->Boolean={false}):LocalLlm {
    private val worker=Executors.newSingleThreadExecutor { r -> Thread(r,"gguf-inference") }
    private val handle:Long
    private var closed=false
    @Volatile var lastStats="GGUF入力処理：未計測"
        private set
    init {
        try {
            System.loadLibrary("pocket_llama")
            handle=nativeLoad(path,threads.coerceIn(1,8),nativeLibraryDir,gpu,object:LoadCallback {
                override fun progress(value:Float):Boolean {
                    if(cancelLoad())return false
                    return try { onLoad(value);!cancelLoad() } catch(_:CancellationException){ false }
                }
            })
            check(handle!=0L) { "GGUFモデルを読み込めません" }
        } catch(t:Throwable) { worker.shutdown();throw IllegalStateException("GGUFの読み込み失敗: ${t.message}",t) }
    }
    override val supportsReuse=false
    override fun session(system:String,history:List<ConversationMemory.Turn>,maxTokens:Int):LocalSession {
        check(!closed)
        return object:LocalSession {
            private val stopped=AtomicBoolean(false)
            private var task:Future<*>?=null
            override fun generate(text:String,onDelta:(String)->Unit,onDone:()->Unit,onError:(Throwable)->Unit) {
                check(task==null) { "GGUFセッションは一回ごとに作成してください" }
                val roles= mutableListOf("system")
                val contents= mutableListOf(system)
                history.forEach { roles.add("user");contents.add(it.user);roles.add("assistant");contents.add(it.assistant) }
                roles.add("user");contents.add(text)
                task=worker.submit {
                    try {
                        if(stopped.get())throw CancellationException("中断")
                        nativeGenerate(handle,roles.toTypedArray(),contents.toTypedArray(),maxTokens,reusePrompt && history.isNotEmpty(),seed,object:NativeCallback {
                            override fun cancelled()=stopped.get()
                            override fun token(bytes:ByteArray) { if(!stopped.get())onDelta(String(bytes,Charsets.UTF_8)) }
                            override fun stats(prompt:Int,reused:Int,promptMs:Long,output:Int,generationMs:Long) {
                                lastStats="GGUF入力処理：${promptMs}ms / 履歴再利用：${reused}/${prompt}トークン\n生成：${output}トークン / ${generationMs}ms"
                            }
                        })
                        if(stopped.get())throw CancellationException("中断")
                        onDone()
                    } catch(t:Throwable) { onError(t) }
                }
            }
            override fun cancelProcess() { stopped.set(true) }
            override fun close() { cancelProcess();task?.get() }
        }
    }
    override fun close() {
        if(closed)return
        closed=true
        worker.shutdown()
        check(worker.awaitTermination(60,TimeUnit.SECONDS)) { "GGUF処理の終了待ちです" }
        nativeFree(handle)
    }
    interface NativeCallback {
        fun cancelled():Boolean
        fun token(bytes:ByteArray)
        fun stats(prompt:Int,reused:Int,promptMs:Long,output:Int,generationMs:Long)
    }
    interface LoadCallback { fun progress(value:Float):Boolean }
    private external fun nativeLoad(path:String,threads:Int,nativeLibraryDir:String,gpu:Boolean,callback:LoadCallback):Long
    private external fun nativeGenerate(handle:Long,roles:Array<String>,contents:Array<String>,maxTokens:Int,reuse:Boolean,seed:Int,callback:NativeCallback)
    private external fun nativeFree(handle:Long)
}

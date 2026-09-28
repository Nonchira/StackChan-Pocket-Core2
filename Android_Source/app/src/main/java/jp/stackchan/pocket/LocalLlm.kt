package jp.stackchan.pocket

import com.google.ai.edge.litertlm.*

interface LocalLlm : AutoCloseable {
    val supportsReuse:Boolean
    fun session(system:String,history:List<ConversationMemory.Turn>,maxTokens:Int):LocalSession
}
interface LocalSession : AutoCloseable {
    fun generate(text:String,onDelta:(String)->Unit,onDone:()->Unit,onError:(Throwable)->Unit)
    fun cancelProcess()
}
class LiteRtLlm(path:String,gpu:Boolean,cache:String):LocalLlm {
    private val engine=Engine(EngineConfig(modelPath=path,backend=if(gpu) Backend.GPU() else Backend.CPU(),maxNumTokens=4096,cacheDir=cache))
    init { try { engine.initialize() } catch(e:Exception) { engine.close();throw e } }
    override val supportsReuse=true
    override fun session(system:String,history:List<ConversationMemory.Turn>,maxTokens:Int):LocalSession {
        val c=engine.createConversation(ConversationConfig(systemInstruction=Contents.of(system),
            initialMessages=history.flatMap { listOf(Message.user(it.user),Message.model(it.assistant)) },maxOutputToken=maxTokens))
        return object:LocalSession {
            override fun generate(text:String,onDelta:(String)->Unit,onDone:()->Unit,onError:(Throwable)->Unit) {
                c.sendMessageAsync(text,object:MessageCallback {
                    override fun onMessage(message:Message)=onDelta(message.toString())
                    override fun onDone()=onDone.invoke()
                    override fun onError(throwable:Throwable)=onError(throwable)
                })
            }
            override fun cancelProcess()=c.cancelProcess()
            override fun close()=c.close()
        }
    }
    override fun close()=engine.close()
}

package jp.stackchan.pocket

/** Callback producer / speech consumer. Bound output, preserve order and final punctuation-free tail. */
@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
class SentenceStream(private val limit:Int=3000, private val earlyClause:Boolean=false) {
    private val pending=StringBuilder()
    private val full=StringBuilder()
    private var firstChunk=true
    private var done=false
    private var error:Throwable?=null
    @Synchronized fun append(delta:String) {
        if(done)return
        if(full.length+delta.length>limit) { error=IllegalStateException("返答が長すぎます");done=true;(this as java.lang.Object).notifyAll();return }
        full.append(delta);pending.append(delta);(this as java.lang.Object).notifyAll()
    }
    @Synchronized fun finish() { done=true;(this as java.lang.Object).notifyAll() }
    @Synchronized fun fail(t:Throwable) { error=t;done=true;(this as java.lang.Object).notifyAll() }
    @Synchronized fun next():String? {
        error?.let { throw IllegalStateException("LLM生成に失敗しました",it) }
        var end=pending.indexOfAny(charArrayOf('。','！','？','!','?','\n'))
        if(earlyClause && firstChunk) {
            // Use punctuation only, with a shorter meaningful first clause. Never split arbitrary text.
            var quoteDepth=0
            for(i in 0 until pending.length) {
                when(pending[i]) {
                    '「','『','（','(' -> quoteDepth++
                    '」','』','）',')' -> quoteDepth=(quoteDepth-1).coerceAtLeast(0)
                }
                if(end>=0 && i>=end)break
                if(pending[i]=='、' && i>=4 && quoteDepth==0) {
                    val clause=pending.substring(0,i).trim()
                    if(clause !in setOf("はい","ええ","なるほど","そうですね","そうですか","わかりました","分かりました")) {
                        end=i;break
                    }
                }
            }
        }
        end=if(end>=0) end+1 else if(done) pending.length else 0
        if(end==0)return null
        firstChunk=false
        val result=pending.substring(0,end);pending.delete(0,end);return result
    }
    @Synchronized fun awaitNext(timeoutMs:Long=50):String? {
        require(timeoutMs>0)
        next()?.let { return it }
        if(done)return null
        (this as java.lang.Object).wait(timeoutMs)
        return next()
    }
    @Synchronized fun complete()=done && pending.isEmpty()
    @Synchronized fun text()=full.toString()
}

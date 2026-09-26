package jp.stackchan.pocket

data class ChatEntry(val id:Long,val speaker:String,val text:String,val time:Long,val parts:List<String> = listOf(text))
data class ChatSnapshot(val revision:Long,val entries:List<ChatEntry>)

/** Display history only: kept in memory across Activity recreation. */
class ChatHistory(private val capacity:Int=100) {
    init { require(capacity>0) }
    private val entries=ArrayDeque<ChatEntry>()
    private var sequence=0L
    private var revision=0L
    @Synchronized fun add(speaker:String,text:String,time:Long=System.currentTimeMillis()):Long {
        if(text.isBlank())return 0
        entries.addLast(ChatEntry(++sequence,speaker,text.take(8000),time))
        while(entries.size>capacity)entries.removeFirst()
        revision++;return sequence
    }
    @Synchronized fun append(id:Long,text:String) {
        val index=entries.indexOfFirst { it.id==id }
        if(index<0 || text.isEmpty())return
        entries[index]=entries[index].copy(text=(entries[index].text+text).take(8000),parts=(entries[index].parts+text).take(256));revision++
    }
    @Synchronized fun snapshot()=ChatSnapshot(revision,entries.toList())
    @Synchronized fun clear(){entries.clear();revision++}
}
object ChatLog { val history=ChatHistory() }

package jp.stackchan.pocket

/** Only completed recognition results cross from ASR to the serial conversation worker. */
internal class SpeechInbox(private val capacity:Int=2) {
    data class Item(val generation:Long,val text:String,val single:Boolean,val registration:Boolean,val heardAt:Long=0L)
    private var generation=0L
    private var reserved=0
    private val items=java.util.ArrayDeque<Item>()
    @Synchronized fun reset(value:Long) { generation=value;items.clear();reserved=0 }
    @Synchronized fun reserve(value:Long):Boolean {
        if(value!=generation || items.size+reserved>=capacity)return false
        reserved++;return true
    }
    @Synchronized fun discard(value:Long) {
        if(value==generation && reserved>0)reserved--
    }
    @Synchronized fun complete(item:Item):Boolean {
        if(item.generation!=generation || reserved==0)return false
        reserved--
        items.addLast(item);return true
    }
    @Synchronized fun poll():Item?=items.pollFirst()
    @Synchronized fun size()=items.size
}

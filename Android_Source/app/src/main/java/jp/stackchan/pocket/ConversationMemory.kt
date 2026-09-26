package jp.stackchan.pocket

/** Completed turns only; bounded context, independent of the display log. */
class ConversationMemory(private val maxTurns:Int=6, private val maxChars:Int=1800) {
    data class Turn(val user:String,val assistant:String)
    private val turns=ArrayDeque<Turn>()
    init { require(maxTurns>0 && maxChars>0) }
    fun snapshot():List<Turn> = turns.toList()
    fun remember(user:String,assistant:String,compactOnOverflow:Boolean=false) {
        if(user.isBlank() || assistant.isBlank())return
        val turn=Turn(user,assistant)
        if(user.length+assistant.length>maxChars) { turns.clear();return }
        turns.addLast(turn)
        // Leave headroom only when an actual limit is exceeded. Exact native history
        // matching remains mandatory; compacting deliberately triggers one rebuild.
        if(compactOnOverflow && (turns.size>maxTurns || turns.sumOf { it.user.length+it.assistant.length }>maxChars)) {
            val targetTurns=(maxTurns*2/3).coerceAtLeast(1)
            val targetChars=(maxChars*2/3).coerceAtLeast(1)
            while(turns.size>1 && (turns.size>targetTurns || turns.sumOf { it.user.length+it.assistant.length }>targetChars))turns.removeFirst()
        }
        while(turns.size>maxTurns || turns.sumOf { it.user.length+it.assistant.length }>maxChars)turns.removeFirst()
    }
    fun clear()=turns.clear()
}

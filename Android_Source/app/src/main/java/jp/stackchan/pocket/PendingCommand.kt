package jp.stackchan.pocket

/** The generation is bound when queued, never replaced after dequeue. */
internal class PendingCommand(val generation:Long,private val action:(Long)->Unit) {
    fun run(current:Long,alive:Boolean) {
        if(alive && generation==current)action(generation)
    }
}

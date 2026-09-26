package jp.stackchan.pocket

/** Keep the beginning of a bounded push-to-talk recording, including VAD pre-speech audio. */
class UtteranceCapture(private val capacity:Int=16000*20) {
    private val data=FloatArray(capacity)
    private var count=0
    fun reset() { count=0 }
    fun append(samples:FloatArray) {
        val n=minOf(samples.size,capacity-count)
        if(n>0) { samples.copyInto(data,count,0,n);count+=n }
    }
    fun samples()=data.copyOf(count)
}

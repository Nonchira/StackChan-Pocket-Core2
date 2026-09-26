package jp.stackchan.pocket

import kotlin.math.sqrt
import kotlin.math.roundToInt

/** Precomputed on the phone from PCM16 mono / 16 kHz, never from system volume. */
internal class MouthTimeline private constructor(private val levels:IntArray,private val totalFrames:Long) {
 fun at(playedFrames:Long):Int {
  if(playedFrames<=0 || playedFrames>=totalFrames)return 0
  return levels[(playedFrames/640).toInt()]
 }
 companion object {
  fun prepare(pcm:ByteArray):MouthTimeline {
   require(pcm.size%2==0)
   val frames=pcm.size/2
   val rms=DoubleArray((frames+639)/640) { block ->
    val start=block*640;val end=minOf(frames,start+640);var sum=0.0
    for(i in start until end) {
     val sample=((pcm[2*i].toInt() and 255) or (pcm[2*i+1].toInt() shl 8)).toShort().toDouble()
     sum+=sample*sample
    }
    sqrt(sum/(end-start))
   }
   val voiced=rms.filter { it>=8.0 }.sorted()
   val reference=if(voiced.isEmpty())8.0 else voiced[((voiced.size-1)*0.9).toInt()].coerceAtLeast(8.0)
   val gate=maxOf(8.0,reference*0.015)
   val levels=IntArray(rms.size) { i ->
    if(rms[i]<gate)0 else (300+2100*(rms[i]/reference).coerceIn(0.0,1.0)).roundToInt().coerceIn(0,2400)
   }
   // Core2 0.3.27 already applies x5 gain: 2400 maps to full opening (12000).
   return MouthTimeline(levels,frames.toLong())
  }
 }
}

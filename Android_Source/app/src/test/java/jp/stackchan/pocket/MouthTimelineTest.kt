package jp.stackchan.pocket
import org.junit.Assert.*
import org.junit.Test
class MouthTimelineTest {
 private fun pcm(vararg amplitudes:Int):ByteArray {
  val out=ByteArray(amplitudes.size*1280)
  amplitudes.forEachIndexed { block,a -> repeat(640) { i -> val s=if(i%2==0)a else -a;val p=block*1280+i*2;out[p]=s.toByte();out[p+1]=(s shr 8).toByte() } }
  return out
 }
 @Test fun silentGapsClose(){val t=MouthTimeline.prepare(pcm(0,100,0,100));assertEquals(0,t.at(1));assertTrue(t.at(641)>0);assertEquals(0,t.at(1281))}
 @Test fun quietAndLoudVoicesGetComparableMovement(){val quiet=MouthTimeline.prepare(pcm(20,40,80,40));val loud=MouthTimeline.prepare(pcm(200,400,800,400));for(i in listOf(1L,641L,1281L,1921L))assertEquals(quiet.at(i),loud.at(i))}
 @Test fun preservesRelativeIntensity(){val t=MouthTimeline.prepare(pcm(10,100,1000,1000));assertTrue(t.at(641)>t.at(1));assertTrue(t.at(1281)>t.at(641));assertEquals(2400,t.at(1281))}
 @Test fun startEndAndEmptyAreClosed(){val t=MouthTimeline.prepare(pcm(100));assertEquals(0,t.at(0));assertEquals(0,t.at(640));assertEquals(0,MouthTimeline.prepare(byteArrayOf()).at(1))}
 @Test fun incompleteLastBlockIsSafe(){val t=MouthTimeline.prepare(pcm(100).copyOf(200));assertTrue(t.at(99)>0);assertEquals(0,t.at(100))}
 @Test fun signedMinimumDoesNotOverflow(){val t=MouthTimeline.prepare(pcm(-32768));assertEquals(2400,t.at(1))}
 @Test(expected=IllegalArgumentException::class) fun oddLengthRejected(){MouthTimeline.prepare(byteArrayOf(1))}
}

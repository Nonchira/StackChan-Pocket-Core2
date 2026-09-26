package jp.stackchan.pocket
import org.junit.Assert.*
import org.junit.Test
class AnimatedMouthTest {
 @Test fun closedBeforeStartAndAfterEnd(){assertEquals(0,PlaybackMouth.animated(16000,0));assertEquals(0,PlaybackMouth.animated(16000,16000));assertEquals(0,PlaybackMouth.animated(0,0))}
 @Test fun opensAndClosesDuringPlayback(){val values=(1L..5759L step 960).map { PlaybackMouth.animated(16000,it) };assertTrue(values.max()>0);assertTrue(values.contains(0));assertTrue(values.distinct().size>=4)}
 @Test fun repeatsWithoutDependingOnAmplitude(){assertEquals(PlaybackMouth.animated(32000,1920),PlaybackMouth.animated(32000,7680));assertEquals(3000,PlaybackMouth.animated(32000,1920))}
}

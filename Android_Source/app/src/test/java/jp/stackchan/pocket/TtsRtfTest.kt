package jp.stackchan.pocket
import org.junit.Assert.*
import org.junit.Test
class TtsRtfTest {
 @Test fun oneSecondOutput() { val r=TtsRtf();r.add(35,32000);assertTrue(r.summary().contains("RTF 0.0350"));assertTrue(r.summary().contains("音声 1000.0ms")) }
 @Test fun totalUsesWeightedDurationNotMeanOfRatios() { val r=TtsRtf();r.add(100,32000);r.add(100,96000);assertTrue(r.summary().contains("RTF 0.0500"));assertTrue(r.summary().contains("RTF 0.1000")) }
 @Test fun noSamples() { assertEquals("TTS RTF：未計測",TtsRtf().summary()) }
 @Test(expected=IllegalArgumentException::class) fun zeroAudioRejected() { TtsRtf().add(1,0) }
 @Test(expected=IllegalArgumentException::class) fun partialSampleRejected() { TtsRtf().add(1,3) }
}

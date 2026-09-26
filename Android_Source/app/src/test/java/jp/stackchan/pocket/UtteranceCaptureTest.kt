package jp.stackchan.pocket
import org.junit.Assert.*
import org.junit.Test
class UtteranceCaptureTest {
 @Test fun initialAudioIsKeptWithLaterSpeech() { val c=UtteranceCapture(8);c.append(floatArrayOf(1f,2f));c.append(floatArrayOf(3f,4f));assertArrayEquals(floatArrayOf(1f,2f,3f,4f),c.samples(),0f) }
 @Test fun resetExcludesPreviousTurn() { val c=UtteranceCapture(8);c.append(floatArrayOf(1f));c.reset();c.append(floatArrayOf(2f));assertArrayEquals(floatArrayOf(2f),c.samples(),0f) }
 @Test fun boundedCaptureKeepsBeginning() { val c=UtteranceCapture(3);c.append(floatArrayOf(1f,2f));c.append(floatArrayOf(3f,4f));assertArrayEquals(floatArrayOf(1f,2f,3f),c.samples(),0f) }
}

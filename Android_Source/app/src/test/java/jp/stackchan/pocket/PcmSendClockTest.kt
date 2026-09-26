package jp.stackchan.pocket
import org.junit.Assert.*
import org.junit.Test

class PcmSendClockTest {
 @Test fun fortySecondsDoesNotAccumulatePerFrameOverhead() {
  val clock=PcmSendClock(0);var now=0L
  repeat(1000) { now+=2_000_000L;now+=clock.afterFrame(1280,now) }
  assertEquals(40_000_000_000L,now)
  // The previous send+sleep algorithm would add 2 seconds here.
 }
 @Test fun sleepOvershootIsCorrectedOnFollowingFrame() {
  val clock=PcmSendClock(0);var now=0L
  repeat(1000) { now+=1_000_000L;now+=clock.afterFrame(1280,now);now+=3_000_000L }
  assertEquals(40_003_000_000L,now)
 }
 @Test fun longPauseDoesNotCauseUnboundedCatchup() {
  val clock=PcmSendClock(0)
  assertEquals(0L,clock.afterFrame(1280,2_000_000_000L))
  assertEquals(0L,clock.afterFrame(1280,2_000_000_000L))
  assertEquals(40_000_000L,clock.afterFrame(1280,2_000_000_000L))
 }
 @Test fun shortFinalFrameUsesActualPcmDuration(){assertEquals(10_000_000L,PcmSendClock(0).afterFrame(320,0))}
 @Test fun eachSpeechSegmentStartsWithFreshClock(){assertEquals(40_000_000L,PcmSendClock(7_000_000_000L).afterFrame(1280,7_000_000_000L))}
 @Test(expected=IllegalArgumentException::class) fun oddPcmRejected(){PcmSendClock(0).afterFrame(3,0)}
}

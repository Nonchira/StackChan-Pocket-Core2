package jp.stackchan.pocket
import org.junit.Assert.*
import org.junit.Test
class TimerRecordTest {
    @Test fun remainingRoundsUp() { assertEquals(2L,TimerRecord("a",2001,3).remaining(1000,3));assertEquals(0L,TimerRecord("a",2001,3).remaining(3000,3)) }
    @Test fun previousAlarmCannotFireReplacement() { assertFalse(TimerRecord("new",2000,3).accepts("old",3));assertTrue(TimerRecord("new",2000,3).accepts("new",3)) }
    @Test fun clearedTimerRejectsOldCallback() { assertFalse(TimerRecord("",0,3).accepts("old",3));assertNull(TimerRecord("",0,3).remaining(0,3)) }
    @Test fun rebootInvalidatesElapsedDeadline() { val timer=TimerRecord("a",99999,3);assertFalse(timer.accepts("a",4));assertNull(timer.remaining(100,4)) }
}

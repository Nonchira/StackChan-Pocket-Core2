package jp.stackchan.pocket

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class CancellationBoundaryTest {
    @Test fun polledCommandCannotRunAfterStop() {
        val generation=AtomicLong(4);val polled=CountDownLatch(1);val resume=CountDownLatch(1)
        var calls=0
        val command=PendingCommand(generation.get()){ calls++ }
        val worker=Thread { polled.countDown();resume.await();command.run(generation.get(),true) }
        worker.start();assertTrue(polled.await(2,TimeUnit.SECONDS))
        generation.incrementAndGet();resume.countDown();worker.join(2000)
        assertFalse(worker.isAlive);assertEquals(0,calls)
    }
    @Test fun actionKeepsEnqueueGenerationEvenIfStopFollowsDispatch() {
        val generation=AtomicLong(4);var received=0L
        PendingCommand(4) { turn -> generation.incrementAndGet();received=turn }.run(4,true)
        assertEquals(4L,received);assertNotEquals(generation.get(),received)
    }
    @Test fun destroyedServiceRejectsCommand() {
        var calls=0;PendingCommand(1){calls++}.run(1,false);assertEquals(0,calls)
    }
    @Test fun queuedSpeechUsesCaptureTimeAcrossWakeExpiry() {
        val wake=WakeWindow();wake.open(1000)
        assertTrue(wake.accepts(30_999));assertTrue(wake.accepts(31_000));assertFalse(wake.accepts(31_001))
    }
    @Test fun wakeWordCanAuthorizeFollowingAlreadyQueuedSpeech() {
        val wake=WakeWindow();assertFalse(wake.accepts(2000))
        wake.open(3000);assertTrue(wake.accepts(2000))
    }
    @Test fun inboxRetainsCaptureTime() {
        val inbox=SpeechInbox();inbox.reset(1);assertTrue(inbox.reserve(1))
        assertTrue(inbox.complete(SpeechInbox.Item(1,"hello",false,false,29000)))
        assertEquals(29000L,inbox.poll()!!.heardAt)
    }
}

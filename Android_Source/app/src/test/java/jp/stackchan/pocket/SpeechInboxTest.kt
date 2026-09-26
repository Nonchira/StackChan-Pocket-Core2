package jp.stackchan.pocket

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SpeechInboxTest {
    private fun item(g:Long,text:String)=SpeechInbox.Item(g,text,false,false)
    @Test fun recognitionAndCompletedRequestsShareTheSameTwoSlots() {
        val q=SpeechInbox();q.reset(1)
        assertTrue(q.reserve(1));assertTrue(q.reserve(1));assertFalse(q.reserve(1))
        assertTrue(q.complete(item(1,"first")));assertFalse(q.reserve(1))
        assertTrue(q.complete(item(1,"second")));assertFalse(q.reserve(1))
        assertEquals("first",q.poll()!!.text);assertTrue(q.reserve(1))
        assertTrue(q.complete(item(1,"third")))
        assertEquals("second",q.poll()!!.text);assertEquals("third",q.poll()!!.text)
        assertNull(q.poll())
    }
    @Test fun stopRejectsLateAsrAndPreservesTheNewSessionsSlot() {
        val q=SpeechInbox();q.reset(1);assertTrue(q.reserve(1))
        q.reset(2);assertTrue(q.reserve(2))
        assertFalse(q.complete(item(1,"old speech")));q.discard(1)
        assertTrue(q.complete(item(2,"new speech")))
        assertEquals("new speech",q.poll()!!.text);assertNull(q.poll())
    }
    @Test fun stopClearsAlreadyRecognizedSpeech() {
        val q=SpeechInbox();q.reserve(0);q.complete(item(0,"do not execute"))
        q.reset(1);assertNull(q.poll());assertFalse(q.reserve(0))
    }
    @Test fun EmptyRecognitionReleasesItsSlot() {
        val q=SpeechInbox();q.reserve(0);q.reserve(0);assertFalse(q.reserve(0))
        q.discard(0);assertTrue(q.reserve(0))
    }
    @Test fun delayedWorkerCannotPublishAfterCancellation() {
        val q=SpeechInbox();val started=CountDownLatch(1);val release=CountDownLatch(1)
        val done=CountDownLatch(1);var accepted=true
        val worker=Thread {
            q.reserve(0);started.countDown();release.await()
            accepted=q.complete(item(0,"late"));done.countDown()
        }
        worker.start();assertTrue(started.await(2,TimeUnit.SECONDS))
        q.reset(1);release.countDown();assertTrue(done.await(2,TimeUnit.SECONDS))
        worker.join();assertFalse(accepted);assertNull(q.poll())
    }
}

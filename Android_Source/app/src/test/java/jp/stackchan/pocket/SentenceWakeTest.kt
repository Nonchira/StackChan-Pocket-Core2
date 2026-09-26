package jp.stackchan.pocket
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.*
class SentenceWakeTest {
 @Test fun queuedBoundaryDoesNotWait() { val s=SentenceStream();s.append("回答です。");assertEquals("回答です。",s.awaitNext()) }
 @Test fun callbackWakesConsumerWithoutLosingText() {
  val s=SentenceStream();val executor=Executors.newSingleThreadExecutor()
  try { val result=executor.submit<String> { var text:String?=null;while(text==null)text=s.awaitNext(500);text }
   s.append("回答");s.append("です。");assertEquals("回答です。",result.get(2,TimeUnit.SECONDS))
  } finally { executor.shutdownNow() }
 }
 @Test fun finishFlushesTailAndFailureIsPropagated() {
  val s=SentenceStream();s.append("終わり");s.finish();assertEquals("終わり",s.awaitNext());assertNull(s.awaitNext())
  val failed=SentenceStream();failed.fail(Exception("failure"));assertThrows(IllegalStateException::class.java){failed.awaitNext()}
 }
 @Test fun timeoutDoesNotForceSplit() { val s=SentenceStream();s.append("途中の文");assertNull(s.awaitNext(1));assertEquals("途中の文",s.text()) }
}

package jp.stackchan.pocket

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.*

class StageWaitTest {
 @Test fun completedAckNeedsNoRetry(){var retries=0;assertEquals("ok",StageWait.await(CompletableFuture.completedFuture("ok"),"ack",1000,{},retry={retries++}));assertEquals(0,retries)}
 @Test fun lostAckCanBeRecoveredWithoutNewFuture(){val ack=CompletableFuture<Unit>();var queries=0;StageWait.await(ack,"ack",2000,{},retryEveryMs=1,retry={queries++;ack.complete(Unit)});assertEquals(1,queries)}
 @Test fun timeoutIdentifiesStage(){val e=runCatching{StageWait.await(CompletableFuture<Unit>(),"Core2 区間3",10,{})}.exceptionOrNull()!!;assertTrue(e.message!!.contains("Core2 区間3"));assertTrue(TaskFailure.message(e,false)!!.contains("タイムアウト"))}
 @Test fun cancelledTurnSendsNoRetry(){var retries=0;val e=runCatching{StageWait.await(CompletableFuture<Unit>(),"ack",1000,{throw CancellationException()},retry={retries++})}.exceptionOrNull();assertTrue(e is CancellationException);assertEquals(0,retries)}
 @Test fun synthesisFailureIsNotRetried(){var retries=0;val future=CompletableFuture<Unit>();future.completeExceptionally(IllegalStateException("TTS stopped"));val e=runCatching{StageWait.await(future,"tts",1000,{},retry={retries++})}.exceptionOrNull()!!;assertTrue(TaskFailure.message(e,false)!!.contains("TTS stopped"));assertEquals(0,retries)}
}

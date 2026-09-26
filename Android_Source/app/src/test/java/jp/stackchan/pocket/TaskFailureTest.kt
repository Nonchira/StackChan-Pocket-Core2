package jp.stackchan.pocket

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.*

class TaskFailureTest {
    @Test fun stopDuringPlaybackDoesNotBecomeNullError() {
        val waiting=CompletableFuture<Unit>()
        waiting.completeExceptionally(CancellationException())
        val failure=runCatching { waiting.get() }.exceptionOrNull()!!
        assertNull(TaskFailure.message(failure,false))
    }
    @Test fun wrappedCancellationIsAlsoNormal() {
        assertNull(TaskFailure.message(ExecutionException(CancellationException()),false))
    }
    @Test fun cancelledNativeTurnCannotOverwriteMenuStatus() {
        assertNull(TaskFailure.message(IllegalStateException("native inference stopped"),true))
    }
    @Test fun missingMessageStillIdentifiesRealFailure() {
        assertEquals("NullPointerException",TaskFailure.message(NullPointerException(),false))
        assertTrue(TaskFailure.message(TimeoutException(),false)!!.contains("タイムアウト"))
    }
    @Test fun realWrappedFailureIsNotHidden() {
        assertEquals("音声合成失敗（IllegalStateException）",TaskFailure.message(ExecutionException(IllegalStateException("音声合成失敗")),false))
    }
}

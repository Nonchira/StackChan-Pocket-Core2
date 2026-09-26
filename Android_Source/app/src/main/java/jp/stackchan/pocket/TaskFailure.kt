package jp.stackchan.pocket

import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException

/** A stop invalidates the old turn; its completion must not overwrite the new UI state. */
object TaskFailure {
    fun message(error: Throwable, obsolete: Boolean): String? {
        if (obsolete) return null
        var cause = error
        while ((cause is ExecutionException || cause is CompletionException) && cause.cause != null) {
            cause = cause.cause!!
        }
        if (cause is CancellationException) return null
        if (cause is TimeoutException) return "処理の応答待ちがタイムアウトしました（TimeoutException）"
        val kind = cause.javaClass.simpleName.ifBlank { cause.javaClass.name }
        val detail = cause.message?.takeIf { it.isNotBlank() && it != "null" }
        return if (detail == null) kind else "$detail（$kind）"
    }
}

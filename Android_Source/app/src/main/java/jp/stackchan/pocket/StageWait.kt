package jp.stackchan.pocket

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Retry only idempotent completion queries, never PCM or speech synthesis. */
internal object StageWait {
 fun <T> await(future:CompletableFuture<T>,stage:String,timeoutMs:Long,check:()->Unit,
               retryEveryMs:Long=1000,retry:(()->Unit)?=null):T {
  require(timeoutMs>0 && retryEveryMs>0)
  val start=System.nanoTime();var nextRetry=retryEveryMs
  while(true) {
   check()
   val elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)
   val remaining=timeoutMs-elapsed
   if(remaining<=0)throw IllegalStateException("$stage の応答待ちがタイムアウトしました")
   try { return future.get(minOf(remaining,100L),TimeUnit.MILLISECONDS).also { check() } }
   catch(e:TimeoutException) {
    check()
    val now=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)
    if(retry!=null && now>=nextRetry && now<timeoutMs && !future.isDone) {
     retry();nextRetry=now+retryEveryMs
    }
   }
  }
 }
}

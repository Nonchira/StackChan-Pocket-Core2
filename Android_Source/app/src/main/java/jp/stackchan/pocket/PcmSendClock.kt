package jp.stackchan.pocket

/** 16 kHz / mono / PCM16. No prefill; at most one frame of catch-up. */
internal class PcmSendClock(startNanos:Long) {
 private var deadline=startNanos
 fun afterFrame(bytes:Int,nowNanos:Long):Long {
  require(bytes in 2..1280 && bytes%2==0)
  val duration=bytes*1_000_000_000L/32_000L
  deadline+=duration
  // A long scheduling pause must not flush a backlog into the Core2 UART.
  if(nowNanos-deadline>40_000_000L)deadline=nowNanos-40_000_000L
  return (deadline-nowNanos).coerceAtLeast(0L)
 }
}

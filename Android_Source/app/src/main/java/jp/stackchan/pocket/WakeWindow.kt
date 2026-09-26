package jp.stackchan.pocket

/** All times are elapsedRealtime; queued speech retains its capture time. */
internal class WakeWindow {
    @Volatile private var until=0L
    fun open(now:Long) { until=now+30_000 }
    fun accepts(heardAt:Long)=heardAt>0 && heardAt<=until
}

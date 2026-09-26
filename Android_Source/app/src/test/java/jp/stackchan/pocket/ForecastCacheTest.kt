package jp.stackchan.pocket

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ForecastCacheTest {
    private val today=LocalDate.parse("2026-09-24")
    private val now=1_790_000_000_000L
    @Test fun cachedTomorrowCanBecomeTodaysForecast() {
        assertTrue(ForecastCachePolicy.usable(today,today,now-24*3600*1000L,now,"予報"))
    }
    @Test fun wrongDateCannotBeReadAsToday() {
        assertFalse(ForecastCachePolicy.usable(today,today.minusDays(1),now-1000,now,"昨日の予報"))
    }
    @Test fun missingExpiredAndFutureEntriesRejected() {
        assertFalse(ForecastCachePolicy.usable(today,today,now-49*3600*1000L,now,"予報"))
        assertFalse(ForecastCachePolicy.usable(today,today,now+1,now,"予報"))
        assertFalse(ForecastCachePolicy.usable(today,today,now-1,now,null))
    }
    @Test fun missingTomorrowDoesNotDiscardToday() {
        val raw="""[{"timeSeries":[{"timeDefines":["2026-09-24T00:00:00+09:00"],"areas":[{"area":{"code":"270000"},"weathers":["晴れ"]}]}]}]"""
        val parsed=ForecastCachePolicy.parseDays(raw,"osaka",listOf(today,today.plusDays(1)))
        assertEquals(setOf(today),parsed.keys)
        assertTrue(parsed.getValue(today).contains("晴れ"))
    }
}

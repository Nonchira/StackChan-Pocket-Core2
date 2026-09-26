package jp.stackchan.pocket

import java.time.LocalDate

object ForecastCachePolicy {
    fun usable(requested:LocalDate,storedDate:LocalDate,savedAt:Long,now:Long,text:String?):Boolean =
        requested==storedDate && !text.isNullOrBlank() && savedAt>0 && now>=savedAt && now-savedAt<=48*3600*1000L

    /** One absent day must not discard another valid day's forecast. */
    fun parseDays(raw:String,region:String,dates:List<LocalDate>):Map<LocalDate,String> =
        dates.mapNotNull { date -> runCatching { date to ForecastParser.parse(raw,region,date) }.getOrNull() }.toMap()
}

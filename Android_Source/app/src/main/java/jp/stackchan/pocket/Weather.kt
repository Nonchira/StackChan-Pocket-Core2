package jp.stackchan.pocket

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

object ForecastParser {
    val names = linkedMapOf("osaka" to "大阪府", "tokyo" to "東京都")
    fun areaCode(region:String):String? = when(region) {
        "osaka" -> "270000"; "tokyo" -> "130010"
        else -> null
    }
    fun officeCode(region:String):String? = when(region) {
        "osaka" -> "270000"; "tokyo" -> "130000"
        else -> null
    }
    fun describe(code: Int): String = when(code) {
        0 -> "快晴"; 1 -> "おおむね晴れ"; 2 -> "晴れ時々くもり"; 3 -> "くもり"
        45,48 -> "霧"; 51,53,55 -> "霧雨"; 56,57 -> "凍る霧雨"
        61,63 -> "雨"; 65 -> "強い雨"; 66,67 -> "凍る雨"
        71,73,77 -> "雪"; 75 -> "強い雪"; 80,81 -> "にわか雨"; 82 -> "激しいにわか雨"
        85,86 -> "にわか雪"; 95 -> "雷雨"; 96,99 -> "ひょうを伴う雷雨"
        else -> error("未対応の天気コード")
    }
    fun parse(raw: String, region: String, date: LocalDate, displayName:String?=null): String {
        val name = displayName ?: names[region] ?: error("地点が不正です")
        val prefix = "${date.monthValue}月${date.dayOfMonth}日、${name}${if(region=="tokyo") "、東京地方" else ""}の天気予報です。"
        if (areaCode(region)!=null) {
            val first = JSONArray(raw).getJSONObject(0)
            val code = areaCode(region)!!
            val series = first.getJSONArray("timeSeries")
            for(s in 0 until series.length()) {
                val item=series.getJSONObject(s); val times=item.getJSONArray("timeDefines"); val areas=item.getJSONArray("areas")
                for(i in 0 until times.length()) if(times.getString(i).startsWith(date.toString())) {
                    for(a in 0 until areas.length()) {
                        val area=areas.getJSONObject(a)
                        if(area.getJSONObject("area").getString("code")==code && area.has("weathers")) {
                            val weather=area.getJSONArray("weathers").getString(i)
                            require(weather.isNotBlank() && weather.length<1000)
                            return prefix+weather.replace("　","、")+"。気象庁の予報です。"
                        }
                    }
                }
            }
            error("対象日の地域予報がありません")
        }
        val daily=JSONObject(raw).getJSONObject("daily"); val times=daily.getJSONArray("time")
        val i=(0 until times.length()).firstOrNull { times.getString(it)==date.toString() } ?: error("対象日の予報がありません")
        val high=daily.getJSONArray("temperature_2m_max").getDouble(i)
        val low=daily.getJSONArray("temperature_2m_min").getDouble(i)
        require(high.isFinite() && low.isFinite() && high>=low && high<=70 && low>=-80)
        val weather=describe(daily.getJSONArray("weather_code").getInt(i))
        val pop=daily.optJSONArray("precipitation_probability_max")?.optDouble(i) ?: Double.NaN
        return prefix+weather+"。最高気温は${high.roundToInt()}度、最低気温は${low.roundToInt()}度です。"+
            (if(pop.isFinite() && pop in 0.0..100.0) "降水確率は一日の中で最大${pop.roundToInt()}パーセントです。" else "")+"オープンメテオの予報です。"
    }
}

class Weather(context: Context) {
    companion object {
        @Volatile var lastStatus="天気：未取得。外出前に「登録地点の天気を保存」で準備できます。"
        val preloading=AtomicBoolean(false)
    }
    private val places=WeatherPlaces(context)
    private val settings=context.getSharedPreferences("settings",Context.MODE_PRIVATE)
    private val prefs=context.getSharedPreferences("forecast",Context.MODE_PRIVATE)
    private val network=WeatherNetwork(context)
    private fun refresh(region:String,today:LocalDate):Map<LocalDate,String> {
        val place=places.find(region) ?: error("地点が未登録です")
        val office=ForecastParser.officeCode(region)
        val url=if(office!=null) "https://www.jma.go.jp/bosai/forecast/data/forecast/$office.json" else
            "https://api.open-meteo.com/v1/forecast?"+"latitude=${place.latitude}&longitude=${place.longitude}"+
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=Asia%2FTokyo&forecast_days=2"
        var route=""
        val raw=network.fetch(url) { route=it; lastStatus="天気：${places.find(region)?.name ?: region}を${it}で取得中…" }
        if(office!=null) {
            val report=OffsetDateTime.parse(JSONArray(raw).getJSONObject(0).getString("reportDatetime")).toInstant()
            val age=Duration.between(report,Instant.now()).seconds
            require(age in 0..36*3600L) { "気象庁の予報発表日時が古いか、端末の時刻が一致していません" }
        }
        val values=listOf(today,today.plusDays(1)).mapNotNull { date -> runCatching { date to ForecastParser.parse(raw,region,date,place.name) }.getOrNull() }.toMap()
        check(values.isNotEmpty()) { "今日・明日の有効な予報がありません" }
        val edit=prefs.edit(); val now=System.currentTimeMillis()
        values.forEach { (date,text) -> edit.putString("$region:$date",text).putLong("$region:$date:at",now) }
        check(edit.commit()) { "予報を保存できませんでした" }
        lastStatus="天気：${places.find(region)?.name ?: region}、${values.size}日分を保存（$route）"
        return values
    }
    fun prefetch():String {
        if(!preloading.compareAndSet(false,true))return "天気の保存を実行中です"
        lastStatus="天気：登録地点の予報を保存中…"
        try {
            val today=LocalDate.now(ZoneId.of("Asia/Tokyo"))
            val results=places.all().map { place ->
                val region=place.id;val name=place.name
                runCatching { val values=refresh(region,today); "$name：${values.size}日分保存" }
                    .getOrElse { "$name：失敗（${TaskFailure.message(it,false)}）" }
            }
            return results.joinToString("\n").also { lastStatus="天気保存結果\n$it" }
        } finally { preloading.set(false) }
    }
    fun selectedSpeech(day:Int=-1,check:()->Unit={}):String {
        val ids=if(settings.getBoolean("weather_wide",false))places.broad() else listOf(settings.getString("region","osaka")!!)
        if(ids.isEmpty())return "広域で読み上げる地点を選んでください。"
        return ids.joinToString(" ") { id -> check();speech(id,day).also { check() } }
    }
    fun speech(region: String, day: Int = -1): String {
        require(places.find(region)!=null && day in -1..1)
        val today=LocalDate.now(ZoneId.of("Asia/Tokyo"))
        val dates=if(day<0) listOf(today,today.plusDays(1)) else listOf(today.plusDays(day.toLong()))
        val fresh=runCatching { refresh(region,today) }.getOrElse {
            lastStatus="天気取得失敗：${TaskFailure.message(it,false)}。保存済み予報を確認します。"
            emptyMap()
        }
        return dates.joinToString(" ") { date ->
            val label=if(date==today) "今日、" else "明日、"
            if(date in fresh) label+fresh.getValue(date) else {
                val key="$region:$date"; val saved=prefs.getString(key,null); val at=prefs.getLong("$key:at",0)
                if(ForecastCachePolicy.usable(date,date,at,System.currentTimeMillis(),saved)) {
                    val stamp=Instant.ofEpochMilli(at).atZone(ZoneId.of("Asia/Tokyo")).format(DateTimeFormatter.ofPattern("M月d日H時m分"))
                    "最新の予報を取得できません。${stamp}に保存した予報です。$label$saved"
                } else "${date.monthValue}月${date.dayOfMonth}日の${places.find(region)?.name ?: region}の予報は、ネットに接続して取得してください。保存済みの予報はありません。"
            }
        }
    }
}

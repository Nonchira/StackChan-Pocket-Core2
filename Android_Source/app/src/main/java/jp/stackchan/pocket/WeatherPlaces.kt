package jp.stackchan.pocket

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

internal data class WeatherPlace(val id:String,val name:String,val latitude:Double,val longitude:Double) {
 init { require(id.isNotBlank() && name.isNotBlank() && name.length<=80 && latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0) }
 fun json()=JSONObject().put("id",id).put("name",name).put("latitude",latitude).put("longitude",longitude)
 companion object {
  fun read(j:JSONObject)=WeatherPlace(j.getString("id"),j.getString("name"),j.getDouble("latitude"),j.getDouble("longitude"))
  fun searchResults(raw:String):List<WeatherPlace> {
   val list=JSONObject(raw).optJSONArray("results") ?: return emptyList()
   return (0 until list.length()).mapNotNull { i -> runCatching {
    val j=list.getJSONObject(i);require(j.optString("country_code")=="JP")
    WeatherPlace("geo_"+j.getLong("id"),listOf(j.optString("admin1"),j.getString("name")).filter { it.isNotBlank() }.distinct().joinToString(" "),j.getDouble("latitude"),j.getDouble("longitude"))
   }.getOrNull() }.distinctBy { it.id }
  }
 }
}
internal class WeatherPlaces(context:Context) {
 private val prefs=context.getSharedPreferences("settings",Context.MODE_PRIVATE)
 private val network=WeatherNetwork(context)
 init {
  if(!prefs.getBoolean("weather_presets_v2",false)) {
   val retired=setOf("kyoto","nara_north","nara_south")
   val edit=prefs.edit().putBoolean("weather_presets_v2",true)
   if(prefs.getString("region","osaka") in retired)edit.putString("region","osaka")
   if(prefs.contains("weather_broad")) {
    val previous=prefs.getStringSet("weather_broad",emptySet())!!.toSet()
    val kept=previous-retired
    edit.putStringSet("weather_broad",if(previous.any { it in retired } && kept.isEmpty())setOf("osaka","tokyo") else kept)
   }
   check(edit.commit()) { "天気設定の更新に失敗しました" }
   // Old Osaka cache described a city forecast, not the new prefecture forecast.
   val cache=context.getSharedPreferences("forecast",Context.MODE_PRIVATE)
   val clear=cache.edit();cache.all.keys.filter { it.startsWith("osaka:") }.forEach { clear.remove(it) };clear.apply()
  }
 }
 fun all():List<WeatherPlace> {
  val base=listOf(WeatherPlace("osaka","大阪府",34.6937,135.5023),WeatherPlace("tokyo","東京都",35.6895,139.6917))
  val custom=runCatching { val a=JSONArray(prefs.getString("weather_places","[]"));(0 until a.length()).mapNotNull { runCatching { WeatherPlace.read(a.getJSONObject(it)) }.getOrNull() } }.getOrDefault(emptyList())
  return (base+custom).distinctBy { it.id }
 }
 fun find(id:String)=all().firstOrNull { it.id==id }
 fun add(place:WeatherPlace) {
  val custom=all().filter { it.id.startsWith("geo_") && it.id!=place.id }
  require(custom.size<20) { "登録地点は20件までです" }
  require(place.id.startsWith("geo_"))
  check(prefs.edit().putString("weather_places",JSONArray((custom+place).map { it.json() }).toString()).commit())
 }
 fun remove(id:String) {
  val remaining=all().filter { it.id.startsWith("geo_") && it.id!=id }
  val edit=prefs.edit().putString("weather_places",JSONArray(remaining.map { it.json() }).toString())
  if(prefs.getString("region","osaka")==id)edit.putString("region","osaka")
  edit.putStringSet("weather_broad",broad().filter { it!=id }.toSet()).apply()
 }
 fun broad():List<String> {
  val selected=prefs.getStringSet("weather_broad",setOf("osaka","tokyo"))!!
  return all().map { it.id }.filter { it in selected }
 }
 fun search(name:String):List<WeatherPlace> {
  require(name.trim().length in 2..80) { "地名を2〜80文字で入力してください" }
  return WeatherPlace.searchResults(network.fetch("https://geocoding-api.open-meteo.com/v1/search?name="+URLEncoder.encode(name.trim(),"UTF-8")+"&count=10&language=ja&countryCode=JP") {})
 }
}

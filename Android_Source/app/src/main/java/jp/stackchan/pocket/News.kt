package jp.stackchan.pocket
import android.content.Context
import java.time.*
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import java.io.StringReader

object NewsParser {
 fun headlines(xml:String,now:Instant,limit:Int=3):List<String> {
  require(limit in 1..10)
  require(xml.length<=128*1024 && !xml.contains("<!DOCTYPE",true) && !xml.contains("<!ENTITY",true)) { "ニュース形式が不正です" }
  val factory=DocumentBuilderFactory.newInstance().apply { isExpandEntityReferences=false }
  val doc=factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
  val nodes=doc.getElementsByTagName("item")
  val result=mutableListOf<String>()
  for(i in 0 until nodes.length) {
   val e=nodes.item(i) as org.w3c.dom.Element
   val title=e.getElementsByTagName("title").item(0)?.textContent?.trim().orEmpty()
   val date=e.getElementsByTagName("pubDate").item(0)?.textContent.orEmpty()
   val time=runCatching { ZonedDateTime.parse(date,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull() ?: continue
   val age=Duration.between(time,now).seconds
   if(age in -300..172800 && title.isNotBlank() && title.length<=200 && title !in result)result.add(title)
   if(result.size==limit)break
  }
  require(result.isNotEmpty()) { "新しい見出しがありません" };return result
 }
}
object NewsOptions {
 val categories=linkedMapOf("top-picks" to "総合", "domestic" to "国内", "world" to "国際", "business" to "経済", "entertainment" to "エンタメ", "sports" to "スポーツ", "it" to "IT", "science" to "科学", "local" to "地域")
 fun cacheKey(category:String,count:Int):String { require(category in categories && count in 1..10);return "$category:$count" }
}
class News(context:Context) {
 private val net=WeatherNetwork(context)
 private val prefs=context.getSharedPreferences("news_cache",Context.MODE_PRIVATE)
 private val settings=context.getSharedPreferences("settings",Context.MODE_PRIVATE)
 fun speech():String {
  val category=settings.getString("news_category","top-picks")!!.takeIf { it in NewsOptions.categories } ?: "top-picks"
  val count=settings.getInt("news_count",3).coerceIn(1,10)
  val key=NewsOptions.cacheKey(category,count);val name=NewsOptions.categories.getValue(category)
  val now=System.currentTimeMillis()
  val at=prefs.getLong("$key:at",0);val cached=prefs.getString("$key:speech",null)
  fun stamp(t:Long)=Instant.ofEpochMilli(t).atZone(ZoneId.of("Asia/Tokyo")).format(DateTimeFormatter.ofPattern("M月d日H時m分"))
  if(cached!=null && now-at in 0..300000)return "${stamp(at)}に取得した、ヤフーニュース、${name}の見出しです。$cached"
  return try {
   val feed=if(category=="top-picks") "topics" else "categories"
   val raw=net.fetch("https://news.yahoo.co.jp/rss/$feed/$category.xml") {}
   val titles=NewsParser.headlines(raw,Instant.ofEpochMilli(now),count)
   val body="${titles.size}件お伝えします。"+titles.mapIndexed { i,t -> "${i+1}件目。$t。" }.joinToString(" ")
   prefs.edit().putString("$key:speech",body).putLong("$key:at",now).apply()
   "${stamp(now)}に取得した、ヤフーニュース、${name}の見出しです。$body"
  } catch(e:Exception) {
   if(cached!=null && now-at in 0..21600000) "最新ニュースを取得できません。${stamp(at)}に保存した、${name}の見出しです。$cached"
   else "${name}のニュースを取得できませんでした。インターネット接続を確認してください。利用できる保存済みニュースはありません。"
  }
 }
}

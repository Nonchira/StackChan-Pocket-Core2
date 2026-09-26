package jp.stackchan.pocket
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
class NewsParserTest {
 private val now=Instant.parse("2026-09-24T16:00:00Z")
 private fun item(title:String,date:String="Thu, 24 Sep 2026 12:00:00 GMT")="<item><title>$title</title><pubDate>$date</pubDate></item>"
 private fun feed(items:String)="<rss><channel><title>配信名</title>$items</channel></rss>"
 @Test fun ignoresChannelTitleAndLimitsThree() { assertEquals(listOf("一","二","三"),NewsParser.headlines(feed(item("一")+item("二")+item("三")+item("四")),now)) }
 @Test fun decodesXmlEntitiesAndCdata() { assertEquals(listOf("A & B","見出し"),NewsParser.headlines(feed(item("A &amp; B")+item("<![CDATA[見出し]]>")),now)) }
 @Test fun excludesOldAndFutureAndDuplicates() { assertEquals(listOf("有効"),NewsParser.headlines(feed(item("古い","Mon, 21 Sep 2026 12:00:00 GMT")+item("未来","Fri, 25 Sep 2026 12:00:00 GMT")+item("有効")+item("有効")),now)) }
 @Test(expected=IllegalArgumentException::class) fun rejectsDtd() { NewsParser.headlines("<!DOCTYPE rss [<!ENTITY x SYSTEM 'file:///etc/passwd'>]>"+feed(item("&x;")),now) }
 @Test(expected=IllegalArgumentException::class) fun refusesNoHeadlines() { NewsParser.headlines(feed(""),now) }
}

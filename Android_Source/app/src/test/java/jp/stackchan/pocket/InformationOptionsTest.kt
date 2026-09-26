package jp.stackchan.pocket

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class InformationOptionsTest {
 private val now=Instant.parse("2026-09-26T00:00:00Z")
 private fun feed(n:Int)="<rss><channel>"+(1..n).joinToString("") { "<item><title>headline $it</title><pubDate>Sat, 26 Sep 2026 00:00:00 GMT</pubDate></item>" }+"</channel></rss>"
 @Test fun defaultThreeAndMaximumTen(){assertEquals(3,NewsParser.headlines(feed(12),now).size);assertEquals(10,NewsParser.headlines(feed(12),now,10).size)}
 @Test fun fewerItemsAreNotInvented(){assertEquals(2,NewsParser.headlines(feed(2),now,10).size)}
 @Test(expected=IllegalArgumentException::class) fun excessiveCountRejected(){NewsParser.headlines(feed(12),now,11)}
 @Test fun cachesAreSeparated(){assertNotEquals(NewsOptions.cacheKey("it",3),NewsOptions.cacheKey("it",10));assertNotEquals(NewsOptions.cacheKey("it",3),NewsOptions.cacheKey("science",3))}
 @Test fun searchCandidatesValidateCountryAndCoordinates(){
  val raw="""{"results":[{"id":1,"name":"札幌市","admin1":"北海道","country_code":"JP","latitude":43.06,"longitude":141.35},{"id":2,"name":"wrong","country_code":"US","latitude":10,"longitude":10},{"id":3,"name":"invalid","country_code":"JP","latitude":100,"longitude":10}]}"""
  val p=WeatherPlace.searchResults(raw);assertEquals(1,p.size);assertEquals("geo_1",p.single().id);assertEquals("北海道 札幌市",p.single().name)
 }
 @Test fun placeRoundTrip(){val p=WeatherPlace("geo_1","札幌",43.0,141.0);assertEquals(p,WeatherPlace.read(p.json()))}
 @Test fun emptySearch(){assertTrue(WeatherPlace.searchResults("{}").isEmpty())}
 @Test fun customPlaceForecastUsesItsName(){
  val raw="""{"daily":{"time":["2026-09-26"],"weather_code":[0],"temperature_2m_max":[24],"temperature_2m_min":[14],"precipitation_probability_max":[10]}}"""
  val text=ForecastParser.parse(raw,"geo_1",LocalDate.parse("2026-09-26"),"札幌市")
  assertTrue(text.contains("札幌市"));assertFalse(text.contains("京都"));assertTrue(text.contains("24度"))
 }
}

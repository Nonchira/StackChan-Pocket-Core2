package jp.stackchan.pocket
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
class PresetForecastTest {
 @Test fun osakaSelectsDateAndArea() {
  val raw="""[{"timeSeries":[{"timeDefines":["2026-09-26T11:00:00+09:00","2026-09-27T00:00:00+09:00"],"areas":[{"area":{"code":"270000"},"weathers":["雨","晴れ"]}]}]}]"""
  val result=ForecastParser.parse(raw,"osaka",LocalDate.parse("2026-09-27"))
  assertTrue(result.contains("大阪府"));assertTrue(result.contains("晴れ"));assertFalse(result.contains("雨"))
 }
 @Test fun presetsAndVoiceCommandsAgree() {
  assertEquals(setOf("osaka","tokyo"),ForecastParser.names.keys)
  assertEquals(RobotIntent("region","osaka"),RobotIntent.parse("天気の場所を大阪府にして"))
  assertEquals("130000",ForecastParser.officeCode("tokyo"))
  assertNull(RobotIntent.parse("天気の場所を京都市にして"))
 }
}

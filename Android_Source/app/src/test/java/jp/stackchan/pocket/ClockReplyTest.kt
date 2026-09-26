package jp.stackchan.pocket
import org.junit.Assert.*
import org.junit.Test
import java.time.*
class ClockReplyTest {
 private val now=ZonedDateTime.of(2026,9,25,0,5,0,0,ZoneId.of("Asia/Tokyo"))
 @Test fun timeAndPoliteness() { assertEquals("今は0時5分です。",ClockReply.answer("今何時ですか？",now)) }
 @Test fun dateAndWeekday() { assertEquals("今日は2026年9月25日、金曜日です。",ClockReply.answer("今日の日付を教えて",now)) }
 @Test fun era() { assertEquals("今年は令和8年です。",ClockReply.answer("いまは令和何年？",now)) }
 @Test fun newYearUsesSuppliedClock() { assertEquals("今年は令和9年です。",ClockReply.answer("令和何年",now.withYear(2027))) }
 @Test fun unrelatedQuestionsPassThrough() { assertNull(ClockReply.answer("明日の天気は何時に変わる？",now));assertNull(ClockReply.answer("令和は何年続くと思う？",now)) }
 @Test fun zoneChangesDate() { assertEquals("今日は2026年9月24日、木曜日です。",ClockReply.answer("今日は何日",now.withZoneSameInstant(ZoneId.of("UTC")))) }
}

package jp.stackchan.pocket
import java.time.ZonedDateTime
import java.time.chrono.JapaneseDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object ClockReply {
 fun answer(text:String,now:ZonedDateTime):String? {
  val q=text.replace(Regex("[\\s　、。？！?!]"),"")
   .replace(Regex("(を教えてください|を教えて|教えてください|教えて|ですか|なの|かな|でしょうか|だっけ)$"),"")
  val weekday=listOf("月","火","水","木","金","土","日")[now.dayOfWeek.value-1]
  return when(q) {
   "今何時","いま何時","今は何時","いまは何時","現在時刻","今の時刻","今の時間" -> "今は${now.hour}時${now.minute}分です。"
   "今日何曜日","今日は何曜日","きょうは何曜日","何曜日" -> "今日は${weekday}曜日です。"
   "今何日","今は何日","今日は何日","今日何日","今日は何月何日","今日の日付","日付","今の日付" -> "今日は${now.year}年${now.monthValue}月${now.dayOfMonth}日、${weekday}曜日です。"
   "今何年","今は何年","今年は何年","今年何年","西暦何年","今年は西暦何年" -> "今年は西暦${now.year}年です。"
   "令和何年","今は令和何年","いまは令和何年","今令和何年","今年は令和何年","今年の和暦","今は和暦何年" -> {
    if(now.year<1873) "端末の時計を確認してください。"
    else "今年は"+JapaneseDate.from(now.toLocalDate()).format(DateTimeFormatter.ofPattern("Gy年",Locale.JAPAN))+"です。"
   }
   else -> null
  }
 }
}

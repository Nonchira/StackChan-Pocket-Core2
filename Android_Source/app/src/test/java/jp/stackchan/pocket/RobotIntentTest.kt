package jp.stackchan.pocket
import org.junit.Assert.*
import org.junit.Test
class RobotIntentTest {
 @Test fun routesDirectMotionOnly() { assertEquals(RobotIntent("motion","right"),RobotIntent.parse("右を向いてください。"));assertNull(RobotIntent.parse("右を向いてと言ったら何が起きる？"));assertNull(RobotIntent.parse("右を向かないで")) }
 @Test fun volumeHasNoInferredNumber() { assertEquals(RobotIntent("volume.set","50"),RobotIntent.parse("音量を半分にして"));assertEquals(RobotIntent("volume.set","30"),RobotIntent.parse("音量を３０パーセントにして"));assertNull(RobotIntent.parse("音量をかなり下げるとどうなる")) }
 @Test fun locationsAreExplicitCommands() { assertEquals(RobotIntent("region","tokyo"),RobotIntent.parse("天気の場所を東京都にして"));assertNull(RobotIntent.parse("東京都の天気は？")) }
 @Test fun timerParsesSecondsMinutesAndKanji() { assertEquals(RobotIntent("timer","180"),RobotIntent.parse("3分たったら教えて"));assertEquals(RobotIntent("timer","180"),RobotIntent.parse("三分たったら教えて"));assertEquals(RobotIntent("timer","5"),RobotIntent.parse("５秒のタイマーをかけて")) }
 @Test fun timerCancellationIsNotNewTimer() { assertEquals(RobotIntent("timer.cancel"),RobotIntent.parse("タイマーを取り消して"));assertNull(RobotIntent.parse("3分前に教えてもらった話")) }
 @Test fun repeatIsNotAnActionReplay() { assertEquals(RobotIntent("repeat"),RobotIntent.parse("もう一度言って"));assertEquals(RobotIntent("repeat.last"),RobotIntent.parse("最後の部分だけ")) }
 @Test fun batteryAndModes() { assertEquals(RobotIntent("battery"),RobotIntent.parse("電池はあとどのくらい？"));assertEquals(RobotIntent("monologue","off"),RobotIntent.parse("独り言をやめて")) }
}

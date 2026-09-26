package jp.stackchan.pocket

data class RobotIntent(val op:String,val value:String="") {
 companion object {
  fun parse(text:String):RobotIntent? {
   val q=text.replace(Regex("[\\s　、。？！?!]"),"").removeSuffix("ください")
   val motion=mapOf("右を向いて" to "right","左を向いて" to "left","上を向いて" to "up","下を向いて" to "down","正面に戻って" to "center","前を向いて" to "center","うなずいて" to "nod","首をかしげて" to "tilt","踊って" to "dance")
   motion[q]?.let { return RobotIntent("motion",it) }
   when(q) {
    "少し小さくして","音量を下げて" -> return RobotIntent("volume.delta","-10")
    "少し大きくして","音量を上げて" -> return RobotIntent("volume.delta","10")
    "音量を半分にして" -> return RobotIntent("volume.set","50")
    "消音して","静かにして" -> return RobotIntent("volume.set","0")
    "電池はあとどのくらい","電池の残量は","バッテリー残量を教えて","電池残量を教えて" -> return RobotIntent("battery")
    "独り言を始めて" -> return RobotIntent("monologue","on")
    "独り言をやめて" -> return RobotIntent("monologue","off")
    "もう一度言って","もう一回言って" -> return RobotIntent("repeat")
    "最後の部分だけ","最後の部分だけもう一度" -> return RobotIntent("repeat.last")
    "タイマーを取り消して","タイマーを止めて" -> return RobotIntent("timer.cancel")
    "タイマーはあと何分","タイマーの残り時間を教えて" -> return RobotIntent("timer.remaining")
   }
   Regex("音量を([0-9０-９]{1,3})(?:パーセント|%)にして").matchEntire(q)?.let { return RobotIntent("volume.set",digits(it.groupValues[1]).toString()) }
   Regex("天気の(?:場所|地点)を(大阪府|東京都)にして").matchEntire(q)?.let { return RobotIntent("region",mapOf("大阪府" to "osaka","東京都" to "tokyo").getValue(it.groupValues[1])) }
   Regex("([0-9０-９]{1,4}|一|二|三|四|五|六|七|八|九|十)(分|秒)(?:たったら教えて|経ったら教えて|のタイマーをかけて|タイマー)").matchEntire(q)?.let {
    val n=mapOf("一" to 1,"二" to 2,"三" to 3,"四" to 4,"五" to 5,"六" to 6,"七" to 7,"八" to 8,"九" to 9,"十" to 10)[it.groupValues[1]] ?: digits(it.groupValues[1])
    return RobotIntent("timer",(n*(if(it.groupValues[2]=="分")60 else 1)).toString())
   }
   return null
  }
  private fun digits(s:String)=s.map { if(it in '０'..'９') '0'+(it-'０') else it }.joinToString("").toInt()
 }
}

package jp.stackchan.pocket
import org.json.JSONObject

/** Phone-side, bounded diagnostics. No audio or recognized text is retained. */
internal class PlaybackAudit {
 data class Sent(val generation:Long,val bytes:Int,val elapsedMs:Long,val maxGapMs:Long,val maxQueued:Long)
 private val pending=linkedMapOf<String,Sent>()
 private val lines=java.util.ArrayDeque<String>()
 private var missing=0
 @Synchronized fun expect(id:String,sent:Sent) {
  if(pending.size>=12){pending.remove(pending.keys.first());missing++}
  pending[id]=sent
 }
 @Synchronized fun accept(j:JSONObject,generation:Long):Boolean {
  val id=j.optString("requestId");val sent=pending.remove(id) ?: return false
  if(sent.generation!=generation)return false
  val received=j.optLong("pcmBytesReceived",-1);val accepted=j.optLong("pcmBytesAccepted",-1)
  val diff=if(received>=0)(sent.bytes-received).toString() else "不明"
  val result="送信登録 ${sent.bytes}B / 本体受信 ${received}B / 受付 ${accepted}B / 差分 ${diff}B\n"+
   "破棄 ${j.optLong("pcmBytesDropped",-1)}B / 不足 ${j.optLong("underflowResets",-1)}回 / 溢れ ${j.optLong("rxOverflowEvents",-1)}回\n"+
   "再生失敗 ${j.optLong("playRawFailEvents",-1)}回 / 再生キュー満杯 ${j.optLong("speakerQueueFullEvents",-1)}回\n"+
   "音声 ${sent.bytes*1000L/32000}ms / 送信 ${sent.elapsedMs}ms / 最大送信間隔 ${sent.maxGapMs}ms / 最大送信待ち ${sent.maxQueued}B"
  val transport="\nUSB起動後累計：CRC ${j.optLong("usbCrcErrorsTotal",-1)} / ヘッダー ${j.optLong("usbHeaderErrorsTotal",-1)} / 途中失効 ${j.optLong("usbParserTimeoutsTotal",-1)}\n"+
   "UARTバッファ満杯 ${j.optLong("usbUartBufferFullTotal",-1)} / FIFO溢れ ${j.optLong("usbUartFifoOverflowTotal",-1)} / 信号エラー ${j.optLong("usbUartFrameErrorsTotal",-1)}\n"+
   "USB処理の最大間隔 ${j.optLong("usbMaxServiceGapMs",-1)}ms / 受信待ち最大 ${j.optLong("usbMaxPendingBytes",-1)}B"
  if(lines.size>=12)lines.removeFirst();lines.addLast(result+transport+"\n最長処理 ${j.optString("loopLongestStage","未対応")} / ${j.optLong("loopLongestStageMs",-1)}ms");return true
 }
 @Synchronized fun cancel(){missing+=pending.size;pending.clear()}
 @Synchronized fun summary():String {
  val body=if(lines.isEmpty())"本体再生の診断結果はまだありません。" else lines.toList().mapIndexed { i,s -> "区間${i+1}\n$s" }.joinToString("\n\n")
  return "Core2音声転送診断（直近12区間）\n未応答 ${pending.size}件 / 取消・取りこぼし $missing 件\n"+
   "不足やキュー満杯だけで故障とは断定できません。音声本文は保存しません。\n\n"+body
 }
}

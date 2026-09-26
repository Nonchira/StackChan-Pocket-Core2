package jp.stackchan.pocket
import java.util.Locale
/** Same-segment synthesis wall time and output PCM duration; no text or audio retained. */
internal class TtsRtf {
 private var count=0
 private var totalMs=0L
 private var totalBytes=0L
 private var first=""
 fun add(synthesisMs:Long,pcmBytes:Int) {
  require(synthesisMs>=0 && pcmBytes>0 && pcmBytes%2==0)
  val durationMs=pcmBytes/32.0 // 16 kHz, signed 16-bit mono
  if(count==0)first=String.format(Locale.JAPAN,"最初の区間：合成 %dms / 音声 %.1fms / RTF %.4f",synthesisMs,durationMs,synthesisMs/durationMs)
  count++;totalMs+=synthesisMs;totalBytes+=pcmBytes
 }
 fun summary():String {
  if(count==0)return "TTS RTF：未計測"
  val durationMs=totalBytes/32.0
  return first+String.format(Locale.JAPAN,"\n返答全体：%d区間 / 合成合計 %dms / 音声合計 %.1fms / RTF %.4f",count,totalMs,durationMs,totalMs/durationMs)+
   "\nRTF＝合成所要時間÷生成音声時間。全体は合計時間の比。音声設定・PCM変換・通信・再生待ち・LLM・ASRは除外。合成API呼び出しから完了通知待ち終了までの実時間で、エンジン内部だけの時間ではありません。"
 }
}

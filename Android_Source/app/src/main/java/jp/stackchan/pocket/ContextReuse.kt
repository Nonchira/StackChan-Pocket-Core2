package jp.stackchan.pocket
internal object ContextReuse {
 fun reason(enabled:Boolean,monologue:Boolean,valid:Boolean,old:List<ConversationMemory.Turn>,current:List<ConversationMemory.Turn>,oldMode:Int,mode:Int):String=when {
  !enabled -> "設定OFF"
  monologue -> "独り言は独立処理"
  !valid -> "初回・中断・モデル再読み込み等"
  oldMode!=mode -> "返答の長さを変更"
  old!=current -> "履歴の整理・リセット・本体操作等で内容変更"
  else -> "一致した完了済み履歴を再利用"
 }
 fun allowed(enabled:Boolean,monologue:Boolean,valid:Boolean,old:List<ConversationMemory.Turn>,current:List<ConversationMemory.Turn>,oldMode:Int,mode:Int)=enabled && !monologue && valid && old==current && oldMode==mode
}

package jp.stackchan.pocket

/** One immutable snapshot; disk presence is deliberately queried separately. */
data class ModelState(val phase:String="unloaded",val detail:String="保存済みモデルは未読み込み",val backend:String="",val percent:Int?=null)

object ModelRuntime {
    @Volatile var state=ModelState()
        private set
    private var owner:Any?=null
    private var importing=false
    @Synchronized fun beginImport():Boolean {
        if(owner!=null || importing)return false
        importing=true;return true
    }
    @Synchronized fun endImport(){ importing=false }
    @Synchronized fun beginLoad(token:Any):Boolean {
        if(owner!=null || importing)return false
        owner=token;state=ModelState("loading","読み込みの準備中");return true
    }
    @Synchronized fun update(token:Any,value:ModelState) { if(owner===token)state=value }
    @Synchronized fun release(token:Any,error:String?=null) {
        if(owner!==token)return
        state=if(error==null)ModelState("unloaded","解放済み・保存ファイルは保持") else ModelState("failed",error)
        owner=null
    }
    @Synchronized fun available()=owner==null && !importing
    @Synchronized fun owns(token:Any)=owner===token
    @Synchronized fun isImporting()=importing
}

object ImportProgress {
    fun percent(copied:Long,total:Long?):Int? = total?.takeIf { it>0 }?.let {
        ((copied.toDouble()/it)*100).toInt().coerceIn(0,100)
    }
}

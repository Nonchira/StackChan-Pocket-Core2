package jp.stackchan.pocket

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class ImportState(val name:String="",val phase:String="",val copied:Long=0,val total:Long?=null)
object ModelImporter {
    @Volatile var state=ImportState()
        private set
    val names=listOf("sensevoice.onnx","tokens.txt","silero_vad.onnx","model.litertlm","model.gguf")
    fun start(context:Context,uri:Uri,name:String):Boolean {
        if(name !in names || !ModelRuntime.beginImport())return false
        val app=context.applicationContext
        state=ImportState(name,"コピー準備中")
        Thread({
            val staging=File(app.filesDir,"$name.part")
            try {
                var original=name
                var total:Long?=null
                app.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE),null,null,null)?.use { c ->
                    if(c.moveToFirst()) {
                        val n=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(n>=0 && !c.isNull(n))original=c.getString(n)
                        val z=c.getColumnIndex(OpenableColumns.SIZE);if(z>=0 && !c.isNull(z))total=c.getLong(z).takeIf { it>0 }
                    }
                }
                state=ImportState(original,"コピー中",0,total)
                app.contentResolver.openInputStream(uri).use { source ->
                    requireNotNull(source) { "ファイルを開けません" }
                    staging.outputStream().use { out ->
                        val buffer=ByteArray(1024*1024);var count=0L
                        while(true) {
                            val n=source.read(buffer);if(n<0)break
                            out.write(buffer,0,n);count+=n
                            state=ImportState(original,"コピー中",count,total)
                        }
                        out.fd.sync()
                    }
                }
                state=state.copy(phase="確認中（まだ完了していません）")
                total?.let { require(staging.length()==it) { "取得容量が一致しません。ダウンロードや保存先を確認してください" } }
                ModelImport.validate(name,staging)
                state=state.copy(phase="保存確定中")
                Files.move(staging.toPath(),File(app.filesDir,name).toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
                app.getSharedPreferences("settings",Context.MODE_PRIVATE).edit().putString("model_name_$name",original).apply()
                state=state.copy(phase="取り込み完了（RAMには未読み込み）")
            } catch(e:Exception) {
                staging.delete();state=state.copy(phase="取り込み失敗：${e.message}（既存ファイルは保持）")
            } finally { ModelRuntime.endImport() }
        },"model-import").start()
        return true
    }
}

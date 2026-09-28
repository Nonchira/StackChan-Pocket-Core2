package jp.stackchan.pocket

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Validate staging files before replacing an already imported model. */
object ModelImport {
    fun validate(name:String,file:File) {
        require(file.length()>0) { "ファイルが空です" }
        val header=file.inputStream().use { it.readNBytes(24) }
        val gguf=header.size>=4 && String(header,0,4,Charsets.US_ASCII)=="GGUF"
        if(name=="model.gguf") {
            require(gguf && header.size==24) { "GGUFファイルではありません（ダウンロード済みのモデル本体を選んでください）" }
            val b=ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            require(b.getInt(4) in 2..3 && b.getLong(8)>0 && b.getLong(16)>0 && file.length()>24) { "GGUFヘッダーが不正、または対応外の形式です" }
        } else if(name=="model.litertlm") {
            require(!gguf) { "GGUFは「LLM .gguf」から取り込み、llama.cppを選んでください" }
        }
    }
}

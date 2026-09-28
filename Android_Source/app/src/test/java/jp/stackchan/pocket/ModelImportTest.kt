package jp.stackchan.pocket
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class ModelImportTest {
    private fun staged(bytes:ByteArray,run:(File)->Unit) {
        val f=File.createTempFile("model-import",".part")
        try { f.writeBytes(bytes);run(f) } finally { f.delete() }
    }
    private fun header(version:Int=3)=ByteBuffer.allocate(25).order(ByteOrder.LITTLE_ENDIAN)
        .put("GGUF".toByteArray()).putInt(version).putLong(1).putLong(1).put(0).array()
    @Test fun wrongFormatCannotReplaceLiteRtModel()=staged(header()) { f ->
        assertThrows(IllegalArgumentException::class.java) { ModelImport.validate("model.litertlm",f) }
    }
    @Test fun rejectsDownloadErrorPagesAndTruncatedFiles() {
        for(bytes in listOf(byteArrayOf(),"<html>Access denied</html>".toByteArray(),"GGUF".toByteArray(),header(99)))staged(bytes) { f ->
            assertThrows(IllegalArgumentException::class.java) { ModelImport.validate("model.gguf",f) }
        }
    }
    @Test fun recognizesGgufHeaderWithoutClaimingModelCompatibility()=staged(header()) { f -> ModelImport.validate("model.gguf",f) }
}

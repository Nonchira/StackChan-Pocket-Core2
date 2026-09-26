package jp.stackchan.pocket

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

object SerialFrames {
    const val MAX=8192
    data class Frame(val type:Int,val sequence:Long,val payload:ByteArray)
    fun encode(type:Int,sequence:Long,payload:ByteArray):ByteArray {
        require(payload.size<=MAX)
        val result=ByteArray(20+payload.size)
        val b=ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN)
        b.put(byteArrayOf(83,67,85,49,1,type.toByte(),0,0));b.putInt(sequence.toInt());b.putInt(payload.size);b.put(payload)
        val crc=CRC32();crc.update(result,4,12+payload.size);b.putInt(crc.value.toInt());return result
    }
    class Decoder {
        private var buffer=ByteArray(0)
        fun accept(bytes:ByteArray):List<Frame> {
            require(bytes.size<=16384)
            buffer+=bytes
            val frames=mutableListOf<Frame>();var offset=0
            while(buffer.size-offset>=16) {
                if(buffer[offset]!=83.toByte() || buffer[offset+1]!=67.toByte() || buffer[offset+2]!=85.toByte() || buffer[offset+3]!=49.toByte() || buffer[offset+4]!=1.toByte()) { offset++;continue }
                val b=ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
                val length=b.getInt(offset+12)
                if(length !in 0..MAX){offset++;continue}
                if(buffer.size-offset<20+length)break
                val crc=CRC32();crc.update(buffer,offset+4,12+length)
                if(crc.value!=(b.getInt(offset+16+length).toLong() and 0xffffffffL)){offset++;continue}
                frames+=Frame(buffer[offset+5].toInt() and 255,b.getInt(offset+8).toLong() and 0xffffffffL,buffer.copyOfRange(offset+16,offset+16+length))
                offset+=20+length
            }
            buffer=buffer.copyOfRange(offset,buffer.size)
            return frames
        }
        fun reset(){buffer=ByteArray(0)}
    }
}

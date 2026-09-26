package jp.stackchan.pocket

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

object AudioData {
    data class Mic(val sequence: Long, val start: Boolean, val samples: FloatArray)
    fun mic(bytes: ByteArray): Mic {
        require(bytes.size >= 16 && String(bytes, 0, 4, Charsets.US_ASCII) == "MIC1")
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val count = b.getShort(12).toInt() and 65535
        require(count in 1..1600 && bytes.size == 16 + count * 2)
        return Mic(b.getInt(4).toLong() and 0xffffffffL, (b.getShort(14).toInt() and 1) != 0,
            FloatArray(count) { b.getShort(16 + 2 * it) / 32768f })
    }
    // Android TTS engines may return different PCM sample rates; Core2 expects 16 kHz mono.
    fun wavToPcm16k(bytes: ByteArray): ByteArray {
        require(bytes.size >= 44 && String(bytes,0,4) == "RIFF" && String(bytes,8,4) == "WAVE")
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var p = 12; var channels = 0; var rate = 0; var bits = 0; var format = 0
        var data = -1; var length = 0
        while (p + 8 <= bytes.size) {
            val n = b.getInt(p+4); require(n >= 0 && n <= bytes.size - p - 8)
            when (String(bytes,p,4)) {
                "fmt " -> { require(n >= 16); format=b.getShort(p+8).toInt(); channels=b.getShort(p+10).toInt(); rate=b.getInt(p+12); bits=b.getShort(p+22).toInt() }
                "data" -> { data=p+8; length=n }
            }
            p += 8 + n + (n and 1)
        }
        require(format == 1 && bits == 16 && channels in 1..2 && rate in 8000..96000 && data >= 0)
        require(length % (2*channels) == 0 && length > 0)
        val count = length / (2*channels)
        fun sample(i: Int): Double = (0 until channels).sumOf { b.getShort(data+i*channels*2+it*2).toDouble() } / channels
        val outputCount = (count.toLong()*16000/rate).toInt()
        val out = ByteBuffer.allocate(outputCount*2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until outputCount) {
            val x = i.toDouble()*rate/16000; val k = floor(x).toInt().coerceAtMost(count-1)
            val s = sample(k)*(1-(x-k)) + sample(min(k+1,count-1))*(x-k)
            out.putShort(s.roundToInt().coerceIn(-32768,32767).toShort())
        }
        return out.array()
    }
}

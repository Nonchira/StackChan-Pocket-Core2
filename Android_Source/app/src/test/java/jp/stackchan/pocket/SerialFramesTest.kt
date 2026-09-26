package jp.stackchan.pocket
import org.junit.Assert.*
import org.junit.Test
class SerialFramesTest {
    @Test fun matchesIndependentCrc32Vector() {
        val expected="534355310101000078563412020000007b7d7360852b".chunked(2).map{it.toInt(16).toByte()}.toByteArray()
        assertArrayEquals(expected,SerialFrames.encode(1,0x12345678,"{}".toByteArray()))
        assertEquals("{}",SerialFrames.Decoder().accept(expected).single().payload.toString(Charsets.UTF_8))
    }
    @Test fun fragmentedEveryByteAndLogNoise() {
        val d=SerialFrames.Decoder();val results=mutableListOf<SerialFrames.Frame>()
        val frame=SerialFrames.encode(3,42,byteArrayOf(1,2,3))
        for(b in "boot log\r\n".toByteArray()+frame)results+=d.accept(byteArrayOf(b))
        assertEquals(1,results.size);assertEquals(42L,results[0].sequence);assertArrayEquals(byteArrayOf(1,2,3),results[0].payload)
    }
    @Test fun multipleFramesAndUnsignedSequence() {
        val frames=SerialFrames.Decoder().accept(SerialFrames.encode(1,0xffffffffL,byteArrayOf())+SerialFrames.encode(2,0,byteArrayOf(7)))
        assertEquals(2,frames.size);assertEquals(0xffffffffL,frames[0].sequence);assertEquals(2,frames[1].type)
    }
    @Test fun corruptedCrcResynchronizes() {
        val bad=SerialFrames.encode(1,1,byteArrayOf(8));bad[16]=9
        val good=SerialFrames.encode(1,2,byteArrayOf(10))
        val out=SerialFrames.Decoder().accept(bad+good)
        assertEquals(1,out.size);assertEquals(2L,out[0].sequence)
    }
    @Test fun invalidLengthResynchronizes() {
        val bad=SerialFrames.encode(1,1,byteArrayOf());for(i in 12..15)bad[i]=127
        val out=SerialFrames.Decoder().accept(bad+SerialFrames.encode(1,3,byteArrayOf(9)))
        assertEquals(1,out.size);assertEquals(3L,out[0].sequence)
    }
    @Test fun partialOldSessionDiscarded() {
        val d=SerialFrames.Decoder();val data=SerialFrames.encode(2,9,ByteArray(100))
        assertTrue(d.accept(data.copyOfRange(0,25)).isEmpty());d.reset()
        assertEquals(1,d.accept(SerialFrames.encode(1,10,byteArrayOf())).size)
    }
    @Test fun payloadMagicIsNotAFrame() {
        val payload="SCU1 SCU1".toByteArray();val out=SerialFrames.Decoder().accept(SerialFrames.encode(3,0,payload))
        assertEquals(1,out.size);assertArrayEquals(payload,out[0].payload)
    }
    @Test fun boundedPayloadAndNoise() {
        val d=SerialFrames.Decoder();repeat(20){assertTrue(d.accept(ByteArray(16384){32}).isEmpty())}
        val bytes=SerialFrames.encode(3,4,ByteArray(8192){it.toByte()})
        assertEquals(8192,d.accept(bytes).single().payload.size)
    }
    @Test(expected=IllegalArgumentException::class) fun oversizedSendRejected(){SerialFrames.encode(2,0,ByteArray(8193))}
}

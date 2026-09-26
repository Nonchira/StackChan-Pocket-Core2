package jp.stackchan.pocket
import org.junit.Test
import org.junit.Assert.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDate

class DataTest {
    @Test fun micPacketAndSignedSamples() {
        val b=ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
        b.put("MIC1".toByteArray()).putInt(-1).putInt(50).putShort(2).putShort(1).putShort(-32768).putShort(16384)
        val m=AudioData.mic(b.array()); assertEquals(0xffffffffL,m.sequence); assertTrue(m.start)
        assertArrayEquals(floatArrayOf(-1f,.5f),m.samples,0f)
    }
    @Test(expected=IllegalArgumentException::class) fun truncatedMicRejected(){ AudioData.mic("MIC1".toByteArray()) }
    @Test(expected=IllegalArgumentException::class) fun countMismatchRejected(){
        val b=ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN); b.put("MIC1".toByteArray()); b.putShort(12,640); AudioData.mic(b.array())
    }
    @Test fun stereo48kConvertedToMono16k() {
        val b=ByteBuffer.allocate(44+24).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(60).put("WAVEfmt ".toByteArray()).putInt(16)
        b.putShort(1).putShort(2).putInt(48000).putInt(192000).putShort(4).putShort(16)
        b.put("data".toByteArray()).putInt(24)
        repeat(6) { b.putShort(1000).putShort(3000) }
        val out=ByteBuffer.wrap(AudioData.wavToPcm16k(b.array())).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(4,out.remaining()); assertEquals(2000,out.short.toInt()); assertEquals(2000,out.short.toInt())
    }
    @Test fun cityMatchesRequestedDateNotArrayPosition() {
        val raw="""{"daily":{"time":["2026-09-23","2026-09-24"],"weather_code":[61,0],"temperature_2m_max":[20,30],"temperature_2m_min":[10,20],"precipitation_probability_max":[80,10]}}"""
        val text=ForecastParser.parse(raw,"geo_osaka",LocalDate.parse("2026-09-24"),"大阪市")
        assertTrue(text.contains("快晴")); assertTrue(text.contains("30度")); assertTrue(text.contains("大阪市"))
    }
    @Test(expected=IllegalStateException::class) fun oldForecastNotRebrandedAsToday() {
        ForecastParser.parse("""{"daily":{"time":["2026-09-23"]}}""","geo_osaka",LocalDate.parse("2026-09-24"),"大阪市")
    }
    @Test fun tokyoExcludesIslands() {
        val raw="""[{"timeSeries":[{"timeDefines":["2026-09-24T00:00:00+09:00"],"areas":[{"area":{"code":"130020"},"weathers":["晴れ"]},{"area":{"code":"130010"},"weathers":["雨"]}]}]}]"""
        val text=ForecastParser.parse(raw,"tokyo",LocalDate.parse("2026-09-24"))
        assertTrue(text.contains("東京地方")); assertTrue(text.contains("雨")); assertFalse(text.contains("晴れ"))
    }
}

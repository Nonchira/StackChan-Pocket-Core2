package jp.stackchan.pocket

import org.junit.Assert.*
import org.junit.Test

class PlaybackMouthTest {
    private fun pcm(vararg samples:Int)=ByteArray(samples.size*2).also { out->
        samples.forEachIndexed { i,v->out[2*i]=v.toByte();out[2*i+1]=(v shr 8).toByte() }
    }
    @Test fun measuresBothSignsWithoutCancellation() {
        assertEquals(1000,PlaybackMouth.envelope(pcm(1000,-1000,1000,-1000),0))
    }
    @Test fun minimumSignedSampleDoesNotOverflow() {
        assertEquals(32768,PlaybackMouth.envelope(pcm(-32768),0))
    }
    @Test fun playbackPositionControlsMouthNotBufferedFutureAudio() {
        val samples=IntArray(1920){if(it in 640..1279)4000 else 0}
        val bytes=pcm(*samples)
        assertEquals(0,PlaybackMouth.envelope(bytes,0))
        assertEquals(4000,PlaybackMouth.envelope(bytes,640))
        assertEquals(0,PlaybackMouth.envelope(bytes,1280))
    }
    @Test fun endOfPlaybackAndEmptyInputCloseMouth() {
        assertEquals(0,PlaybackMouth.envelope(byteArrayOf(),0))
        assertEquals(0,PlaybackMouth.envelope(pcm(10000),1))
        assertEquals(0,PlaybackMouth.envelope(pcm(10000),Long.MAX_VALUE))
        assertEquals(0,PlaybackMouth.envelope(pcm(10000),-1))
    }
}

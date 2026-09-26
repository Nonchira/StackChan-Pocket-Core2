package jp.stackchan.pocket

/** Measure PCM at the consumed AudioTrack position, not at the write-ahead position. */
internal object PlaybackMouth {
    /** Deliberate animation during phone playback, independent of TTS amplitude. */
    fun animated(totalFrames:Long,playedFrames:Long):Int {
        if(playedFrames<=0 || playedFrames>=totalFrames)return 0
        val phase=((playedFrames%5760)/960).toInt() // six 60 ms steps at 16 kHz
        return intArrayOf(800,1800,3000,1800,600,0)[phase]
    }

    fun envelope(pcm:ByteArray,playedFrames:Long):Int {
        if(playedFrames<0 || playedFrames>=pcm.size/2)return 0
        val start=playedFrames.toInt()
        val end=minOf(pcm.size/2,start+640) // 40 ms at 16 kHz
        var sum=0L
        for(i in start until end) {
            val sample=((pcm[i*2].toInt() and 255) or (pcm[i*2+1].toInt() shl 8)).toShort().toInt()
            sum+=kotlin.math.abs(sample)
        }
        return (sum/(end-start)).toInt()
    }
}

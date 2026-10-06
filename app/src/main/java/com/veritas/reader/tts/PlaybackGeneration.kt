package com.veritas.reader.tts

/** Cancellation alone cannot stop a native call; reject its late result explicitly. */
internal class PlaybackGeneration {
    private var value = 0L
    @Synchronized fun current(): Long = value
    @Synchronized fun isCurrent(token: Long): Boolean = value == token
    @Synchronized fun advance(invalidate: () -> Unit = {}): Long {
        value++
        invalidate()
        return value
    }
    @Synchronized fun publishIfCurrent(token: Long, publish: () -> Unit): Boolean {
        if (value != token) return false
        publish()
        return true
    }
}

internal interface PcmOutput {
    fun write(samples: ShortArray, offset: Int, count: Int): Int
    fun renderedFrames(): Long
}

internal object PcmCompletion {
    suspend fun play(
        samples: ShortArray, sampleRate: Int, output: PcmOutput,
        isActive: () -> Boolean,
        now: () -> Long,
        waitForOutput: suspend () -> Unit
    ): Boolean {
        if (samples.isEmpty() || sampleRate <= 0 || !isActive()) return false
        val started = output.renderedFrames()
        var submitted = 0
        while (submitted < samples.size && isActive()) {
            val written = output.write(samples, submitted, samples.size - submitted)
            if (written <= 0 || written > samples.size - submitted) return false
            submitted += written
        }
        if (submitted != samples.size || !isActive()) return false
        val timeoutAt = now() + (samples.size.toDouble() / sampleRate * 2000).toLong() + 2_000L
        // AudioTrack exposes an unsigned 32-bit counter, which also wraps after
        // 0xffffffff. Flush/stop must invalidate this output generation first.
        fun renderedSinceStart() = (output.renderedFrames() - started) and 0xffffffffL
        while (isActive() && renderedSinceStart() < submitted && now() < timeoutAt) waitForOutput()
        return isActive() && renderedSinceStart() >= submitted
    }
}

package com.veritas.reader.tts

import kotlin.math.*

/** Mono engine PCM retains headroom until the playback/export sink quantizes it. */
data class FloatPcmAudio(val samples: FloatArray, val sampleRate: Int) {
    init { require(sampleRate in 8000..192000); require(samples.all(Float::isFinite)) { "Non-finite engine audio." } }
    fun pcm16(): ShortArray {
        val limiter = PcmPeakLimiter(sampleRate)
        return limiter.process(samples) + limiter.finish()
    }
}

/** Sample-peak protection with 5 ms lookahead, 0.5 ms attack and 50 ms release. No loudness normalization. */
internal class PcmPeakLimiter(sampleRate: Int) {
    private val lookAhead = (sampleRate * .005).roundToInt().coerceAtLeast(1)
    private val attack = exp(-1.0 / (.0005 * sampleRate))
    private val release = exp(-1.0 / (.05 * sampleRate))
    private val pending = java.util.ArrayDeque<Float>()
    private data class Peak(val index: Long, val value: Float)
    private val peaks = java.util.ArrayDeque<Peak>()
    private var added = 0L
    private var emitted = 0L
    private var gain = 1.0
    private var finished = false
    var limitedFrames = 0L; private set

    init { require(sampleRate in 8000..192000) }

    fun process(samples: FloatArray): ShortArray {
        check(!finished)
        require(samples.all(Float::isFinite)) { "Non-finite audio cannot be rendered." }
        val result = ShortArray((pending.size + samples.size - lookAhead).coerceAtLeast(0))
        var out = 0
        for (sample in samples) {
            pending.addLast(sample)
            val magnitude = abs(sample)
            while (peaks.isNotEmpty() && peaks.last.value <= magnitude) peaks.removeLast()
            peaks.addLast(Peak(added++, magnitude))
            if (pending.size > lookAhead) result[out++] = emit()
        }
        return result
    }

    fun finish(): ShortArray {
        if (finished) return ShortArray(0)
        finished = true
        return ShortArray(pending.size) { emit() }
    }

    private fun emit(): Short {
        val sample = pending.removeFirst().toDouble()
        val maximum = peaks.first.value.toDouble()
        val target = if (maximum > CEILING) CEILING / maximum else 1.0
        val coefficient = if (target < gain) attack else release
        gain = target + (gain - target) * coefficient
        if (abs(sample) * gain > CEILING) gain = CEILING / abs(sample)
        if (gain < .999999) limitedFrames++
        if (peaks.first.index == emitted++) peaks.removeFirst()
        return (sample * gain * 32768.0).roundToInt().coerceIn(-32768, 32767).toShort()
    }

    companion object { const val CEILING = .98 }
}

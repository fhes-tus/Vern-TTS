package com.veritas.reader

import kotlin.math.log10

/** Perceived loudness, with silence at zero and no invented motion between phrases. */
internal object SpeechAmplitude {
    fun normalized(meanSquare: Double): Float {
        if (meanSquare <= 0.000003) return 0f
        val db = 10 * log10(meanSquare)
        return ((db + 55) / 45).toFloat().coerceIn(0f, 1f)
    }

    fun pcmLevel(pcm: ShortArray, endFrame: Int, sampleRate: Int): Float {
        val end = endFrame.coerceIn(0, pcm.size)
        val start = (end - sampleRate / 20).coerceAtLeast(0)
        if (end <= start) return 0f
        var sum = 0.0
        for (i in start until end) { val sample = pcm[i] / 32768.0; sum += sample * sample }
        return normalized(sum / (end - start))
    }
}

/** Compact 50 ms envelope; supports split PCM callbacks without holding raw audio. */
internal class SpeechEnvelope(val sampleRate: Int, format: Int, channels: Int) {
    private val bytesPerSample = when (format) { 2 -> 2; 3 -> 1; 4 -> 4; else -> 0 }
    private val windowSamples = (sampleRate * channels.coerceAtLeast(1) / 20).coerceAtLeast(1)
    private val levels = ArrayList<Float>()
    private val pending = ArrayList<Byte>(4)
    private var sum = 0.0
    private var count = 0

    @Synchronized fun append(bytes: ByteArray) {
        if (bytesPerSample == 0) return
        for (byte in bytes) {
            pending.add(byte)
            if (pending.size < bytesPerSample) continue
            val sample = when (bytesPerSample) {
                1 -> ((pending[0].toInt() and 255) - 128) / 128.0
                2 -> (((pending[0].toInt() and 255) or (pending[1].toInt() shl 8)).toShort()) / 32768.0
                else -> Float.fromBits((pending[0].toInt() and 255) or ((pending[1].toInt() and 255) shl 8) or
                    ((pending[2].toInt() and 255) shl 16) or (pending[3].toInt() shl 24)).toDouble()
            }
            pending.clear()
            val finite = if (sample.isFinite()) sample.coerceIn(-1.0, 1.0) else 0.0
            sum += finite * finite; count++
            if (count == windowSamples) {
                if (levels.size < 2_400) levels.add(SpeechAmplitude.normalized(sum / count))
                sum = 0.0; count = 0
            }
        }
    }

    @Synchronized fun levelAt(millis: Long): Float {
        if (millis < 0) return 0f
        val index = (millis / 50).toInt()
        return levels.getOrNull(index) ?: if (index == levels.size && count > 0) SpeechAmplitude.normalized(sum / count) else 0f
    }
}

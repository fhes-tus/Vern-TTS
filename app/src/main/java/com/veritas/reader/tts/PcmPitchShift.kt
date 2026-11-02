package com.veritas.reader.tts

import kotlin.math.*
import com.veritas.reader.SpeechDelivery

/** Mono speech pitch adjustment with waveform-aligned overlap/add; duration stays fixed. */
internal object PcmPitchShift {
    fun apply(input: ShortArray, sampleRate: Int, pitch: Float): ShortArray {
        if (input.isEmpty() || abs(SpeechDelivery.pitch(pitch) - 1f) < .0001f) {
            require(sampleRate > 0); return input
        }
        return FloatPcmAudio(apply(FloatArray(input.size) { input[it] / 32768f }, sampleRate, pitch), sampleRate).pcm16()
    }

    fun apply(input: FloatArray, sampleRate: Int, pitch: Float): FloatArray {
        require(sampleRate > 0)
        require(input.all(Float::isFinite)) { "Non-finite pitch input." }
        val factor = SpeechDelivery.pitch(pitch).toDouble()
        if (input.isEmpty() || abs(factor - 1.0) < 0.0001) return input
        val window = (sampleRate * 0.025).roundToInt().coerceAtLeast(32)
        val hop = window / 2
        val search = (sampleRate * 0.005).roundToInt().coerceAtLeast(1)
        // Resampling changes pitch. WSOLA below restores the original duration.
        val resampled = BandLimitedResampler.convert(input, factor)
        val resampledSize = resampled.size
        val samples = resampled.copyOf(resampledSize + window)
        val output = FloatArray(input.size + window)
        val weights = FloatArray(output.size)
        val envelope = FloatArray(window) { i -> (0.5 - 0.5 * cos(2 * PI * (i + 0.5) / window)).toFloat() }
        var destination = 0
        while (destination < input.size) {
            if (Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException()
            val expected = (destination / factor).roundToInt().coerceIn(0, resampledSize)
            var source = expected
            if (destination > 0) {
                var best = Double.NEGATIVE_INFINITY
                for (candidate in maxOf(0, expected - search)..minOf(resampledSize, expected + search) step 2) {
                    var cross = 0.0; var a2 = 0.0; var b2 = 0.0
                    for (i in 0 until hop step 4) {
                        val at = destination + i
                        val a = if (weights[at] > 0.0001f) output[at] / weights[at] else 0f
                        val b = samples[candidate + i]
                        cross += a * b; a2 += a * a; b2 += b * b
                    }
                    val score = cross / sqrt((a2 * b2).coerceAtLeast(1e-20))
                    if (score > best) { best = score; source = candidate }
                }
            }
            for (i in 0 until window) {
                output[destination + i] += samples[source + i] * envelope[i]
                weights[destination + i] += envelope[i]
            }
            destination += hop
        }
        return FloatArray(input.size) { i -> output[i] / weights[i].coerceAtLeast(.0001f) }
    }
}

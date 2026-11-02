package com.veritas.reader.tts

import kotlin.math.*

/** Model repair precedes pitch. Tone/gain can be bypassed; peak protection belongs to the final sink. */
internal object KokoroVoiceProcessor {
    fun render(raw: FloatArray, sampleRate: Int, pitch: Float, shaping: Boolean): FloatPcmAudio {
        require(raw.all(Float::isFinite)) { "Non-finite model output." }
        val working = raw.copyOf()
        if (shaping) {
            BiquadSection.notch(sampleRate, sampleRate / 5.0, 60.0).process(working)
            BiquadSection.notch(sampleRate, 2 * sampleRate / 5.0, 60.0).process(working)
            BiquadSection.shelf(sampleRate, 5000.0, 2.5).process(working)
        }
        val shifted = PcmPitchShift.apply(working, sampleRate, pitch)
        if (shaping) for (i in shifted.indices) shifted[i] *= 1.7f
        return FloatPcmAudio(shifted, sampleRate)
    }
}

/** Cascaded sections keep double coefficients/state; transposed DF-II uses two state values. */
internal class BiquadSection private constructor(private val b0: Double, private val b1: Double,
    private val b2: Double, private val a1: Double, private val a2: Double) {
    private var z1 = 0.0; private var z2 = 0.0
    init {
        require(listOf(b0, b1, b2, a1, a2).all(Double::isFinite))
        require(abs(a2) < 1 && 1 + a1 + a2 > 0 && 1 - a1 + a2 > 0) { "Unstable filter." }
    }
    fun process(samples: FloatArray) {
        require(samples.all(Float::isFinite))
        for (i in samples.indices) {
            val x = samples[i].toDouble(); val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2; z2 = b2 * x - a2 * y
            samples[i] = y.toFloat()
        }
        check(z1.isFinite() && z2.isFinite() && samples.all(Float::isFinite)) { "Non-finite filter output." }
    }
    fun reset() { z1 = 0.0; z2 = 0.0 }
    companion object {
        private fun frequency(rate: Int, hz: Double): Double {
            require(rate in 8000..192000 && hz.isFinite() && hz > 0 && hz < rate / 2.0)
            return 2 * PI * hz / rate
        }
        fun notch(rate: Int, hz: Double, q: Double): BiquadSection {
            require(q.isFinite() && q in .1..100.0)
            val w = frequency(rate, hz); val alpha = sin(w) / (2 * q); val a0 = 1 + alpha
            return BiquadSection(1 / a0, -2 * cos(w) / a0, 1 / a0, -2 * cos(w) / a0, (1 - alpha) / a0)
        }
        fun shelf(rate: Int, hz: Double, gainDb: Double): BiquadSection {
            require(gainDb.isFinite() && gainDb in -12.0..12.0)
            val w = frequency(rate, hz); val a = 10.0.pow(gainDb / 40); val c = cos(w)
            val alpha = sin(w) / 2 * sqrt((a + 1 / a) * (1 / .7 - 1) + 2)
            val t = 2 * sqrt(a) * alpha; val a0 = a + 1 - (a - 1) * c + t
            return BiquadSection(a * (a + 1 + (a - 1) * c + t) / a0,
                -2 * a * (a - 1 + (a + 1) * c) / a0, a * (a + 1 + (a - 1) * c - t) / a0,
                2 * (a - 1 - (a + 1) * c) / a0, (a + 1 - (a - 1) * c - t) / a0)
        }
    }
}

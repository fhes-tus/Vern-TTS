package com.veritas.reader

import com.veritas.reader.tts.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class SpeechDspTest {
    @Test fun floatingPointHeadroomSurvivesPitchUntilFinalPeakProtection() {
        val input = FloatArray(24000) { (1.8 * sin(2 * PI * 220 * it / 24000)).toFloat() }
        val pitched = PcmPitchShift.apply(input, 24000, 1.2f)
        assertEquals(input.size, pitched.size)
        assertTrue(pitched.any { abs(it) > 1 })
        val pcm = FloatPcmAudio(pitched, 24000).pcm16()
        assertTrue(pcm.all { abs(it.toInt()) <= ceil(PcmPeakLimiter.CEILING * 32768).toInt() })
    }
    @Test fun limiterPreservesQuietSignalsAndExactDurationAcrossArbitraryBlocks() {
        val input = FloatArray(20003) { if (it in 7000..7100) 1.8f else .1f }
        val whole = FloatPcmAudio(input, 24000).pcm16()
        val limiter = PcmPeakLimiter(24000)
        val pieces = input.toList().chunked(137).map { limiter.process(it.toFloatArray()) } + listOf(limiter.finish())
        assertArrayEquals(whole, pieces.fold(ShortArray(0)) { a, b -> a + b })
        assertEquals(input.size, whole.size)
        assertEquals(3277, whole.first().toInt())
        assertTrue(limiter.limitedFrames > 0)
        assertTrue(limiter.finish().isEmpty())
        assertTrue(runCatching { limiter.process(floatArrayOf(0f)) }.isFailure)
    }
    @Test fun filtersAreStableBlockInvariantAndResettable() {
        for (rate in listOf(16000, 22050, 24000, 48000)) {
            val impulse = FloatArray(rate) { if (it == 0) 1f else 0f }
            val whole = impulse.copyOf(); BiquadSection.notch(rate, rate / 5.0, 60.0).process(whole)
            val filter = BiquadSection.notch(rate, rate / 5.0, 60.0)
            val blocks = impulse.toList().chunked(137).map { it.toFloatArray().also(filter::process) }.flatMap { it.toList() }.toFloatArray()
            assertArrayEquals(whole, blocks, 0f)
            assertTrue(whole.all(Float::isFinite))
            assertTrue(whole.takeLast(100).all { abs(it) < 1e-8 })
            filter.reset(); val reset = impulse.copyOf(); filter.process(reset); assertArrayEquals(whole, reset, 0f)
        }
    }
    @Test fun notchRejectsItsCentreWithoutChangingDcAndInvalidDesignsFail() {
        val rate = 24000
        val signal = FloatArray(rate) { sin(2 * PI * 4800 * it / rate).toFloat() }
        BiquadSection.notch(rate, 4800.0, 60.0).process(signal)
        assertTrue(signal.takeLast(rate / 2).maxOf { abs(it) } < .00001)
        val dc = FloatArray(rate) { .2f }; BiquadSection.notch(rate, 4800.0, 60.0).process(dc)
        assertEquals(.2f, dc.last(), .000001f)
        assertTrue(runCatching { BiquadSection.notch(rate, 12000.0, 60.0) }.isFailure)
        assertTrue(runCatching { BiquadSection.notch(rate, 4800.0, Double.NaN) }.isFailure)
    }
    @Test fun originalProfileBypassesToneAndGainWithoutMutatingModelOutput() {
        val original = FloatArray(1024) { .1f }; val copy = original.copyOf()
        val result = KokoroVoiceProcessor.render(original, 24000, 1f, false)
        assertArrayEquals(original, result.samples, 0f)
        assertArrayEquals(copy, original, 0f)
        assertTrue(runCatching { FloatPcmAudio(floatArrayOf(Float.POSITIVE_INFINITY), 24000) }.isFailure)
    }
}

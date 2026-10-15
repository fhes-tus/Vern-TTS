package com.veritas.reader

import com.veritas.reader.tts.PcmPitchShift
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class PcmPitchShiftTest {
    @Test fun pitchChangesFrequencyWithoutChangingDuration() {
        val rate = 24000
        val input = ShortArray(rate * 2) { (10000 * sin(2 * PI * 220 * it / rate)).toInt().toShort() }
        for (factor in listOf(0.7f, 0.9f, 1.2f, 1.4f)) {
            val result = PcmPitchShift.apply(input, rate, factor)
            assertEquals(input.size, result.size)
            val start = rate / 4; val end = rate * 7 / 4
            val crossings = (start + 1 until end).count { result[it - 1] <= 0 && result[it] > 0 }
            val frequency = crossings * rate.toDouble() / (end - start)
            assertEquals("pitch $factor", 220 * factor.toDouble(), frequency, 8.0)
            val rms = sqrt((start until end).sumOf { result[it].toDouble().pow(2) } / (end - start))
            assertTrue("Speech must not collapse in amplitude: $rms", rms > 4500)
        }
        assertSame(input, PcmPitchShift.apply(input, rate, 1f))
    }
    @Test fun shortAndSilentClipsAreFiniteAndKeepLength() {
        for (input in listOf(ShortArray(0), ShortArray(100), shortArrayOf(1000, -1000))) {
            assertEquals(input.size, PcmPitchShift.apply(input, 22050, 1.4f).size)
        }
    }
}

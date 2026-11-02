package com.veritas.reader

import com.veritas.reader.tts.BandLimitedResampler
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

class BandLimitedResamplerTest {
    private fun tone(rate: Int, hz: Double) = FloatArray(rate / 2) { (.5 * sin(2 * PI * hz * it / rate)).toFloat() }
    private fun rms(signal: FloatArray): Double {
        val skip = minOf(2048, signal.size / 4)
        return sqrt((skip until signal.size - skip).sumOf { signal[it].toDouble().pow(2) } / (signal.size - 2 * skip))
    }
    @Test fun downsamplingRejectsTheDemonstratedEighteenKhzAlias() {
        val output = BandLimitedResampler.convert(tone(48000, 18000.0), 2.0)
        assertEquals(12000, output.size)
        assertTrue("Alias RMS ${rms(output)}", rms(output) < .000035) // >80 dB below the input tone
    }
    @Test fun passbandAndStopbandHoldAcrossSpeechAndDeviceRates() {
        for ((from, to) in listOf(48000 to 24000, 44100 to 24000, 22050 to 24000, 24000 to 48000, 192000 to 8000)) {
            val pass = minOf(from, to) * .4
            val output = BandLimitedResampler.convert(tone(from, pass), from.toDouble() / to)
            assertEquals("$from/$to passband", .5 / sqrt(2.0), rms(output), .004)
            if (from > to * 1.2) {
                val stop = minOf(from * .47, to * .6)
                assertTrue("$from/$to stopband", rms(BandLimitedResampler.convert(tone(from, stop), from.toDouble() / to)) < .00011)
            }
        }
    }
    @Test fun unityDcShortClipsAndInvalidValuesHaveDefinedBehaviour() {
        val input = floatArrayOf(.1f, -.2f)
        assertSame(input, BandLimitedResampler.convert(input, 1.0))
        for (step in listOf(.5, 22050.0 / 24000, 2.0, 24.0)) {
            val dc = BandLimitedResampler.convert(FloatArray(500) { .2f }, step)
            assertTrue(dc.all { abs(it - .2f) < .000001 })
            assertTrue(BandLimitedResampler.convert(floatArrayOf(.1f), step).all(Float::isFinite))
        }
        assertTrue(runCatching { BandLimitedResampler.convert(floatArrayOf(Float.NaN), 1.0) }.isFailure)
        assertTrue(runCatching { BandLimitedResampler.kernel(0.0) }.isFailure)
    }
    @Test fun absolutePositionsKeepWholeArrayAndBlockedWavConversionIdentical() {
        val samples = ShortArray(30017) { (12000 * sin(2 * PI * 3711 * it / 44100)).roundToInt().toShort() }
        val source = File.createTempFile("sinc-source", ".wav"); val destination = File.createTempFile("sinc-output", ".wav")
        try {
            PcmWavWriter(source, 44100).use { it.append(samples) }
            PcmWavWriter(destination, 24000).use { WavAudioNormalizer.append(source, it, 24000) }
            val expected = WavAudioNormalizer.resample(samples, 44100, 24000)
            RandomAccessFile(destination, "r").use { file ->
                file.seek(44)
                assertEquals(expected.size * 2L + 44, file.length())
                expected.forEach { assertEquals(it, java.lang.Short.reverseBytes(file.readShort())) }
            }
        } finally { source.delete(); destination.delete() }
    }
}

package com.veritas.reader

import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.*
import org.junit.Test

class WavAudioNormalizerTest {
    @Test fun stereoDownmixAndSampleRateConversionKeepDurationAndSamples() {
        val source = File.createTempFile("stereo", ".wav"); val output = File.createTempFile("mono", ".wav")
        try {
            RandomAccessFile(source, "rw").use { file ->
                fun tag(s: String) = file.write(s.toByteArray())
                fun int(n: Int) = file.writeInt(Integer.reverseBytes(n))
                fun short(n: Int) = file.writeShort(java.lang.Short.reverseBytes(n.toShort()).toInt())
                tag("RIFF"); int(36 + 16000 * 4); tag("WAVE"); tag("fmt "); int(16)
                short(1); short(2); int(16000); int(16000 * 4); short(4); short(16); tag("data"); int(16000 * 4)
                repeat(16000) { short(1000); short(3000) }
            }
            PcmWavWriter(output, 24000).use { WavAudioNormalizer.append(source, it, 24000) }
            assertEquals(44L + 24000 * 2, output.length())
            RandomAccessFile(output, "r").use { it.seek(44); assertEquals(2000, java.lang.Short.reverseBytes(it.readShort()).toInt()) }
        } finally { source.delete(); output.delete() }
    }
    @Test fun cancellationInterruptsNormalizationBeforeAllSamplesAreCopied() {
        val source = File.createTempFile("cancel-source", ".wav"); val output = File.createTempFile("cancel-output", ".wav")
        try {
            PcmWavWriter(source, 8000).use { it.append(ShortArray(80000)) }
            var checks = 0
            try { PcmWavWriter(output, 24000).use { writer -> WavAudioNormalizer.append(source, writer, 24000) { if (++checks > 5) error("cancel") } }; fail("Expected cancellation") }
            catch (e: IllegalStateException) { assertEquals("cancel", e.message) }
            assertTrue(output.length() < 44 + 240000 * 2)
        } finally { source.delete(); output.delete() }
    }
}

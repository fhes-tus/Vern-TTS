package com.veritas.reader

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SpeechAmplitudeTest {
    @Test fun levelTracksActualLoudnessAndReturnsToSilence() {
        assertEquals(0f, SpeechAmplitude.pcmLevel(ShortArray(100), 100, 1000), 0f)
        val soft = SpeechAmplitude.pcmLevel(ShortArray(100) { 1_000 }, 100, 1000)
        val loud = SpeechAmplitude.pcmLevel(ShortArray(100) { 16_000 }, 100, 1000)
        assertTrue(soft > 0f && loud > soft && loud <= 1f)
        val phrase = ShortArray(100) { if (it < 50) 16_000 else 0 }
        assertTrue(SpeechAmplitude.pcmLevel(phrase, 50, 1000) > .5f)
        assertEquals(0f, SpeechAmplitude.pcmLevel(phrase, 100, 1000), 0f)
    }

    @Test fun splitPcmCallbacksPreserveTimingAndSilence() {
        val bytes = ByteBuffer.allocate(200).order(ByteOrder.LITTLE_ENDIAN).apply {
            repeat(50) { putShort(16_000) }; repeat(50) { putShort(0) }
        }.array()
        val envelope = SpeechEnvelope(1000, 2, 1)
        envelope.append(bytes.copyOfRange(0, 37))
        envelope.append(bytes.copyOfRange(37, bytes.size))
        assertTrue(envelope.levelAt(0) > .5f)
        assertEquals(0f, envelope.levelAt(50), 0f)
        assertEquals(0f, envelope.levelAt(100), 0f)
    }

    @Test fun stereoFloatAndEightBitFormatsHaveMatchingLevels() {
        val bytes = ByteBuffer.allocate(400).order(ByteOrder.LITTLE_ENDIAN).apply {
            repeat(100) { putFloat(.5f) }
        }.array()
        val floatEnvelope = SpeechEnvelope(1000, 4, 2)
        floatEnvelope.append(bytes)
        val eightBit = SpeechEnvelope(1000, 3, 1)
        eightBit.append(ByteArray(50) { 192.toByte() })
        assertEquals(floatEnvelope.levelAt(0), eightBit.levelAt(0), .001f)
    }
}

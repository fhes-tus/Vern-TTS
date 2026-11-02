package com.veritas.reader

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.audiofx.Equalizer
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Real effect lifetime checks; creates a silent dedicated track and never reads the user's library. */
class SpeechEqualizerInstrumentedTest {
    private fun track(): AudioTrack {
        check(InstrumentationRegistry.getInstrumentation().targetContext.packageName.endsWith(".checks"))
        return AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(24000)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(4800).build()
    }

    private fun assertReleased(effect: Equalizer) {
        try { effect.enabled; fail("Released effect remained usable") }
        catch (_: IllegalStateException) { }
    }

    @Test fun dedicatedSessionHasReadBackBandsAndCanBeReleasedRepeatedly() {
        repeat(3) {
            val track = track()
            try {
                val effect = SpeechEqualizer.create(track.audioSessionId)
                try {
                    assertTrue(effect.enabled)
                    assertTrue(effect.hasControl())
                    assertTrue(effect.numberOfBands > 0)
                    val range = effect.bandLevelRange
                    for (band in 0 until effect.numberOfBands.toInt()) {
                        assertTrue(effect.getCenterFreq(band.toShort()) > 0)
                        assertTrue(effect.getBandLevel(band.toShort()) in range[0]..range[1])
                    }
                } finally { effect.release() }
                assertReleased(effect)
            } finally { track.release() }
        }
    }

    @Test fun setupFailureReleasesItsPartiallyConfiguredEffect() {
        val track = track()
        var partial: Equalizer? = null
        try {
            try {
                SpeechEqualizer.create(track.audioSessionId) {
                    partial = it
                    throw IllegalStateException("Deliberate setup failure")
                }
                fail("Setup failure was swallowed")
            } catch (expected: IllegalStateException) {
                assertEquals("Deliberate setup failure", expected.message)
            }
            assertNotNull(partial)
            assertReleased(requireNotNull(partial))
        } finally { track.release() }
    }

    @Test fun globalAndInvalidSessionsAreRejectedBeforeEffectCreation() {
        for (id in listOf(0, -1)) {
            try { SpeechEqualizer.create(id); fail("Invalid session accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun playbackOwnerReleasesAndClearsTheSessionIdempotently() {
        val track = track()
        val effect = SpeechEqualizer.create(track.audioSessionId)
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val owner = PlaybackService()
                owner.ttsSessionId = track.audioSessionId
                owner.ttsEqualizer = effect
                owner.releaseVoiceShaping()
                assertNull(owner.ttsEqualizer)
                assertEquals(android.media.AudioManager.ERROR, owner.ttsSessionId)
                owner.releaseVoiceShaping()
                owner.serviceJob.cancel()
            }
            assertReleased(effect)
        } finally { effect.release(); track.release() }
    }
}

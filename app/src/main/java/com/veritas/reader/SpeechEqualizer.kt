package com.veritas.reader

import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.util.Log

/** Owns setup until every step succeeds; the playback service then owns release. */
internal object SpeechEqualizer {
    private const val TAG = "VeritasSystemTts"
    internal fun create(sessionId: Int, configureBands: (Equalizer) -> Unit = ::configureBands): Equalizer {
        require(sessionId > 0) { "Voice shaping requires a dedicated audio session" }
        val effect = Equalizer(0, sessionId)
        try {
            check(effect.hasControl()) { "Voice shaping has no effect control" }
            configureBands(effect)
            check(effect.setEnabled(true) == AudioEffect.SUCCESS && effect.enabled && effect.hasControl()) {
                "Voice shaping could not be enabled"
            }
            val bands = (0 until effect.numberOfBands.toInt()).joinToString { band ->
                "${effect.getCenterFreq(band.toShort()) / 1000}Hz:${effect.getBandLevel(band.toShort())}mB"
            }
            val range = effect.bandLevelRange
            Log.i(TAG, "Voice shaping session=$sessionId effect=${effect.descriptor.name} " +
                "range=${range[0]}..${range[1]}mB bands=[$bands]")
            return effect
        } catch (failure: Throwable) {
            runCatching { effect.release() }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
    }

    private fun configureBands(effect: Equalizer) {
        val range = effect.bandLevelRange
        check(range.size >= 2 && range[0] <= range[1] && effect.numberOfBands > 0) {
            "Voice shaping has an invalid band layout"
        }
        for (band in 0 until effect.numberOfBands.toInt()) {
            val centreHz = effect.getCenterFreq(band.toShort()) / 1000
            // Existing profile, unchanged. Read-back diagnostics describe the actual OEM effect.
            val millibels = when {
                centreHz < 120 -> -100
                centreHz < 500 -> 250
                centreHz < 1500 -> 0
                centreHz < 5000 -> 300
                else -> -150
            }
            effect.setBandLevel(band.toShort(), millibels.coerceIn(range[0].toInt(), range[1].toInt()).toShort())
        }
    }
}

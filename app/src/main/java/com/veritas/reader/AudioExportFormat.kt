package com.veritas.reader

import kotlin.math.ceil

enum class AudioExportFormat(val extension: String, val mimeType: String, private val bytesPerSecond: Int) {
    WAV("wav", "audio/wav", EXPORT_SAMPLE_RATE * Short.SIZE_BYTES),
    M4A("m4a", "audio/mp4", EXPORT_AAC_BIT_RATE / 8);

    internal fun estimateBytes(seconds: Double): Long = ceil(seconds.coerceAtLeast(0.0) * bytesPerSecond).toLong()

    companion object {
        internal fun forFileName(name: String): AudioExportFormat =
            entries.firstOrNull { name.endsWith(".${it.extension}", ignoreCase = true) } ?: WAV
    }
}

internal const val EXPORT_SAMPLE_RATE = 24_000
internal const val EXPORT_AAC_BIT_RATE = 64_000

/** Estimates speech time before synthesis. Actual voices and inserted pauses change the result. */
internal fun estimateAudioExportSeconds(chunks: List<String>, rate: Float): Double {
    val units = chunks.sumOf { chunk ->
        var words = 0
        var inWord = false
        var nonSpacingLetters = 0
        for (character in chunk) {
            val wordCharacter = character.isLetterOrDigit()
            if (wordCharacter && !inWord) words++
            inWord = wordCharacter
            if (Character.UnicodeScript.of(character.code) in syllableScripts) nonSpacingLetters++
        }
        maxOf(words.toLong(), nonSpacingLetters / 2L)
    }
    return units * 60.0 / (150 * SpeechDelivery.rate(rate)) + chunks.size * 0.12
}

private val syllableScripts = setOf(
    Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA,
    Character.UnicodeScript.HANGUL, Character.UnicodeScript.THAI
)

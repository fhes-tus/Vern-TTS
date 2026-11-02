package com.veritas.reader.tts

import com.veritas.reader.SpokenText

/** Final neural preparation, with a composed map back to the unchanged stored book. */
internal object NeuralSpeechText {
    private val invisible = Regex("[\\u00AD\\u200B\\u2060\\uFEFF\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]")
    private val whitespace = Regex("[\\s\\u00A0\\u202F]+")

    fun prepare(text: String): String = prepare(SpokenText.identity(text)).text

    fun prepare(text: SpokenText): SpokenText {
        // PDF/web copies can contain soft hyphens and decomposed accents. Remove discretionary
        // breaks without separating the two halves of a word; keep real apostrophes/hyphens.
        val normalized = text.replace(invisible) { "" }.normalizeNfc()
        return normalized.sanitize().replace(whitespace) { " " }.trim()
    }
}

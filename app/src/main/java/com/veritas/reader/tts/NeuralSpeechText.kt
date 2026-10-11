package com.veritas.reader.tts

import com.veritas.reader.SpeechSanitizer
import java.text.Normalizer

/** Last preparation step for both neural engines; never changes the stored book or source offsets. */
internal object NeuralSpeechText {
    private val invisible = Regex("[\\u00AD\\u200B\\u2060\\uFEFF\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]")
    private val whitespace = Regex("[\\s\\u00A0\\u202F]+")

    fun prepare(text: String): String {
        // PDF/web copies can contain soft hyphens and decomposed accents. Remove discretionary
        // breaks without separating the two halves of a word; keep real apostrophes/hyphens.
        val normalized = Normalizer.normalize(text.replace(invisible, ""), Normalizer.Form.NFC)
        return SpeechSanitizer.forSpeech(normalized).replace(whitespace, " ").trim()
    }
}

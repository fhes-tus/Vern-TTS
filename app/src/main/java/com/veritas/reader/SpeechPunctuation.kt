package com.veritas.reader

/** Shared recognition without deleting characters or changing display offsets. */
object SpeechPunctuation {
    enum class Ending { STATEMENT, QUESTION, EXCLAMATION, HESITATION }

    fun normalize(char: Char): Char = when (char) {
        '？', '﹖', '؟' -> '?'
        '！', '﹗' -> '!'
        '。', '．', '｡' -> '.'
        '，', '﹐', '،' -> ','
        '；', '﹔', '؛' -> ';'
        '：', '﹕' -> ':'
        '‽' -> '?'
        else -> char
    }

    fun normalize(text: String): String = text.map(::normalize).joinToString("")

    fun isTerminator(char: Char): Boolean = normalize(char) in ".!?"

    fun isClosingDelimiter(char: Char): Boolean = char in "\"'”’»›)]}"

    fun ending(text: String): Ending {
        var end = text.length - 1
        while (end >= 0 && (text[end].isWhitespace() || isClosingDelimiter(text[end]))) end--
        if (end < 0) return Ending.STATEMENT
        var start = end
        while (start >= 0 && isTerminator(text[start])) start--
        val marks = text.substring(start + 1, end + 1).map(::normalize)
        return when {
            '?' in marks -> Ending.QUESTION
            '!' in marks -> Ending.EXCLAMATION
            text[end] == '…' || marks.count { it == '.' } >= 3 -> Ending.HESITATION
            else -> Ending.STATEMENT
        }
    }

    // Preserve the voice's natural intonation. Global pitch cannot implement a
    // rising question boundary; gentle optional pacing is the only added cue.
    fun rateMultiplier(text: String, strength: Float): Float = when (ending(text)) {
        Ending.QUESTION -> 1f - 0.08f * strength.coerceIn(0f, 1f)
        Ending.EXCLAMATION -> 1f - 0.04f * strength.coerceIn(0f, 1f)
        Ending.HESITATION -> 1f - 0.06f * strength.coerceIn(0f, 1f)
        Ending.STATEMENT -> 1f
    }

    @Suppress("UNUSED_PARAMETER")
    fun pitchMultiplier(text: String, strength: Float): Float = 1f
}

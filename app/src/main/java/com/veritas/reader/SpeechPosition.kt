package com.veritas.reader

/** Source UTF-16 ranges, never offsets into expanded pronunciation text. */
enum class SpeechPositionTiming { WORD_RANGE, ESTIMATED_PCM, SENTENCE }
data class SpeechPosition(val documentId: String, val sentenceIndex: Int, val sourceStart: Int,
    val sourceEnd: Int, val timing: SpeechPositionTiming, val utteranceId: String)

/** Estimated word boundaries use rendered PCM progress, including a small punctuation weight.
 * This is deliberately labelled estimated: PCM duration is not an acoustic alignment. */
internal class SpeechTimingEstimate(text: String) {
    private val words = Regex("\\S+").findAll(text).map { match ->
        val word = match.value
        val pause = when (word.lastOrNull()) { '.', '!', '?' -> 2f; ',', ';', ':' -> 1.4f; else -> 1f }
        Triple(match.range.first, match.range.last + 1, (word.length.coerceAtMost(12).coerceAtLeast(2) * pause))
    }.toList()
    private val cumulative = words.runningFold(0f) { total, word -> total + word.third }.drop(1)
    fun rangeAt(renderedFrames: Long, totalFrames: Int): IntRange? {
        if (words.isEmpty() || totalFrames <= 0 || renderedFrames <= 0) return null
        val target = (renderedFrames.toDouble() / totalFrames).coerceIn(0.0, 1.0) * cumulative.last()
        val search = cumulative.binarySearch(target.toFloat())
        val index = (if (search >= 0) search else -search - 1).coerceIn(0, words.lastIndex)
        val word = words[index]
        return word.first until word.second
    }
}

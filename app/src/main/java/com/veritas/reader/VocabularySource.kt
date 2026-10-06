package com.veritas.reader

internal data class VocabularySource(val sentenceIndex: Int?, val contextSentence: String?)

internal fun resolveVocabularySource(
    sentences: List<String>, word: String, selectedSentenceIndex: Int? = null, selectedContext: String? = null
): VocabularySource {
    val explicit = selectedSentenceIndex?.takeIf { it in sentences.indices }
    if (explicit != null) return VocabularySource(explicit, selectedContext?.trim()?.takeIf { it.isNotEmpty() } ?: sentences[explicit].trim())
    val query = word.trim()
    if (query.isEmpty()) return VocabularySource(null, null)
    val pattern = Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(query) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
    val matches = sentences.indices.filter { pattern.containsMatchIn(sentences[it]) }
    val unique = matches.singleOrNull()
    return VocabularySource(unique, selectedContext?.trim()?.takeIf { it.isNotEmpty() } ?: unique?.let { sentences[it].trim() })
}

internal fun formatVocabularyEntry(entry: VocabularyEntry, timestamp: String? = null): String = buildString {
    fun oneLine(value: String) = value.replace(Regex("\\s+"), " ").trim()
    appendLine(oneLine(entry.word))
    appendLine("  ${oneLine(entry.explanation)}")
    appendLine("  ${oneLine(entry.source)}")
    timestamp?.let { appendLine("  [$it]") }
    entry.contextSentence?.takeIf { it.isNotBlank() }?.let { appendLine("  context: \"${oneLine(it)}\"") }
    entry.pronunciation?.takeIf { it.isNotBlank() }?.let { appendLine("  pronunciation: ${oneLine(it)}") }
}.trimEnd()

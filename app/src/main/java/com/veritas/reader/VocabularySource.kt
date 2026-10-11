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
    entry.lookupId?.let { appendLine("  lookup: ${oneLine(it)}") }
    entry.meaningSourceUrl?.let { appendLine("  meaning-source: ${oneLine(it)}") }
}.trimEnd()

/** Replace only the requested entry, retaining its original timestamp and source. */
internal fun updateVocabularyMeaning(content: String, lookupId: String, meaning: VocabularyDefinition): String =
    content.split("\n\n").joinToString("\n\n") { chunk ->
        if (parseVocabularyNoteContent(chunk).singleOrNull()?.lookupId != lookupId) chunk else {
            val lines = chunk.lines().toMutableList()
            if (lines.size < 2) chunk else {
                lines[1] = "  " + meaning.definition.replace(Regex("\\s+"), " ").trim()
                lines.removeAll { it.trim().startsWith("pronunciation:", ignoreCase = true) }
                lines.removeAll { it.trim().startsWith("meaning-source:", ignoreCase = true) }
                meaning.pronunciation?.let { lines.add("  pronunciation: ${it.replace(Regex("\\s+"), " ").trim()}") }
                meaning.sourceUrl?.let { lines.add("  meaning-source: $it") }
                lines.joinToString("\n")
            }
        }
    }

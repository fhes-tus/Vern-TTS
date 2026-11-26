package com.veritas.reader

internal data class PdfSelectionMatch(
    val chunkIndex: Int,
    val charOffset: Int
)

internal data class PdfSelectionRange(val start: PdfSelectionMatch, val endChunkIndex: Int)

internal object PdfSelectionLocator {
    /** Resolve the complete selection as one contiguous occurrence. A repeated match on
     * the selection page cannot be disambiguated using the unrelated reader cursor. */
    fun findExactRange(
        selectedText: String,
        model: ReaderTextModel,
        currentPage: Int?,
        allowAdjacentPages: Boolean = true
    ): PdfSelectionRange? {
        val query = normalize(selectedText)
        if (query.isBlank()) return null
        val page = currentPage?.takeIf { it >= 1 }
        val candidates = when {
            page == null -> 1..Int.MAX_VALUE
            allowAdjacentPages -> (page - 1).coerceAtLeast(1)..(page + 1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            else -> page..page
        }
        val sentences = model.sentences
        val firstCandidate = sentences.indexOfFirst { it.pageNumber in candidates }
        if (firstCandidate < 0) return null
        val lastCandidate = sentences.indexOfLast { it.pageNumber in candidates }
        val text = StringBuilder()
        val ranges = mutableListOf<RangeSentence>()
        var candidateEnd = Int.MAX_VALUE
        for (index in firstCandidate until sentences.size) {
            val sentence = sentences[index]
            val normalized = normalizeWithOffsets(sentence.text)
            if (normalized.text.isNotEmpty()) {
                if (text.isNotEmpty()) text.append(' ')
                ranges += RangeSentence(sentence.index, sentence.pageNumber, text.length, normalized.offsets)
                text.append(normalized.text)
            }
            if (index == lastCandidate) candidateEnd = text.length
            // A match may start on the selection page and end on a later page.
            if (index > lastCandidate && text.length - candidateEnd >= query.length) break
        }

        var match: PdfSelectionRange? = null
        var offset = text.indexOf(query)
        while (offset >= 0 && offset < candidateEnd) {
            val end = offset + query.length
            val before = text.getOrNull(offset - 1)
            val after = text.getOrNull(end)
            if ((before == null || before == ' ') && (after == null || after == ' ')) {
                val first = ranges.at(offset)
                if (first.pageNumber in candidates) {
                    if (match != null) return null
                    val last = ranges.at(end - 1)
                    match = PdfSelectionRange(PdfSelectionMatch(first.index, first.offsets[offset - first.start]), last.index)
                }
            }
            offset = text.indexOf(query, offset + 1)
        }
        return match
    }

    private data class RangeSentence(val index: Int, val pageNumber: Int, val start: Int, val offsets: IntArray)

    private fun List<RangeSentence>.at(offset: Int): RangeSentence {
        val index = binarySearchBy(offset) { it.start }
        return this[if (index >= 0) index else -index - 2]
    }

    fun findMatch(
        selectedText: String,
        model: ReaderTextModel,
        currentPage: Int?,
        preferredSentenceIndex: Int = 0
    ): PdfSelectionMatch? {
        val query = normalize(selectedText)
        if (query.isBlank()) return null
        val candidates = searchCandidates(query)
        if (candidates.isEmpty()) return null

        val safePage = currentPage?.coerceAtLeast(1)
        val broadFallbackAllowed = query.length >= 24 || query.split(' ').count { it.isNotBlank() } >= 4
        val pageWindows = buildList {
            if (safePage != null) {
                add(safePage..safePage)
                add((safePage - 1).coerceAtLeast(1)..(safePage + 1).coerceAtMost(model.pageCount.coerceAtLeast(safePage)))
            }
            if (safePage == null || broadFallbackAllowed) {
                add(1..model.pageCount.coerceAtLeast(1))
            }
        }
        val normalizedSentences = model.sentences.map { sentence ->
            SentenceCandidate(sentence.index, sentence.pageNumber, normalizeWithOffsets(sentence.text))
        }
        // Prefer an exact selection on an adjacent page over a shortened prefix on this one.
        val windows = pageWindows.map { pageWindow ->
            val pageSentences = normalizedSentences
                .filter { it.pageNumber in pageWindow }
                .sortedBy { kotlin.math.abs(it.index - preferredSentenceIndex) }
            pageSentences
        }
        windows.forEach { findInSentences(listOf(query), it)?.let { match -> return match } }
        windows.forEach { findInSentences(candidates.drop(1), it)?.let { match -> return match } }
        return null
    }

    fun findMatch(selectedText: String, chunks: List<String>): PdfSelectionMatch? {
        val query = normalize(selectedText)
        if (query.isBlank()) return null
        val candidates = searchCandidates(query)
        if (candidates.isEmpty()) return null

        val normalizedChunks = chunks.map(::normalizeWithOffsets)
        for (candidate in candidates) {
            normalizedChunks.forEachIndexed { index, chunk ->
                val normalizedOffset = candidateOffset(chunk.text, candidate)
                if (normalizedOffset >= 0) {
                    val originalOffset = chunk.offsets.getOrElse(normalizedOffset) { 0 }
                    return PdfSelectionMatch(index, originalOffset)
                }
            }
        }
        return null
    }

    private fun findInSentences(candidates: List<String>, sentences: List<SentenceCandidate>): PdfSelectionMatch? {
        for (candidate in candidates) {
            sentences.forEach { sentence ->
                val normalizedOffset = candidateOffset(sentence.normalized.text, candidate)
                if (normalizedOffset >= 0) {
                    val originalOffset = sentence.normalized.offsets.getOrElse(normalizedOffset) { 0 }
                    return PdfSelectionMatch(sentence.index, originalOffset)
                }
            }
        }
        return null
    }

    private fun searchCandidates(query: String): List<String> {
        val words = query.split(' ').filter { it.isNotBlank() }
        if (words.size < 4) return listOf(query)
        val prefixCandidates = listOf(120, 80, 60, 40, 25, 15)
            .mapNotNull { limit -> query.takeIf { it.length > limit }?.take(limit)?.substringBeforeLast(' ', "")?.takeIf { it.split(' ').size >= 3 } }
        val wordCandidates = listOf(16, 12, 8, 5, 3)
            .mapNotNull { count ->
                words.takeIf { it.size >= count }
                    ?.take(count)
                    ?.joinToString(" ")
                    ?.trim()
            }
        return (listOf(query) + prefixCandidates + wordCandidates)
            .filter { it.length >= MIN_CANDIDATE_LENGTH || it == query }
            .distinct()
    }

    private fun candidateOffset(text: String, candidate: String): Int {
        var offset = text.indexOf(candidate, ignoreCase = true)
        while (offset >= 0) {
            val before = text.getOrNull(offset - 1)
            val after = text.getOrNull(offset + candidate.length)
            if ((before == null || before == ' ') && (after == null || after == ' ')) return offset
            offset = text.indexOf(candidate, offset + 1, ignoreCase = true)
        }
        return -1
    }

    private fun normalize(value: String): String {
        val normalized = StringBuilder()
        var pendingSpace = false
        value.forEach { char ->
            if (char.isLetterOrDigit()) {
                if (pendingSpace) {
                    if (normalized.isNotEmpty()) {
                        normalized.append(' ')
                    }
                    pendingSpace = false
                }
                normalized.append(foldChar(char))
            } else if (char.isWhitespace() || char == '/' || char == '\\' || char == '_' || char == '|') {
                pendingSpace = true
            }
        }
        return normalized.toString().trim()
    }

    private fun normalizeWithOffsets(value: String): NormalizedChunk {
        val normalized = StringBuilder()
        val offsets = mutableListOf<Int>()
        var pendingSpaceOffset: Int? = null

        value.forEachIndexed { index, char ->
            if (char.isLetterOrDigit()) {
                pendingSpaceOffset?.let { offset ->
                    normalized.append(' ')
                    offsets.add(offset)
                    pendingSpaceOffset = null
                }
                // A folded char may expand (ligature "ﬁ" -> "fi"); every emitted char
                // maps back to the same original index so offsets stay valid.
                foldChar(char).forEach { folded ->
                    normalized.append(folded)
                    offsets.add(index)
                }
            } else if (char.isWhitespace() || char == '/' || char == '\\' || char == '_' || char == '|') {
                if (normalized.isNotEmpty() && pendingSpaceOffset == null) {
                    pendingSpaceOffset = index
                }
            }
        }

        return NormalizedChunk(normalized.toString(), offsets.toIntArray())
    }

    // Fold accents and ligatures so a PDF selection matches extracted text regardless of
    // Unicode form: "é" == "e" + combining accent, "ﬁ" == "fi".
    private fun foldChar(char: Char): String {
        if (char.code < 128) return char.lowercaseChar().toString()
        val decomposed = java.text.Normalizer.normalize(char.toString(), java.text.Normalizer.Form.NFKD)
        val folded = StringBuilder()
        decomposed.forEach { c ->
            if (Character.getType(c) != Character.NON_SPACING_MARK.toInt()) {
                folded.append(c.lowercaseChar())
            }
        }
        return if (folded.isEmpty()) char.lowercaseChar().toString() else folded.toString()
    }

    private data class NormalizedChunk(
        val text: String,
        val offsets: IntArray
    )

    private data class SentenceCandidate(
        val index: Int,
        val pageNumber: Int,
        val normalized: NormalizedChunk
    )

    private const val MIN_CANDIDATE_LENGTH = 8
}

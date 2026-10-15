package com.veritas.reader

internal data class InlineIllustrationAnchor(val imageIndex: Int, val start: Int, val endExclusive: Int)

internal object InlineIllustrationPlanner {
    private val marker = Regex("\\[\\[VERITAS_IMAGE:(\\d+)]]")
    fun anchors(part: ReaderPart, followingTexts: List<String?> = emptyList()): List<InlineIllustrationAnchor> {
        val explicit = marker.findAll(part.text).mapNotNull {
            it.groupValues[1].toIntOrNull()?.let { index -> InlineIllustrationAnchor(index, it.range.first, it.range.last + 1) }
        }.toList()
        val (normalized, offsets) = normalize(part.text)
        val inferred = followingTexts.mapIndexedNotNull { index, following ->
            if (explicit.any { it.imageIndex == index } || following.isNullOrBlank()) return@mapIndexedNotNull null
            val query = normalize(following).first.take(100).trim()
            if (query.length < 12) return@mapIndexedNotNull null
            val found = normalized.indexOf(query)
            if (found < 0 || normalized.indexOf(query, found + 1) >= 0) return@mapIndexedNotNull null
            val offset = offsets[found]
            // Preserve whole source sentences and their existing global identities.
            val boundary = part.sentenceRanges.firstOrNull { offset in it.start until it.endExclusive }?.start ?: offset
            InlineIllustrationAnchor(index, boundary, boundary)
        }
        return (explicit + inferred).sortedWith(compareBy<InlineIllustrationAnchor> { it.start }.thenBy { it.imageIndex })
    }
    private fun normalize(text: String): Pair<String, List<Int>> {
        val chars = StringBuilder()
        val offsets = mutableListOf<Int>()
        text.forEachIndexed { index, char ->
            if (char.isLetterOrDigit()) {
                chars.append(char.lowercaseChar())
                offsets.add(index)
            }
        }
        return chars.toString() to offsets
    }
}

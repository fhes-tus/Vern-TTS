package com.veritas.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrFuzzyMatchingTest {

    private fun sanitizeOcr(str: String): String = str
        .replace('\u2018', '\'')
        .replace('\u2019', '\'')
        .replace('\u201C', '"')
        .replace('\u201D', '"')
        .replace('\u2014', ' ')
        .replace('\u2013', ' ')
        .replace('\u2212', '-')
        .replace("ﬁ", "fi")
        .replace("ﬂ", "fl")
        .replace("ﬀ", "ff")
        .replace("ﬃ", "ffi")
        .replace("ﬄ", "ffl")
        .replace(Regex("[\\r\\n\\t]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun generateCandidates(sentence: String): List<String> {
        val cleanSentence = sanitizeOcr(sentence)
        val words = cleanSentence.split(' ').filter { it.isNotBlank() }
        val alphanumericWords = words.map { it.replace(Regex("[^a-zA-Z0-9]"), "") }.filter { it.length >= 3 }
        val stopWords = setOf("the", "and", "for", "are", "but", "not", "you", "all", "any", "can", "had", "her", "was", "one", "our", "out", "day", "get", "has", "him", "his", "how", "man", "new", "now", "old", "see", "two", "way", "who", "boy", "did", "its", "let", "put", "say", "she", "too", "use", "with", "from", "that", "this", "they", "have", "been", "were", "what", "when", "your", "said", "each", "which", "their", "time", "will", "about", "many", "then", "them", "some", "into", "more", "other")
        val distinctiveWords = alphanumericWords.filter { it.lowercase() !in stopWords }

        return buildList {
            add(cleanSentence.take(160))
            if (cleanSentence.length > 80) add(cleanSentence.take(80))

            val stripped = cleanSentence.replace(Regex("[.,:;!?\"'()\\[\\]{}]"), " ").replace(Regex("\\s+"), " ").trim()
            if (stripped != cleanSentence) {
                add(stripped.take(120))
                if (stripped.length > 60) add(stripped.take(60))
            }

            if (distinctiveWords.size >= 4) {
                add(distinctiveWords.take(4).joinToString(" "))
                if (distinctiveWords.size >= 8) {
                    add(distinctiveWords.drop(distinctiveWords.size / 2).take(4).joinToString(" "))
                }
            }

            if (words.size >= 5) {
                add(words.take(5).joinToString(" "))
                add(words.takeLast(minOf(5, words.size)).joinToString(" "))
                if (words.size >= 8) {
                    val mid = words.size / 2
                    add(words.subList(maxOf(0, mid - 2), minOf(words.size, mid + 3)).joinToString(" "))
                }
            }
            if (words.size in 3..4) {
                add(words.joinToString(" "))
            }

            val longestWords = alphanumericWords.sortedByDescending { it.length }.take(3)
            if (longestWords.size >= 2 && longestWords.first().length >= 6) {
                add(longestWords.joinToString(" "))
            }
        }.map { sanitizeOcr(it) }
            .filter { it.length >= 6 }
            .distinct()
    }

    private fun findMatchingChunkIndex(selectedText: String, chunks: List<String>): Int {
        fun normalize(s: String) = s.lowercase().replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()
        val normQuery = normalize(selectedText)
        val queryWords = normQuery.split(' ').filter { it.length >= 3 }

        var bestIndex = -1
        var bestScore = 0

        chunks.forEachIndexed { index, chunk ->
            val normChunk = normalize(chunk)
            if (normChunk.contains(normQuery) || (normQuery.length >= 12 && normChunk.contains(normQuery.take(30)))) {
                return index
            }
            if (queryWords.isNotEmpty()) {
                val matchedWords = queryWords.count { normChunk.contains(it) }
                if (matchedWords > bestScore && matchedWords >= maxOf(1, queryWords.size / 2)) {
                    bestScore = matchedWords
                    bestIndex = index
                }
            }
        }
        return bestIndex
    }

    @Test
    fun testSanitizeOcrLigaturesAndQuotes() {
        val raw = "“The ﬁnal decision on the aﬃliation was made—clearly.”"
        val clean = sanitizeOcr(raw)
        assertEquals("\"The final decision on the affiliation was made clearly.\"", clean)
    }

    @Test
    fun testSanitizeOcrHyphensAndSpaces() {
        val raw = "Multi-\nline\ttext with   em—dash and en–dash"
        val clean = sanitizeOcr(raw)
        assertEquals("Multi- line text with em dash and en dash", clean)
    }

    @Test
    fun testCandidateGenerationForShortSentence() {
        val sentence = "Quantum computing transforms cryptographic security."
        val candidates = generateCandidates(sentence)
        assertTrue(candidates.isNotEmpty())
        assertTrue(candidates.contains("Quantum computing transforms cryptographic security."))
        assertTrue(candidates.any { it.contains("Quantum") })
    }

    @Test
    fun testCandidateGenerationForLongComplexOcrSentence() {
        val sentence = "In this comprehensive study (2026), we demonstrate that neural network architectures with attention mechanisms achieve superior performance on document parsing tasks."
        val candidates = generateCandidates(sentence)
        assertTrue(candidates.size >= 4)
        assertTrue(candidates.any { it.startsWith("In this comprehensive study") })
        assertTrue(candidates.any { it.contains("comprehensive study") })
    }

    @Test
    fun testFuzzyMatchingSingleWordOrShortPhrase() {
        val chunks = listOf(
            "The quick brown fox jumps over the lazy dog.",
            "Artificial intelligence is rapidly advancing in document synthesis.",
            "Modern mobile applications require responsive and accessible user interfaces."
        )
        val index = findMatchingChunkIndex("artificial intelligence advancing", chunks)
        assertEquals(1, index)
    }

    @Test
    fun testFuzzyMatchingArbitrarySelectedSubStringWithPunctuationMismatch() {
        val chunks = listOf(
            "First chunk introduces the premise.",
            "Section 2.4: High-performance text parsing and rendering requires memory bounds!",
            "Concluding remarks on the architecture."
        )
        val index = findMatchingChunkIndex("high performance text parsing and rendering", chunks)
        assertEquals(1, index)
    }

    @Test
    fun testFuzzyMatchingNoMatchReturnsNegative() {
        val chunks = listOf("Alpha", "Beta", "Gamma")
        val index = findMatchingChunkIndex("Unrelated completely different string", chunks)
        assertEquals(-1, index)
    }
}

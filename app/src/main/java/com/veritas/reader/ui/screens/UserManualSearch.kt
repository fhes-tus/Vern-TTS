package com.veritas.reader.ui.screens

import java.util.Locale

private val manualWord = Regex("[\\p{L}\\p{N}]+")
private val manualStopWords = setOf(
    "a", "an", "and", "are", "can", "do", "does", "for", "from", "how", "i", "in", "is",
    "it", "me", "my", "of", "on", "s", "t", "the", "to", "want", "when", "where", "why",
    "with", "you"
)

private fun manualWords(text: String): List<String> =
    manualWord.findAll(text.lowercase(Locale.ROOT)).map { it.value }
        .filterNot { it in manualStopWords }.toList()

internal fun searchManual(sections: List<ManualSection>, query: String): List<ManualSection> {
    val words = manualWords(query)
    if (words.isEmpty()) return sections
    val terms = words.distinct()
    val phrase = words.joinToString(" ")

    return sections.mapNotNull { section ->
        val sectionTitleMatches = manualWords(section.title).joinToString(" ") == phrase
        val ranked = section.chapters.mapNotNull { chapter ->
            val score = manualScore(chapter, terms, phrase)
            if (score == 0) null else chapter to score
        }.sortedByDescending { it.second }

        when {
            sectionTitleMatches -> section to 150
            ranked.isNotEmpty() -> section.copy(chapters = ranked.map { it.first }) to ranked.first().second
            else -> null
        }
    }.sortedByDescending { it.second }.map { it.first }
}

private fun manualScore(chapter: ManualChapter, terms: List<String>, phrase: String): Int {
    val title = manualWords(chapter.title)
    val tags = chapter.tags.map(::manualWords)
    val subtitle = manualWords(chapter.subtitle)
    val instructions = manualWords((listOf(chapter.content) + chapter.flowSteps).joinToString(" "))
    val keywords = (title + tags.flatten() + subtitle).toSet()
    val detail = instructions.toSet()
    val weights = terms.map { term ->
        when {
            term in title -> 9
            term in keywords -> 6
            term in detail -> 3
            term.length >= 4 && title.any { it.startsWith(term) || isNearManualWord(term, it) } -> 3
            term.length >= 4 && keywords.any { it.startsWith(term) } -> 2
            term.length >= 4 && keywords.any { isNearManualWord(term, it) } -> 1
            else -> 0
        }
    }
    val matches = weights.count { it > 0 }
    if (matches * 2 < terms.size || (terms.size > 2 && matches < 2) || matches == 0) return 0

    val phraseBonus = when {
        title.joinToString(" ") == phrase -> 90
        tags.any { it.joinToString(" ") == phrase } -> 75
        terms.size > 1 && (listOf(title, subtitle) + tags).any { field ->
            " ${field.joinToString(" ")} ".contains(" $phrase ")
        } -> 35
        else -> 0
    }
    return 40 * matches / terms.size + weights.sum() * 3 / terms.size + phraseBonus
}

private fun isNearManualWord(term: String, word: String): Boolean {
    if (kotlin.math.abs(term.length - word.length) > 1) return false
    if (term.length == word.length) {
        val differences = term.indices.filter { term[it] != word[it] }
        return differences.size == 1 || (differences.size == 2 &&
            differences[1] == differences[0] + 1 &&
            term[differences[0]] == word[differences[1]] &&
            term[differences[1]] == word[differences[0]])
    }
    val shorter = if (term.length < word.length) term else word
    val longer = if (term.length < word.length) word else term
    val firstDifference = shorter.indices.firstOrNull { shorter[it] != longer[it] } ?: shorter.length
    return shorter.substring(firstDifference) == longer.substring(firstDifference + 1)
}

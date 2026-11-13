package com.veritas.reader

import java.text.Normalizer
import java.util.Locale

internal object EnglishDictionaryPack {
    const val ID = "oewn-2025"
    const val TITLE = "English · Open English WordNet 2025"
    const val URL = "https://en-word.net/static/english-wordnet-2025.zip"
    const val SHA256 = "38b16326159f51853626b7d24a44c453fa88ab33f06fce5ec8fc5996d1c2be93"
    const val DOWNLOAD_BYTES = 9_616_299L
    const val SOURCE = "https://en-word.net/"
    const val LICENSE = "https://creativecommons.org/licenses/by/4.0/"
    const val CREDIT = "Open English WordNet Community, derived from Princeton WordNet. CC BY 4.0. Data converted and indexed for Vern."
}

internal data class DictionarySense(val partOfSpeech: String, val definition: String,
    val examples: List<String>, val synonyms: List<String>)
internal data class DictionaryEntry(val query: String, val headword: String, val senses: List<DictionarySense>)
internal data class DictionaryPackInfo(val words: Int, val bytes: Long)
internal data class DictionaryWordPage(val words: List<String>, val hasMore: Boolean)

/** The documented WNDB records; headers are skipped, malformed content is never installed. */
internal object WordNetDictionary {
    const val MAX_RECORD_LENGTH = 65_536
    data class Synset(val offset: Long, val pos: String, val words: List<String>, val gloss: String)
    data class Index(val lemma: String, val pos: String, val offsets: List<Long>)
    private val spaces = Regex("\\s+")
    private val example = Regex(""";\s*["“]([^"”]+)["”]""")
    val parts = listOf("noun" to "n", "verb" to "v", "adj" to "a", "adv" to "r")

    fun query(value: String): String? {
        val clean = Normalizer.normalize(value.replace('’', '\'').replace('_', ' '), Normalizer.Form.NFC)
            .trim().replace(spaces, " ").lowercase(Locale.ROOT)
        return clean.takeIf { it.length in 1..80 && it.none(Char::isISOControl) }
    }

    fun synset(line: String, expectedPos: String): Synset? {
        if (line.isBlank() || line.first().isWhitespace()) return null
        require(line.length <= MAX_RECORD_LENGTH) { "Dictionary record is too large." }
        val separator = line.indexOf('|')
        require(separator > 0) { "Dictionary definition is missing." }
        val fields = line.substring(0, separator).trim().split(spaces)
        require(fields.size >= 6)
        val pos = if (fields[2] == "s") "a" else fields[2]
        require(pos == expectedPos)
        val count = fields[3].toInt(16)
        require(count in 1..256 && fields.size >= 4 + count * 2)
        val words = (0 until count).map { index ->
            fields[4 + index * 2].replace(Regex("\\((?:a|p|ip)\\)$"), "").replace('_', ' ')
        }
        val gloss = line.substring(separator + 1).trim()
        require(gloss.isNotEmpty() && words.all { it.length in 1..256 })
        return Synset(fields[0].toLong().also { require(it >= 0) }, pos, words, gloss)
    }

    fun index(line: String, expectedPos: String): Index? {
        if (line.isBlank() || line.first().isWhitespace()) return null
        require(line.length <= MAX_RECORD_LENGTH)
        val fields = line.trim().split(spaces)
        require(fields.size >= 7 && fields[1] == expectedPos)
        val count = fields[2].toInt()
        val pointers = fields[3].toInt()
        require(count in 1..256 && pointers in 0..64 && fields.size == 6 + pointers + count)
        val lemma = query(fields[0]) ?: error("Invalid dictionary headword.")
        val offsets = fields.drop(6 + pointers).map { it.toLong().also { offset -> require(offset >= 0) } }
        return Index(lemma, expectedPos, offsets)
    }

    fun sense(pos: String, gloss: String, words: List<String>, headword: String): DictionarySense {
        val examples = example.findAll(gloss).map { it.groupValues[1] }.take(4).toList()
        val definition = example.replace(gloss, "").trim().trimEnd(';').trim()
        return DictionarySense(when (pos) { "n" -> "noun"; "v" -> "verb"; "a" -> "adjective"; "r" -> "adverb"; else -> pos },
            definition, examples, words.filterNot { query(it) == headword }.distinct().take(12))
    }

    /** Candidate forms are used only if the exact word is absent, and must exist in the pack. */
    fun candidateForms(word: String): List<String> = buildList {
        for ((ending, replacement) in listOf("s" to "", "ses" to "s", "xes" to "x", "zes" to "z",
            "ches" to "ch", "shes" to "sh", "men" to "man", "ies" to "y", "es" to "e", "es" to "",
            "ed" to "e", "ed" to "", "ing" to "e", "ing" to "", "er" to "", "er" to "e", "est" to "", "est" to "e")) {
            if (word.length > ending.length + 1 && word.endsWith(ending)) add(word.dropLast(ending.length) + replacement)
        }
    }.distinct().take(20)
}

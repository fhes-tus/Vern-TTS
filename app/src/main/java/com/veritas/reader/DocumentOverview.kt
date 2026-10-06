package com.veritas.reader

import java.util.Locale
import java.text.BreakIterator
import kotlin.math.ln

/** Bounded extractive overview: prefer an abstract, otherwise representative sentences. */
internal object DocumentOverview {
    private val boilerplate = Regex("(?i)copyright|all rights reserved|project gutenberg|isbn|table of contents|permission to|printed in|published by|license agreement")
    private val words = Regex("\\p{L}{3,}")
    private val stop = "the and that this with from have were been their there would could should about into which when where what your they them then some more also only such other these those each very much well does its his her she for are was but not you our can has had will who how why".split(' ').toSet()

    /** Read a small amount across the stored UTF-8 text without loading a whole book. */
    fun sample(file: java.io.File): String {
        if (!file.isFile) return ""
        java.io.RandomAccessFile(file, "r").use { input ->
            val length = input.length()
            if (length <= 48000) {
                val bytes = ByteArray(length.toInt()); input.readFully(bytes)
                return String(bytes, Charsets.UTF_8)
            }
            return (0 until 12).joinToString("\n\n") { part ->
                val offset = part.toLong() * (length - 4000) / 11
                input.seek(offset)
                val bytes = ByteArray(4000); input.readFully(bytes)
                // UTF-8 continuation bytes can straddle a sampled boundary.
                var firstByte = 0
                while (firstByte < bytes.size && (bytes[firstByte].toInt() and 0xc0) == 0x80) firstByte++
                val window = String(bytes, firstByte, bytes.size - firstByte, Charsets.UTF_8)
                val first = if (offset == 0L) 0 else window.indexOfAny(charArrayOf('.', '!', '?', '\n')).let { if (it < 0) window.length else it + 1 }
                val last = window.lastIndexOfAny(charArrayOf('.', '!', '?', '\n')).let { if (it < first) first else it + 1 }
                window.substring(first, last)
            }
        }
    }

    fun sample(text: String): String {
        if (text.length <= 48000) return text
        return (0 until 12).joinToString("\n\n") { part ->
            val start = (part.toLong() * (text.length - 4000) / 11).toInt()
            val window = text.substring(start, minOf(text.length, start + 4000))
            // Drop clipped boundary sentences rather than manufacture snippets.
            val first = if (start == 0) 0 else window.indexOfAny(charArrayOf('.', '!', '?', '\n')).let { if (it < 0) 0 else it + 1 }
            val last = window.lastIndexOfAny(charArrayOf('.', '!', '?', '\n')).let { if (it < first) window.length else it + 1 }
            window.substring(first, last)
        }
    }

    fun summarize(text: String, maxChars: Int = 650): String {
        if (text.isBlank()) return "No readable text is available yet."
        val sampled = ReaderTextIndex.stripInternalMarkers(sample(text))
        val abstract = Regex("(?is)(?:^|\\n)\\s*(?:#{1,6}\\s*)?(?:abstract|executive summary|synopsis|book description)\\s*[:\\n]+(.{50,1800}?)(?:\\n\\s*\\n|$)").find(sampled)?.groupValues?.get(1)
        val sentences = sentences(abstract ?: sampled).map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.length in 60..maxChars && it.lastOrNull() in listOf('.', '!', '?', '”', '"') && !boilerplate.containsMatchIn(it) }
            .distinct().take(1200)
        if (sentences.isEmpty()) {
            val headings = sampled.lineSequence().map(String::trim)
                .filter { com.veritas.reader.ui.screens.looksLikeOutlineHeading(it) && !boilerplate.containsMatchIn(it) }
                .map { it.trimStart('#').trim() }.distinct().take(4).toList()
            return if (headings.isEmpty()) "A reliable overview could not be inferred from this document's text."
                else "This document includes sections on ${headings.joinToString("; ")}."
        }
        if (abstract != null) return fit(sentences, maxChars)
        val terms = sentences.map { sentence -> words.findAll(sentence.lowercase(Locale.ROOT)).map { it.value }.filterNot { it in stop }.toSet() }
        val frequencies = terms.flatten().groupingBy { it }.eachCount()
        val chosen = mutableListOf<Int>()
        var length = 0
        repeat(2) {
            val best = sentences.indices.filter { it !in chosen && length + sentences[it].length + 1 <= maxChars }
                .maxByOrNull { index ->
                    val topic = terms[index].sumOf { ln(1.0 + (frequencies[it] ?: 0)) } / kotlin.math.sqrt(terms[index].size.coerceAtLeast(1).toDouble())
                    val overlap = chosen.maxOfOrNull { other -> terms[index].intersect(terms[other]).size.toDouble() / terms[index].union(terms[other]).size.coerceAtLeast(1) } ?: 0.0
                    topic * (1.0 - overlap) - if (sentences[index].startsWith('“')) 0.8 else 0.0
                } ?: return@repeat
            chosen.add(best); length += sentences[best].length + 1
        }
        return chosen.sorted().joinToString(" ") { sentences[it] }
    }

    private fun sentences(text: String): List<String> {
        val iterator = BreakIterator.getSentenceInstance(Locale.ENGLISH)
        iterator.setText(text)
        val result = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            text.substring(start, end).trim().takeIf { it.isNotEmpty() }?.let(result::add)
            start = end; end = iterator.next()
        }
        return result
    }

    /** Also bounds old cached AI overviews and curated descriptions at display time. */
    fun limitSentences(text: String): String = sentences(text.replace(Regex("\\s+"), " ")).take(2).joinToString(" ")

    private fun fit(sentences: List<String>, limit: Int): String {
        val selected = mutableListOf<String>(); var length = 0
        for (sentence in sentences.take(2)) {
            if (length + sentence.length + 1 > limit) break
            selected.add(sentence); length += sentence.length + 1
        }
        return selected.joinToString(" ")
    }
}

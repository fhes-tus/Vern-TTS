package com.veritas.reader

import java.util.Locale
import kotlin.math.ceil

data class ReaderPageRange(
    val partIndex: Int,
    val startPage: Int,
    val endPage: Int
) {
    val pageCount: Int
        get() = (endPage - startPage + 1).coerceAtLeast(0)
}

data class ReaderSentence(
    val index: Int,
    val text: String,
    val pageNumber: Int,
    val separatorBefore: String = ""
)

data class ReaderPartSentenceRange(
    val sentenceIndex: Int,
    val start: Int,
    val endExclusive: Int,
    val sentenceCharOffset: Int = 0
)

data class ReaderPart(
    val index: Int,
    val pageRange: ReaderPageRange,
    val sentenceStartIndex: Int,
    val sentenceEndIndexExclusive: Int,
    val text: String,
    val sentenceRanges: List<ReaderPartSentenceRange>
) {
    val markerText: String
        get() = "(Part ${index + 1} of ${pageRange.partIndex + 1})"
}

data class ReaderTextModel(
    val sentences: List<ReaderSentence>,
    val parts: List<ReaderPart>,
    val pageCount: Int,
    val cleanText: String
) {
    fun partForSentence(sentenceIndex: Int): ReaderPart? {
        if (parts.isEmpty()) return null
        val safeIndex = sentenceIndex.coerceIn(0, (sentences.size - 1).coerceAtLeast(0))
        return parts.firstOrNull { safeIndex in it.sentenceStartIndex until it.sentenceEndIndexExclusive }
            ?: parts.lastOrNull()
    }

    fun sentenceForPartOffset(part: ReaderPart, offset: Int): ReaderPartSentenceRange? {
        if (part.sentenceRanges.isEmpty()) return null
        val safeOffset = offset.coerceIn(0, part.text.length)
        return part.sentenceRanges.firstOrNull { safeOffset in it.start until it.endExclusive }
            ?: part.sentenceRanges.minByOrNull { range ->
                when {
                    safeOffset < range.start -> range.start - safeOffset
                    else -> safeOffset - range.endExclusive
                }
            }
    }
}

object ReaderPagePartPlanner {
    fun plan(pageCount: Int): List<ReaderPageRange> {
        val safePageCount = pageCount.coerceAtLeast(0)
        if (safePageCount == 0) return emptyList()
        val maxPagesPerPart = 12
        val minPagesPerPart = if (safePageCount > 30) 9 else 6
        var partCount = ceil(safePageCount.toDouble() / maxPagesPerPart.toDouble()).toInt().coerceAtLeast(1)
        while (partCount > 1 && safePageCount / partCount < minPagesPerPart) {
            partCount--
        }
        val baseSize = safePageCount / partCount
        val extra = safePageCount % partCount
        var nextPage = 1
        return List(partCount) { index ->
            val size = baseSize + if (index < extra) 1 else 0
            val start = nextPage
            val end = nextPage + size - 1
            nextPage = end + 1
            ReaderPageRange(index, start, end)
        }
    }
}

object ReaderTextIndex {
    private val pageMarkerRegex = Regex("""(?i)^\s*#?\s*\[\[?\{?VERITAS[_\s]PAGE:?\s*(\d+)\}?]\]?\s*$""")
    private val inlineMarkerRegex = Regex("""(?i)#?\s*\[\[?\{?VERITAS[_\s]PAGE:?\s*\d+\}?]\]?""")
    private val sentenceEndMarks = setOf('.', '!', '?')
    private val abbreviations = setOf(
        "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "v", "vs", "etc", "e.g", "i.e",
        "fig", "figs", "no", "nos", "vol", "pp", "p", "ch", "dept", "univ", "inc", "ltd",
        "et al", "al", "ed", "eds", "ref", "refs", "sec", "secs", "para", "paras", "eq", "eqs",
        "app", "approx", "c", "ca", "cf", "ff", "op", "cit", "ibid"
    )
    private val BULLET_NUMBERED_PATTERN = Regex("""^(\d{1,4}[.)]|\([a-zA-Z0-9]+\)|[a-zA-Z][.)]|[IVXLCDM]{1,6}[.)])\s+""", RegexOption.IGNORE_CASE)
    private val ROMAN_NUMERAL_REGEX = Regex("""^[IVXLCDM]+$""", RegexOption.IGNORE_CASE)
    private val RUNNING_VERBS = setOf(
        "updates", "explores", "describes", "examines", "focuses", "covers",
        "discusses", "presents", "analyzes", "reviews", "addresses", "investigates",
        "illustrates", "shows", "demonstrates", "argues", "explains", "details",
        "is", "was", "are", "were", "will", "has", "have", "had", "contains", "provides"
    )
    private val CHAPTER_KEYWORDS = setOf(
        "chapter", "prologue", "epilogue", "introduction", "preface", "part", "book", "section", "act", "scene"
    )
    private val MULTI_NEWLINE_REGEX = Regex("""\n{3,}""")
    private val MULTI_INITIALS_PATTERN = Regex("""^([a-z]\.){2,}[a-z]?$""")
    private val CHAPTER_NUMBERED_DOT_PATTERN = Regex("""^(CHAPTER|Chapter|Part|Section)?\s*[IVXLCDM\d]+(\.[IVXLCDM\d]+)*\.$""", RegexOption.IGNORE_CASE)
    private val WHITESPACE_REGEX = Regex("""\s+""")

    fun pageMarker(pageNumber: Int): String = "[[VERITAS_PAGE:${pageNumber.coerceAtLeast(1)}]]"

    fun stripInternalMarkers(text: String): String {
        return text.replace('\r', '\n')
            .lineSequence()
            .map { line -> line.replace(inlineMarkerRegex, "").trimEnd() }
            .filterNot { pageMarkerRegex.matches(it.trim()) }
            .joinToString("\n")
            .replace(MULTI_NEWLINE_REGEX, "\n\n")
            .trim()
    }

    fun build(rawText: String, storedPageCount: Int = 0, legacySentenceBoundaries: Boolean = false): ReaderTextModel {
        val pages = extractPages(rawText, storedPageCount)
        val cleanText = pages.joinToString("\n\n") { it.text }.trim()
        val sentences = mutableListOf<ReaderSentence>()
        pages.forEach { page ->
            splitSentenceFragments(page.text, legacySentenceBoundaries).forEach { sentence ->
                sentences.add(
                    ReaderSentence(
                        index = sentences.size,
                        text = sentence.text,
                        pageNumber = page.pageNumber,
                        separatorBefore = sentence.separatorBefore
                    )
                )
            }
        }
        val fallbackSentences = if (sentences.isEmpty() && cleanText.isNotBlank()) {
            listOf(ReaderSentence(0, cleanText, 1))
        } else {
            sentences
        }
        val pageCount = (fallbackSentences.maxOfOrNull { it.pageNumber } ?: 1).coerceAtLeast(storedPageCount).coerceAtLeast(1)
        val partRanges = ReaderPagePartPlanner.plan(pageCount)
        val parts = buildParts(fallbackSentences, partRanges)
        return ReaderTextModel(
            sentences = fallbackSentences,
            parts = parts,
            pageCount = pageCount,
            cleanText = cleanText
        )
    }

    fun sentences(text: String): List<String> = build(text).sentences.map { it.text }

    fun replaceSentenceRange(
        rawText: String,
        storedPageCount: Int,
        startSentenceIndex: Int,
        endSentenceIndexExclusive: Int,
        replacement: String
    ): String {
        val model = build(rawText, storedPageCount)
        if (model.sentences.isEmpty()) return replacement.trim()
        val safeStart = startSentenceIndex.coerceIn(0, model.sentences.lastIndex)
        val safeEnd = endSentenceIndexExclusive.coerceIn(safeStart + 1, model.sentences.size)
        val replacementText = replacement.trim()
        if (replacementText.isBlank()) return rawText

        val replacementPage = model.sentences.getOrNull(safeStart)?.pageNumber ?: 1
        val replacementFragments = splitSentenceFragments(replacementText)
            .ifEmpty { listOf(SentenceFragment(replacementText, "")) }
        val replacementSentences = replacementFragments.mapIndexed { index, fragment ->
            ReaderSentence(
                index = -1,
                text = fragment.text,
                pageNumber = replacementPage,
                separatorBefore = if (index == 0) model.sentences.getOrNull(safeStart)?.separatorBefore.orEmpty() else fragment.separatorBefore
            )
        }

        val edited = buildList {
            addAll(model.sentences.take(safeStart))
            addAll(replacementSentences)
            addAll(model.sentences.drop(safeEnd))
        }
        return rebuildPages(edited, model.pageCount)
    }

    fun replacePart(rawText: String, storedPageCount: Int, partIndex: Int, replacement: String): String {
        val model = build(rawText, storedPageCount)
        val part = model.parts.getOrNull(partIndex) ?: return rawText
        return replaceSentenceRange(
            rawText = rawText,
            storedPageCount = storedPageCount,
            startSentenceIndex = part.sentenceStartIndex,
            endSentenceIndexExclusive = part.sentenceEndIndexExclusive,
            replacement = replacement
        )
    }

    private fun extractPages(rawText: String, storedPageCount: Int): List<PageText> {
        val normalized = rawText.replace('\r', '\n')
        val pages = mutableListOf<PageText>()
        var currentPage = 1
        var sawMarker = false
        val buffer = StringBuilder()

        fun flush() {
            val pageText = buffer.toString().trim()
            if (pageText.isNotBlank()) {
                pages.add(PageText(currentPage, pageText))
            }
            buffer.clear()
        }

        normalized.lineSequence().forEach { line ->
            val trimmed = line.trim()
            val marker = pageMarkerRegex.matchEntire(trimmed)
            if (marker != null) {
                flush()
                sawMarker = true
                currentPage = marker.groupValues[1].toIntOrNull()?.coerceAtLeast(1) ?: currentPage
            } else if (trimmed.contains("VERITAS_PAGE", ignoreCase = true) || trimmed.contains("veritas page", ignoreCase = true)) {
                val cleaned = trimmed.replace(inlineMarkerRegex, "").trim()
                if (cleaned.isNotBlank()) {
                    buffer.append(cleaned).append('\n')
                }
            } else {
                buffer.append(line).append('\n')
            }
        }
        flush()

        if (sawMarker && pages.isNotEmpty()) return pages
        val clean = stripInternalMarkers(normalized)
        if (clean.isBlank()) return emptyList()
        val pageCount = storedPageCount.takeIf { it > 0 } ?: estimatePageCount(clean)
        if (pageCount <= 1) return listOf(PageText(1, clean))

        val targetSize = (clean.length / pageCount).coerceAtLeast(1)
        val result = mutableListOf<PageText>()
        var cursor = 0
        for (page in 1..pageCount) {
            if (cursor >= clean.length) break
            val remainingPages = pageCount - page + 1
            val remainingChars = clean.length - cursor
            val desiredEnd = if (remainingPages == 1) clean.length else cursor + minOf(targetSize, remainingChars)
            val end = nearestSoftBreak(clean, desiredEnd, cursor)
            result.add(PageText(page, clean.substring(cursor, end).trim()))
            cursor = end
        }
        return result.filter { it.text.isNotBlank() }.ifEmpty { listOf(PageText(1, clean)) }
    }

    private fun buildParts(sentences: List<ReaderSentence>, ranges: List<ReaderPageRange>): List<ReaderPart> {
        if (sentences.isEmpty()) return emptyList()
        val effectiveRanges = ranges.ifEmpty { ReaderPagePartPlanner.plan(estimatePageCount(sentences.joinToString(" ") { it.text })) }
        val sentencesByPage = sentences.groupBy { it.pageNumber }
        return effectiveRanges.mapIndexedNotNull { index, range ->
            val partSentences = (range.startPage..range.endPage).flatMap { sentencesByPage[it].orEmpty() }
                .ifEmpty {
                    val startFraction = (index.toFloat() / effectiveRanges.size.toFloat()).coerceIn(0f, 1f)
                    val endFraction = ((index + 1).toFloat() / effectiveRanges.size.toFloat()).coerceIn(0f, 1f)
                    val start = (startFraction * sentences.size).toInt().coerceIn(0, sentences.lastIndex)
                    val endExclusive = (endFraction * sentences.size).toInt().coerceIn(start + 1, sentences.size)
                    sentences.subList(start, endExclusive)
                }
            if (partSentences.isEmpty()) return@mapIndexedNotNull null
            val textBuilder = StringBuilder()
            val rangesInPart = mutableListOf<ReaderPartSentenceRange>()
            partSentences.forEachIndexed { sentenceOffset, sentence ->
                if (textBuilder.isNotBlank()) {
                    textBuilder.append(
                        displaySeparator(
                            rawSeparator = sentence.separatorBefore,
                            previousSentence = partSentences.getOrNull(sentenceOffset - 1),
                            currentSentence = sentence
                        )
                    )
                }
                val start = textBuilder.length
                textBuilder.append(sentence.text)
                rangesInPart.add(ReaderPartSentenceRange(sentence.index, start, textBuilder.length))
            }
            ReaderPart(
                index = index,
                pageRange = range,
                sentenceStartIndex = partSentences.first().index,
                sentenceEndIndexExclusive = partSentences.last().index + 1,
                text = textBuilder.toString().trim(),
                sentenceRanges = rangesInPart
            )
        }
    }

    internal fun looksLikeTableOfContentsRow(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < 3) return false
        val lastChar = trimmed.last()
        if (!lastChar.isDigit()) return false

        var i = trimmed.length - 1
        var digitCount = 0
        while (i >= 0 && trimmed[i].isDigit()) {
            digitCount++
            i--
        }
        if (digitCount !in 1..5 || i < 0) return false

        var wsCount = 0
        while (i >= 0 && (trimmed[i] == ' ' || trimmed[i] == '\t')) {
            if (trimmed[i] == '\t') wsCount += 4 else wsCount++
            i--
        }
        if (i < 0) return false

        var leaderCharCount = 0
        while (i >= 0 && (trimmed[i] in ".·•…-_" || trimmed[i] == ' ')) {
            if (trimmed[i] in ".·•…-_") leaderCharCount++
            i--
        }

        if (leaderCharCount >= 2 || wsCount >= 2) {
            return i >= 0 && trimmed.substring(0, i + 1).any { it.isLetter() }
        }

        if (trimmed[0].isDigit()) {
            val spaceIdx = trimmed.indexOf(' ')
            if (spaceIdx in 2..15) {
                val prefix = trimmed.substring(0, spaceIdx)
                if (prefix.contains('.') && prefix.all { it.isDigit() || it == '.' }) {
                    return true
                }
            }
        }

        return false
    }

    internal fun displaySeparator(
        rawSeparator: String,
        previousSentence: ReaderSentence?,
        currentSentence: ReaderSentence
    ): String {
        val normalized = rawSeparator.replace('\r', '\n')
        val currText = currentSentence.text.trim()
        val prevText = previousSentence?.text?.trim().orEmpty()
        val isHeading = currText.startsWith("#") || prevText.startsWith("#")
        val isTable = currText.startsWith("|") || prevText.startsWith("|")
        val isTocRow = looksLikeTableOfContentsRow(currText) || looksLikeTableOfContentsRow(prevText)
        val isBulletOrList = currText.startsWith("- ") || currText.startsWith("* ") || currText.startsWith("• ") || BULLET_NUMBERED_PATTERN.containsMatchIn(currText)
        val isDialogueStart = (currText.startsWith("\"") || currText.startsWith("“") || currText.startsWith("—") || currText.startsWith("–")) && (prevText.endsWith("\"") || prevText.endsWith("”") || prevText.endsWith(".") || prevText.endsWith("!") || prevText.endsWith("?"))

        return when {
            isHeading -> "\n\n"
            isTable -> "\n"
            isTocRow -> if (normalized.count { it == '\n' } >= 2) "\n\n" else "\n"
            previousSentence != null && previousSentence.pageNumber != currentSentence.pageNumber -> "\n\n"
            isBulletOrList -> if (normalized.count { it == '\n' } >= 2) "\n\n" else "\n"
            isDialogueStart && normalized.contains('\n') -> "\n\n"
            normalized.count { it == '\n' } >= 2 -> "\n\n"
            normalized.contains('\n') -> " "
            normalized.contains('\t') -> " "
            normalized.isNotBlank() -> " "
            else -> " "
        }
    }

    private fun splitSentences(source: String): List<String> {
        return splitSentenceFragments(source).map { it.text }
    }

    private fun splitSentenceFragments(source: String, legacy: Boolean = false): List<SentenceFragment> {
        val text = source.replace('\r', '\n').trim()
        if (text.isBlank()) return emptyList()
        val sentences = mutableListOf<SentenceFragment>()
        var start = 0
        var index = 0
        var previousEnd = 0
        while (index < text.length) {
            val char = text[index]
            val boundary = when {
                char in sentenceEndMarks -> if (legacy) isLegacySentenceBoundary(text, index) else isSentenceBoundary(text, index)
                char == '\n' -> isLineBoundary(text, index) || (!legacy && LiteraryDialogue.hasSpeakerLabel(text.substring(index + 1, (index + 121).coerceAtMost(text.length)).substringBefore('\n')))
                else -> false
            }
            if (boundary) {
                var end = index + 1
                if (!legacy && char in sentenceEndMarks) {
                    while (end < text.length && text[end] in "!?.”’\"')]}»") end++
                }
                previousEnd = addTrimmedSentence(text, start, end, previousEnd, sentences)
                start = end
                index = end - 1
            }
            index++
        }
        addTrimmedSentence(text, start, text.length, previousEnd, sentences)
        return sentences.ifEmpty { listOf(SentenceFragment(text, "")) }
    }

    private fun rebuildPages(sentences: List<ReaderSentence>, storedPageCount: Int): String {
        if (sentences.isEmpty()) return ""
        val output = StringBuilder()
        val ordered = sentences.groupBy { it.pageNumber.coerceAtLeast(1) }.toSortedMap()
        ordered.forEach { (pageNumber, pageSentences) ->
            if (output.isNotBlank()) output.append("\n\n")
            output.append(pageMarker(pageNumber)).append('\n')
            val pageText = StringBuilder()
            pageSentences.forEachIndexed { index, sentence ->
                if (pageText.isNotBlank()) {
                    pageText.append(
                        displaySeparator(
                            rawSeparator = sentence.separatorBefore,
                            previousSentence = pageSentences.getOrNull(index - 1),
                            currentSentence = sentence
                        )
                    )
                }
                pageText.append(sentence.text.trim())
            }
            output.append(pageText.toString().trim())
        }
        return output.toString().trim()
    }

    private fun addTrimmedSentence(
        text: String,
        rawStart: Int,
        rawEnd: Int,
        previousEnd: Int,
        output: MutableList<SentenceFragment>
    ): Int {
        var start = rawStart.coerceIn(0, text.length)
        var end = rawEnd.coerceIn(start, text.length)
        while (start < end && text[start].isWhitespace()) start++
        while (end > start && text[end - 1].isWhitespace()) end--
        if (end > start) {
            val separator = if (output.isEmpty()) "" else text.substring(previousEnd.coerceIn(0, start), start)
            output.add(SentenceFragment(text.substring(start, end), separator))
            return end
        }
        return previousEnd
    }

    private val NUMBERED_LINE_PREFIX = Regex("(?:#+\\s*)?(?:(?:[0-9]+\\.)*[0-9]+|[IVXLCDM]+)", RegexOption.IGNORE_CASE)

    private fun isSentenceBoundary(text: String, markIndex: Int): Boolean {
        val mark = text[markIndex]
        if (mark == '.' && text.getOrNull(markIndex + 1)?.isDigit() == true && text.getOrNull(markIndex - 1)?.isDigit() == true) return false
        var end = markIndex + 1
        while (end < text.length && text[end] in "!?.”’\"')]}»") end++
        if (end < text.length && !text[end].isWhitespace()) return false
        val next = text.indexOfFirstAfter(end - 1) { !it.isWhitespace() } ?: return true
        if (mark == '.') {
            var tokenStart = markIndex
            while (tokenStart > 0 && (text[tokenStart - 1].isLetterOrDigit() || text[tokenStart - 1] == '.')) tokenStart--
            val token = text.substring(tokenStart, markIndex).trim('.').lowercase(Locale.ROOT)
            if (token in abbreviations || token in LiteraryDialogue.speakerAbbreviations) return false
            if (token.length == 1 && token.firstOrNull()?.isLetter() == true) return false
            if (MULTI_INITIALS_PATTERN.matches(token)) return false
            val lineStart = text.lastIndexOf('\n', markIndex).let { if (it < 0) 0 else it + 1 }
            val line = text.substring(lineStart, markIndex).trim()
            if (line.matches(NUMBERED_LINE_PREFIX)) return false
            if (looksLikeTableOfContentsRow(text.substring(lineStart, text.indexOf('\n', markIndex).takeIf { it >= 0 } ?: text.length))) return false
            if (end - markIndex > 1 && text.substring(markIndex, end).count { it == '.' } >= 2 && text[next].isLowerCase()) return false
        }
        // A closing speech quote followed by its attribution is one sentence, not a second utterance.
        if (text.substring(markIndex + 1, end).any { it in "”’\"'" } &&
            LiteraryDialogue.startsWithAttribution(text.substring(next, (next + 100).coerceAtMost(text.length)))) return false
        return true
    }

    private fun isLegacySentenceBoundary(text: String, markIndex: Int): Boolean {
        val mark = text[markIndex]
        if (mark == '.') {
            // Check if this dot is part of a run of dots (e.g. "...", "......")
            if (markIndex > 0 && text[markIndex - 1] == '.') return false
            if (markIndex < text.lastIndex && text[markIndex + 1] == '.') return false
            // Check if preceded by another dot/bullet separated only by whitespace (e.g. ". . .")
            val prevNonWs = text.lastNonWhitespaceBefore(markIndex)
            if (prevNonWs == '.' || prevNonWs == '…' || prevNonWs == '•' || prevNonWs == '·') return false
            // Check if this dot leads directly to a trailing page number on the same line (TOC row)
            val remainingLine = text.substring(markIndex + 1).substringBefore('\n').trim()
            if (remainingLine.length in 1..5 && remainingLine.all { it.isDigit() }) return false
        }
        val next = text.indexOfFirstAfter(markIndex) { !it.isWhitespace() } ?: return true
        val nextChar = text[next]
        if (!nextChar.isUpperCase() && !nextChar.isDigit() && nextChar !in "\"'`(") return false
        if (markIndex > 0 && markIndex < text.lastIndex && text[markIndex - 1].isDigit() && text[markIndex + 1].isDigit()) return false
        val token = text.substring(0, markIndex)
            .takeLastWhile { it.isLetterOrDigit() || it == '.' }
            .trim('.')
            .lowercase()
        if (token in abbreviations) return false
        if (token.length == 1 && token.firstOrNull()?.isLetter() == true) return false
        // Detect multi-part initials like J.R.R. or U.S.A.
        if (MULTI_INITIALS_PATTERN.matches(token)) return false

        // Detect leading list or chapter item numbers at the start of a line/paragraph (e.g. "1.", "2.", "1.1.", "IV.")
        if (token.isNotEmpty() && (token.all { it.isDigit() || it == '.' } || ROMAN_NUMERAL_REGEX.matches(token))) {
            val beforeToken = text.substring(0, markIndex - token.length).trimEnd(' ', '\t')
            val cleanBefore = beforeToken.trim(' ', '\t', '#')
            val lowerBefore = cleanBefore.lowercase()
            val isPrefixOrStart = cleanBefore.isEmpty() || cleanBefore.endsWith('\n') ||
                cleanBefore.endsWith('•') || cleanBefore.endsWith('-') || cleanBefore.endsWith('*') ||
                lowerBefore.endsWith("chapter") || lowerBefore.endsWith("part") ||
                lowerBefore.endsWith("section") || lowerBefore.endsWith("book")
            if (isPrefixOrStart) {
                return false
            }
        }
        return true
    }

    private fun isTitleCasedSubheading(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.length !in 3..55) return false
        if (trimmed.startsWith("\"") || trimmed.startsWith("“") || trimmed.startsWith("‘") || trimmed.startsWith("—") || trimmed.startsWith("-")) return false
        if (trimmed.endsWith(",") || trimmed.endsWith(";") || trimmed.endsWith("-") || trimmed.endsWith(":")) return false
        if (trimmed.endsWith(".") && !CHAPTER_NUMBERED_DOT_PATTERN.matches(trimmed)) {
            return false
        }
        val words = trimmed.split(WHITESPACE_REGEX).filter { it.isNotBlank() }
        if (words.isEmpty() || words.size > 8) return false
        val minorWords = setOf("a", "an", "the", "and", "but", "or", "for", "nor", "on", "at", "to", "by", "with", "in", "of", "vs", "vs.", "v", "v.")
        val significantWords = words.filter { it.lowercase(Locale.getDefault()) !in minorWords }
        if (significantWords.isEmpty()) return false
        val capitalizedSignificant = significantWords.count { word -> word.firstOrNull()?.isUpperCase() == true }
        return capitalizedSignificant == significantWords.size
    }

    private fun isStructuralChapterLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.length !in 3..70) return false
        val firstSpace = trimmed.indexOf(' ')
        val firstWord = (if (firstSpace == -1) trimmed else trimmed.substring(0, firstSpace)).lowercase(Locale.getDefault())
        if (firstWord !in CHAPTER_KEYWORDS) return false
        val words = trimmed.split(WHITESPACE_REGEX).filter { it.isNotBlank() }
        if (words.size in 1..2) return true
        val third = words.getOrNull(2).orEmpty().lowercase(Locale.getDefault())
        if (third in RUNNING_VERBS || (words.size > 2 && words[2].firstOrNull()?.isLowerCase() == true)) return false
        if (trimmed.endsWith(',') || trimmed.endsWith(';') || trimmed.endsWith('?')) return false
        return true
    }

    private fun isLineBoundary(text: String, newlineIndex: Int): Boolean {
        val previous = text.lastNonWhitespaceBefore(newlineIndex) ?: return false
        val next = text.firstNonWhitespaceAfter(newlineIndex) ?: return false

        // Double newline (blank line between lines) is always a paragraph boundary
        if (text.getOrNull(newlineIndex + 1) == '\n') return true
        if (newlineIndex > 0 && text.getOrNull(newlineIndex - 1) == '\n') return true

        // If next character is lowercase, this is normal running wrapped prose (unless prev was a table pipe)
        if (next.isLowerCase() && previous != '|') {
            return false
        }

        // Fast-path punctuation boundaries first
        if (previous in listOf('.', '!', '?')) {
            if (next.isUpperCase() || next.isDigit() || next in "\"'`([") return true
        }
        if (previous == ':' && (next.isUpperCase() || next in "\"'`([")) {
            return true
        }

        if (previous == '-') return false

        // Only inspect line slices if there are structural cues (#, |, -, *, •, digit, or uppercase)
        val hasStructuralCue = previous == '|' || next == '#' || next == '|' || next == '-' || next == '*' || next == '•' || next.isDigit() || next.isUpperCase()
        if (!hasStructuralCue) {
            return false
        }

        val prevLine = text.substring(0, newlineIndex).substringAfterLast('\n').trim()
        val nextLine = text.substring(newlineIndex + 1).substringBefore('\n').trim()

        // Markdown headings
        if (prevLine.startsWith("#") || nextLine.startsWith("#")) return true

        // Table rows (keep each table row on its own boundary)
        if (prevLine.startsWith("|") || nextLine.startsWith("|")) return true

        // Bullet / numbered list starts
        if (nextLine.startsWith("- ") || nextLine.startsWith("* ") || nextLine.startsWith("• ") || BULLET_NUMBERED_PATTERN.containsMatchIn(nextLine)) {
            return true
        }

        // Table of contents rows (keep each TOC row on its own boundary even without terminal punctuation)
        if (looksLikeTableOfContentsRow(prevLine) || looksLikeTableOfContentsRow(nextLine)) return true

        // Explicit structural chapter or major section labels
        if (isStructuralChapterLine(prevLine) || isStructuralChapterLine(nextLine)) return true

        return false
    }

    private fun nearestSoftBreak(text: String, desiredEnd: Int, min: Int): Int {
        val safeEnd = desiredEnd.coerceIn(min + 1, text.length)
        val searchStart = (safeEnd - 600).coerceAtLeast(min + 1)
        for (index in safeEnd downTo searchStart) {
            if (text.getOrNull(index) == '\n' && text.getOrNull(index - 1) == '\n') return index
        }
        for (index in safeEnd downTo searchStart) {
            val char = text.getOrNull(index)
            if (char in sentenceEndMarks && isSentenceBoundary(text, index)) {
                return (index + 1).coerceAtMost(text.length)
            }
        }
        // Secondary fallback: find nearest preceding whitespace to avoid splitting mid-word
        for (index in safeEnd downTo searchStart) {
            if (text.getOrNull(index)?.isWhitespace() == true) return index
        }
        return safeEnd
    }

    private fun estimatePageCount(text: String): Int {
        if (text.isBlank()) return 1
        return ceil(text.length.toDouble() / 2200.0).toInt().coerceAtLeast(1)
    }

    private inline fun String.indexOfFirstAfter(start: Int, predicate: (Char) -> Boolean): Int? {
        for (index in (start + 1)..lastIndex) {
            if (predicate(this[index])) return index
        }
        return null
    }

    private fun String.lastNonWhitespaceBefore(index: Int): Char? {
        for (cursor in (index - 1).coerceAtLeast(0) downTo 0) {
            val char = this[cursor]
            if (!char.isWhitespace()) return char
        }
        return null
    }

    private fun String.firstNonWhitespaceAfter(index: Int): Char? {
        for (cursor in (index + 1)..lastIndex) {
            val char = this[cursor]
            if (!char.isWhitespace()) return char
        }
        return null
    }

    private data class PageText(
        val pageNumber: Int,
        val text: String
    )

    private data class SentenceFragment(
        val text: String,
        val separatorBefore: String
    )
}

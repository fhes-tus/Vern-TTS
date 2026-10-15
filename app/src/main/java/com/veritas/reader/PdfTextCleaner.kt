package com.veritas.reader

import java.util.Locale

data class PdfCleanupResult(
    val text: String,
    val removedRepeatedLineCount: Int,
    val removedPageNumberCount: Int,
    val joinedHyphenationCount: Int
)

object PdfTextCleaner {
    private val WHITESPACE_REGEX = Regex("""\s+""")
    private val NUMBERED_HEADING_REGEX = Regex("""^\d+(\.\d+)*\s+[A-Z0-9].*""")
    private val NUMBERED_ITEM_REGEX = Regex("""^\s*(\d{1,3}[.)]|\d{1,3}\s+[A-Za-z0-9])\s+\S+""")
    private val BULLET_ITEM_REGEX = Regex("""^\s*([•◦▪▫‣⁃∙*–—\-]|(?:\([a-zA-Z0-9]+\)|[a-zA-Z]\)))\s+\S+""")
    private val EXPLICIT_CHAPTER_REGEX = Regex(
        """^(CHAPTER|Chapter|PROLOGUE|Prologue|EPILOGUE|Epilogue|INTRODUCTION|Introduction|PREFACE|Preface|PART|Part|BOOK|Book|SECTION|Section|ACT|Act|SCENE|Scene)\b.*""",
        RegexOption.IGNORE_CASE
    )
    private val CHAPTER_NUMBERED_DOT_PATTERN = Regex("""^(CHAPTER|Chapter|Part|Section)?\s*[IVXLCDM\d]+(\.[IVXLCDM\d]+)*\.$""", RegexOption.IGNORE_CASE)
    private val STANDALONE_PAGE_NUMBER_REGEX = Regex("""^[-–—]?\s*\d{1,4}\s*[-–—]?$""")
    private val STANDALONE_PAGE_WORD_REGEX = Regex("""^(page|p\.)\s*\d{1,4}(\s*(of|/)\s*\d{1,4})?$""", RegexOption.IGNORE_CASE)

    private val RUNNING_PROSE_VERBS = setOf(
        "describes", "explores", "examines", "focuses", "covers", "discusses",
        "presents", "analyzes", "reviews", "addresses", "investigates",
        "illustrates", "shows", "demonstrates", "argues", "explains", "details",
        "is", "was", "are", "were", "will", "has", "have", "had", "contains", "provides",
        "updates", "offers", "introduces", "concludes", "follows", "leads", "opens", "begins", "ends"
    )

    private val MINOR_WORDS = setOf(
        "a", "an", "the", "and", "but", "or", "for", "nor", "on", "at", "to", "by", "with",
        "in", "of", "vs", "vs.", "v", "v."
    )

    private val BARE_CHAPTER_PREFIX_REGEX = Regex(
        """^(CHAPTER|Chapter|PART|Part|BOOK|Book|SECTION|Section)(\s+[IVXLCDM\d]+|\s+[A-Za-z]+)?\.?$|^\d{1,3}\.?$|^[IVXLCDM]{1,7}\.?$""",
        RegexOption.IGNORE_CASE
    )

    fun cleanPages(pageTexts: List<String>, pageNumbers: List<Int> = pageTexts.indices.map { it + 1 }, options: PdfImportOptions = PdfImportOptions()): PdfCleanupResult {
        val pageLines = pageTexts.map { page ->
            page.replace('\r', '\n')
                .split('\n')
                .map { it.trim() }
        }

        val repeatedKeys = if (options.cleanupRepeatedLines) findRepeatedHeaderFooterKeys(pageLines) else emptySet()
        var removedRepeated = 0
        var removedPageNumbers = 0
        var joinedHyphenations = 0
        val documentOutput = StringBuilder()

        pageLines.forEachIndexed { pageIndex, lines ->
            val cleanedLines = mutableListOf<String>()
            lines.forEach { line ->
                val cleanLine = if (isPipeTableLine(line)) {
                    line.trim()
                } else {
                    line.replace(WHITESPACE_REGEX, " ").trim()
                }
                val key = normalizedLineKey(cleanLine)
                when {
                    options.cleanupRepeatedLines && key in repeatedKeys -> removedRepeated++
                    options.removePageNumbers && isStandalonePageNumber(cleanLine) -> removedPageNumbers++
                    else -> cleanedLines.add(cleanLine)
                }
            }

            val merged = mergePdfLines(cleanedLines, repairHyphenation = options.repairHyphenation) { joinedHyphenations++ }
            if (merged.isNotBlank()) {
                if (documentOutput.isNotBlank()) documentOutput.append("\n\n")
                val pageNumber = pageNumbers.getOrNull(pageIndex) ?: (pageIndex + 1)
                documentOutput.append(ReaderTextIndex.pageMarker(pageNumber)).append("\n")
                // Page identity lives in the internal marker and reader chrome;
                // an audible "Page N" line would duplicate it in the prose.
                documentOutput.append(merged)
            }
            if (pageIndex < pageLines.lastIndex && merged.isNotBlank()) {
                documentOutput.append("\n")
            }
        }

        return PdfCleanupResult(
            text = documentOutput.toString(),
            removedRepeatedLineCount = removedRepeated,
            removedPageNumberCount = removedPageNumbers,
            joinedHyphenationCount = joinedHyphenations
        )
    }

    private fun findRepeatedHeaderFooterKeys(pageLines: List<List<String>>): Set<String> {
        if (pageLines.size < 3) return emptySet()
        val counts = mutableMapOf<String, Int>()
        pageLines.forEach { lines ->
            val candidates = buildSet {
                lines.take(3).forEach { add(it) }
                lines.takeLast(3).forEach { add(it) }
            }
            candidates.forEach { line ->
                val key = normalizedLineKey(line)
                if (key.length in 4..120 && !isStandalonePageNumber(line)) {
                    counts[key] = (counts[key] ?: 0) + 1
                }
            }
        }
        val threshold = maxOf(2, (pageLines.size * 0.55f).toInt())
        return counts.filterValues { it >= threshold }.keys
    }

    private fun looksLikeTableOfContentsRow(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.length < 3 || !trimmed.last().isDigit()) return false

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

    private fun mergePdfLines(lines: List<String>, repairHyphenation: Boolean = true, onHyphenationJoined: () -> Unit): String {
        val output = StringBuilder()
        var previousWasHeading = false
        var previousHeadingText: String? = null
        var previousWasTable = false
        var previousWasNumberedItem = false

        lines.forEachIndexed { index, originalLine ->
            val line = originalLine.trim()
            if (line.isBlank()) {
                if (output.isNotEmpty() && !output.endsWith("\n\n")) {
                    output.append("\n\n")
                }
                previousWasHeading = false
                previousHeadingText = null
                previousWasTable = false
                previousWasNumberedItem = false
                return@forEachIndexed
            }
            val currentIsTable = isPipeTableLine(line)
            val prevLine = lines.getOrNull(index - 1)
            val nextLine = lines.getOrNull(index + 1)
            val currentIsHeading = !currentIsTable && looksLikeHeading(line, prevLine, nextLine)
            val currentIsNumberedItem = !currentIsTable && !currentIsHeading && looksLikeNumberedItemStart(line)

            if (!currentIsTable && repairHyphenation && output.endsWith("-") && line.firstOrNull()?.isLowerCase() == true) {
                output.deleteCharAt(output.length - 1)
                output.append(line)
                previousWasHeading = false
                previousHeadingText = null
                previousWasTable = false
                previousWasNumberedItem = false
                onHyphenationJoined()
                return@forEachIndexed
            }

            if (output.isBlank()) {
                if (currentIsHeading && !line.startsWith("#")) {
                    output.append("# ").append(line)
                } else {
                    output.append(line)
                }
                previousWasHeading = currentIsHeading
                previousHeadingText = if (currentIsHeading) line else null
                previousWasTable = currentIsTable
                previousWasNumberedItem = currentIsNumberedItem
                return@forEachIndexed
            }

            // Heading Reassembly (Image 1 fix):
            // When previous line was a bare chapter/number prefix (e.g. "Chapter 1" or "1.") without a title,
            // and current line is the chapter subtitle/title, glue them into a single cohesive heading.
            val prevHeading = previousHeadingText
            val canReassembleHeading = previousWasHeading && prevHeading != null &&
                BARE_CHAPTER_PREFIX_REGEX.matches(prevHeading.trim()) &&
                (currentIsHeading || isTitleCasedSubheading(line)) &&
                !line.startsWith("\"") && !line.startsWith("“") && !line.startsWith("‘")

            if (canReassembleHeading) {
                val prev = prevHeading.trim()
                val glue = if (prev.endsWith(".") || prev.endsWith(":")) " " else ": "
                output.append(glue).append(line.removePrefix("#").trim())
                previousHeadingText = "$prev$glue$line"
                previousWasHeading = true
                previousWasTable = false
                previousWasNumberedItem = false
                return@forEachIndexed
            }

            when {
                output.endsWith("\n\n") -> {
                    if (currentIsHeading && !line.startsWith("#")) {
                        output.append("# ")
                    }
                }
                currentIsTable -> {
                    if (previousWasTable) {
                        output.append("\n")
                    } else {
                        output.append("\n\n")
                    }
                }
                previousWasTable -> {
                    output.append("\n\n")
                    if (currentIsHeading && !line.startsWith("#")) {
                        output.append("# ")
                    }
                }
                currentIsHeading -> {
                    output.append("\n\n")
                    if (!line.startsWith("#")) {
                        output.append("# ")
                    }
                }
                currentIsNumberedItem -> {
                    if (previousWasNumberedItem) {
                        output.append("\n")
                    } else {
                        output.append("\n\n")
                    }
                }
                previousWasHeading -> {
                    output.append("\n\n")
                }
                else -> {
                    output.append(' ')
                }
            }
            output.append(line)
            previousWasHeading = currentIsHeading
            previousHeadingText = if (currentIsHeading) line else null
            previousWasTable = currentIsTable
            previousWasNumberedItem = currentIsNumberedItem
        }
        return output.toString()
    }

    internal fun isPipeTableLine(line: String): Boolean {
        return line.startsWith("|") && line.endsWith("|") && line.length > 2
    }

    private fun looksLikeHeading(line: String, prevLine: String? = null, nextLine: String? = null): Boolean {
        val trimmed = line.trim()
        if (trimmed.length > 90 || trimmed.isEmpty()) return false
        if (trimmed.startsWith("[[VERITAS_") || trimmed.contains("VERITAS_PAGE", ignoreCase = true) || trimmed.contains("veritas page", ignoreCase = true)) return false
        if (trimmed.startsWith("#")) return true
        if (looksLikeTableOfContentsRow(trimmed)) return false

        // Check whether next line starts with lowercase (sentence continues across wrapped line)
        val nextTrimmed = nextLine?.trim().orEmpty()
        val nextStartsLower = nextTrimmed.firstOrNull()?.isLowerCase() == true
        if (nextStartsLower) {
            // A line whose sentence spills onto the next line in lowercase is NEVER a heading!
            return false
        }

        val isExplicitChapter = isStructuralChapterHeading(trimmed)
        val isNumberedHeading = NUMBERED_HEADING_REGEX.containsMatchIn(trimmed)

        // Check whether previous line was an unfinished sentence
        val prevTrimmed = prevLine?.trim().orEmpty()
        val prevEndsWithTerminal = prevTrimmed.isEmpty() ||
            prevTrimmed.startsWith("#") ||
            looksLikeExplicitHeading(prevTrimmed) ||
            prevTrimmed.lastOrNull() in listOf('.', '!', '?', ':', '"', '”', '’', '\'')

        if (!prevEndsWithTerminal) {
            // In the middle of running prose, only unambiguous short structural headings qualify
            if (!isExplicitChapter && !isNumberedHeading) return false
            val words = trimmed.split(WHITESPACE_REGEX).filter { it.isNotBlank() }
            if (words.size > 5) return false
        }

        if (isExplicitChapter || isNumberedHeading) return true

        val letters = trimmed.filter { it.isLetter() }
        if (letters.length in 4..65) {
            val upperRatio = letters.count { it.isUpperCase() }.toFloat() / letters.length
            if (upperRatio >= 0.85f) return true
        }
        if (isTitleCasedSubheading(trimmed)) return true
        return false
    }

    private fun isStructuralChapterHeading(trimmed: String): Boolean {
        if (!EXPLICIT_CHAPTER_REGEX.matches(trimmed)) return false
        val words = trimmed.split(WHITESPACE_REGEX).filter { it.isNotBlank() }
        if (words.size in 1..2) return true
        if (words.size > 8) return false
        // If 3rd word is an active verb (e.g. "Part 5 describes...", "Part 2 updates..."), this is inline prose
        val thirdWord = words.getOrNull(2)?.lowercase(Locale.ROOT).orEmpty().trim('.', ',', ':', ';')
        if (thirdWord in RUNNING_PROSE_VERBS) return false
        // Complete sentences ending in periods/quotes are prose
        if (words.size > 4 && (trimmed.endsWith('.') || trimmed.endsWith('?') || trimmed.endsWith(',') || trimmed.endsWith('"') || trimmed.endsWith('”'))) {
            return false
        }
        return true
    }

    private fun looksLikeExplicitHeading(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.startsWith("#")) return true
        if (EXPLICIT_CHAPTER_REGEX.matches(trimmed)) return true
        if (NUMBERED_HEADING_REGEX.containsMatchIn(trimmed)) return true
        return false
    }

    private fun isTitleCasedSubheading(trimmed: String): Boolean {
        if (trimmed.length !in 3..60) return false
        if (trimmed.contains("\t") || trimmed.contains("   ")) return false
        if (trimmed.startsWith("\"") || trimmed.startsWith("“") || trimmed.startsWith("‘") || trimmed.startsWith("—") || trimmed.startsWith("-")) return false
        if (trimmed.endsWith(",") || trimmed.endsWith(";") || trimmed.endsWith("-") || trimmed.endsWith(":")) return false
        if (trimmed.endsWith(".") && !CHAPTER_NUMBERED_DOT_PATTERN.matches(trimmed)) {
            return false
        }
        val words = trimmed.split(WHITESPACE_REGEX).filter { it.isNotBlank() }
        if (words.isEmpty() || words.size > 10) return false
        val significantWords = words.filter { it.lowercase(Locale.ROOT) !in MINOR_WORDS }
        if (significantWords.isEmpty()) return false
        val capitalizedSignificant = significantWords.count { word -> word.firstOrNull()?.isUpperCase() == true }
        return capitalizedSignificant == significantWords.size
    }

    private fun looksLikeNumberedItemStart(line: String): Boolean {
        val trimmed = line.trim()
        return NUMBERED_ITEM_REGEX.containsMatchIn(trimmed) ||
            BULLET_ITEM_REGEX.containsMatchIn(trimmed) ||
            looksLikeTableOfContentsRow(trimmed)
    }

    private fun normalizedLineKey(line: String): String {
        return line.lowercase(Locale.ROOT)
            .replace(Regex("\\d+"), "#")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    private fun isStandalonePageNumber(line: String): Boolean {
        val trimmed = line.trim()
        return STANDALONE_PAGE_NUMBER_REGEX.matches(trimmed) ||
            STANDALONE_PAGE_WORD_REGEX.matches(trimmed)
    }
}

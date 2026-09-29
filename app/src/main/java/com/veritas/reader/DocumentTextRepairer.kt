package com.veritas.reader

/**
 * Repairs documents that were corrupted by pseudo-table formatting
 * (where ordinary words in paragraphs were wrapped with '|' pipe symbols).
 */
object DocumentTextRepairer {
    private val WHITESPACE_REGEX = Regex("""\s+""")

    private val TABLE_DIVIDER_REGEX = Regex("""^\|(?:\s*:?---+:?\s*\|)+$""")

    fun isTableDividerLine(line: String): Boolean = line.trim().matches(TABLE_DIVIDER_REGEX)

    private fun isPipeTableLine(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.length > 2 && !isTableDividerLine(trimmed)
    }

    private fun StringBuilder.lastNonWhitespaceChar(): Char? {
        for (i in length - 1 downTo 0) {
            val c = this[i]
            if (!c.isWhitespace()) return c
        }
        return null
    }

    fun isPseudoTableLine(line: String): Boolean {
        val trimmed = line.trim()
        if (!trimmed.contains("|") || isTableDividerLine(trimmed)) return false
        val segments = trimmed.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        if (segments.isEmpty()) return false

        // In pseudo-tables, almost every cell is a single word of prose (e.g. "| me | to | a | larger |")
        if (segments.size >= 3) {
            val singleWordCount = segments.count { !it.contains(" ") }
            if (singleWordCount.toFloat() / segments.size >= 0.75f) {
                return true
            }
        }

        // Trailing/leading pipe on short 1-2 word fragments (e.g. "sidewalk | as |", "moments |", "the |")
        if (trimmed.startsWith("|") || trimmed.endsWith("|")) {
            if (segments.size in 1..2 && segments.all { !it.contains(" ") || it.split(WHITESPACE_REGEX).size <= 2 }) {
                return true
            }
        }
        return false
    }

    fun repairPseudoTables(rawText: String): String {
        if (!rawText.contains("|")) return rawText

        // Fast streaming check: does the text actually contain any pseudo-table lines or divider lines?
        val hasRepairsNeeded = rawText.lineSequence().any {
            val trimmed = it.trim()
            isPseudoTableLine(trimmed) || isTableDividerLine(trimmed)
        }
        if (!hasRepairsNeeded) return rawText

        val rawLines = rawText.lines()
        val pseudoIndices = rawLines.indices.filter { isPseudoTableLine(rawLines[it]) }.toSet()
        val repairedLines = mutableListOf<String>()

        for (idx in rawLines.indices) {
            val line = rawLines[idx]
            val trimmed = line.trim()
            if (isPseudoTableLine(trimmed)) {
                val segments = trimmed.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                val cleanLine = segments.joinToString(" ")
                if (cleanLine.isNotBlank()) {
                    repairedLines.add(cleanLine)
                }
            } else if (isTableDividerLine(trimmed)) {
                // If it is adjacent to a repaired pseudo-table line or not between two valid pipe table lines, discard it!
                val prevValid = idx > 0 && isPipeTableLine(rawLines[idx - 1]) && (idx - 1) !in pseudoIndices
                val nextValid = idx < rawLines.lastIndex && isPipeTableLine(rawLines[idx + 1]) && (idx + 1) !in pseudoIndices
                if (prevValid && nextValid) {
                    repairedLines.add(line)
                }
                // Otherwise this is an orphaned separator inside prose - drop it.
            } else {
                repairedLines.add(line)
            }
        }

        // Merge lines that were artificially broken across newlines because of pseudo-table rows
        val output = StringBuilder()
        var i = 0
        while (i < repairedLines.size) {
            val line = repairedLines[i].trim()
            if (line.isBlank()) {
                // If previous line did not finish a sentence, do not start a new paragraph
                val prevChar = output.lastNonWhitespaceChar()
                val isPrevTerminal = prevChar in listOf('.', '!', '?', ':', '"', '”')
                if (isPrevTerminal) {
                    if (output.isNotEmpty() && !output.endsWith("\n\n")) {
                        output.append("\n\n")
                    }
                }
                i++
                continue
            }

            // Keep structural lines (page markers, headings, real markdown tables) intact
            if (line.startsWith("[[VERITAS_PAGE:") || line.startsWith("#") || (line.startsWith("|") && line.endsWith("|"))) {
                if (output.isNotEmpty() && !output.endsWith("\n\n")) {
                    output.append("\n\n")
                }
                output.append(line).append("\n\n")
                i++
                continue
            }

            if (output.isEmpty() || output.endsWith("\n\n")) {
                output.append(line)
            } else {
                val prevChar = output.lastNonWhitespaceChar()
                val isPrevTerminal = prevChar in listOf('.', '!', '?', ':', '"', '”')

                if (isPrevTerminal) {
                    // Check if next line starts with lowercase (sentence wrapped after period/abbrev)
                    val nextStartsLower = line.firstOrNull()?.isLowerCase() == true
                    if (nextStartsLower) {
                        output.append(" ").append(line)
                    } else {
                        output.append("\n\n").append(line)
                    }
                } else {
                    // Line was wrapped in the middle of a running sentence
                    output.append(" ").append(line)
                }
            }
            i++
        }

        return output.toString().replace(Regex("""\n{3,}"""), "\n\n").trim()
    }
}

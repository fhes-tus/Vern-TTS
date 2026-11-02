package com.veritas.reader

import org.json.JSONArray
import java.util.Locale

/**
 * Pure decision logic for playback advancement, extracted from PlaybackService so the
 * "rat bug" class of errors (advancing from a UI-mutated shared index instead of the
 * sentence actually spoken) stays pinned by unit tests.
 */
object PlaybackAdvance {
    /**
     * The authoritative index of the sentence that was just spoken. [activeChunkIndex] is
     * the service's internal record (-1 when nothing was spoken); the shared index is a
     * fallback only, clamped to bounds, because the UI can mutate it mid-utterance.
     */
    fun resolveCurrentIndex(activeChunkIndex: Int, sharedIndex: Int, lastIndex: Int): Int {
        if (lastIndex < 0) return 0
        return activeChunkIndex.takeIf { it in 0..lastIndex }
            ?: sharedIndex.coerceIn(0, lastIndex)
    }

    /** Next index to speak after [current], or null when the document is finished. */
    fun nextIndex(current: Int, lastIndex: Int): Int? =
        if (current < lastIndex) current + 1 else null
}

/**
 * Silences decorative glyphs (list bullets, arrows, dingbats, box-drawing, dot
 * leaders) before text reaches the TTS engine, so it never verbalizes "black
 * circle" or "rightwards arrow". Glyph substitutions retain their length;
 * deletions and table formatting compose source spans through SpokenText so
 * engine offsets still refer to the unchanged displayed book.
 */
object SpeechSanitizer {
    private val extraSilentGlyphs = setOf(
        '#', // markdown heading delimiter
        '|', // table / column delimiter
        '•', // • bullet
        '‣', // ‣ triangular bullet
        '⁃', // ⁃ hyphen bullet
        '∙', // ∙ bullet operator
        '▪', '▫', '●', '○', '■', '□', '◆', '◇',
        '▶', '◀', '▲', '▼', '►', '◄',
        '★', '☆', // ★ ☆
        '✓', '✔', '✗', '✘', // ✓ ✔ ✗ ✘
        '❤' // ❤
    )

    private val MONTH_TICK_TOKENS = setOf(
        "jan", "january", "feb", "february", "mar", "march", "apr", "april",
        "may", "jun", "june", "jul", "july", "aug", "august", "sep", "sept", "september",
        "oct", "october", "nov", "november", "dec", "december"
    )

    private val DAY_TICK_TOKENS = setOf(
        "mon", "monday", "tue", "tues", "tuesday", "wed", "wednesday",
        "thu", "thur", "thursday", "fri", "friday", "sat", "saturday", "sun", "sunday"
    )

    private val AXIS_TICK_PATTERN = Regex(
        """^[-+]?[$€£¥]?\d+(?:[.,]\d+)?(?:%|[kmbx]|ms|s)?$""",
        RegexOption.IGNORE_CASE
    )

    private val QUARTER_TICK_PATTERN = Regex(
        """^q[1-4]$""",
        RegexOption.IGNORE_CASE
    )

    private fun isSilent(char: Char): Boolean {
        val code = char.code
        return char in extraSilentGlyphs ||
            code in 0x2190..0x21FF || // arrows
            code in 0x2500..0x25FF || // box drawing, blocks, geometric shapes
            code in 0x2700..0x27BF    // dingbats
    }

    /**
     * Identifies markdown table divider lines (e.g. `| --- | --- |` or `|:---|---:|`)
     * which must be silently bypassed so TTS never verbalizes dashes or colons.
     */
    fun isTableSeparatorLine(text: String): Boolean {
        val trimmed = text.trim()
        if (!trimmed.contains('|')) return false
        val content = trimmed.removePrefix("|").removeSuffix("|").trim()
        if (content.isEmpty()) return false
        val cells = content.split('|')
        return cells.all { cell ->
            val c = cell.trim()
            c.isNotEmpty() && c.all { it == '-' || it == ':' || it == ' ' } && c.contains('-')
        }
    }

    /**
     * Identifies markdown pipe table content rows (excluding pure separator lines).
     */
    fun isTableRow(text: String): Boolean {
        val trimmed = text.trim()
        if (!trimmed.contains('|')) return false
        if (isTableSeparatorLine(trimmed)) return false
        return trimmed.startsWith("|") || trimmed.endsWith("|") || trimmed.count { it == '|' } >= 2
    }

    /**
     * Determines if an individual token is an axis tick (numeric, currency, percentage, quarter, month, day).
     */
    fun isAxisTickToken(rawToken: String): Boolean {
        val token = rawToken.trim().trimEnd('.', ',', ';', ':')
        if (token.isEmpty()) return false
        val lower = token.lowercase(Locale.ROOT)
        return lower in MONTH_TICK_TOKENS ||
            lower in DAY_TICK_TOKENS ||
            QUARTER_TICK_PATTERN.matches(lower) ||
            AXIS_TICK_PATTERN.matches(token)
    }

    /**
     * Detects floating graph axis tick sequences (e.g. "0 10 20 30 40 50", "0% 25% 50% 75%", "Jan Feb Mar Apr")
     * extracted from charts. This heuristic alone cannot distinguish a chart from a meaningful list;
     * it must not be used to mute ordinary book text without structural evidence.
     */
    fun isGraphAxisNoise(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.startsWith("#") || trimmed.startsWith("|")) return false

        val tokens = trimmed.split(Regex("""[\s,;]+""")).filter { it.isNotBlank() }
        if (tokens.size < 3) return false

        val tickCount = tokens.count { isAxisTickToken(it) }
        val tickRatio = tickCount.toDouble() / tokens.size.toDouble()
        return tickRatio >= 0.80
    }

    /**
     * Adds a pause to a table cell without assigning a meaning to missing or ambiguous values.
     */
    fun formatTableCellForSpeech(cell: String): String {
        return formatTableCell(SpokenText.identity(cell)).text
    }

    private fun formatTableCell(cell: SpokenText): SpokenText {
        val cleaned = cell.trim().mapChars { _, char -> SpeechPunctuation.normalize(char) }
        if (cleaned.text.isEmpty()) return cleaned
        return if (cleaned.text.trimEnd { it.isWhitespace() || SpeechPunctuation.isClosingDelimiter(it) }
            .lastOrNull()?.let { it in ".!?:…" } == true) cleaned else cleaned.appendPause()
    }

    /**
     * Turns a table row into spaced sentences where each column ends with a period (.) to trigger
     * a natural vocal pitch drop and breathing pause between columns.
     */
    fun formatTableRowForSpeech(rowText: String): String {
        return formatTableRow(SpokenText.identity(rowText)).text
    }

    private fun tableCellRanges(row: String): List<IntRange> {
        var start = row.indexOfFirst { !it.isWhitespace() }
        if (start < 0) return emptyList()
        var end = row.indexOfLast { !it.isWhitespace() } + 1
        if (row[start] == '|') start++
        if (end > start && row[end - 1] == '|') end--
        val cells = mutableListOf<IntRange>()
        for (index in start until end) if (row[index] == '|') {
            cells.add(start until index)
            start = index + 1
        }
        cells.add(start until end)
        return cells
    }

    private fun formatTableRow(row: SpokenText): SpokenText = row.join(
        tableCellRanges(row.text).map { formatTableCell(row.slice(it.first, it.last + 1)) }
            .filter { it.text.isNotEmpty() }
    )

    /**
     * Maps an original source offset within a table row to its column (0-based).
     */
    fun tableColumnIndexAt(rowText: String, sourceCharOffset: Int): Int {
        if (!isTableRow(rowText)) return -1
        val cells = tableCellRanges(rowText)
        if (cells.isEmpty()) return -1
        cells.forEachIndexed { index, cell ->
            if (sourceCharOffset <= cell.last + 1) return index
        }
        return cells.lastIndex
    }

    fun forSpeech(text: String): String = forSpeech(SpokenText.identity(text)).text

    internal fun forSpeech(input: SpokenText): SpokenText {
        if (input.text.isEmpty()) return input
        val raw = if (input.text.contains("[[VERITAS_")) {
            input.replace(Regex("""\[\[VERITAS_[^\]]+\]\]""")) { "" }.trim()
        } else {
            input
        }
        if (raw.text.isEmpty()) return raw

        // Mute table separator lines (e.g. | --- | --- |)
        if (isTableSeparatorLine(raw.text)) return raw.replace(Regex("[\\s\\S]+")) { "" }

        // Table content row: speak column by column with punctuation cadence
        if (isTableRow(raw.text)) return formatTableRow(raw)

        val chars = CharArray(raw.text.length) { index ->
            val char = raw.text[index]
            when {
                isSilent(char) -> ' '
                char == '…' -> '.' // … one pause, not "dot dot dot"
                else -> SpeechPunctuation.normalize(char)
            }
        }
        // Older imports contain synthetic page labels in their saved prose.
        // Silence those without reindexing existing notes or progress offsets.
        Regex("(?m)^Page \\d+[ \\t]*(?=\\n|$)").findAll(raw.text).forEach { match ->
            for (i in match.range) chars[i] = ' '
        }
        // Dot leaders ("......" in tables of contents): keep the first dot for a
        // single pause, blank the rest — run length is preserved.
        var index = 0
        while (index < chars.size) {
            if (chars[index] == '.') {
                var end = index + 1
                while (end < chars.size && chars[end] == '.') end++
                if (end - index >= 3) {
                    for (i in index + 1 until end) chars[i] = ' '
                }
                index = end
            } else {
                index++
            }
        }
        return raw.mapChars { index, _ -> chars[index] }
    }

    /** False when a chunk is pure decoration and should be skipped, not spoken. */
    fun isSpeakable(text: String): Boolean = forSpeech(text).isNotBlank()
}

/**
 * Pure fallback decision for the resilient prefs-JSON store: prefer the primary value if
 * it parses, else recover from the last-known-good backup, else empty. Extracted so the
 * recovery path is actually exercised by tests rather than only running on corruption.
 */
internal object ResilientJson {
    fun chooseArray(primary: String?, backup: String?): JSONArray {
        primary?.let { raw ->
            runCatching { JSONArray(raw) }.getOrNull()?.let { return it }
        }
        backup?.let { raw ->
            runCatching { JSONArray(raw) }.getOrNull()?.let { return it }
        }
        return JSONArray()
    }
}

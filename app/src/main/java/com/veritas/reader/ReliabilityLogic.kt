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
 * circle" or "rightwards arrow". CRITICAL invariant: every replacement is
 * length-preserving (glyph → space, ellipsis → period) because word-level
 * highlight sync maps engine character offsets back onto the DISPLAYED string —
 * deleting characters would drift the highlight out of sync with the audio.
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
     * extracted from charts, returning true to mute them while letting figure captions be spoken.
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
     * Formats an individual table cell for natural speech with full-stop pitch drops and empty-cell handling.
     */
    fun formatTableCellForSpeech(cell: String): String {
        val cleaned = SpeechPunctuation.normalize(cell.trim())
        return when {
            cleaned.isEmpty() || cleaned == "-" || cleaned == "—" || cleaned == "–" ||
                cleaned.equals("N/A", ignoreCase = true) || cleaned.equals("NA", ignoreCase = true) ||
                cleaned.equals("None", ignoreCase = true) -> "None."
            cleaned.trimEnd { it.isWhitespace() || SpeechPunctuation.isClosingDelimiter(it) }
                .lastOrNull()?.let { it in ".!?:…" } == true -> cleaned
            else -> "$cleaned."
        }
    }

    /**
     * Turns a table row into spaced sentences where each column ends with a period (.) to trigger
     * a natural vocal pitch drop and breathing pause between columns.
     */
    fun formatTableRowForSpeech(rowText: String): String {
        val trimmed = rowText.trim()
        val content = trimmed.removePrefix("|").removeSuffix("|")
        val cells = content.split('|').map { it.trim() }
        return cells.joinToString(" ") { formatTableCellForSpeech(it) }
    }

    /**
     * Maps spoken character offset within a table row back to the corresponding column index (0-based).
     */
    fun tableColumnIndexAt(rowText: String, spokenCharOffset: Int): Int {
        if (!isTableRow(rowText)) return -1
        val content = rowText.trim().removePrefix("|").removeSuffix("|")
        val cells = content.split('|').map { it.trim() }
        if (cells.isEmpty()) return -1
        var cumulative = 0
        cells.forEachIndexed { index, cell ->
            val spokenCell = formatTableCellForSpeech(cell)
            val cellLen = spokenCell.length
            if (spokenCharOffset in cumulative..(cumulative + cellLen)) {
                return index
            }
            cumulative += cellLen + 1
        }
        return cells.lastIndex
    }

    fun forSpeech(text: String): String {
        if (text.isEmpty()) return text
        val raw = if (text.contains("[[VERITAS_")) {
            text.replace(Regex("""\[\[VERITAS_[^\]]+\]\]"""), "").trim()
        } else {
            text
        }
        if (raw.isEmpty()) return ""

        // Mute table separator lines (e.g. | --- | --- |)
        if (isTableSeparatorLine(raw)) return ""

        // Mute floating graph axis ticks (e.g. 0 10 20 30...)
        if (isGraphAxisNoise(raw)) return ""

        // Table content row: speak column by column with punctuation cadence
        if (isTableRow(raw)) return formatTableRowForSpeech(raw)

        val chars = CharArray(raw.length) { index ->
            val char = raw[index]
            when {
                isSilent(char) -> ' '
                char == '…' -> '.' // … one pause, not "dot dot dot"
                else -> SpeechPunctuation.normalize(char)
            }
        }
        // Older imports contain synthetic page labels in their saved prose.
        // Silence those without reindexing existing notes or progress offsets.
        Regex("(?m)^Page \\d+[ \\t]*(?=\\n|$)").findAll(raw).forEach { match ->
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
        return String(chars)
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

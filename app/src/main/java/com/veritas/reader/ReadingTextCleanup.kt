package com.veritas.reader

/** Built-in presentation cleanup; never rewrites the saved book or user pronunciation rules. */
internal object ReadingTextCleanup {
    private val emphasis = listOf(
        Regex("""(?<![\p{L}\p{N}\p{M}_\\])(_{1,3})(?=\S)[^_\r\n]+(?<![\s\\])\1(?![\p{L}\p{N}\p{M}_])"""),
        Regex("""(?<![\p{L}\p{N}\p{M}*\\])(\*{1,3})(?=\S)[^*\r\n]+(?<![\s\\])\1(?![\p{L}\p{N}\p{M}*])""")
    )
    private val heading = Regex("""(?m)^[ \t]{0,3}#{1,6}(?:[ \t]+|$)""")
    private val headingEnd = Regex("""[ \t]+#+[ \t]*$""")
    private val divider = Regex("""(?m)^[ \t]{0,3}(?:(?:-[ \t]*){3,}|(?:\*[ \t]*){3,}|(?:_[ \t]*){3,})\r?$""")
    private val reference = Regex("""\[[1-9][0-9]{0,2}\]""")
    private val mathematicalContext = Regex(
        """[=+×÷^<>]|\b(?:equals|squared|cubed|matrix|vector|array|indices|index|subscript)\b|\b(?:plus|minus|times)\s+[0-9]""",
        RegexOption.IGNORE_CASE
    )
    private val indexedValue = Regex("""(?:\b[\p{L}]|\b[\p{L}\p{N}]*_[\p{L}\p{N}_]*|\b(?:values?|items?|list|dimensions?))\s*$""", RegexOption.IGNORE_CASE)
    private val numberLabel = Regex("""\b(?:number|count|age|temperature|figure|equation|question|exercise|example|page|chapter|volume|element|entry|is|are|was|were|becomes?|contains?|holds?|has|have)\s*$""", RegexOption.IGNORE_CASE)
    private val quantityAfter = Regex("""^\s*(?:people|participants|patients|subjects|items?|books?|pages?|chapters?|days?|hours?|minutes?|seconds?|years?|months?|degrees?|percent|kg|cm|mm|km)\b|^\s*%""", RegexOption.IGNORE_CASE)
    private val codeIndexLine = Regex("""\s*[\p{L}_][\p{L}\p{N}_]*\[[0-9]+\](?:\.[\p{L}_][\p{L}\p{N}_]*)?\s*""")
    private val citations = DocumentRepository.CompiledPronunciationRule { source ->
        val code = codeRanges(source)
        reference.findAll(source).filter { match ->
            val start = match.range.first
            val end = match.range.last + 1
            val before = source.getOrNull(start - 1)
            val after = source.getOrNull(end)
            val lineStart = source.lastIndexOf('\n', start).let { if (it < 0) 0 else it + 1 }
            val lineEnd = source.indexOf('\n', end).let { if (it < 0) source.length else it }
            val prefix = source.substring(lineStart, start)
            val line = source.substring(lineStart, lineEnd)
            before !in listOf('\\', '[') && after !in listOf(']', '(', ':') &&
                (after != '[' || reference.matchAt(source, end) != null) &&
                code.none { start in it } &&
                !SpeechSanitizer.isTableRow(line) &&
                !mathematicalContext.containsMatchIn(line) &&
                !codeIndexLine.matches(line) &&
                !indexedValue.containsMatchIn(prefix) &&
                !numberLabel.containsMatchIn(prefix) &&
                !quantityAfter.containsMatchIn(source.substring(end, lineEnd)) &&
                // A bracketed number leading a sentence may be a quantity/list label.
                !(prefix.isBlank() && source.substring(end, lineEnd).any(Char::isLetterOrDigit))
        }.map { PronunciationRulesEngine.Edit(it.range, "", preserveWordGap = true) }.asSequence()
    }

    fun forDisplay(source: String): String = markdown(SpokenText.identity(source)).text.trim()

    fun forSpeech(input: SpokenText): SpokenText = markdown(input).applyRules(listOf(citations))

    private fun markdown(input: SpokenText): SpokenText {
        val text = input.text
        if (text.none { it in "_*#-" }) return input
        val code = codeRanges(text)
        val hidden = mutableListOf<IntRange>()
        fun hide(range: IntRange) {
            if (code.none { it.first <= range.last && range.first <= it.last }) hidden.add(range)
        }
        divider.findAll(text).forEach { hide(it.range) }
        heading.findAll(text).forEach { match ->
            hide(match.range)
            val end = text.indexOf('\n', match.range.last + 1).let { if (it < 0) text.length else it }
            headingEnd.find(text.substring(match.range.last + 1, end))?.let {
                hide((it.range.first + match.range.last + 1)..(it.range.last + match.range.last + 1))
            }
        }
        emphasis.forEach { pattern -> pattern.findAll(text).forEach { match ->
            if (code.none { it.first <= match.range.last && match.range.first <= it.last }) {
                val width = match.groupValues[1].length
                hidden.add(match.range.first until match.range.first + width)
                hidden.add(match.range.last + 1 - width..match.range.last)
            }
        } }
        return input.omit(hidden)
    }

    /** Inline/fenced backticks protect literal code from prose heuristics. */
    internal fun codeRanges(text: String): List<IntRange> {
        val result = mutableListOf<IntRange>()
        var cursor = 0
        while (cursor < text.length) {
            val start = text.indexOf('`', cursor)
            if (start < 0) break
            var end = start + 1
            while (end < text.length && text[end] == '`') end++
            val close = text.indexOf(text.substring(start, end), end)
            cursor = if (close < 0) text.length else close + end - start
            result.add(start until cursor)
        }
        return result
    }
}

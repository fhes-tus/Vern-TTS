package com.veritas.reader

/** Literal and pattern rules match original input once. Replacement output is never matched again. */
internal object PronunciationRulesEngine {
    data class Edit(val range: IntRange, val replacement: String, val preserveWordGap: Boolean = false)
    fun compile(rules: List<PronunciationRule>): List<DocumentRepository.CompiledPronunciationRule> =
        rules.filter { it.enabled && it.find.isNotBlank() }.distinctBy(SpeechPatternRules::identity)
            .mapNotNull { runCatching { SpeechPatternRules.compile(it) }.getOrNull() }

    fun edits(source: String, rules: List<DocumentRepository.CompiledPronunciationRule>): List<Edit> {
        data class Candidate(val order: Int, val edit: Edit, val remaining: Iterator<Edit>)
        val queue = java.util.PriorityQueue(compareBy<Candidate> { it.edit.range.first }
            .thenByDescending { it.edit.range.last - it.edit.range.first }.thenBy { it.order })
        rules.forEachIndexed { order, rule ->
            val iterator = rule.edits(source).iterator()
            if (iterator.hasNext()) queue.add(Candidate(order, iterator.next(), iterator))
        }
        val selected = mutableListOf<Edit>()
        var end = 0
        while (queue.isNotEmpty()) {
            val candidate = queue.remove()
            val edit = candidate.edit
            if (edit.range.first >= end) { selected.add(edit); end = edit.range.last + 1 }
            if (candidate.remaining.hasNext()) queue.add(candidate.copy(edit = candidate.remaining.next()))
        }
        fun outputBefore(index: Int): Int? {
            var offset = selected[index].range.first
            var previous = index - 1
            while (previous >= 0 && selected[previous].range.last + 1 == offset) {
                val value = selected[previous].replacement
                if (value.isNotEmpty()) return value.codePointBefore(value.length)
                offset = selected[previous].range.first; previous--
            }
            return if (offset > 0) source.codePointBefore(offset) else null
        }
        fun outputAfter(index: Int): Int? {
            var offset = selected[index].range.last + 1
            var next = index + 1
            while (next < selected.size && selected[next].range.first == offset) {
                val value = selected[next].replacement
                if (value.isNotEmpty()) return value.codePointAt(0)
                offset = selected[next].range.last + 1; next++
            }
            return if (offset < source.length) source.codePointAt(offset) else null
        }
        fun marker(code: Int) = code == '_'.code || !wordPoint(code)
        // Template markers separate inserted words. Regex replacements remain exact.
        // Use output neighbours so consecutive replacements/omissions share one gap.
        var at = 0
        while (at < selected.size) {
            val edit = selected[at]
            if (edit.replacement.isNotEmpty()) {
                if (edit.preserveWordGap) {
                    val left = marker(source.codePointAt(edit.range.first)) && wordPoint(edit.replacement.codePointAt(0)) &&
                        outputBefore(at)?.let(::wordPoint) == true
                    val right = marker(source.codePointBefore(edit.range.last + 1)) &&
                        wordPoint(edit.replacement.codePointBefore(edit.replacement.length)) && outputAfter(at)?.let(::wordPoint) == true
                    selected[at] = edit.copy(replacement = (if (left) " " else "") + edit.replacement + if (right) " " else "")
                }
                at++; continue
            }
            var end = at
            while (end + 1 < selected.size && selected[end + 1].replacement.isEmpty() &&
                selected[end + 1].range.first == selected[end].range.last + 1) end++
            if ((at..end).any { selected[it].preserveWordGap } && outputBefore(at)?.let(::wordPoint) == true &&
                outputAfter(end)?.let(::wordPoint) == true)
                selected[at] = selected[at].copy(replacement = " ")
            at = end + 1
        }
        // A fully skipped sentence must not submit a leftover full stop or quotation mark to TTS.
        if (selected.any { it.replacement.isBlank() }) {
            fun silent(ch: Char) = ch.isWhitespace() || ch in ".,;:!?…—–-()[]{}\"'‘’“”"
            var cursor = 0
            var hasContent = false
            for (edit in selected) {
                if ((cursor until edit.range.first).any { !silent(source[it]) } || edit.replacement.any { !silent(it) }) {
                    hasContent = true; break
                }
                cursor = edit.range.last + 1
            }
            if (!hasContent && (cursor until source.length).none { !silent(source[it]) })
                return listOf(Edit(source.indices, ""))
        }
        return selected
    }

    private fun wordPoint(code: Int): Boolean = Character.isLetterOrDigit(code) || code == '_'.code ||
        when (Character.getType(code)) {
            Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(),
            Character.ENCLOSING_MARK.toInt() -> true
            else -> false
        }

    fun apply(source: String, rules: List<PronunciationRule>): String = applyCompiled(source, compile(rules))

    fun applyCompiled(source: String, rules: List<DocumentRepository.CompiledPronunciationRule>): String {
        val edits = edits(source, rules)
        if (edits.isEmpty()) return source
        return buildString {
            var cursor = 0
            edits.forEach { edit -> append(source, cursor, edit.range.first); append(edit.replacement); cursor = edit.range.last + 1 }
            append(source, cursor, source.length)
        }
    }
}

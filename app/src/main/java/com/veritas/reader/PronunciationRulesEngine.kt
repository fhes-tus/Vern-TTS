package com.veritas.reader

/** Literal, case-insensitive word/phrase rules. Replacements are never matched again. */
internal object PronunciationRulesEngine {
    data class Edit(val range: IntRange, val replacement: String)
    fun compile(rules: List<PronunciationRule>): List<DocumentRepository.CompiledPronunciationRule> =
        rules.filter { it.enabled && it.find.isNotBlank() }.distinctBy { it.find.trim().lowercase(java.util.Locale.ROOT) }
            .map { rule ->
                val find = rule.find.trim()
                val body = find.split(Regex("[\\p{Zs}\\t]+")) .joinToString("[\\p{Zs}\\t]+") { Regex.escape(it) }
                val word = "[\\p{L}\\p{M}\\p{N}_'’]"
                val start = if (find.first().isLetterOrDigit() || find.first() == '_') "(?<!$word)" else ""
                val end = if (find.last().isLetterOrDigit() || find.last() == '_') "(?!$word)" else ""
                DocumentRepository.CompiledPronunciationRule(Regex(start + body + end, RegexOption.IGNORE_CASE), rule.replaceWith)
            }

    fun edits(source: String, rules: List<DocumentRepository.CompiledPronunciationRule>): List<Edit> {
        val candidates = rules.flatMap { rule -> rule.regex.findAll(source).map { Edit(it.range, rule.replaceWith) }.toList() }
            .sortedWith(compareBy<Edit> { it.range.first }.thenByDescending { it.range.last - it.range.first })
        var end = 0
        return candidates.filter { edit ->
            if (edit.range.first < end) false else { end = edit.range.last + 1; true }
        }
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

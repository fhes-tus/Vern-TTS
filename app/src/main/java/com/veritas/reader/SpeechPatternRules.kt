package com.veritas.reader

import com.google.re2j.Pattern
import java.util.Locale

enum class SpeechRuleKind(val label: String) { LITERAL("Text"), PATTERN("Pattern"), REGEX("Regex") }

/** User expressions use RE2/J; legacy literal rules keep their existing Unicode word boundaries. */
internal object SpeechPatternRules {
    const val MAX_PATTERN = 512
    const val MAX_REPLACEMENT = 512

    fun identity(rule: PronunciationRule): String = "${rule.matchKind}:${rule.caseSensitive}:" +
        if (rule.matchKind == SpeechRuleKind.LITERAL && !rule.caseSensitive) rule.find.trim().lowercase(Locale.ROOT)
        else rule.find.trim()

    fun error(rule: PronunciationRule): String? = try { compile(rule); null }
        catch (failure: com.google.re2j.PatternSyntaxException) { failure.message?.take(220) ?: "Check this pattern's syntax." }
        catch (failure: IllegalArgumentException) { failure.message?.take(220) ?: "Check this rule's pattern and replacement." }

    fun compile(rule: PronunciationRule): DocumentRepository.CompiledPronunciationRule {
        val find = rule.find.trim()
        require(find.isNotBlank()) { "Enter text or a pattern to match." }
        if (rule.matchKind == SpeechRuleKind.LITERAL) {
            val body = find.split(Regex("[\\p{Zs}\\t]+")) .joinToString("[\\p{Zs}\\t]+") { Regex.escape(it) }
            val word = "[\\p{L}\\p{M}\\p{N}_'’]"
            val start = if (find.first().isLetterOrDigit() || find.first() == '_') "(?<!$word)" else ""
            val end = if (find.last().isLetterOrDigit() || find.last() == '_') "(?!$word)" else ""
            val options = if (rule.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
            val regex = Regex(start + body + end, options)
            return DocumentRepository.CompiledPronunciationRule { source ->
                regex.findAll(source).map { PronunciationRulesEngine.Edit(it.range, rule.replaceWith) }
            }
        }
        require(find.length <= MAX_PATTERN) { "Patterns can contain up to $MAX_PATTERN characters." }
        require(rule.replaceWith.length <= MAX_REPLACEMENT) { "Say instead can contain up to $MAX_REPLACEMENT characters." }
        val expression = if (rule.matchKind == SpeechRuleKind.PATTERN) template(find) else find
        val pattern = Pattern.compile(expression, if (rule.caseSensitive) 0 else Pattern.CASE_INSENSITIVE)
        require(!pattern.matcher("").find()) { "Use a pattern that consumes text, not an empty position." }
        val groups = pattern.matcher("").groupCount()
        require(groups <= 32) { "Use no more than 32 capture groups." }
        val pieces = replacement(rule.replaceWith, groups)
        return DocumentRepository.CompiledPronunciationRule { source -> sequence {
            val matcher = pattern.matcher(source)
            while (matcher.find()) {
                val start = matcher.start(); val end = matcher.end()
                if (end <= start) continue
                val spoken = buildString { pieces.forEach { part ->
                    if (part.group != null) append(matcher.group(part.group).orEmpty()) else append(part.text)
                } }
                yield(PronunciationRulesEngine.Edit(start until end, spoken,
                    preserveWordGap = spoken.isEmpty() || rule.matchKind == SpeechRuleKind.PATTERN))
            }
        } }
    }

    private fun template(input: String): String {
        val source = if (input == "[n]") "[{number}]" else input
        val result = StringBuilder()
        val literal = StringBuilder()
        fun flush() { if (literal.isNotEmpty()) { result.append(Pattern.quote(literal.toString())); literal.setLength(0) } }
        var at = 0
        while (at < source.length) {
            val ch = source[at]
            if ((ch == '{' || ch == '}') && source.getOrNull(at + 1) == ch) {
                literal.append(ch); at += 2; continue
            }
            if (ch == '{') {
                flush()
                val end = source.indexOf('}', at + 1)
                require(end >= 0) { "Close the pattern token with }. Use {{ for a literal {." }
                val token = source.substring(at + 1, end)
                result.append(when (token) {
                    "number" -> "(\\p{Nd}+)"
                    "word" -> "([\\p{L}\\p{M}\\p{N}_'’]+)"
                    "text" -> if (end == source.lastIndex) "([^\\r\\n]+)" else "([^\\r\\n]+?)"
                    else -> throw IllegalArgumentException("Use {number}, {word} or {text}. Use {{ and }} for literal braces.")
                })
                at = end + 1
            } else {
                require(ch != '}') { "Use }} for a literal }." }
                literal.append(ch); at++
            }
        }
        flush()
        return result.toString()
    }

    private data class Piece(val text: String = "", val group: Int? = null)
    private fun replacement(source: String, groups: Int): List<Piece> {
        val parts = mutableListOf<Piece>()
        val literal = StringBuilder()
        fun flush() { if (literal.isNotEmpty()) { parts.add(Piece(literal.toString())); literal.setLength(0) } }
        var at = 0
        while (at < source.length) {
            val ch = source[at]
            if (ch == '$' && source.getOrNull(at + 1) == '$') { literal.append('$'); at += 2; continue }
            if (ch == '\\' && source.getOrNull(at + 1) in listOf('$', '\\')) {
                literal.append(source[at + 1]); at += 2; continue
            }
            if (ch == '$' && (source.getOrNull(at + 1)?.isDigit() == true || source.getOrNull(at + 1) == '{')) {
                flush()
                val braced = source[at + 1] == '{'
                val start = at + if (braced) 2 else 1
                var end = start
                while (source.getOrNull(end)?.isDigit() == true) end++
                require(end > start && (!braced || source.getOrNull(end) == '}')) { "Use \$1 or \${1} for captured text." }
                val group = source.substring(start, end).toIntOrNull()
                require(group != null && group in 0..groups) { "This pattern has $groups capture groups. Use \$\$ for a literal dollar sign." }
                parts.add(Piece(group = group))
                at = end + if (braced) 1 else 0
            } else { literal.append(ch); at++ }
        }
        flush()
        return parts
    }
}

package com.veritas.reader

data class SpeechRuleDraft(val find: String = "", val replaceWith: String = "",
    val kind: SpeechRuleKind = SpeechRuleKind.LITERAL, val caseSensitive: Boolean = false,
    val skip: Boolean = false, val id: String? = null) {
    internal fun rule(): PronunciationRule {
        val cleanFind = find.trim()
        val spoken = if (skip) "" else if (kind == SpeechRuleKind.LITERAL) replaceWith.trim() else replaceWith
        val example = if (kind == SpeechRuleKind.PATTERN && !skip) SpeechPatternRules.fromNumberPair(cleanFind, spoken) else null
        return PronunciationRule(id ?: "unsaved-rule", example?.first ?: cleanFind, example?.second ?: spoken,
            matchKind = kind, caseSensitive = caseSensitive)
    }
    internal fun error(): String? {
        if (kind == SpeechRuleKind.LITERAL && (find.trim().length > 120 || (!skip && replaceWith.trim().length > 120)))
            return "Text rules support up to 120 characters per field."
        return SpeechPatternRules.error(rule()) ?: if (!skip && replaceWith.isBlank()) "Enter what to say, or choose Skip." else null
    }
    companion object {
        fun from(rule: PronunciationRule) = SpeechRuleDraft(rule.find, rule.replaceWith, rule.matchKind,
            rule.caseSensitive, rule.replaceWith.isEmpty(), rule.id)
    }
}

internal object SpeechRulePreview {
    data class Result(val spoken: String, val matches: Int, val includesDraft: Boolean)
    fun build(source: String, saved: List<PronunciationRule>, draft: SpeechRuleDraft, voice: VoiceSettings): Result {
        val valid = draft.find.isNotBlank() && draft.error() == null
        val candidate = draft.rule()
        val identity = SpeechPatternRules.identity(candidate)
        val existing = saved.firstOrNull { if (draft.id != null) it.id == draft.id else SpeechPatternRules.identity(it) == identity }
        val includes = valid && existing?.enabled != false
        val rules = if (valid) listOf(candidate.copy(enabled = includes)) +
            saved.filterNot { it.id == draft.id || SpeechPatternRules.identity(it) == identity } else saved
        val compiled = PronunciationRulesEngine.compile(rules)
        val segment = SpeechPlan.segment("rule-preview", "draft", 0, source, voice, NarrationSettings(),
            voice.preferredRate, voice.preferredPitch, compiled)
        return Result(segment.spoken.text, PronunciationRulesEngine.edits(source, compiled).size, includes)
    }
}

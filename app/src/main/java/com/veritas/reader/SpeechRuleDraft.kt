package com.veritas.reader

data class SpeechRuleDraft(val find: String = "", val replaceWith: String = "",
    val kind: SpeechRuleKind = SpeechRuleKind.LITERAL, val caseSensitive: Boolean = false,
    val skip: Boolean = false, val id: String? = null) {
    internal fun rule() = PronunciationRule(id ?: "unsaved-rule", find.trim(), if (skip) "" else
        if (kind == SpeechRuleKind.LITERAL) replaceWith.trim() else replaceWith,
        matchKind = kind, caseSensitive = caseSensitive)
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
        val includes = draft.find.isNotBlank() && draft.error() == null
        val candidate = draft.rule()
        val rules = if (includes) listOf(candidate) + saved.filterNot { it.id == draft.id ||
            SpeechPatternRules.identity(it) == SpeechPatternRules.identity(candidate) } else saved
        val compiled = PronunciationRulesEngine.compile(rules)
        val segment = SpeechPlan.segment("rule-preview", "draft", 0, source, voice, NarrationSettings(),
            voice.preferredRate, voice.preferredPitch, compiled)
        return Result(segment.spoken.text, PronunciationRulesEngine.edits(source, compiled).size, includes)
    }
}

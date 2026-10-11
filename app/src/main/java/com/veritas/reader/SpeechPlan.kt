package com.veritas.reader

import java.security.MessageDigest
import java.util.Locale

internal enum class SpeechEngineFamily { ANDROID, PIPER, KOKORO }

/** Immutable source decisions, shared by reading, preview and portable audio export. */
internal data class SpeechSegment(
    val documentId: String,
    val contentRevision: String,
    val sentenceIndex: Int,
    val sourceStart: Int,
    val sourceEnd: Int,
    val sourceText: String,
    val spoken: SpokenText,
    val role: BookCharacter,
    val voice: VoiceSettings,
    val family: SpeechEngineFamily,
    val rate: Float,
    val pitch: Float,
    val leadingPauseMs: Long
)

internal object SpeechPlan {
    fun revision(chunks: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        chunks.forEach { text ->
            digest.update(text.length.toString().toByteArray()); digest.update(0.toByte())
            digest.update(text.toByteArray(Charsets.UTF_8))
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun voiceFor(original: String, base: VoiceSettings, narration: NarrationSettings, cue: NarrationCue? = null): VoiceSettings {
        val role = NarrationAnalyzer.getActiveCharacter(original, narration, cue)
        val name = role.voiceName?.takeIf { narration.enabled && narration.fullCastEnabled && it.isNotBlank() }
            ?: return base.copy(enginePackage = VoiceManager.engineForVoice(base.voiceName) ?: base.enginePackage)
        // Legacy cast names retain their assignment. Neural IDs identify their engine;
        // old Android names inherit the Android reading engine, or system default.
        val engine = role.enginePackage ?: VoiceManager.engineForVoice(name)
            ?: base.enginePackage.takeUnless(VoiceManager::isVeritasEngine).orEmpty()
        return base.copy(voiceName = name, voiceLabel = name, enginePackage = engine,
            localeTag = role.localeTag.ifBlank { base.localeTag })
    }

    fun segment(documentId: String, revision: String, index: Int, original: String,
        base: VoiceSettings, narration: NarrationSettings, rate: Float, pitch: Float,
        rules: List<DocumentRepository.CompiledPronunciationRule>, cue: NarrationCue? = null,
        resumeOffset: Int = 0, stripSpeakerLabel: Boolean = false, leadingPauseMs: Long = 0): SpeechSegment {
        val role = NarrationAnalyzer.getActiveCharacter(original, narration, cue)
        val voice = voiceFor(original, base, narration, cue)
        val family = when (VoiceManager.engineForVoice(voice.voiceName) ?: voice.enginePackage) {
            VoiceManager.VERITAS_LITE -> SpeechEngineFamily.PIPER
            VoiceManager.VERITAS_STUDIO, "com.veritas.voice" -> SpeechEngineFamily.KOKORO
            else -> SpeechEngineFamily.ANDROID
        }
        val start = maxOf(resumeOffset, if (stripSpeakerLabel) cue?.labelEnd ?: 0 else 0).coerceIn(0, original.length)
        val source = original.substring(start)
        val english = (voice.localeTag.ifBlank { Locale.getDefault().toLanguageTag() }).startsWith("en", true)
        val spoken = ReadingSymbols.expand(SpokenText.identity(source).applyRules(rules), english).sanitize()
        return SpeechSegment(documentId, revision, index, start, original.length, original, spoken, role, voice, family,
            NarrationAnalyzer.effectiveRate(rate, narration, original, family == SpeechEngineFamily.ANDROID, cue),
            NarrationAnalyzer.effectivePitch(pitch, narration, original, family == SpeechEngineFamily.ANDROID, cue), leadingPauseMs)
    }

    /** Bounded synthesis without reclassifying transformed text or splitting surrogate pairs. */
    fun split(segment: SpeechSegment, limit: Int = 800): List<SpeechSegment> {
        require(limit >= 2)
        val text = segment.spoken.text
        val result = mutableListOf<SpeechSegment>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + limit, text.length)
            if (end < text.length) {
                val space = text.lastIndexOf(' ', end - 1)
                if (space > start + limit / 2) end = space + 1
                if (Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) end--
            }
            val part = segment.spoken.slice(start, end)
            if (part.text.isNotBlank()) result.add(segment.copy(spoken = part,
                leadingPauseMs = if (start == 0) segment.leadingPauseMs else 0))
            start = end
        }
        return result
    }
}

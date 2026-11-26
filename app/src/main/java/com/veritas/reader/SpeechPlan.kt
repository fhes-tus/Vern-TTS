package com.veritas.reader

import com.veritas.reader.tts.NeuralSpeechText
import java.security.MessageDigest
import java.text.BreakIterator
import java.util.Locale

internal enum class SpeechEngineFamily { ANDROID, PIPER, KOKORO, KITTEN }

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
            ?: return base.copy(enginePackage = SpeechEngineRouting.engineForVoice(base.voiceName) ?: base.enginePackage)
        // Legacy cast names retain their assignment. Neural IDs identify their engine;
        // old Android names inherit the Android reading engine, or system default.
        val engine = role.enginePackage ?: SpeechEngineRouting.engineForVoice(name)
            ?: base.enginePackage.takeUnless(SpeechEngineRouting::isVeritasEngine).orEmpty()
        return base.copy(voiceName = name, voiceLabel = name, enginePackage = engine,
            localeTag = role.localeTag.ifBlank { base.localeTag })
    }

    fun segment(documentId: String, revision: String, index: Int, original: String,
        base: VoiceSettings, narration: NarrationSettings, rate: Float, pitch: Float,
        rules: List<DocumentRepository.CompiledPronunciationRule>, cue: NarrationCue? = null,
        resumeOffset: Int = 0, stripSpeakerLabel: Boolean = false, leadingPauseMs: Long = 0,
        sourceEnd: Int = original.length, prepared: SpokenText? = null): SpeechSegment {
        val role = NarrationAnalyzer.getActiveCharacter(original, narration, cue)
        val voice = voiceFor(original, base, narration, cue)
        val family = when (SpeechEngineRouting.engineForVoice(voice.voiceName) ?: voice.enginePackage) {
            VoiceManager.VERITAS_LITE -> SpeechEngineFamily.PIPER
            VoiceManager.KITTEN -> SpeechEngineFamily.KITTEN
            VoiceManager.VERITAS_STUDIO, "com.veritas.voice" -> SpeechEngineFamily.KOKORO
            else -> SpeechEngineFamily.ANDROID
        }
        val start = maxOf(resumeOffset, if (stripSpeakerLabel) cue?.labelEnd ?: 0 else 0).coerceIn(0, original.length)
        val end = sourceEnd.coerceIn(start, original.length)
        val source = original.substring(start, end)
        val english = family == SpeechEngineFamily.KITTEN ||
            voice.localeTag.ifBlank { AppLanguages.systemSpeechLocale().toLanguageTag() }.startsWith("en", true)
        val expanded = ReadingSymbols.expand(prepared ?: SpokenText.identity(source).applyRules(rules), english).sanitize()
        val spoken = if (family == SpeechEngineFamily.ANDROID) expanded else NeuralSpeechText.prepare(expanded)
        val deliveryText = if (prepared == null) original else source
        return SpeechSegment(documentId, revision, index, start, end, original, spoken, role, voice, family,
            NarrationAnalyzer.effectiveRate(rate, narration, deliveryText, family == SpeechEngineFamily.ANDROID, cue),
            NarrationAnalyzer.effectivePitch(pitch, narration, deliveryText, family == SpeechEngineFamily.ANDROID, cue), leadingPauseMs)
    }

    fun segments(documentId: String, revision: String, index: Int, original: String,
        base: VoiceSettings, narration: NarrationSettings, rate: Float, pitch: Float,
        rules: List<DocumentRepository.CompiledPronunciationRule>, cue: NarrationCue? = null,
        resumeOffset: Int = 0, stripSpeakerLabel: Boolean = false, leadingPauseMs: Long = 0): List<SpeechSegment> {
        val runs = cue?.runs.orEmpty()
        if (!narration.enabled || !narration.dialogueDetection || runs.isEmpty()) {
            return listOf(segment(documentId, revision, index, original, base, narration, rate, pitch, rules,
                cue, resumeOffset, stripSpeakerLabel, leadingPauseMs))
        }
        val start = maxOf(resumeOffset, if (stripSpeakerLabel) cue?.labelEnd ?: 0 else 0)
            .coerceIn(0, original.length)
        val mapped = SpokenText.identity(original.substring(start)).applyRules(rules)
        return buildList {
            var cursor = 0
            runs.forEachIndexed { runIndex, run ->
                val end = if (runIndex == runs.lastIndex) original.length else runs[runIndex + 1].start
                val first = cursor
                while (cursor < mapped.text.length && start + mapped.sourceOffset(cursor) < end) cursor++
                if (first == cursor) return@forEachIndexed
                val runStart = start + mapped.sourceOffset(first)
                val runEnd = start + mapped.sourceEndOffset(cursor)
                val part = mapped.slice(first, cursor).rebase(runStart - start, runEnd - start)
                val speaker = if (run.dialogue) run.speaker ?: cue?.speaker else null
                val role = NarrationCue(run.dialogue, speaker)
                val segment = segment(documentId, revision, index, original, base, narration, rate, pitch, rules,
                    role, runStart, leadingPauseMs = if (isEmpty()) leadingPauseMs else 85L,
                    sourceEnd = runEnd, prepared = part)
                if (segment.spoken.text.isNotBlank()) add(segment)
            }
        }
    }

    /** Bounded final text without reclassifying roles or damaging a grapheme. */
    fun split(segment: SpeechSegment, limit: Int = 800): List<SpeechSegment> {
        require(limit >= 2)
        val text = segment.spoken.text
        val boundaries = BreakIterator.getCharacterInstance(Locale.ROOT)
        boundaries.setText(text)
        val result = mutableListOf<SpeechSegment>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + limit, text.length)
            if (end < text.length) {
                val space = (end - 1 downTo start).firstOrNull { text[it].isWhitespace() } ?: -1
                if (space > start + limit / 2) end = space + 1
                if (!boundaries.isBoundary(end)) end = boundaries.preceding(end)
            }
            require(end > start) { "A text character sequence exceeds the speech input budget" }
            val part = segment.spoken.slice(start, end)
            if (part.text.isNotBlank()) result.add(segment.copy(spoken = part,
                leadingPauseMs = if (result.isEmpty()) segment.leadingPauseMs else 0))
            start = end
        }
        return result
    }
}

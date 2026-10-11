package com.veritas.reader

import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.tts.TtsEngine
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

class MixedSpeechPlanInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also { check(it.packageName.endsWith(".checks")) }
    private val base = VoiceSettings(localeTag = "en-US")
    private val cast = NarrationSettings(enabled = true, punctuationExpressionEnabled = false, characterProfiles = listOf(
        BookCharacter("narrator", "Narrator", voiceName = "piper_fixture", enginePackage = VoiceManager.VERITAS_LITE),
        BookCharacter("alice", "Alice", voiceName = "android_fixture", enginePackage = "system.fixture", localeTag = "en-GB"),
        BookCharacter("dialogue", "Dialogue (Default)")))

    @Test fun sourceRoleAndUnicodeOffsetsSurvivePronunciationAndSplitting() {
        val original = "Alice: \"β😀 ${"word ".repeat(220)}\""
        val cue = LiteraryDialogue.cues(listOf(ReaderSentence(0, original, 1))).first()
        val rules = PronunciationRulesEngine.compile(listOf(PronunciationRule(id = "test", find = "Alice", replaceWith = "Narrator")))
        val segment = SpeechPlan.segment("doc", SpeechPlan.revision(listOf(original)), 4, original, base, cast, 1.2f, .9f, rules,
            cue, stripSpeakerLabel = true)
        assertEquals("alice", segment.role.id); assertEquals("system.fixture", segment.voice.enginePackage)
        assertEquals(SpeechEngineFamily.ANDROID, segment.family)
        assertFalse(segment.spoken.text.startsWith("Narrator"))
        val parts = SpeechPlan.split(segment, 100)
        assertEquals(segment.spoken.text, parts.joinToString("") { it.spoken.text })
        assertTrue(parts.all { it.spoken.text.length <= 100 && !Character.isHighSurrogate(it.spoken.text.last()) })
        val beta = segment.spoken.text.indexOf("beta")
        assertTrue(beta >= 0); assertEquals(original.indexOf('β'), segment.sourceStart + segment.spoken.sourceOffset(beta))
        assertEquals(segment.contentRevision, parts.last().contentRevision)
    }

    @Test fun legacyCastKeepsVoiceAndMigratesEngineWithoutChangingSavedJson() {
        val legacy = JSONObject().put("id", "alice").put("name", "Alice").put("voiceName", "piper_old")
        val role = BookCharacter.fromJson(legacy)
        val settings = cast.copy(characterProfiles = cast.characterProfiles.map { if (it.id == "alice") role else it })
        val selected = SpeechPlan.voiceFor("Alice: \"hello.\"", base, settings)
        assertEquals("piper_old", selected.voiceName); assertEquals(VoiceManager.VERITAS_LITE, selected.enginePackage)
        val restored = BookCharacter.fromJson(role.copy(enginePackage = "custom.engine", localeTag = "fr-FR").toJson())
        assertEquals("custom.engine", restored.enginePackage); assertEquals("fr-FR", restored.localeTag)
        assertFalse(legacy.has("enginePackage")); assertEquals(2, settings.toJson().getInt("castVersion"))
    }

    @Test fun mixedExportNormalizesAudioAndReleasesEachOwnerBeforeSwitch() = runBlocking {
        var owners = 0; var releases = 0
        val chunks = listOf("Narration.", "Alice: \"Hello.\"", "Narration again.")
        val cues = LiteraryDialogue.cues(chunks.mapIndexed { i, text -> ReaderSentence(i, text, 1) })
        val segments = chunks.mapIndexed { i, text -> SpeechPlan.segment("doc", "revision", i, text, base, cast, 1f, 1f, emptyList(), cues[i], stripSpeakerLabel = true) }
        assertEquals(listOf(SpeechEngineFamily.PIPER, SpeechEngineFamily.ANDROID, SpeechEngineFamily.PIPER), segments.map { it.family })
        val exporter = MixedAudioExporter(context, createNeural = { object : TtsEngine {
            init { owners++; assertEquals(1, owners) }
            override val sampleRate = 22050
            override fun isReady() = true
            override suspend fun synthesize(sentence: String, rate: Float, pitch: Float) = ShortArray(sampleRate) { 1234 }
            override fun shutdown() { owners--; releases++ }
        } }, renderAndroid = { _, output ->
            assertEquals(0, owners)
            PcmWavWriter(output, 16000).use { it.append(ShortArray(16000) { 2222 }) }
        })
        val result = exporter.export("Mixed synthetic fixture", segments)
        try {
            assertEquals(3, result.synthesizedParts); assertEquals(44L + 3 * 24000 * 2, result.file.length())
            assertEquals(0, owners); assertEquals(2, releases)
            RandomAccessFile(result.file, "r").use { input ->
                input.seek(24); assertEquals(24000, Integer.reverseBytes(input.readInt()))
                input.seek(44 + 24000 * 2L); assertEquals(2222, java.lang.Short.reverseBytes(input.readShort()).toInt())
            }
        } finally { result.file.delete() }
    }

    @Test fun mixedExportAcceptsRealOfflineAndroidWavBetweenSyntheticNeuralParts() = runBlocking {
        val system = VoiceManager.loadVoices(context, "").first { !VoiceManager.isVeritasVoice(it.name) && !it.requiresNetwork && it.localeTag.startsWith("en") }
        val settings = cast.copy(characterProfiles = cast.characterProfiles.map { if (it.id == "alice")
            it.copy(voiceName = system.name, enginePackage = system.enginePackage, localeTag = system.localeTag) else it })
        val chunks = listOf("Narration.", "Alice: \"Hello, this is a quiz recording.\"", "Narration again.")
        val cues = LiteraryDialogue.cues(chunks.mapIndexed { i, text -> ReaderSentence(i, text, 1) })
        val segments = chunks.mapIndexed { i, text -> SpeechPlan.segment("doc", "revision", i, text, base, settings, 1f, 1f, emptyList(), cues[i]) }
        assertEquals(SpeechEngineFamily.ANDROID, segments[1].family)
        var released = 0
        val exporter = MixedAudioExporter(context, createNeural = { object : TtsEngine {
            override val sampleRate = 22050
            override fun isReady() = true
            override suspend fun synthesize(sentence: String, rate: Float, pitch: Float) = ShortArray(2205)
            override fun shutdown() { released++ }
        } })
        val before = DocumentRepository(context).loadVoiceSettings()
        val result = exporter.export("Real Android mixed fixture", segments)
        try {
            assertEquals(3, result.synthesizedParts); assertEquals(2, released)
            assertTrue(result.file.length() > 44 + 9600)
            assertEquals(before, DocumentRepository(context).loadVoiceSettings())
        } finally { result.file.delete() }
    }

    @Test fun cancellationRemovesOnlyItsExportAndPreservesVoiceChoice() = runBlocking {
        val repository = DocumentRepository(context)
        val voiceBefore = repository.loadVoiceSettings()
        val folder = File(context.cacheDir, "VernExports").apply { mkdirs() }
        val unrelated = File(folder, "keep-${UUID.randomUUID()}.wav").apply { writeText("keep") }
        val before = folder.listFiles().orEmpty().map { it.name }.toSet()
        var released = false
        val exporter = MixedAudioExporter(context, createNeural = { object : TtsEngine {
            override val sampleRate = 24000
            override fun isReady() = true
            override suspend fun synthesize(sentence: String, rate: Float, pitch: Float): ShortArray? { delay(2000); return null }
            override fun shutdown() { released = true }
        } })
        try {
            val segment = SpeechPlan.segment("doc", "revision", 0, "Narration.", base, cast, 1f, 1f, emptyList())
            try { withTimeout(150) { exporter.export("Cancelled", listOf(segment)) }; fail("Expected cancellation") }
            catch (_: TimeoutCancellationException) { }
            assertTrue(released); assertEquals(before, folder.listFiles().orEmpty().map { it.name }.toSet())
            assertEquals(voiceBefore, repository.loadVoiceSettings()); assertEquals("keep", unrelated.readText())
        } finally { unrelated.delete() }
    }
}

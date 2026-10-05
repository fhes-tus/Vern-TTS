package com.veritas.reader

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Exports a saved reading with its selected Android, Kokoro or Piper voice.
 *
 * Android's platform TTS API can synthesize speech to files. Long documents are synthesized
 * section-by-section, then the PCM data chunks are merged into a single WAV file.
 * Neural voices stream sentence PCM directly into the same WAV format.
 */
class AudioExportManager(private val context: Context) {
    data class ExportResult(
        val file: File,
        val displayName: String,
        val synthesizedParts: Int
    )

    suspend fun exportToWav(
        title: String,
        chunks: List<String>,
        rate: Float,
        pitch: Float,
        transformText: (String) -> String
    ): ExportResult {
        val repository = DocumentRepository(context)
        val voiceSettings = repository.loadVoiceSettings()
        val english = (voiceSettings.localeTag.takeIf { it.isNotBlank() }?.let(Locale::forLanguageTag) ?: Locale.getDefault()).language == "en"
        val cleanChunks = chunks
            .map { ReadingSymbols.prepare(SpeechPunctuation.normalize(transformText(it)), english).text.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() }

        require(cleanChunks.isNotEmpty()) { "This document has no readable text to export." }

        val narrationSettings = repository.loadNarrationSettings()
        if (VoiceManager.isVeritasEngine(voiceSettings.enginePackage) || VoiceManager.isVeritasEngine(VoiceManager.engineForVoice(voiceSettings.voiceName))) {
            return NeuralAudioExporter(context).export(title, cleanChunks, voiceSettings, narrationSettings, rate, pitch)
        }
        val tts = createReadyTts(voiceSettings)
        // Keep the configured engine voice so a per-character voice does not spill into
        // following parts that intentionally use the default voice.
        val baseVoice = tts.voice
        val tempDir = File(context.cacheDir, "tts_export_${UUID.randomUUID()}").apply { mkdirs() }
        val partFiles = mutableListOf<File>()

        try {
            val maxLen = (TextToSpeech.getMaxSpeechInputLength() - 250).coerceAtLeast(1000)
            val speechParts = cleanChunks.flatMap { splitForTts(it, maxLen) }

            speechParts.forEachIndexed { index, text ->
                coroutineContext.ensureActive()
                val activeChar = NarrationAnalyzer.getActiveCharacter(text, narrationSettings)
                require(!(narrationSettings.enabled && narrationSettings.fullCastEnabled && VoiceManager.isVeritasEngine(VoiceManager.engineForVoice(activeChar.voiceName)))) {
                    "Mixed Android and neural character voices cannot yet be exported together. Choose Android character voices or turn off full cast."
                }
                val characterVoice = if (narrationSettings.enabled && narrationSettings.fullCastEnabled) {
                    activeChar.voiceName?.let { name -> tts.voices?.find { it.name == name } }
                } else {
                    null
                }
                (characterVoice ?: baseVoice)?.let { tts.voice = it }
                tts.setSpeechRate(NarrationAnalyzer.effectiveRate(rate, narrationSettings, text))
                tts.setPitch(NarrationAnalyzer.effectivePitch(pitch, narrationSettings, text))
                val partFile = File(tempDir, "part_${index.toString().padStart(5, '0')}.wav")
                withTimeout((30_000L + (text.length / (8f * rate.coerceAtLeast(0.5f)) * 1000).toLong()).coerceAtMost(600_000L)) {
                    synthesizePart(tts, text, partFile, "vern_export_$index")
                }
                require(partFile.exists() && partFile.length() > 44L) { "The voice produced no audio for part ${index + 1}." }
                partFiles.add(partFile)
            }

            require(partFiles.isNotEmpty()) { "The TTS engine did not create audio. Try another installed voice/engine." }

            val exportDir = File(context.cacheDir, "VernExports").apply { mkdirs() }
            val displayName = "${safeFileName(title)}_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}_${UUID.randomUUID().toString().take(8)}.wav"
            val finalFile = File(exportDir, displayName)

            val staging = File(exportDir, ".${finalFile.name}.partial")
            val exportContext = coroutineContext
            try {
                withContext(Dispatchers.IO) {
                    StreamingWavMerger.merge(partFiles, staging) { exportContext.ensureActive() }
                    exportContext.ensureActive()
                    java.nio.file.Files.move(staging.toPath(), finalFile.toPath())
                }
            } finally {
                staging.delete()
            }
            Log.i(TAG, "Exported $title (${partFiles.size} audio parts)")
            return ExportResult(finalFile, displayName, partFiles.size)
        } catch (e: CancellationException) {
            Log.i(TAG, "WAV export cancelled for $title")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "WAV export failed for $title: ${e.message}", e)
            throw e
        } finally {
            runCatching { tts.stop() }.onFailure { Log.w(TAG, "Failed to stop TTS after export", it) }
            runCatching { tts.shutdown() }.onFailure { Log.w(TAG, "Failed to shut down TTS after export", it) }
            runCatching { tempDir.deleteRecursively() }.onFailure { Log.w(TAG, "Failed to delete temporary export files", it) }
        }
    }

    private suspend fun createReadyTts(voiceSettings: VoiceSettings): TextToSpeech = withTimeout(10_000L) {
        suspendCancellableCoroutine { continuation ->
            var engine: TextToSpeech? = null
            val requestedEngine = voiceSettings.enginePackage.ifBlank { null }
            val listener = TextToSpeech.OnInitListener { status ->
                val current = engine
                if (current == null) {
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException("TTS engine was not created."))
                } else if (status == TextToSpeech.SUCCESS) {
                    VoiceConfigurator.apply(current, voiceSettings)
                    if (continuation.isActive) continuation.resume(current)
                } else {
                    runCatching { current.shutdown() }
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Could not initialize the TTS engine."))
                }
            }
            engine = if (requestedEngine == null) {
                TextToSpeech(context.applicationContext, listener)
            } else {
                TextToSpeech(context.applicationContext, listener, requestedEngine)
            }
            continuation.invokeOnCancellation { runCatching { engine.shutdown() } }
        }
    }

    private suspend fun synthesizePart(
        tts: TextToSpeech,
        text: String,
        outputFile: File,
        utteranceId: String
    ) = suspendCancellableCoroutine<Unit> { continuation ->
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(doneId: String?) {
                if (doneId == utteranceId && continuation.isActive) continuation.resume(Unit)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(errorId: String?) {
                if (errorId == utteranceId && continuation.isActive) {
                    continuation.resumeWithException(IllegalStateException("TTS failed while exporting audio."))
                }
            }

            override fun onError(errorId: String?, errorCode: Int) {
                if (errorId == utteranceId && continuation.isActive) {
                    continuation.resumeWithException(IllegalStateException("TTS export failed with code $errorCode."))
                }
            }
        })

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        continuation.invokeOnCancellation {
            runCatching { tts.stop() }.onFailure { error -> Log.w(TAG, "Failed to stop TTS after export cancellation", error) }
            runCatching { outputFile.delete() }
        }
        val result = tts.synthesizeToFile(text, params, outputFile, utteranceId)
        if (result == TextToSpeech.ERROR && continuation.isActive) {
            continuation.resumeWithException(IllegalStateException("The TTS engine rejected audio export."))
        }
    }

    private fun splitForTts(text: String, maxLen: Int): List<String> {
        if (text.length <= maxLen) return listOf(text)
        val parts = mutableListOf<String>()
        var remaining = text.trim()
        while (remaining.length > maxLen) {
            val window = remaining.take(maxLen)
            val cut = listOf(
                window.lastIndexOf(". "),
                window.lastIndexOf("! "),
                window.lastIndexOf("? "),
                window.lastIndexOf("; "),
                window.lastIndexOf(", "),
                window.lastIndexOf(" ")
            ).filter { it > maxLen / 2 }.maxOrNull() ?: maxLen
            parts.add(remaining.take(cut + 1).trim())
            remaining = remaining.drop(cut + 1).trim()
        }
        if (remaining.isNotBlank()) parts.add(remaining)
        return parts.filter { it.isNotBlank() }
    }

    private fun safeFileName(title: String): String {
        return title
            .replace(Regex("[^A-Za-z0-9 _.-]"), " ")
            .replace(Regex("\\s+"), "_")
            .trim('_', '.', ' ')
            .ifBlank { "vern_audio" }
            .take(50)
    }

    private companion object {
        private const val TAG = "AudioExportManager"
    }
}

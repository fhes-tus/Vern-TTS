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

    suspend fun exportToWav(title: String, chunks: List<String>, rate: Float, pitch: Float,
        documentId: String = "", pageNumbers: IntArray? = null, curatedLabels: Boolean = false): ExportResult {
        val repository = DocumentRepository(context)
        val base = repository.loadVoiceSettings()
        val narration = repository.loadNarrationSettings()
        val revision = SpeechPlan.revision(chunks)
        val cues = LiteraryDialogue.cues(chunks.mapIndexed { index, text -> ReaderSentence(index, text, pageNumbers?.getOrNull(index) ?: 1) })
        val rules = repository.compiledPronunciationRules()
        val segments = chunks.mapIndexed { index, original ->
            val label = curatedLabels || (narration.enabled && narration.dialogueDetection)
            SpeechPlan.segment(documentId, revision, index, original, base, narration, rate, pitch, rules,
                cues[index], stripSpeakerLabel = label,
                leadingPauseMs = maxOf(PlaybackService.leadingSilenceMs(pageNumbers, index, original),
                    if (index > 0 && label && cues[index].labelEnd > 0) 120 else 0))
        }.flatMap { SpeechPlan.split(it) }
        require(segments.isNotEmpty()) { "This document has no readable text to export." }
        return MixedAudioExporter(context).export(title, segments)
    }

    internal suspend fun createReadyTts(voiceSettings: VoiceSettings): TextToSpeech = withTimeout(10_000L) {
        suspendCancellableCoroutine { continuation ->
            var engine: TextToSpeech? = null
            val requestedEngine = voiceSettings.enginePackage.ifBlank { null }
            val listener = TextToSpeech.OnInitListener { status ->
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    val current = engine
                    if (!continuation.isActive) { current?.shutdown() }
                    else if (current != null && status == TextToSpeech.SUCCESS) {
                        if (voiceSettings.voiceName.isNotBlank() && current.voices.orEmpty().none { it.name == voiceSettings.voiceName }) {
                            current.shutdown()
                            continuation.resumeWithException(IllegalStateException("${voiceSettings.displayName} is unavailable. Choose a voice in Voice Studio."))
                        } else {
                            val message = VoiceConfigurator.apply(current, voiceSettings)
                            if (message.startsWith("Voice data is missing") || message.contains("not supported")) {
                                current.shutdown()
                                continuation.resumeWithException(IllegalStateException(message))
                            } else continuation.resume(current)
                        }
                    } else {
                        current?.shutdown()
                        continuation.resumeWithException(IllegalStateException("Could not initialize the selected voice engine."))
                    }
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

    internal suspend fun synthesizePart(
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

    private companion object { private const val TAG = "AudioExportManager" }
}

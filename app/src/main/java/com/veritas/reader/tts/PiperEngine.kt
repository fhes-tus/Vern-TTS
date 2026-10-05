package com.veritas.reader.tts

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Piper VITS synthesis backed by Sherpa-ONNX and eSpeak-NG phonemization. */
class PiperEngine(context: Context, private val voiceId: String) : TtsEngine {
    private var engine: OfflineTts? = null

    override val sampleRate: Int
        get() = engine?.sampleRate()?.takeIf { it > 0 } ?: 22_050

    init {
        val dir = VoiceModelManager.getVoiceDirectory(context, OfflineEngineType.PIPER, voiceId)
        // Each Piper voice ships its model under its own name, so it has to come from
        // the catalog rather than being hardcoded.
        val modelBase = VoiceModelManager.availableVoices
            .firstOrNull { it.id == voiceId }?.modelBaseName
        if (modelBase != null && VoiceModelManager.isVoiceInstalled(context, voiceId)) {
            engine = runCatching {
                OfflineTts(
                    config = OfflineTtsConfig(
                        model = OfflineTtsModelConfig(
                            vits = OfflineTtsVitsModelConfig(
                                model = "${dir.absolutePath}/$modelBase.onnx",
                                tokens = "${dir.absolutePath}/tokens.txt",
                                // One bundle shared by every Piper voice.
                                dataDir = VoiceModelManager.sharedEspeakDirectory(context).absolutePath
                            ),
                            numThreads = 2
                        )
                    )
                )
            }.onFailure { Log.e(TAG, "Could not initialize Piper voice $voiceId", it) }.getOrNull()
        } else if (modelBase == null) {
            Log.e(TAG, "No catalog entry for Piper voice $voiceId")
        }
    }

    override fun isReady(): Boolean = engine != null

    override suspend fun synthesize(sentence: String, rate: Float, pitch: Float): ShortArray? = withContext(Dispatchers.Default) {
        val tts = engine ?: return@withContext null
        val clean = sentence.trim()
        if (clean.isBlank()) return@withContext null
        val safeSpeed = rate.coerceIn(0.5f, 2.0f)
        runCatching {
            val audio = tts.generate(clean, sid = 0, speed = safeSpeed)
            val samples = audio.samples
            if (samples.isEmpty()) return@withContext null
            ShortArray(samples.size) { index ->
                (samples[index].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            }
        }.onFailure { Log.e(TAG, "Piper synthesis failed", it) }.getOrNull()?.let { PcmPitchShift.apply(it, sampleRate, pitch) }
    }

    override fun shutdown() {
        engine?.free()
        engine = null
    }

    companion object { private const val TAG = "PiperEngine" }
}

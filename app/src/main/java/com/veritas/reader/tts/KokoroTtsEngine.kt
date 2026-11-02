package com.veritas.reader.tts

import android.content.Context
import android.util.Log
import com.veritas.reader.SpeechDelivery
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Kokoro synthesis backed by Sherpa-ONNX, including its native phonemizer. */
class KokoroTtsEngine(context: Context, private val voiceId: String, private val voiceShaping: Boolean = true) : TtsEngine {
    private var engine: OfflineTts? = null

    override val sampleRate: Int
        get() = engine?.sampleRate()?.takeIf { it > 0 } ?: 24_000

    init {
        val dir = VoiceModelManager.getVoiceDirectory(context, OfflineEngineType.KOKORO)
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        val isCriticallyLowMemory = memInfo.lowMemory || (memInfo.availMem in 1L until 150_000_000L)

        if (isCriticallyLowMemory) {
            Log.w(TAG, "Device memory is critically low (${memInfo.availMem / (1024 * 1024)}MB available); skipping Kokoro ONNX neural allocation to avoid LMK crash.")
        } else if (VoiceModelManager.isVoiceInstalled(context, voiceId)) {
            engine = runCatching {
                val modelFile = listOf("model.onnx", "model.fp16.onnx", "model.int8.onnx")
                    .map { java.io.File(dir, it) }
                    .firstOrNull { it.isFile && it.length() > 0L }
                    ?.absolutePath ?: "${dir.absolutePath}/model.int8.onnx"

                val isUkVoice = voiceId.startsWith("kokoro_bf_") || voiceId.startsWith("kokoro_bm_")
                // Only ever hand sherpa paths that exist. It validates lexicon and
                // dictDir up front and refuses to build the engine if either is
                // missing — which is a silent failure, since a null engine just
                // returns no audio. An empty string means "fall back to espeak-ng",
                // which is a working voice rather than silence.
                val lexiconFile = listOfNotNull(
                    "lexicon-gb-en.txt".takeIf { isUkVoice },
                    "lexicon-us-en.txt"
                ).map { java.io.File(dir, it) }
                    .firstOrNull { it.isFile && it.length() > 0L }
                    ?.absolutePath
                    .orEmpty()

                val dictPath = java.io.File(dir, "dict")
                    .takeIf { it.isDirectory }?.absolutePath.orEmpty()

                val espeakVoice = resolveEspeakVoice(dir, isUkVoice)

                if (lexiconFile.isEmpty()) {
                    Log.w(TAG, "No Kokoro lexicon in $dir — falling back to espeak-ng phonemisation")
                }

                // Balanced 2/4/4/2 phone trials favoured two workers and avoided extra contention.
                val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 2)
                Log.i(TAG, "Kokoro using $threads threads")

                OfflineTts(
                    config = OfflineTtsConfig(
                        model = OfflineTtsModelConfig(
                            kokoro = OfflineTtsKokoroModelConfig(
                                model = modelFile,
                                voices = "${dir.absolutePath}/voices.bin",
                                tokens = "${dir.absolutePath}/tokens.txt",
                                dataDir = "${dir.absolutePath}/espeak-ng-data",
                                lexicon = lexiconFile,
                                lang = espeakVoice,
                                dictDir = dictPath
                            ),
                            numThreads = threads,
                            debug = false
                        )
                    )
                )
            }.onFailure { Log.e(TAG, "Could not initialize Kokoro", it) }.getOrNull()

            // The speaker table below is the ordering of kokoro-multi-lang-v1_0. A
            // package with a different speaker count is a different release, and its
            // ids mean different voices — v1.1-zh, for instance, is 103 speakers of
            // which only three are English, so every voice here would come out
            // Chinese. Say so loudly rather than synthesising the wrong voice.
            val speakers = engine?.takeIf { it.isReady }?.let { runCatching { it.numSpeakers() }.getOrDefault(0) } ?: 0
            if (speakers > 0 && !KokoroPackageShape.supports(speakers)) {
                Log.e(
                    TAG,
                    "Kokoro package has $speakers speakers, expected " +
                        "53 or 54. This is not " +
                        "kokoro-multi-lang-v1_0 — voice selection will be wrong. " +
                        "Delete and re-download the voice."
                )
            }
        }
    }

    override fun isReady(): Boolean = engine?.isReady == true

    override suspend fun synthesize(sentence: String, rate: Float, pitch: Float): ShortArray? =
        synthesizeFloat(sentence, rate, pitch)?.pcm16()

    override suspend fun synthesizeFloat(sentence: String, rate: Float, pitch: Float): FloatPcmAudio? =
        generateRaw(sentence, rate)?.let { KokoroVoiceProcessor.render(it, sampleRate, pitch, voiceShaping) }

    internal suspend fun generateRaw(sentence: String, rate: Float): FloatArray? = withContext(Dispatchers.Default) {
        generateRawBlocking(sentence, rate, voiceId)
    }

    internal fun generateRawBlocking(sentence: String, rate: Float, requestedVoice: String): FloatArray? {
        val tts = engine?.takeIf { it.isReady } ?: return null
        val clean = NeuralSpeechText.prepare(sentence)
        if (clean.isBlank()) return null
        val numSpk = runCatching { tts.numSpeakers() }.getOrDefault(0)
        val requestedSid = speakerId(requestedVoice)
        if (!KokoroPackageShape.supports(numSpk) || requestedSid !in 0 until numSpk) return null
        val safeSpeed = SpeechDelivery.rate(rate)

        return runCatching {
            tts.generate(clean, sid = requestedSid, speed = safeSpeed).samples
        }.onFailure { Log.w(TAG, "Synthesis failed for requested sid $requestedSid", it) }.getOrNull()
    }

    override fun shutdown() {
        engine?.free()
        engine = null
    }

    private fun resolveEspeakVoice(dir: java.io.File, isUkVoice: Boolean): String {
        val candidates = if (isUkVoice) UK_VOICE_CANDIDATES else US_VOICE_CANDIDATES
        val root = java.io.File(dir, "espeak-ng-data/lang")
        require(root.isDirectory) { "Kokoro phonemizer language data is missing." }
        val available = root.walkTopDown().filter { it.isFile }.map { it.name.lowercase() }.toHashSet()
        return requireNotNull(candidates.firstOrNull { it.lowercase() in available }) {
            "This Kokoro package has no supported English phonemizer voice."
        }
    }

    companion object {
        private const val TAG = "KokoroTtsEngine"

        // Most preferred first. These are eSpeak voice file names, not BCP-47 tags.
        private val UK_VOICE_CANDIDATES = listOf("en-GB-x-rp", "en", "en-US")
        private val US_VOICE_CANDIDATES = listOf("en-US", "en")
        private val speakerIds = listOf(
            "af_alloy", "af_aoede", "af_bella", "af_heart", "af_jessica", "af_kore", "af_nicole", "af_nova", "af_river", "af_sarah", "af_sky",
            "am_adam", "am_echo", "am_eric", "am_fenrir", "am_liam", "am_michael", "am_onyx", "am_puck", "am_santa",
            "bf_alice", "bf_emma", "bf_isabella", "bf_lily", "bm_daniel", "bm_fable", "bm_george", "bm_lewis"
        )
        private fun speakerId(voiceId: String): Int = speakerIds.indexOf(voiceId.removePrefix("kokoro_").lowercase())
    }
}

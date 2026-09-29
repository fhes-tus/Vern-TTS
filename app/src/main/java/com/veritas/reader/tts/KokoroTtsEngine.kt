package com.veritas.reader.tts

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.veritas.reader.tts.KokoroTtsEngine.Companion.SOFT_KNEE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Kokoro synthesis backed by Sherpa-ONNX, including its native phonemizer. */
class KokoroTtsEngine(context: Context, private val voiceId: String) : TtsEngine {
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

                val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
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
            if (speakers > 0 && speakers != VoiceModelManager.KOKORO_EXPECTED_SPEAKERS) {
                Log.e(
                    TAG,
                    "Kokoro package has $speakers speakers, expected " +
                        "${VoiceModelManager.KOKORO_EXPECTED_SPEAKERS}. This is not " +
                        "kokoro-multi-lang-v1_0 — voice selection will be wrong. " +
                        "Delete and re-download the voice."
                )
            }
        }
    }

    override fun isReady(): Boolean = engine?.isReady == true

    override suspend fun synthesize(sentence: String, rate: Float, pitch: Float): ShortArray? = withContext(Dispatchers.Default) {
        val tts = engine?.takeIf { it.isReady } ?: return@withContext null
        if (sentence.isBlank()) return@withContext null
        val numSpk = runCatching { tts.numSpeakers() }.getOrDefault(0)
        val requestedSid = speakerId(voiceId)
        val safeSid = if (numSpk > 0) requestedSid.coerceIn(0, numSpk - 1) else 0
        val safeSpeed = rate.coerceIn(0.5f, 2.0f)

        runCatching {
            tts.generate(sentence, sid = safeSid, speed = safeSpeed).samples.toPcm16()
        }.getOrElse {
            Log.w(TAG, "Synthesis failed for sid $safeSid, trying fallback sid 0", it)
            runCatching { tts.generate(sentence, sid = 0, speed = safeSpeed).samples.toPcm16() }.getOrNull()
        }
    }

    override fun shutdown() {
        engine?.free()
        engine = null
    }

    /**
     * Kokoro already emits floats in roughly [-1, 1] at a consistent level, so this
     * only applies fixed headroom and clips.
     *
     * It used to normalise every sentence to a 0.95 peak independently, which is
     * what made reading sound uneven and gritty: a quiet sentence was amplified to
     * the same peak as a loud one — lifting the model's noise floor with it — and
     * the gain jumped at every sentence boundary. Loudness has to be consistent
     * across a whole book, so per-sentence gain is exactly the wrong unit.
     */
    private fun FloatArray.toPcm16(): ShortArray {
        if (isEmpty()) return ShortArray(0)
        val sr = sampleRate
        // Two cascaded notches remove the vocoder's whine before gain is applied, so
        // the limiter is not reacting to a tone that is about to be filtered out.
        val filtered = copyOf()
        Biquad.notch(sr, sr / 5.0, NOTCH_Q).processInPlace(filtered)
        Biquad.notch(sr, 2.0 * sr / 5.0, NOTCH_Q).processInPlace(filtered)
        // Kokoro's int8 output is dull in the presence band, and the notches take a
        // little more out of it. A gentle shelf puts the air back: measured at
        // +1.8dB across 5-11kHz for +0.01dB in the 300-3400Hz speech band.
        Biquad.highShelf(sr, PRESENCE_HZ, PRESENCE_GAIN_DB).processInPlace(filtered)
        return ShortArray(size) { index ->
            val v = softLimit(filtered[index] * OUTPUT_GAIN)
            (v * Short.MAX_VALUE).toInt().toShort()
        }
    }

    /**
     * Linear below [SOFT_KNEE], then a smooth curve into the ceiling.
     *
     * Fixed gain alone would hard-clip the occasional sample that Kokoro pushes past
     * full scale, and square-edged clipping is exactly the buzz this was meant to
     * remove. Compressing only the top fifth keeps ordinary speech untouched while
     * making peaks lean rather than crack.
     */
    private fun softLimit(x: Float): Float {
        val mag = kotlin.math.abs(x)
        if (mag <= SOFT_KNEE) return x
        val over = (mag - SOFT_KNEE) / (1f - SOFT_KNEE)
        val shaped = SOFT_KNEE + (1f - SOFT_KNEE) * kotlin.math.tanh(over)
        return if (x < 0f) -shaped else shaped
    }

    /**
     * Picks an eSpeak-ng voice that actually exists in this package.
     *
     * sherpa passes this straight to `espeak_SetVoiceByName`, which matches on the
     * voice *file* name — and a miss throws `std::runtime_error` from C++, which
     * crosses the JNI boundary and aborts the process. It cannot be caught from
     * Kotlin, so the name has to be verified before it is handed over.
     *
     * "en-gb" was being passed for UK voices and there is no such file: British
     * English lives in `en` ("English (Great Britain)"). Only `en-US` happened to
     * match, which is why US voices worked and every UK voice killed the app.
     */
    private fun resolveEspeakVoice(dir: java.io.File, isUkVoice: Boolean): String {
        val candidates = if (isUkVoice) UK_VOICE_CANDIDATES else US_VOICE_CANDIDATES
        val langRoot = java.io.File(dir, "espeak-ng-data/lang")
        if (!langRoot.isDirectory) {
            Log.w(TAG, "No espeak-ng lang directory in $dir; defaulting to ${candidates.last()}")
            return candidates.last()
        }
        val available = langRoot.walkTopDown()
            .filter { it.isFile }
            .map { it.name.lowercase() }
            .toHashSet()
        val match = candidates.firstOrNull { it.lowercase() in available }
        if (match == null) {
            Log.w(TAG, "None of $candidates present in espeak-ng-data; falling back to en-US")
            return "en-US"
        }
        Log.i(TAG, "Using eSpeak voice '$match' for ${if (isUkVoice) "UK" else "US"} speaker $voiceId")
        return match
    }

    /**
     * RBJ cookbook biquad, direct form I. Used for two jobs here.
     *
     * **The notches** kill Kokoro's vocoder whistle. Measured from a dump of the
     * model's own float output: steady tones at exactly `sampleRate / 5` (4799.90 Hz)
     * and `2 * sampleRate / 5` (9600.05 Hz), present in 78% of frames including
     * near-silent ones. Landing on exact rational fractions of the sample rate makes
     * them an upsampling artifact of the GAN vocoder, not anything to do with the
     * text — which is why they were audible as a constant whistle behind the voice
     * rather than as distortion of it.
     *
     * Q is 60, so each notch is only `4800/60` = 80 Hz wide and still takes 21 dB off
     * the tone. An earlier Q of 15 removed more of the tone but was 320 Hz wide, and
     * a 320 Hz hole in the presence band is audible as dullness — that was the cost,
     * not the cure.
     *
     * State is per call. The filter settles in roughly Q cycles — a few milliseconds
     * at 4.8 kHz — so starting fresh each sentence costs nothing audible.
     */
    private class Biquad(
        private val b0: Float,
        private val b1: Float,
        private val b2: Float,
        private val a1: Float,
        private val a2: Float
    ) {
        fun processInPlace(buffer: FloatArray) {
            var x1 = 0f; var x2 = 0f; var y1 = 0f; var y2 = 0f
            for (i in buffer.indices) {
                val x0 = buffer[i]
                val y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
                x2 = x1; x1 = x0
                y2 = y1; y1 = y0
                buffer[i] = y0
            }
        }

        companion object {
            fun notch(sampleRate: Int, centreHz: Double, q: Double): Biquad {
                val w0 = 2.0 * Math.PI * centreHz / sampleRate
                val alpha = kotlin.math.sin(w0) / (2.0 * q)
                val cosW0 = kotlin.math.cos(w0)
                val a0 = 1.0 + alpha
                return Biquad(
                    (1.0 / a0).toFloat(),
                    (-2.0 * cosW0 / a0).toFloat(),
                    (1.0 / a0).toFloat(),
                    (-2.0 * cosW0 / a0).toFloat(),
                    ((1.0 - alpha) / a0).toFloat()
                )
            }

            fun highShelf(sampleRate: Int, cornerHz: Double, gainDb: Double, slope: Double = 0.7): Biquad {
                val a = Math.pow(10.0, gainDb / 40.0)
                val w0 = 2.0 * Math.PI * cornerHz / sampleRate
                val cosW0 = kotlin.math.cos(w0)
                val alpha = kotlin.math.sin(w0) / 2.0 *
                    kotlin.math.sqrt((a + 1.0 / a) * (1.0 / slope - 1.0) + 2.0)
                val twoSqrtAAlpha = 2.0 * kotlin.math.sqrt(a) * alpha
                val a0 = (a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha
                return Biquad(
                    (a * ((a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha) / a0).toFloat(),
                    (-2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0) / a0).toFloat(),
                    (a * ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0).toFloat(),
                    (2.0 * ((a - 1.0) - (a + 1.0) * cosW0) / a0).toFloat(),
                    (((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0).toFloat()
                )
            }
        }
    }

    companion object {
        private const val TAG = "KokoroTtsEngine"

        /**
         * Fixed output gain, applied identically to every sentence.
         *
         * Kokoro is quiet: a measured sentence peaked at 0.443, so the old 0.92 left
         * nearly 8 dB of headroom unused and the reading sounded faint. 1.7 brings
         * that peak to ~0.75 with nothing reaching the limiter; louder speakers are
         * caught by the soft knee rather than clipped.
         */
        private const val OUTPUT_GAIN = 1.7f

        /** 80 Hz wide at 4.8 kHz — narrow enough to be inaudible, still 21 dB down. */
        private const val NOTCH_Q = 60.0

        /** Presence shelf: restores the air the notches and the model both lack. */
        private const val PRESENCE_HZ = 5000.0
        private const val PRESENCE_GAIN_DB = 2.5

        /** Level above which peaks are compressed rather than clipped. */
        private const val SOFT_KNEE = 0.8f

        // Most preferred first. These are eSpeak voice file names, not BCP-47 tags.
        private val UK_VOICE_CANDIDATES = listOf("en-GB-x-rp", "en", "en-US")
        private val US_VOICE_CANDIDATES = listOf("en-US", "en")
        private val speakerIds = listOf(
            "af_alloy", "af_aoede", "af_bella", "af_heart", "af_jessica", "af_kore", "af_nicole", "af_nova", "af_river", "af_sarah", "af_sky",
            "am_adam", "am_echo", "am_eric", "am_fenrir", "am_liam", "am_michael", "am_onyx", "am_puck", "am_santa",
            "bf_alice", "bf_emma", "bf_isabella", "bf_lily", "bm_daniel", "bm_fable", "bm_george", "bm_lewis"
        )
        private fun speakerId(voiceId: String): Int = speakerIds.indexOf(voiceId.removePrefix("kokoro_").lowercase()).takeIf { it >= 0 } ?: 3
    }
}

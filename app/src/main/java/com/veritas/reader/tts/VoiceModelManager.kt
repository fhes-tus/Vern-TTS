package com.veritas.reader.tts

import android.content.Context
import android.util.Log
import com.veritas.reader.tts.VoiceModelManager.isVoiceInstalled
import com.veritas.reader.tts.VoiceModelManager.pruneNonEnglishAssets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(
        val bytesDownloaded: Long, val totalBytes: Long, val percent: Int,
        val installing: Boolean = false, val queued: Boolean = false
    ) : DownloadState() {
        val label: String get() = when {
            queued -> "Queued"
            installing -> "Installing…"
            totalBytes <= 0 -> "Downloading…"
            else -> "Downloading $percent%"
        }
    }
    object Completed : DownloadState()
    data class Failed(val reason: String) : DownloadState()
}

enum class OfflineEngineType {
    KOKORO,
    PIPER
}

data class OfflineVoiceDescriptor(
    val id: String,
    val name: String,
    val engineType: OfflineEngineType,
    val language: String,
    val localeTag: String,
    val downloadSizeMb: Float,
    val isCoreRequired: Boolean,
    val modelUrl: String,
    val voiceUrl: String? = null,
    val expectedChecksum: String? = null,
    /** Piper only: the `<name>.onnx` inside the archive, e.g. `en_GB-alan-medium`. */
    val modelBaseName: String? = null
)

data class NeuralPackageDescriptor(
    val id: String,
    val title: String,
    val description: String,
    val engineType: OfflineEngineType,
    val targetVoiceId: String
)

object VoiceModelManager {
    private const val TAG = "VoiceModelManager"
    private const val PREFS_NAME = "veritas_offline_tts_prefs"
    private const val KEY_INSTALLED_VOICES = "installed_voice_ids"

    /**
     * Kokoro **int8 multi-lang v1.0** — the only release that matches this app.
     *
     * Its original 53 and updated 54 speakers use the same English ordering as [KokoroTtsEngine]'s speaker table
     * expects (ids 0-27 are the English voices this catalog offers), and it ships the
     * `lexicon-*.txt` files the engine needs. Its Chinese assets are removed after
     * extraction — see [pruneNonEnglishAssets].
     *
     * The other candidates are all wrong for an English reader, and each was tried:
     *  - `kokoro-en-v0_19` (305MB) has 11 speakers in a different order and ships
     *    neither lexicon nor dict, so the engine cannot initialise against it.
     *  - `kokoro-*-v1_1` is the Chinese release: 103 speakers of which only three
     *    (af_maple, af_sol, bf_vale) are English. Every catalog voice would land on
     *    a Chinese embedding.
     * Do not "upgrade" this URL by version number without re-checking the speaker
     * table — newer is not better here.
     */
    private const val KOKORO_MODEL_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-multi-lang-v1_0.tar.bz2"

    /** Speakers in kokoro-int8-multi-lang-v1_0; used to catch a mismatched package. */
    const val KOKORO_EXPECTED_SPEAKERS = 53

    /** eSpeak dictionaries kept after pruning. Everything else is another language. */
    private val KEPT_ESPEAK_DICTS = setOf("en_dict")

    private val _downloadState = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadState: StateFlow<Map<String, DownloadState>> = _downloadState.asStateFlow()
    private val packageLocks = ConcurrentHashMap<String, Mutex>()
    private val downloadSlots = Semaphore(3)
    private val transientLock = Any()
    private val activeTransientPaths = mutableSetOf<String>()
    private val espeakInstallLock = Any()
    // Downloads outlive lazy-list rows and the voice screen, but use only the application context.
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requestLock = Any()
    private val inFlight = mutableMapOf<String, Deferred<Boolean>>()

    val availablePackages = listOf(
        NeuralPackageDescriptor(
            id = "veritas_studio_pack",
            title = "Vern Studio",
            description = "Richest, most expressive voices — 28 English speakers in one " +
                "126MB download. Heavy: on mid-range phones it pauses between sentences.",
            engineType = OfflineEngineType.KOKORO,
            targetVoiceId = "kokoro_af_heart"
        ),
        NeuralPackageDescriptor(
            id = "veritas_lite_pack",
            title = "Vern Lite",
            description = "12 UK and US voices, about 20MB each. Select several " +
                "to download together; additional downloads wait their turn.",
            engineType = OfflineEngineType.PIPER,
            targetVoiceId = "piper_en_us_amy"
        )
    )

    /**
     * Catalog of curated offline neural voices available for download.
     */
    val availableVoices = listOf(
        // Kokoro US Female
        OfflineVoiceDescriptor("kokoro_af_heart", "Heart (US Female • Studio)", OfflineEngineType.KOKORO, "English (US)", "en-US", 126.0f, true, KOKORO_MODEL_URL),
        OfflineVoiceDescriptor("kokoro_af_bella", "Bella (US Female • Studio)", OfflineEngineType.KOKORO, "English (US)", "en-US", 0.0f, false, KOKORO_MODEL_URL),
        OfflineVoiceDescriptor("kokoro_af_nicole", "Nicole (US Female • Studio)", OfflineEngineType.KOKORO, "English (US)", "en-US", 0.0f, false, KOKORO_MODEL_URL),
        OfflineVoiceDescriptor("kokoro_af_aoede", "Aoede (US Female • Studio)", OfflineEngineType.KOKORO, "English (US)", "en-US", 0.0f, false, KOKORO_MODEL_URL),
        OfflineVoiceDescriptor("kokoro_af_kore", "Kore (US Female • Studio)", OfflineEngineType.KOKORO, "English (US)", "en-US", 0.0f, false, KOKORO_MODEL_URL),
        OfflineVoiceDescriptor("kokoro_af_sarah", "Sarah (US Female • Studio)", OfflineEngineType.KOKORO, "English (US)", "en-US", 0.0f, false, KOKORO_MODEL_URL),
        OfflineVoiceDescriptor("kokoro_af_sky", "Sky (US Female • Studio)", OfflineEngineType.KOKORO, "English (US)", "en-US", 0.0f, false, KOKORO_MODEL_URL),

        // Kokoro US Male
        OfflineVoiceDescriptor("kokoro_am_adam", "Adam (US Male • Studio)", OfflineEngineType.KOKORO, "English (US)", "en-US", 0.0f, false, KOKORO_MODEL_URL),
        OfflineVoiceDescriptor("kokoro_am_echo", "Echo (US Male • Studio)", OfflineEngineType.KOKORO, "English (US)", "en-US", 0.0f, false, KOKORO_MODEL_URL),
        OfflineVoiceDescriptor("kokoro_am_eric", "Eric (US Male • Studio)", OfflineEngineType.KOKORO, "English (US)", "en-US", 0.0f, false, KOKORO_MODEL_URL),
        OfflineVoiceDescriptor("kokoro_am_fenrir", "Fenrir (US Male • Studio)", OfflineEngineType.KOKORO, "English (US)", "en-US", 0.0f, false, KOKORO_MODEL_URL),
        OfflineVoiceDescriptor("kokoro_am_liam", "Liam (US Male • Studio)", OfflineEngineType.KOKORO, "English (US)", "en-US", 0.0f, false, KOKORO_MODEL_URL),
        OfflineVoiceDescriptor("kokoro_am_michael", "Michael (US Male • Studio)", OfflineEngineType.KOKORO, "English (US)", "en-US", 0.0f, false, KOKORO_MODEL_URL),
        OfflineVoiceDescriptor("kokoro_am_onyx", "Onyx (US Male • Studio)", OfflineEngineType.KOKORO, "English (US)", "en-US", 0.0f, false, KOKORO_MODEL_URL),
        OfflineVoiceDescriptor("kokoro_am_puck", "Puck (US Male • Studio)", OfflineEngineType.KOKORO, "English (US)", "en-US", 0.0f, false, KOKORO_MODEL_URL),
        OfflineVoiceDescriptor("kokoro_am_santa", "Santa (US Male • Studio)", OfflineEngineType.KOKORO, "English (US)", "en-US", 0.0f, false, KOKORO_MODEL_URL),

        // Kokoro UK Female
        OfflineVoiceDescriptor("kokoro_bf_emma", "Emma (UK Female • Studio)", OfflineEngineType.KOKORO, "English (UK)", "en-GB", 0.0f, false, KOKORO_MODEL_URL),
        OfflineVoiceDescriptor("kokoro_bf_isabella", "Isabella (UK Female • Studio)", OfflineEngineType.KOKORO, "English (UK)", "en-GB", 0.0f, false, KOKORO_MODEL_URL),
        OfflineVoiceDescriptor("kokoro_bf_alice", "Alice (UK Female • Studio)", OfflineEngineType.KOKORO, "English (UK)", "en-GB", 0.0f, false, KOKORO_MODEL_URL),
        OfflineVoiceDescriptor("kokoro_bf_lily", "Lily (UK Female • Studio)", OfflineEngineType.KOKORO, "English (UK)", "en-GB", 0.0f, false, KOKORO_MODEL_URL),

        // Kokoro UK Male
        OfflineVoiceDescriptor("kokoro_bm_george", "George (UK Male • Studio)", OfflineEngineType.KOKORO, "English (UK)", "en-GB", 0.0f, false, KOKORO_MODEL_URL),
        OfflineVoiceDescriptor("kokoro_bm_fable", "Fable (UK Male • Studio)", OfflineEngineType.KOKORO, "English (UK)", "en-GB", 0.0f, false, KOKORO_MODEL_URL),
        OfflineVoiceDescriptor("kokoro_bm_lewis", "Lewis (UK Male • Studio)", OfflineEngineType.KOKORO, "English (UK)", "en-GB", 0.0f, false, KOKORO_MODEL_URL),
        OfflineVoiceDescriptor("kokoro_bm_daniel", "Daniel (UK Male • Studio)", OfflineEngineType.KOKORO, "English (UK)", "en-GB", 0.0f, false, KOKORO_MODEL_URL),

        // Piper Light Voices
        OfflineVoiceDescriptor("piper_en_gb_alan", "Alan (UK Male) • Lite", OfflineEngineType.PIPER, "English (UK)", "en-GB", 20.1f, false, "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_GB-alan-medium-int8.tar.bz2", modelBaseName = "en_GB-alan-medium"),
        OfflineVoiceDescriptor("piper_en_gb_northern", "Fletcher (UK Male) • Lite", OfflineEngineType.PIPER, "English (UK)", "en-GB", 20.1f, false, "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_GB-northern_english_male-medium-int8.tar.bz2", modelBaseName = "en_GB-northern_english_male-medium"),
        OfflineVoiceDescriptor("piper_en_gb_southern", "Nigel (UK Male) • Lite", OfflineEngineType.PIPER, "English (UK)", "en-GB", 22.5f, false, "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_GB-southern_english_male-medium-int8.tar.bz2", modelBaseName = "en_GB-southern_english_male-medium"),
        OfflineVoiceDescriptor("piper_en_gb_jenny", "Jenny (UK Female) • Lite", OfflineEngineType.PIPER, "English (UK)", "en-GB", 20.0f, false, "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_GB-jenny_dioco-medium-int8.tar.bz2", modelBaseName = "en_GB-jenny_dioco-medium"),
        OfflineVoiceDescriptor("piper_en_gb_alba", "Alba (UK Female) • Lite", OfflineEngineType.PIPER, "English (UK)", "en-GB", 20.1f, false, "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_GB-alba-medium-int8.tar.bz2", modelBaseName = "en_GB-alba-medium"),
        OfflineVoiceDescriptor("piper_en_gb_cori", "Cori (UK Female) • Lite", OfflineEngineType.PIPER, "English (UK)", "en-GB", 20.1f, false, "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_GB-cori-medium-int8.tar.bz2", modelBaseName = "en_GB-cori-medium"),
        OfflineVoiceDescriptor("piper_en_us_ryan", "Ryan (US Male) • Lite", OfflineEngineType.PIPER, "English (US)", "en-US", 20.1f, false, "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-ryan-medium-int8.tar.bz2", modelBaseName = "en_US-ryan-medium"),
        OfflineVoiceDescriptor("piper_en_us_joe", "Joe (US Male) • Lite", OfflineEngineType.PIPER, "English (US)", "en-US", 20.2f, false, "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-joe-medium-int8.tar.bz2", modelBaseName = "en_US-joe-medium"),
        OfflineVoiceDescriptor("piper_en_us_hfc_male", "Marcus (US Male) • Lite", OfflineEngineType.PIPER, "English (US)", "en-US", 20.0f, false, "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-hfc_male-medium-int8.tar.bz2", modelBaseName = "en_US-hfc_male-medium"),
        OfflineVoiceDescriptor("piper_en_us_amy", "Amy (US Female) • Lite", OfflineEngineType.PIPER, "English (US)", "en-US", 20.1f, false, "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-amy-medium-int8.tar.bz2", modelBaseName = "en_US-amy-medium"),
        OfflineVoiceDescriptor("piper_en_us_lessac", "Lessac (US Female) • Lite", OfflineEngineType.PIPER, "English (US)", "en-US", 20.0f, false, "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-lessac-medium-int8.tar.bz2", modelBaseName = "en_US-lessac-medium"),
        OfflineVoiceDescriptor("piper_en_us_hfc_fem", "Hana (US Female) • Lite", OfflineEngineType.PIPER, "English (US)", "en-US", 20.0f, false, "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-hfc_female-medium-int8.tar.bz2", modelBaseName = "en_US-hfc_female-medium"),
    )

    fun hasAnyModelInstalled(context: Context): Boolean {
        return availableVoices.any { isVoiceInstalled(context, it.id) }
    }

    fun getTtsDirectory(context: Context): File {
        val dir = File(context.filesDir, "tts")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Kokoro is a single package, so it keeps one directory. Piper ships a model per
     * voice, so each gets its own — otherwise installing a second voice would wipe
     * the first.
     */
    fun getVoiceDirectory(context: Context, engineType: OfflineEngineType, voiceId: String? = null): File =
        when (engineType) {
            OfflineEngineType.KOKORO -> File(getTtsDirectory(context), "kokoro")
            OfflineEngineType.PIPER -> File(getTtsDirectory(context), voiceId ?: "piper")
        }

    /**
     * One eSpeak-NG bundle shared by every Piper voice.
     *
     * Each Piper archive carries its own complete copy — 391 files, about 18MB
     * extracted. Twelve voices would therefore store the same data twelve times,
     * roughly 216MB of pure duplication. The first voice installed populates this
     * directory (pruned to English) and the rest reuse it.
     */
    fun sharedEspeakDirectory(context: Context): File =
        File(getTtsDirectory(context), "espeak-ng-data")

    fun isVoiceInstalled(context: Context, voiceId: String): Boolean {
        val engine = if (voiceId.startsWith("piper_")) OfflineEngineType.PIPER else OfflineEngineType.KOKORO
        val dir = getVoiceDirectory(context, engine, voiceId)
        return when (engine) {
            OfflineEngineType.KOKORO -> {
                val hasModel = listOf("model.onnx", "model.fp16.onnx", "model.int8.onnx")
                    .any { File(dir, it).let { f -> f.isFile && f.length() > 0L } }
                // The lexicon and dict are what separate the multi-lang v1.0 package
                // from kokoro-en-v0_19. Without them here, a v0_19 payload used to
                // report "installed" while the engine silently failed to initialise
                // against the lexicon path it needs — so check for them up front.
                val hasRequired = listOf(
                    "tokens.txt", "espeak-ng-data/phontab", "lexicon-us-en.txt"
                ).all { File(dir, it).exists() }
                hasModel && hasRequired && isExpectedKokoroRelease(dir)
            }
            OfflineEngineType.PIPER -> {
                val base = availableVoices.firstOrNull { it.id == voiceId }?.modelBaseName
                val model = base?.let { File(dir, "$it.onnx") }
                model != null && model.isFile && model.length() > 0L &&
                    File(dir, "tokens.txt").isFile &&
                    File(sharedEspeakDirectory(context), "phontab").isFile
            }
        }
    }

    fun getInstalledVoices(context: Context): List<OfflineVoiceDescriptor> {
        return availableVoices.filter { isVoiceInstalled(context, it.id) }
    }

    // isVoiceInstalled runs once per catalog voice during composition, so a bad
    // package would otherwise log the same warning two dozen times a frame.
    @Volatile private var lastWarnedVoicesBinSize = -1L

    /**
     * Deletes the parts of the Kokoro package an English reader can never use.
     *
     * The upstream archive is the multi-language build, so a reader who only ever
     * opens English books still stores Chinese word-segmentation tables and eSpeak
     * dictionaries for eighty-odd languages. Measured on a real install: `dict/` is
     * 14MB of jieba data, `lexicon-zh.txt` another 2.3MB, and the non-English
     * `*_dict` files come to roughly 17MB — `ru_dict` alone is 8.3MB. That is about
     * 33MB of 182MB, none of which is ever read.
     *
     * Only the Chinese-specific files and foreign eSpeak dictionaries go. The shared
     * phoneme tables (`phondata`, `phonindex`, `phontab`, `intonations`, `voices`,
     * `lang`) are what eSpeak actually needs and are left alone, as is `en_dict`.
     *
     * Returns the number of bytes reclaimed.
     */
    private fun pruneNonEnglishAssets(dir: File): Long {
        var freed = 0L

        fun drop(file: File) {
            if (!file.exists()) return
            val size = if (file.isDirectory) {
                file.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
            } else {
                file.length()
            }
            if (file.deleteRecursively()) freed += size
        }

        // Chinese segmentation and lexicon: only reachable through a Chinese voice,
        // which this catalog does not offer. (Kokoro only; Piper ships neither.)
        drop(File(dir, "dict"))
        drop(File(dir, "lexicon-zh.txt"))
        listOf("date-zh.fst", "number-zh.fst", "phone-zh.fst").forEach { drop(File(dir, it)) }

        // eSpeak loads only the dictionary for the voice it is set to, and that is
        // pinned to English in KokoroTtsEngine.
        File(dir, "espeak-ng-data").listFiles()
            ?.filter { it.isFile && it.name.endsWith("_dict") && it.name !in KEPT_ESPEAK_DICTS }
            ?.forEach { drop(it) }

        return freed
    }

    /**
     * Moves a freshly extracted Piper voice's eSpeak data into the shared directory.
     *
     * Every Piper archive bundles its own copy, so without this each installed voice
     * would keep a duplicate of the same ~18MB. The first voice populates the shared
     * copy; later ones find it already there and simply drop theirs.
     */
    private fun hoistSharedEspeak(context: Context, staging: File) = synchronized(espeakInstallLock) {
        val local = File(staging, "espeak-ng-data")
        if (!local.isDirectory) return@synchronized
        val shared = sharedEspeakDirectory(context)
        if (File(shared, "phontab").isFile) {
            val reclaimed = local.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
            local.deleteRecursively()
            Log.i(TAG, "Reused shared eSpeak data, saving ${reclaimed / (1024 * 1024)}MB")
        } else {
            shared.parentFile?.mkdirs()
            // An interrupted old install may have left a partial directory.
            shared.deleteRecursively()
            check(local.renameTo(shared)) { "Could not install shared pronunciation data" }
            Log.i(TAG, "Installed shared eSpeak data at $shared")
        }
    }

    /** Accept both complete v1.0 exports, excluding older and incompatible voice tables. */
    private fun isExpectedKokoroRelease(dir: File): Boolean {
        val voices = File(dir, "voices.bin")
        if (!voices.isFile) return false
        val size = voices.length()
        if (KokoroPackageShape.speakerCount(size) != null) return true
        if (size != lastWarnedVoicesBinSize) {
            lastWarnedVoicesBinSize = size
            Log.w(TAG, "voices.bin is $size bytes, expected a complete 53- or 54-speaker v1.0 export; re-download the voice")
        }
        return false
    }

    /** The same completeness rules as [isVoiceInstalled], against an arbitrary directory. */
    /**
     * Checks a freshly extracted package before it replaces the installed one.
     *
     * Piper names its model after the voice, so the expected file has to come from
     * the catalog entry rather than being hardcoded — every voice ships a different
     * `.onnx`. This runs before the eSpeak bundle is hoisted out to the shared
     * directory, so it still expects to find it locally.
     */
    private fun isPackageComplete(dir: File, voice: OfflineVoiceDescriptor): Boolean =
        when (voice.engineType) {
            OfflineEngineType.KOKORO -> {
                val hasModel = listOf("model.onnx", "model.fp16.onnx", "model.int8.onnx")
                    .any { File(dir, it).let { f -> f.isFile && f.length() > 0L } }
                val hasRequired = listOf(
                    "tokens.txt", "espeak-ng-data/phontab", "lexicon-us-en.txt"
                ).all { File(dir, it).exists() }
                hasModel && hasRequired && isExpectedKokoroRelease(dir)
            }
            OfflineEngineType.PIPER -> {
                val base = voice.modelBaseName
                base != null &&
                    File(dir, "$base.onnx").let { it.isFile && it.length() > 0L } &&
                    File(dir, "tokens.txt").isFile &&
                    File(dir, "espeak-ng-data/phontab").isFile
            }
        }

    /**
     * Removes archives and stray model files left behind by interrupted or older
     * downloads. An abandoned `*_archive.tmp` is the size of a full model package,
     * so these are worth hundreds of megabytes.
     */
    fun cleanupOrphans(context: Context): Long = synchronized(transientLock) {
        val dir = getTtsDirectory(context)
        var freed = 0L
        dir.listFiles()?.forEach { file ->
            if (file.absolutePath in activeTransientPaths) return@forEach
            val stale = file.isFile && (
                file.name.endsWith("_archive.tmp") ||
                    file.name.endsWith(".onnx") ||
                    file.name.endsWith(".tar.bz2")
                )
            val staleStaging = file.isDirectory && file.name.endsWith("_staging")
            if (stale) {
                freed += file.length()
                file.delete()
            } else if (staleStaging) {
                freed += file.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
                file.deleteRecursively()
            }
        }
        if (freed > 0) Log.i(TAG, "Reclaimed $freed bytes of orphaned TTS downloads")
        freed
    }

    suspend fun downloadVoice(context: Context, voice: OfflineVoiceDescriptor): Boolean {
        val task = synchronized(requestLock) {
            inFlight[voice.id]?.takeIf { it.isActive } ?: downloadScope.async(start = CoroutineStart.LAZY) {
                runDownload(context.applicationContext, voice)
            }.also { pending ->
                inFlight[voice.id] = pending
                pending.invokeOnCompletion {
                    synchronized(requestLock) { if (inFlight[voice.id] === pending) inFlight.remove(voice.id) }
                }
                pending.start()
            }
        }
        return task.await()
    }

    private suspend fun runDownload(context: Context, voice: OfflineVoiceDescriptor): Boolean {
        // Kokoro catalog entries all install one shared package; Piper entries are independent.
        val packageKey = if (voice.engineType == OfflineEngineType.KOKORO) "kokoro" else voice.id
        updateState(voice.id, DownloadState.Downloading(0, 0, 0, queued = true))
        try {
            return packageLocks.computeIfAbsent(packageKey) { Mutex() }.withLock {
                downloadSlots.withPermit {
                    withContext(Dispatchers.IO) { installVoice(context, voice) }
                }
            }
        } catch (cancelled: CancellationException) {
            updateState(voice.id, if (isVoiceInstalled(context, voice.id)) DownloadState.Completed else DownloadState.Idle)
            throw cancelled
        }
    }

    private suspend fun installVoice(context: Context, voice: OfflineVoiceDescriptor): Boolean {
        val baseDir = getTtsDirectory(context)
        val attempt = "${voice.id}_${UUID.randomUUID()}"
        val targetFile = File(baseDir, "${attempt}_archive.tmp")
        val staging = File(baseDir, "${attempt}_staging")
        synchronized(transientLock) {
            activeTransientPaths.add(targetFile.absolutePath)
            activeTransientPaths.add(staging.absolutePath)
            cleanupOrphans(context)
        }
        try {
            if (isVoiceInstalled(context, voice.id)) {
                updateState(voice.id, DownloadState.Completed)
                return true
            }
            require(voice.modelUrl.endsWith(".tar.bz2")) { "Unsupported offline voice archive." }
            updateState(voice.id, DownloadState.Downloading(0, 0, 0))
            val downloadContext = coroutineContext
            val success = downloadFile(voice.modelUrl, targetFile) { bytesRead, totalBytes ->
                downloadContext.ensureActive()
                // Installation still has to succeed. Never report a ready voice at network EOF.
                val pct = if (totalBytes > 0) ((bytesRead * 100) / totalBytes).toInt().coerceIn(0, 99) else 0
                updateState(voice.id, DownloadState.Downloading(bytesRead, totalBytes, pct))
            }

            if (!success) {
                targetFile.delete()
                updateState(voice.id, DownloadState.Failed("Network error or timeout"))
                return false
            }

            // Extract to a staging directory and only swap it in once it is verified
            // complete. Extracting straight over the live directory meant a failed or
            // mismatched download destroyed a working voice.
            val voiceDir = getVoiceDirectory(context, voice.engineType, voice.id)
            updateState(voice.id, DownloadState.Downloading(targetFile.length(), targetFile.length(), 99, installing = true))
            val extracted = extractTarBz2(targetFile, staging)
            targetFile.delete()
            if (!extracted) {
                staging.deleteRecursively()
                updateState(voice.id, DownloadState.Failed("Archive extraction failed"))
                return false
            }

            if (!isPackageComplete(staging, voice)) {
                staging.deleteRecursively()
                updateState(voice.id, DownloadState.Failed("Package is incomplete; required model data was not found"))
                return false
            }

            val saved = pruneNonEnglishAssets(staging)
            if (saved > 0) Log.i(TAG, "Pruned ${saved / (1024 * 1024)}MB of unused language data")

            if (voice.engineType == OfflineEngineType.PIPER) {
                hoistSharedEspeak(context, staging)
            }

            voiceDir.deleteRecursively()
            if (!staging.renameTo(voiceDir)) {
                staging.deleteRecursively()
                updateState(voice.id, DownloadState.Failed("Could not install the downloaded voice"))
                return false
            }

            if (!isVoiceInstalled(context, voice.id)) {
                updateState(voice.id, DownloadState.Failed("Package is incomplete; required model data was not found"))
                return false
            }
            updateState(voice.id, DownloadState.Completed)
            return true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading voice ${voice.id}", e)
            updateState(voice.id, DownloadState.Failed(e.localizedMessage ?: "Download failed"))
            return false
        } finally {
            synchronized(transientLock) {
                targetFile.delete()
                staging.deleteRecursively()
                activeTransientPaths.remove(targetFile.absolutePath)
                activeTransientPaths.remove(staging.absolutePath)
            }
        }
    }

    private suspend fun extractTarBz2(archiveFile: File, outputDir: File): Boolean {
        return try {
            outputDir.deleteRecursively()
            outputDir.mkdirs()
            val rootPath = outputDir.canonicalFile.toPath()
            archiveFile.inputStream().buffered().use { bin ->
            org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream(bin).use { bzIn ->
            org.apache.commons.compress.archivers.tar.TarArchiveInputStream(bzIn).use { tarIn ->
            var entry = tarIn.nextEntry
            val buffer = ByteArray(65536)

            while (entry != null) {
                coroutineContext.ensureActive()
                require(!entry.isSymbolicLink && !entry.isLink) { "Archive contains a linked path" }
                val relativeName = entry.name.substringAfter('/', "")
                if (relativeName.isNotBlank()) {
                    val outFile = File(outputDir, relativeName).canonicalFile
                    require(outFile.toPath().startsWith(rootPath)) { "Archive contains an unsafe path" }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out ->
                            var count: Int
                            while (tarIn.read(buffer).also { count = it } != -1) {
                                coroutineContext.ensureActive()
                                out.write(buffer, 0, count)
                            }
                        }
                    }
                }
                entry = tarIn.nextEntry
            }
            }
            }
            }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting tar.bz2 archive", e)
            outputDir.deleteRecursively()
            false
        }
    }

    fun deleteVoice(context: Context, voiceId: String): Boolean {
        val voice = availableVoices.firstOrNull { it.id == voiceId } ?: return false
        val affected = availableVoices.filter {
            it.id == voiceId || (voice.engineType == OfflineEngineType.KOKORO && it.engineType == OfflineEngineType.KOKORO)
        }
        if (affected.any { _downloadState.value[it.id] is DownloadState.Downloading }) return false
        getVoiceDirectory(context, voice.engineType, voice.id).deleteRecursively()

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentSet = prefs.getStringSet(KEY_INSTALLED_VOICES, emptySet())?.toMutableSet() ?: mutableSetOf()
        currentSet.remove(voiceId)
        prefs.edit().putStringSet(KEY_INSTALLED_VOICES, currentSet).apply()

        updateState(voiceId, DownloadState.Idle)
        return true
    }

    private fun updateState(voiceId: String, state: DownloadState) {
        _downloadState.update { it + (voiceId to state) }
    }

    private fun downloadFile(
        urlString: String,
        targetFile: File,
        onProgress: (Long, Long) -> Unit
    ): Boolean {
        var currentUrl = urlString
        var redirectCount = 0
        val maxRedirects = 10

        while (redirectCount < maxRedirects) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(currentUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 20_000
                connection.readTimeout = 60_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Vern)")
                connection.connect()

                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                    code == HttpURLConnection.HTTP_MOVED_TEMP ||
                    code == HttpURLConnection.HTTP_SEE_OTHER ||
                    code == 307 || code == 308) {
                    val newUrl = connection.getHeaderField("Location")
                    if (newUrl.isNullOrBlank()) return false
                    currentUrl = URL(URL(currentUrl), newUrl).toString()
                    redirectCount++
                    connection.disconnect()
                    continue
                }

                if (code != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "Download failed with HTTP code $code for $currentUrl")
                    return false
                }

                val totalBytes = connection.contentLengthLong
                var totalRead = 0L
                connection.inputStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(32768)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            onProgress(totalRead, totalBytes)
                        }
                        output.flush()
                    }
                }
                if (totalRead == 0L || (totalBytes > 0L && totalRead != totalBytes)) return false
                return true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e(TAG, "Download error for $currentUrl", e)
                return false
            } finally {
                connection?.disconnect()
            }
        }
        return false
    }

}

package com.veritas.reader

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class TtsEngineOption(
    val packageName: String,
    val label: String
)

data class TtsVoiceOption(
    val name: String,
    val label: String,
    val localeTag: String,
    val requiresNetwork: Boolean,
    val quality: Int,
    val latency: Int
)

private fun inferVoiceGender(name: String): String {
    val normalized = name.lowercase(Locale.getDefault())
    val femaleMarkers = listOf(
        "female", "woman", "girl", "samantha", "susan", "zira", "victoria", "karen",
        "moira", "tessa", "veena", "joanna", "salli", "kimberly", "ivy", "nicole",
        "emma", "amy", "olivia", "aria", "jenny", "natasha", "elizabeth"
    )
    val maleMarkers = listOf(
        "male", "man", "boy", "david", "mark", "daniel", "alex", "fred", "tom",
        "brian", "matthew", "joey", "justin", "russell", "arthur", "guy"
    )
    return when {
        femaleMarkers.any { it in normalized } -> "F"
        maleMarkers.any { it in normalized } -> "M"
        else -> ""
    }
}

private fun voiceLanguageLabel(localeTag: String): String {
    if (localeTag.isBlank()) return "Default language"
    val locale = Locale.forLanguageTag(localeTag)
    val display = locale.getDisplayName(Locale.getDefault())
    return display.ifBlank { localeTag }
}

private fun buildVoiceLabel(name: String, localeTag: String, requiresNetwork: Boolean): String {
    val networkLabel = if (requiresNetwork) "N" else "L"
    val genderLabel = inferVoiceGender(name)
    val baseLabel = listOf(voiceLanguageLabel(localeTag), networkLabel, genderLabel, name)
        .filter { it.isNotBlank() }
        .joinToString(" • ")
    return if (requiresNetwork) "[HQ] $baseLabel" else baseLabel
}

object VoiceNamingRegistry {
    private val humanVoiceMap = mapOf(
        // US Voices (Google / Samsung)
        "en-US-language" to Pair("Amanda", "Female"),
        "en-us-x-iob" to Pair("Bella", "Female"),
        "en-us-x-iog" to Pair("Amanda (v)", "Female"),
        "en-us-x-iol" to Pair("David", "Male"),
        "en-us-x-iom" to Pair("Ethan", "Male"),
        "en-us-x-sfg" to Pair("Fiona", "Female"),
        "en-us-x-tpc" to Pair("Grace", "Female"),
        "en-us-x-tpd" to Pair("Henry", "Male"),
        "en-us-x-tpf" to Pair("Ivy", "Female"),

        // UK Voices (Google / Samsung)
        "en-GB-language" to Pair("Victoria", "Female"),
        "en-gb-x-gba" to Pair("Alice", "Female"),
        "en-gb-x-gbb" to Pair("Arthur", "Male"),
        "en-gb-x-gbc" to Pair("Beatrice", "Female"),
        "en-gb-x-gbd" to Pair("Charles", "Male"),
        "en-gb-x-gbg" to Pair("Victoria (v)", "Female"),
        "en-gb-x-rjs" to Pair("Edward", "Male"),

        // Australia Voices
        "en-au-x-afh" to Pair("Ava", "Female"),
        "en-au-x-aub" to Pair("Ayla", "Female"),
        "en-au-x-aud" to Pair("Archie", "Male"),
        "en-au-x-aue" to Pair("Audrey", "Female"),

        // Canada Voices
        "en-ca-x-cab" to Pair("Claire", "Female"),
        "en-ca-x-cac" to Pair("Caleb", "Male"),
        "en-ca-x-cad" to Pair("Chloe", "Female"),
        "en-ca-x-cae" to Pair("Connor", "Male"),

        // India Voices
        "en-in-x-ena" to Pair("Ananya", "Female"),
        "en-in-x-enb" to Pair("Arjun", "Male"),
        "en-in-x-enc" to Pair("Diya", "Female"),
        "en-in-x-end" to Pair("Rohan", "Male"),

        // Ireland Voices
        "en-ie-x-iea" to Pair("Fiona", "Female"),
        "en-ie-x-ieb" to Pair("Liam", "Male")
    )

    fun resolveHumanName(voiceName: String, localeTag: String, isNetwork: Boolean): String {
        val baseId = voiceName.removeSuffix("-local").removeSuffix("-network")
        val mapped = humanVoiceMap[baseId] ?: humanVoiceMap[voiceName]
        val region = when {
            localeTag.equals("en-US", ignoreCase = true) || localeTag.equals("en_US", ignoreCase = true) -> "US"
            localeTag.equals("en-GB", ignoreCase = true) || localeTag.equals("en_GB", ignoreCase = true) -> "UK"
            localeTag.equals("en-AU", ignoreCase = true) -> "AU"
            localeTag.equals("en-CA", ignoreCase = true) -> "CA"
            localeTag.equals("en-IN", ignoreCase = true) -> "IN"
            localeTag.equals("en-IE", ignoreCase = true) -> "IE"
            else -> localeTag.uppercase().take(2).ifBlank { "Voice" }
        }
        val typeBadge = if (isNetwork) "Network" else "Offline"

        if (mapped != null) {
            val (name, gender) = mapped
            return "$name ($region $gender • $typeBadge)"
        }

        val cleanBaseName = voiceName
            .replace(Regex("(English|Offline|voice|quality|latency|network|local|\\d+)"), "")
            .replace(Regex("[^A-Za-z0-9]"), " ")
            .trim()
            .take(24)
            .ifBlank { "Voice ${voiceName.takeLast(4)}" }
        return "$cleanBaseName ($region • $typeBadge)"
    }
}

object VoiceManager {
    /** The two offline engines. Both route to the neural playback path. */
    const val VERITAS_STUDIO = "com.veritas.voice.studio"
    const val VERITAS_LITE = "com.veritas.voice.lite"

    /** True for either offline engine, including the legacy combined id. */
    fun isVeritasEngine(enginePackage: String?): Boolean =
        enginePackage == VERITAS_STUDIO || enginePackage == VERITAS_LITE ||
            enginePackage == "com.veritas.voice"

    /** True if the voice name belongs to a Veritas offline model (Piper or Kokoro). */
    fun isVeritasVoice(voiceName: String?): Boolean =
        !voiceName.isNullOrBlank() && (
            voiceName.startsWith("piper_", ignoreCase = true) ||
            voiceName.startsWith("kokoro_", ignoreCase = true) ||
            com.veritas.reader.tts.VoiceModelManager.availableVoices.any { it.id.equals(voiceName, ignoreCase = true) }
        )

    /** Returns the Veritas engine identifier for a given voice name, or null if system TTS. */
    fun engineForVoice(voiceName: String?): String? = when {
        voiceName.isNullOrBlank() -> null
        voiceName.startsWith("piper_", ignoreCase = true) -> VERITAS_LITE
        voiceName.startsWith("kokoro_", ignoreCase = true) -> VERITAS_STUDIO
        else -> com.veritas.reader.tts.VoiceModelManager.availableVoices
            .firstOrNull { it.id.equals(voiceName, ignoreCase = true) }?.let {
                if (it.engineType == com.veritas.reader.tts.OfflineEngineType.PIPER) VERITAS_LITE else VERITAS_STUDIO
            }
    }

    fun loadInstalledEngines(context: Context): List<TtsEngineOption> {
        val pm = context.packageManager
        val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
        val systemEngines = pm.queryIntentServices(intent, 0)
            .mapNotNull { resolveInfo ->
                val serviceInfo = resolveInfo.serviceInfo ?: return@mapNotNull null
                val label = resolveInfo.loadLabel(pm).toString().ifBlank { serviceInfo.packageName }
                TtsEngineOption(packageName = serviceInfo.packageName, label = label)
            }
            // Samsung's engine does not serve third-party apps, so offering it only
            // gives users a choice that silently fails.
            .filterNot { it.packageName.startsWith("com.samsung.SMT") }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }

        // Studio and Lite are separate choices, so each lists only its own voices
        // instead of one mixed roster.
        fun installed(engine: com.veritas.reader.tts.OfflineEngineType) =
            com.veritas.reader.tts.VoiceModelManager.availableVoices
                .any { it.engineType == engine && com.veritas.reader.tts.VoiceModelManager.isVoiceInstalled(context, it.id) }

        val studio = TtsEngineOption(
            packageName = VERITAS_STUDIO,
            label = if (installed(com.veritas.reader.tts.OfflineEngineType.KOKORO)) "Vern Studio"
            else "Vern Studio (tap to download)"
        )
        val lite = TtsEngineOption(
            packageName = VERITAS_LITE,
            label = if (installed(com.veritas.reader.tts.OfflineEngineType.PIPER)) "Vern Lite"
            else "Vern Lite (tap to download)"
        )
        return listOf(studio, lite) + systemEngines
    }

    /**
     * Studio (Kokoro) voices first, then Lite (Piper), each alphabetical.
     *
     * The quality and latency figures are the ones the picker displays, so they are
     * the measured reality rather than a flat 500/50 for everything: Studio sounds
     * richer but synthesises at roughly twice real time on mid-range hardware, while
     * Lite runs at about a sixth of real time and never makes the reader wait.
     */
    fun loadVeritasVoices(context: Context, enginePackage: String? = null): List<TtsVoiceOption> {
        val only = when (enginePackage) {
            VERITAS_STUDIO -> com.veritas.reader.tts.OfflineEngineType.KOKORO
            VERITAS_LITE -> com.veritas.reader.tts.OfflineEngineType.PIPER
            else -> null
        }
        return com.veritas.reader.tts.VoiceModelManager.availableVoices
            .filter { only == null || it.engineType == only }
            .sortedWith(compareBy({ it.engineType != com.veritas.reader.tts.OfflineEngineType.KOKORO }, { it.name }))
            .map { voice ->
                val isInstalled = com.veritas.reader.tts.VoiceModelManager.isVoiceInstalled(context, voice.id)
                val isStudio = voice.engineType == com.veritas.reader.tts.OfflineEngineType.KOKORO
                TtsVoiceOption(
                    name = voice.id,
                    label = if (isInstalled) voice.name else "${voice.name} (Download Required)",
                    localeTag = voice.localeTag,
                    requiresNetwork = false,
                    quality = if (isStudio) 500 else 400,
                    latency = if (isStudio) 300 else 50
                )
            }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    suspend fun loadVoices(context: Context, enginePackage: String): List<TtsVoiceOption> {
        if (isVeritasEngine(enginePackage)) {
            return loadVeritasVoices(context, enginePackage)
        }
        val installedVeritas = if (enginePackage.isBlank()) {
            loadVeritasVoices(context).filter { option ->
                com.veritas.reader.tts.VoiceModelManager.isVoiceInstalled(context, option.name)
            }
        } else emptyList()

        val systemVoices = withTimeoutOrNull(8_000L) {
            suspendCancellableCoroutine { continuation ->
                val engineDeferred = CompletableDeferred<TextToSpeech>()
                val listener = TextToSpeech.OnInitListener { status ->
                    val current = engineDeferred.getCompleted()
                    if (status == TextToSpeech.SUCCESS) {
                        val voices = current.voices.orEmpty()
                            .map { voice ->
                                val localeTag = voice.locale?.toLanguageTag().orEmpty()
                                TtsVoiceOption(
                                    name = voice.name,
                                    label = VoiceNamingRegistry.resolveHumanName(voice.name, localeTag, voice.isNetworkConnectionRequired),
                                    localeTag = localeTag,
                                    requiresNetwork = voice.isNetworkConnectionRequired,
                                    quality = voice.quality,
                                    latency = voice.latency
                                )
                            }
                            .distinctBy { it.name }
                            .sortedWith { a, b ->
                                val aEn = a.localeTag.startsWith("en", ignoreCase = true)
                                val bEn = b.localeTag.startsWith("en", ignoreCase = true)
                                val enCompare = when {
                                    aEn && !bEn -> -1
                                    !aEn && bEn -> 1
                                    aEn && bEn -> {
                                        val aUS = a.localeTag.equals("en-US", ignoreCase = true) || a.localeTag.equals("en_US", ignoreCase = true)
                                        val bUS = b.localeTag.equals("en-US", ignoreCase = true) || b.localeTag.equals("en_US", ignoreCase = true)
                                        val aGB = a.localeTag.equals("en-GB", ignoreCase = true) || a.localeTag.equals("en_GB", ignoreCase = true)
                                        val bGB = b.localeTag.equals("en-GB", ignoreCase = true) || b.localeTag.equals("en_GB", ignoreCase = true)
                                        when {
                                            aUS && !bUS -> -1
                                            !aUS && bUS -> 1
                                            aGB && !bGB -> -1
                                            !aGB && bGB -> 1
                                            else -> a.localeTag.compareTo(b.localeTag, ignoreCase = true)
                                        }
                                    }
                                    else -> a.localeTag.compareTo(b.localeTag, ignoreCase = true)
                                }
                                if (enCompare != 0) enCompare
                                else {
                                    val netCompare = a.requiresNetwork.compareTo(b.requiresNetwork)
                                    if (netCompare != 0) netCompare
                                    else a.name.compareTo(b.name, ignoreCase = true)
                                }
                            }
                        runCatching { current.shutdown() }
                        if (continuation.isActive) continuation.resume(voices)
                        else runCatching { current.shutdown() }
                    } else {
                        runCatching { current.shutdown() }
                        if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Could not initialize this voice engine."))
                    }
                }
                val engine = if (enginePackage.isBlank()) {
                    TextToSpeech(context.applicationContext, listener)
                } else {
                    TextToSpeech(context.applicationContext, listener, enginePackage)
                }
                engineDeferred.complete(engine)
                continuation.invokeOnCancellation { runCatching { engine.shutdown() } }
            }
        } ?: emptyList()
        return installedVeritas + systemVoices
    }

    // Cached preview engine. Voice auditioning happens in a settings screen, so one
    // engine at a time is plenty; it is released when the requested voice changes.
    private var previewEngine: com.veritas.reader.tts.TtsEngine? = null
    private var previewEngineVoiceId: String? = null
    private var previewTrack: android.media.AudioTrack? = null

    private fun previewEngineFor(
        appContext: Context,
        voiceName: String
    ): com.veritas.reader.tts.TtsEngine {
        previewEngine?.let { if (previewEngineVoiceId == voiceName) return it }
        releasePreviewEngine()
        val isPiper = voiceName.contains("piper", ignoreCase = true)
        val engine: com.veritas.reader.tts.TtsEngine = if (isPiper) {
            com.veritas.reader.tts.PiperEngine(appContext, voiceName)
        } else {
            com.veritas.reader.tts.KokoroTtsEngine(appContext, voiceName)
        }
        previewEngine = engine
        previewEngineVoiceId = voiceName
        return engine
    }

    private val previewScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default + kotlinx.coroutines.SupervisorJob())
    private var previewJob: kotlinx.coroutines.Job? = null
    private var previewSystemTts: TextToSpeech? = null
    private var lastPreviewTapTime: Long = 0L
    private var lastPreviewVoiceId: String = ""

    private fun releasePreviewTrack() {
        previewTrack?.let { track ->
            runCatching { track.stop() }
            runCatching { track.release() }
        }
        previewTrack = null
    }

    private fun releaseSystemTts() {
        previewSystemTts?.let { tts ->
            runCatching { tts.stop() }
            runCatching { tts.shutdown() }
        }
        previewSystemTts = null
    }

    /** Frees the cached preview engine, active preview coroutine, and any track still holding its audio. */
    fun releasePreviewEngine() {
        previewJob?.cancel()
        previewJob = null
        releasePreviewTrack()
        releaseSystemTts()
        previewEngine?.shutdown()
        previewEngine = null
        previewEngineVoiceId = null
    }

    fun previewVoice(
        context: Context,
        enginePackage: String,
        voiceName: String,
        text: String = "This is a preview of the selected voice.",
        rate: Float = 1.0f,
        pitch: Float = 1.0f
    ) {
        val now = System.currentTimeMillis()
        if (voiceName == lastPreviewVoiceId && now - lastPreviewTapTime < 450L) {
            // Ignore rapid duplicate tap on the exact same voice preview
            return
        }
        lastPreviewTapTime = now
        lastPreviewVoiceId = voiceName

        // Cancel previous preview immediately so multiple previews never overlap
        previewJob?.cancel()
        releasePreviewTrack()
        releaseSystemTts()

        val isVeritas = isVeritasVoice(voiceName) || isVeritasEngine(enginePackage)
        if (isVeritas) {
            previewJob = previewScope.launch {
                // Auditioning voices used to build a whole engine per tap — a 114MB
                // model load and teardown to speak one phrase. Keep the last one and
                // only rebuild when the voice actually changes.
                val ttsEngine = previewEngineFor(context.applicationContext, voiceName)
                if (!ttsEngine.isReady()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context.applicationContext,
                            "Voice model not downloaded yet. Please download in Settings.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                    releasePreviewEngine()
                    return@launch
                }
                val pcm = ttsEngine.synthesize(text, rate, pitch)
                if (pcm != null && pcm.isNotEmpty()) {
                    releasePreviewTrack()
                    val audioTrack = android.media.AudioTrack.Builder()
                        .setAudioAttributes(
                            android.media.AudioAttributes.Builder()
                                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        .setAudioFormat(
                            android.media.AudioFormat.Builder()
                                .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(ttsEngine.sampleRate)
                                .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(pcm.size * 2)
                        .setTransferMode(android.media.AudioTrack.MODE_STATIC)
                        .build()
                    audioTrack.write(pcm, 0, pcm.size)
                    previewTrack = audioTrack
                    audioTrack.play()
                } else {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context.applicationContext,
                            "Voice synthesis failed. Ensure model files are downloaded.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            return
        }

        var engine: TextToSpeech? = null
        val listener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS && previewSystemTts === engine) {
                engine?.voices?.firstOrNull { it.name == voiceName }?.let { voice ->
                    engine?.voice = voice
                }
                engine?.setSpeechRate(rate)
                engine?.setPitch(pitch)
                engine?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (previewSystemTts === engine) {
                            releaseSystemTts()
                        }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (previewSystemTts === engine) {
                            releaseSystemTts()
                        }
                    }
                })
                engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "preview")
            } else {
                runCatching { engine?.shutdown() }
            }
        }
        engine = if (enginePackage.isBlank()) {
            TextToSpeech(context.applicationContext, listener)
        } else {
            TextToSpeech(context.applicationContext, listener, enginePackage)
        }
        previewSystemTts = engine
    }
}

object VoiceConfigurator {
    fun apply(tts: TextToSpeech, settings: VoiceSettings): String {
        val locale = settings.localeTag.takeIf { it.isNotBlank() }?.let { Locale.forLanguageTag(it) } ?: Locale.getDefault()
        val languageStatus = tts.setLanguage(locale)
        val selectedVoice = settings.voiceName.takeIf { it.isNotBlank() }
            ?.let { name -> tts.voices?.firstOrNull { it.name == name } }
        if (selectedVoice != null) {
            tts.voice = selectedVoice
        }
        return when (languageStatus) {
            TextToSpeech.LANG_MISSING_DATA -> "Voice data is missing for ${locale.displayLanguage}."
            TextToSpeech.LANG_NOT_SUPPORTED -> "${locale.displayLanguage} is not supported by this TTS engine."
            else -> "Voice ready: ${settings.displayName}."
        }
    }
}

object TutorialSpeaker {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingText: String? = null

    fun init(context: Context) {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isInitialized = true
                    pendingText?.let {
                        tts?.speak(it, TextToSpeech.QUEUE_FLUSH, null, "tutorial")
                        pendingText = null
                    }
                }
            }
        }
    }

    fun speak(text: String) {
        speakWithTuning(text, 1.0f, 1.0f)
    }

    fun speakWithTuning(text: String, speed: Float = 1.0f, pitch: Float = 1.0f) {
        if (tts != null && isInitialized) {
            tts?.setSpeechRate(speed.coerceIn(0.5f, 2.5f))
            tts?.setPitch(pitch.coerceIn(0.5f, 2.0f))
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tutorial")
        } else {
            pendingText = text
        }
    }

    fun isSpeaking(): Boolean {
        return tts?.isSpeaking == true
    }

    fun stop() {
        pendingText = null
        if (tts != null && isInitialized) {
            tts?.stop()
        }
    }

    fun shutdown() {
        pendingText = null
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}

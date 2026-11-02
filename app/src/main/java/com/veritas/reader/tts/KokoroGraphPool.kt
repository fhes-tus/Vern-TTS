package com.veritas.reader.tts

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One private reloadable graph per owner; wrappers retain immutable SID/profile identity. */
internal class KokoroGraphPool(private val context: Context, private val role: SpeechModelRole = SpeechModelRole.EXPORT) {
    private val lock = Mutex()
    private val pins = HashSet<Any>()
    @Volatile private var native: NativeModel<KokoroTtsEngine>? = null
    private var loadedKey = ""
    @Volatile private var closed = false
    internal var onNativeStart: (String) -> Unit = {}
    internal var modelCreations = 0; private set

    private fun key(voice: String): String {
        val dir = VoiceModelManager.getVoiceDirectory(context, OfflineEngineType.KOKORO)
        val files = listOf("model.onnx", "model.fp16.onnx", "model.int8.onnx", "voices.bin", "tokens.txt", "lexicon-us-en.txt", "lexicon-gb-en.txt")
        val identity = files.joinToString { name -> java.io.File(dir, name).let { "$name:${it.length()}:${it.lastModified()}" } }
        val locale = if (voice.startsWith("kokoro_bf_") || voice.startsWith("kokoro_bm_")) "UK" else "US"
        return "${dir.canonicalPath}:$locale:$identity"
    }
    private suspend fun load(voice: String): NativeModel<KokoroTtsEngine> {
        val wanted = key(voice)
        if (native == null || wanted != loadedKey) {
            native?.let { it.close(); it.awaitClosed() }
            native = NativeModel(NativeModelResources.get(context), role,
                { KokoroTtsEngine(context, voice).also {
                    check(it.isReady()) { "The selected Kokoro model could not initialize. Check available memory and the downloaded voice." }
                    modelCreations++
                } }, KokoroTtsEngine::shutdown).also {
                synchronized(pins) { it.setPlaybackActive(pins.isNotEmpty()) }
            }
            loadedKey = wanted
        }
        return requireNotNull(native)
    }
    fun voice(voiceId: String, shaping: Boolean): TtsEngine = object : TtsEngine, PlaybackModelPin {
        @Volatile private var retired = false
        @Volatile private var outputRate = 24000
        override val sampleRate get() = outputRate
        override fun isReady(): Boolean = runBlocking {
            lock.withLock { if (retired || closed) false else load(voiceId).use { outputRate = it.sampleRate; it.isReady() } }
        }
        override suspend fun synthesize(sentence: String, rate: Float, pitch: Float): ShortArray? =
            synthesizeFloat(sentence, rate, pitch)?.pcm16()
        override suspend fun synthesizeFloat(sentence: String, rate: Float, pitch: Float): FloatPcmAudio? = withContext(Dispatchers.Default) {
            val raw = lock.withLock {
                if (retired || closed) null else load(voiceId).use { active ->
                    onNativeStart(voiceId)
                    active.generateRawBlocking(sentence, rate, voiceId)?.let { it to active.sampleRate }
                }
            } ?: return@withContext null
            currentCoroutineContext().ensureActive()
            if (retired || closed) return@withContext null
            outputRate = raw.second
            KokoroVoiceProcessor.render(raw.first, raw.second, pitch, shaping)
        }
        override fun setPlaybackActive(active: Boolean) = synchronized(pins) {
            if (active && !retired) pins.add(this) else pins.remove(this)
            native?.setPlaybackActive(pins.isNotEmpty())
            Unit
        }
        override fun shutdown() { retired = true; setPlaybackActive(false) }
    }
    suspend fun closeAndAwait() {
        closed = true
        lock.withLock { native?.let { it.close(); it.awaitClosed() }; native = null; loadedKey = "" }
    }
    fun close() = runBlocking { closeAndAwait() }
    fun closeAsync(): Job {
        closed = true
        return CoroutineScope(Dispatchers.Default).launch { closeAndAwait() }
    }
}

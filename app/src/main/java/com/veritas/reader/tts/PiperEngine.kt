package com.veritas.reader.tts

import android.content.Context
import android.util.Log
import com.veritas.reader.SpeechDelivery
import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** Private, reloadable Piper graph. Full-precision updates are adopted between native calls. */
class PiperEngine(private val context: Context, private val voiceId: String,
    private val role: SpeechModelRole = SpeechModelRole.EXPORT) : TtsEngine, PlaybackModelPin {
    @Volatile private var outputRate = 22_050
    @Volatile private var closed = false
    private val calls = Mutex()
    private val pinLock = Any()
    private var playbackActive = false
    private var native: NativeModel<OfflineTts>? = null
    private var loadedIdentity = ""
    internal var modelCreations = 0; private set
    override val sampleRate: Int get() = outputRate
    private val base get() = requireNotNull(VoiceModelManager.availableVoices.firstOrNull { it.id == voiceId }?.modelBaseName)

    private fun createNative(file: File): OfflineTts {
        val dir = file.parentFile!!
        require(VoiceModelManager.isVoiceInstalled(context, voiceId)) { "Download the selected Piper voice in Voice Studio." }
        return OfflineTts(config = OfflineTtsConfig(model = OfflineTtsModelConfig(
            vits = OfflineTtsVitsModelConfig(model = file.path, tokens = File(dir, "tokens.txt").path,
                dataDir = VoiceModelManager.sharedEspeakDirectory(context).absolutePath), numThreads = 2)))
            .also { outputRate = it.sampleRate(); modelCreations++ }
    }
    private suspend fun <R> withNative(work: (OfflineTts) -> R): R = calls.withLock {
        check(!closed) { "Piper voice is closed" }
        val directory = VoiceModelManager.getVoiceDirectory(context, OfflineEngineType.PIPER, voiceId)
        val file = requireNotNull(PiperModelFiles.preferred(directory, base)) { "Download the selected Piper voice in Voice Studio." }
        val identity = "${file.canonicalPath}:${file.length()}:${file.lastModified()}"
        if (native == null || loadedIdentity != identity) {
            native?.let { it.close(); it.awaitClosed() }
            synchronized(pinLock) {
                check(!closed) { "Piper voice is closed" }
                native = NativeModel(NativeModelResources.get(context), role, { createNative(file) }, OfflineTts::free)
                    .also { it.setPlaybackActive(playbackActive) }
                loadedIdentity = identity
            }
        }
        requireNotNull(native).use(work)
    }
    override fun isReady(): Boolean = !closed && runBlocking { withNative { it.isReady } }
    override suspend fun synthesize(sentence: String, rate: Float, pitch: Float): ShortArray? =
        synthesizeFloat(sentence, rate, pitch)?.pcm16()
    override suspend fun synthesizeFloat(sentence: String, rate: Float, pitch: Float): FloatPcmAudio? = withContext(Dispatchers.Default) {
        val clean = NeuralSpeechText.prepare(sentence)
        if (clean.isBlank() || closed) return@withContext null
        try {
            val generated = withNative { it.generate(clean, sid = 0, speed = SpeechDelivery.rate(rate)) to loadedIdentity.contains(".fp32.onnx:") }
            val audio = generated.first
            currentCoroutineContext().ensureActive()
            if (closed || audio.samples.isEmpty()) return@withContext null
            outputRate = audio.sampleRate
            val processed = PcmPitchShift.apply(audio.samples, audio.sampleRate, pitch)
            PiperCapture.record(context, voiceId, rate, pitch, audio.samples, processed, audio.sampleRate,
                if (generated.second) "fp32" else "legacy")
            FloatPcmAudio(processed, audio.sampleRate)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (busy: SpeechModelBusyException) { throw busy }
        catch (failure: Exception) { Log.e("PiperEngine", "Piper synthesis failed", failure); null }
    }
    override fun setPlaybackActive(active: Boolean) = synchronized(pinLock) {
        playbackActive = active
        native?.setPlaybackActive(active)
        Unit
    }
    override fun shutdown() = synchronized(pinLock) { closed = true; native?.close(); Unit }
    override suspend fun awaitShutdown() { synchronized(pinLock) { native }?.awaitClosed() }
}

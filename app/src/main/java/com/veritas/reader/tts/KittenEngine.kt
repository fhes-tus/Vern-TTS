package com.veritas.reader.tts

import android.content.Context
import com.k2fsa.sherpa.onnx.*
import com.veritas.reader.SpeechDelivery
import kotlinx.coroutines.*
import java.io.File

/** Kitten Nano 0.8 FP32, its own phoneme table and all eight reference-style rows. */
internal class KittenEngine(context: Context, voiceId: String, role: SpeechModelRole = SpeechModelRole.EXPORT) : TtsEngine, PlaybackModelPin {
    private val sid = voiceId.removePrefix("kitten_").toInt().also { require(it in 0..7) }
    private val directory = VoiceModelManager.getVoiceDirectory(context,OfflineEngineType.KITTEN)
    private val native = NativeModel(NativeModelResources.get(context),role, {
        require(VoiceModelManager.isVoiceInstalled(context,voiceId)) { "Download the selected Kitten voice first." }
        OfflineTts(config = OfflineTtsConfig(model = OfflineTtsModelConfig(kitten = OfflineTtsKittenModelConfig(
            model = File(directory,"model.fp32.onnx").path,voices = File(directory,"voices.bin").path,
            tokens = File(directory,"tokens.txt").path,dataDir = File(directory,"espeak-ng-data").path),numThreads = 2)))
            .also { if(it.numSpeakers() != 8) { it.free(); error("Incompatible Kitten speaker table") } }
    },OfflineTts::free)
    @Volatile private var closed = false
    override val sampleRate: Int get() = 24000
    override fun isReady() = !closed && runBlocking { native.use { it.isReady } }
    override suspend fun synthesize(sentence: String,rate: Float,pitch: Float): ShortArray? = synthesizeFloat(sentence,rate,pitch)?.pcm16()
    override suspend fun synthesizeFloat(sentence: String,rate: Float,pitch: Float): FloatPcmAudio? = withContext(Dispatchers.Default) {
        val clean = NeuralSpeechText.prepare(sentence)
        if(clean.isBlank() || closed) return@withContext null
        val generated = native.use { it.generate(clean,sid,SpeechDelivery.rate(rate)) }
        ensureActive()
        if(closed || generated.samples.isEmpty()) return@withContext null
        FloatPcmAudio(PcmPitchShift.apply(generated.samples,generated.sampleRate,pitch),generated.sampleRate)
    }
    override fun setPlaybackActive(active: Boolean) = native.setPlaybackActive(active)
    override fun shutdown() { closed = true; native.close() }
    override suspend fun awaitShutdown() = native.awaitClosed()
}

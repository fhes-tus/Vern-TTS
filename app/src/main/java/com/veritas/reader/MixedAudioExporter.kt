package com.veritas.reader

import android.content.Context
import android.speech.tts.TextToSpeech
import com.veritas.reader.tts.KokoroTtsEngine
import com.veritas.reader.tts.PiperEngine
import com.veritas.reader.tts.TtsEngine
import com.veritas.reader.tts.VoiceModelManager
import kotlinx.coroutines.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.coroutines.coroutineContext

/** Export owns independent engines and a single partial file; live voice settings stay unchanged. */
internal class MixedAudioExporter(private val context: Context,
    private val createNeural: ((String) -> TtsEngine)? = null,
    private val renderAndroid: (suspend (SpeechSegment, File) -> Unit)? = null
) {
    suspend fun export(title: String, segments: List<SpeechSegment>): AudioExportManager.ExportResult = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "VernExports").apply { mkdirs() }
        val name = title.replace(Regex("[^\\p{L}\\p{N} _.-]"), " ").trim().take(50).ifBlank { "vern_audio" } + "_${UUID.randomUUID()}.wav"
        val final = File(directory, name); val partial = File(directory, ".$name.partial")
        val part = File(directory, ".$name.part.wav")
        val manager = AudioExportManager(context)
        var neural: TtsEngine? = null; var neuralVoice = ""
        var neuralShaping: Boolean? = null
        var graphPool: com.veritas.reader.tts.KokoroGraphPool? = null
        var android: TextToSpeech? = null; var androidPackage: String? = null
        var parts = 0
        try {
            PcmWavWriter(partial, 24000).use { writer ->
                for (requested in segments) {
                    val segment = requested.copy(rate = SpeechDelivery.rate(requested.rate), pitch = SpeechDelivery.pitch(requested.pitch))
                    coroutineContext.ensureActive()
                    if (segment.spoken.text.isBlank()) continue
                    if (segment.leadingPauseMs > 0) writer.append(ShortArray((segment.leadingPauseMs.coerceIn(0, 10000) * 24).toInt())) { coroutineContext.ensureActive() }
                    if (segment.family == SpeechEngineFamily.ANDROID) {
                        neural?.shutdown(); neural = null; neuralVoice = ""
                        graphPool?.close(); graphPool = null
                        if (renderAndroid != null) renderAndroid.invoke(segment, part)
                        else {
                            if (android == null || androidPackage != segment.voice.enginePackage) {
                                android?.stop(); android?.shutdown(); android = null
                                android = manager.createReadyTts(segment.voice); androidPackage = segment.voice.enginePackage
                            }
                            val engine = requireNotNull(android)
                            require(segment.voice.voiceName.isBlank() || engine.voices.orEmpty().any { it.name == segment.voice.voiceName }) { "${segment.voice.displayName} is unavailable. Choose another cast voice." }
                            val message = VoiceConfigurator.apply(engine, segment.voice)
                            require(!message.startsWith("Voice data is missing") && !message.contains("not supported")) { message }
                            require(VoiceConfigurator.applyDelivery(engine, segment.rate, segment.pitch)) {
                                "The speech engine could not apply the selected speed or pitch."
                            }
                            withTimeout((30000L + segment.spoken.text.length * 200L).coerceAtMost(600000L)) {
                                manager.synthesizePart(engine, segment.spoken.text, part, "export:${UUID.randomUUID()}")
                            }
                        }
                        coroutineContext.ensureActive()
                        WavAudioNormalizer.append(part, writer, 24000) { coroutineContext.ensureActive() }
                        part.delete()
                    } else {
                        android?.stop(); android?.shutdown(); android = null
                        if (neuralVoice != segment.voice.voiceName || neuralShaping != segment.voice.voiceShaping) {
                            neural?.shutdown(); neural = null
                            val voice = segment.voice.voiceName
                            neural = if (createNeural != null) createNeural.invoke(voice) else {
                                require(VoiceModelManager.isVoiceInstalled(context, voice)) { "Download $voice before exporting." }
                                if (segment.family == SpeechEngineFamily.PIPER) {
                                    graphPool?.close(); graphPool = null; PiperEngine(context, voice)
                                } else (graphPool ?: com.veritas.reader.tts.KokoroGraphPool(context).also { graphPool = it })
                                    .voice(voice, segment.voice.voiceShaping)
                            }
                            neuralVoice = voice; neuralShaping = segment.voice.voiceShaping
                        }
                        val engine = requireNotNull(neural)
                        require(engine.isReady()) { "${segment.voice.displayName} could not initialize." }
                        val audio = engine.synthesizeFloat(segment.spoken.text, segment.rate, segment.pitch)
                        coroutineContext.ensureActive()
                        require(audio != null && audio.samples.isNotEmpty()) { "${segment.voice.displayName} produced no audio for part ${parts + 1}." }
                        val converted = com.veritas.reader.tts.BandLimitedResampler.convert(audio.samples, audio.sampleRate / 24000.0)
                        writer.append(com.veritas.reader.tts.FloatPcmAudio(converted, 24000).pcm16()) { coroutineContext.ensureActive() }
                    }
                    parts++
                }
            }
            require(parts > 0) { "No speech was generated." }
            coroutineContext.ensureActive()
            Files.move(partial.toPath(), final.toPath(), StandardCopyOption.ATOMIC_MOVE)
            AudioExportManager.ExportResult(final, name, parts)
        } catch (error: Throwable) { final.delete(); throw error }
        finally { partial.delete(); part.delete(); neural?.shutdown(); graphPool?.close(); android?.stop(); android?.shutdown() }
    }
}

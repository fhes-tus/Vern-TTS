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
    private val createNeural: (String) -> TtsEngine = { voice ->
        require(VoiceModelManager.isVoiceInstalled(context, voice)) { "Download $voice in Voice Studio before exporting." }
        if (VoiceManager.engineForVoice(voice) == VoiceManager.VERITAS_LITE) PiperEngine(context, voice) else KokoroTtsEngine(context, voice)
    },
    private val renderAndroid: (suspend (SpeechSegment, File) -> Unit)? = null
) {
    suspend fun export(title: String, segments: List<SpeechSegment>): AudioExportManager.ExportResult = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "VernExports").apply { mkdirs() }
        val name = title.replace(Regex("[^\\p{L}\\p{N} _.-]"), " ").trim().take(50).ifBlank { "vern_audio" } + "_${UUID.randomUUID()}.wav"
        val final = File(directory, name); val partial = File(directory, ".$name.partial")
        val part = File(directory, ".$name.part.wav")
        val manager = AudioExportManager(context)
        var neural: TtsEngine? = null; var neuralVoice = ""
        var android: TextToSpeech? = null; var androidPackage: String? = null
        var parts = 0
        try {
            PcmWavWriter(partial, 24000).use { writer ->
                for (segment in segments) {
                    coroutineContext.ensureActive()
                    if (segment.spoken.text.isBlank()) continue
                    if (segment.leadingPauseMs > 0) writer.append(ShortArray((segment.leadingPauseMs.coerceIn(0, 10000) * 24).toInt())) { coroutineContext.ensureActive() }
                    if (segment.family == SpeechEngineFamily.ANDROID) {
                        neural?.shutdown(); neural = null; neuralVoice = ""
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
                            engine.setSpeechRate(segment.rate); engine.setPitch(segment.pitch)
                            withTimeout((30000L + segment.spoken.text.length * 200L).coerceAtMost(600000L)) {
                                manager.synthesizePart(engine, segment.spoken.text, part, "export:${UUID.randomUUID()}")
                            }
                        }
                        coroutineContext.ensureActive()
                        WavAudioNormalizer.append(part, writer, 24000) { coroutineContext.ensureActive() }
                        part.delete()
                    } else {
                        android?.stop(); android?.shutdown(); android = null
                        if (neuralVoice != segment.voice.voiceName) {
                            neural?.shutdown(); neural = null
                            neural = createNeural(segment.voice.voiceName); neuralVoice = segment.voice.voiceName
                        }
                        val engine = requireNotNull(neural)
                        require(engine.isReady()) { "${segment.voice.displayName} could not initialize." }
                        val pcm = engine.synthesize(segment.spoken.text, segment.rate, segment.pitch)
                        coroutineContext.ensureActive()
                        require(pcm != null && pcm.isNotEmpty()) { "${segment.voice.displayName} produced no audio for part ${parts + 1}." }
                        writer.append(WavAudioNormalizer.resample(pcm, engine.sampleRate, 24000)) { coroutineContext.ensureActive() }
                    }
                    parts++
                }
            }
            require(parts > 0) { "No speech was generated." }
            coroutineContext.ensureActive()
            Files.move(partial.toPath(), final.toPath(), StandardCopyOption.ATOMIC_MOVE)
            AudioExportManager.ExportResult(final, name, parts)
        } catch (error: Throwable) { final.delete(); throw error }
        finally { partial.delete(); part.delete(); neural?.shutdown(); android?.stop(); android?.shutdown() }
    }
}

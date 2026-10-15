package com.veritas.reader

import android.content.Context
import com.veritas.reader.tts.KokoroTtsEngine
import com.veritas.reader.tts.PiperEngine
import com.veritas.reader.tts.TtsEngine
import com.veritas.reader.tts.VoiceModelManager
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/** One native model and one sentence of PCM in memory, with atomic WAV publication. */
internal class NeuralAudioExporter(
    private val context: Context,
    private val createEngine: (String) -> TtsEngine = { voice ->
        require(VoiceModelManager.isVoiceInstalled(context, voice)) { "Download the selected voice before exporting." }
        if (VoiceManager.engineForVoice(voice) == VoiceManager.VERITAS_LITE) PiperEngine(context, voice) else KokoroTtsEngine(context, voice)
    }
) {
    suspend fun export(title: String, chunks: List<String>, voice: VoiceSettings, narration: NarrationSettings, rate: Float, pitch: Float): AudioExportManager.ExportResult = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "VernExports").apply { mkdirs() }
        val name = title.replace(Regex("[^\\p{L}\\p{N} _.-]"), " ").trim().take(50).ifBlank { "vern_audio" } + "_${UUID.randomUUID()}.wav"
        val destination = File(directory, name)
        val staging = File(directory, ".${name}.partial")
        var engine: TtsEngine? = null
        var engineVoice = ""
        var parts = 0
        fun engineFor(id: String): TtsEngine {
            require(VoiceManager.isVeritasEngine(VoiceManager.engineForVoice(id))) { "Mixed Android and neural character voices cannot yet be exported together. Choose neural character voices or turn off full cast." }
            if (engineVoice != id) {
                engine?.shutdown(); engine = null
                engine = createEngine(id)
                engineVoice = id
            }
            return requireNotNull(engine).also { require(it.isReady()) { "The selected neural voice could not initialize." } }
        }
        try {
            val outputRate = engineFor(voice.voiceName).sampleRate
            PcmWavWriter(staging, outputRate).use { writer ->
                for (chunk in chunks) {
                    // Bound native synthesis and PCM memory even for one huge paragraph.
                    for (text in split(chunk, 800)) {
                        coroutineContext.ensureActive()
                        val character = NarrationAnalyzer.getActiveCharacter(text, narration)
                        val selected = if (narration.enabled && narration.fullCastEnabled) character.voiceName?.takeIf(String::isNotBlank) ?: voice.voiceName else voice.voiceName
                        val current = engineFor(selected)
                        val samples = current.synthesize(text,
                            NarrationAnalyzer.effectiveRate(rate, narration, text, false),
                            NarrationAnalyzer.effectivePitch(pitch, narration, text, false))
                        coroutineContext.ensureActive()
                        require(samples != null && samples.isNotEmpty()) { "The neural voice produced no audio for part ${parts + 1}." }
                        writer.append(resample(samples, current.sampleRate, outputRate)) { coroutineContext.ensureActive() }
                        parts++
                    }
                }
            }
            require(parts > 0) { "No speech was generated." }
            coroutineContext.ensureActive()
            java.nio.file.Files.move(staging.toPath(), destination.toPath())
            AudioExportManager.ExportResult(destination, name, parts)
        } catch (error: Throwable) {
            destination.delete()
            throw error
        } finally {
            staging.delete()
            engine?.shutdown()
        }
    }

    private fun split(text: String, limit: Int): List<String> {
        val parts = mutableListOf<String>(); var rest = text.trim()
        while (rest.length > limit) {
            val cut = rest.lastIndexOf(' ', limit).takeIf { it > limit / 2 } ?: limit
            parts.add(rest.take(cut)); rest = rest.drop(cut).trimStart()
        }
        if (rest.isNotBlank()) parts.add(rest)
        return parts
    }

    private fun resample(samples: ShortArray, from: Int, to: Int): ShortArray {
        if (from == to) return samples
        require(from > 0 && to > 0)
        return ShortArray((samples.size.toDouble() * to / from).roundToInt().coerceAtLeast(1)) { i ->
            val at = i.toDouble() * from / to
            val index = at.toInt().coerceAtMost(samples.lastIndex)
            (samples[index] + (samples[minOf(index + 1, samples.lastIndex)] - samples[index]) * (at - index)).roundToInt().toShort()
        }
    }
}

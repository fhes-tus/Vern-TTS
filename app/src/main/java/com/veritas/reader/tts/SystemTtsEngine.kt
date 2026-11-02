package com.veritas.reader.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import com.veritas.reader.VoiceConfigurator
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Adapter wrapping Android's system TextToSpeech engine behind the unified TtsEngine interface.
 */
class SystemTtsEngine(
    private val context: Context,
    private val voiceName: String? = null,
    private val enginePackage: String? = null
) : TtsEngine {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val initDeferred = CompletableDeferred<Boolean>()

    override val sampleRate: Int = 22050

    init {
        val listener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.let { engine ->
                    if (!voiceName.isNull_or_blank()) {
                        engine.voices?.firstOrNull { it.name == voiceName }?.let { v ->
                            engine.voice = v
                        }
                    }
                }
                isInitialized = true
                initDeferred.complete(true)
            } else {
                isInitialized = false
                initDeferred.complete(false)
            }
        }

        tts = if (!enginePackage.isNull_or_blank()) {
            TextToSpeech(context.applicationContext, listener, enginePackage)
        } else {
            TextToSpeech(context.applicationContext, listener)
        }
    }

    override fun isReady(): Boolean = isInitialized

    override suspend fun synthesize(sentence: String, rate: Float, pitch: Float): ShortArray? = withContext(Dispatchers.IO) {
        if (!initDeferred.await()) return@withContext null
        if (tts?.let { VoiceConfigurator.applyDelivery(it, rate, pitch) } != true) return@withContext null

        val tempWav = File(context.cacheDir, "sys_tts_${System.currentTimeMillis()}.wav")
        val synthDeferred = CompletableDeferred<Boolean>()
        val utteranceId = "sys_synth_${System.currentTimeMillis()}"

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (id == utteranceId) synthDeferred.complete(true)
            }
            override fun onError(id: String?) {
                if (id == utteranceId) synthDeferred.complete(false)
            }
        })

        val params = Bundle()
        val result = tts?.synthesizeToFile(sentence, params, tempWav, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            tempWav.delete()
            return@withContext null
        }

        val success = synthDeferred.await()
        if (!success || !tempWav.exists()) {
            tempWav.delete()
            return@withContext null
        }

        try {
            val pcmBytes = readWavPcmData(tempWav)
            tempWav.delete()
            if (pcmBytes == null) return@withContext null

            val shorts = ShortArray(pcmBytes.size / 2)
            ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
            shorts
        } catch (e: Exception) {
            Log.e("SystemTtsEngine", "Error reading WAV file", e)
            tempWav.delete()
            null
        }
    }

    override fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e("SystemTtsEngine", "Error shutting down System TTS", e)
        }
        isInitialized = false
    }

    private fun readWavPcmData(wavFile: File): ByteArray? {
        val fileBytes = FileInputStream(wavFile).use { it.readBytes() }
        if (fileBytes.size <= 44) return null
        // Skip 44-byte WAV header
        return fileBytes.copyOfRange(44, fileBytes.size)
    }

    private fun String?.isNull_or_blank(): Boolean = this == null || this.isBlank()
}

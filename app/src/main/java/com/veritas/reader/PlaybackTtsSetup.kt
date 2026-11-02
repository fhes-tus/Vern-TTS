package com.veritas.reader

import android.speech.tts.TextToSpeech
import com.veritas.reader.tts.VoiceModelManager
import kotlinx.coroutines.launch

internal fun PlaybackService.ensureTtsReadyAndSpeak() = speakCurrent()
internal fun PlaybackService.ensureTtsReadyAndSpeakSelection(text: String) = speakSelectionText(text)

/** One live output owner; cast changes happen only before the next source segment. */
internal fun PlaybackService.prepareSpeechEngine(settings: VoiceSettings, selection: String? = null): Boolean {
    val requested = settings.enginePackage.ifBlank { null }
    val neural = VoiceManager.isVeritasEngine(requested)
    if (neural && !VoiceModelManager.isVoiceInstalled(applicationContext, settings.voiceName)) {
        handleTtsFailure("${settings.displayName} is not downloaded. Open Voice Studio to download it or choose another voice.")
        return false
    }
    pendingSpeak = selection == null
    pendingSelectionText = selection
    if (activeEnginePackage != requested || (neural && veritasAudioBuffer != null &&
            (veritasAudioVoiceId != settings.voiceName || veritasVoiceShaping != settings.voiceShaping))) {
        ++engineSetupGeneration
        clearQueuedChunk()
        retireUtterance(activeChunkUtteranceId)
        activeChunkUtteranceId = null
        tts?.stop(); tts?.shutdown(); tts = null; ttsReady = false
        releaseVoiceShaping()
        veritasAudioBuffer?.let { it.shutdown(); retiringAudioBuffer = it }
        veritasAudioBuffer = null; veritasAudioVoiceId = null
        if (requested != VoiceManager.VERITAS_STUDIO && requested != "com.veritas.voice") {
            kokoroGraphPool?.let { retiringKokoroGraphJob = it.closeAsync() }
            kokoroGraphPool = null
        }
        activeTtsRate = Float.NaN; activeTtsPitch = Float.NaN
        activeEnginePackage = requested
    }
    val old = retiringAudioBuffer
    val retiringGraph = retiringKokoroGraphJob
    if (old != null || retiringGraph != null) {
        val generation = ++engineSetupGeneration
        serviceScope.launch {
            old?.awaitShutdown()
            retiringGraph?.join()
            if (generation != engineSetupGeneration) return@launch
            retiringAudioBuffer = null
            retiringKokoroGraphJob = null
            if (pendingSpeak && PlaybackStateStore.isPlaying) speakCurrent()
            else pendingSelectionText?.let(::speakSelectionText)
        }
        return false
    }
    if (neural) {
        ttsReady = true; pendingSpeak = false; pendingSelectionText = null
        return true
    }
    if (ttsReady && tts != null) {
        if (settings.voiceShaping) attachVoiceShaping() else releaseVoiceShaping()
        val engine = requireNotNull(tts)
        if (settings.voiceName.isNotBlank() && engine.voices.orEmpty().none { it.name == settings.voiceName }) {
            handleTtsFailure("${settings.displayName} is unavailable in this engine. Choose a voice in Voice Studio.")
            return false
        }
        val message = VoiceConfigurator.apply(engine, settings)
        if (message.startsWith("Voice data is missing") || message.contains("not supported")) {
            handleTtsFailure(message); return false
        }
        pendingSpeak = false; pendingSelectionText = null
        return true
    }
    if (tts == null) {
        val generation = ++engineSetupGeneration
        val callback = TextToSpeech.OnInitListener { status ->
            // Post also covers engines that invoke OnInit before the constructor returns.
            mainHandler.post { if (generation == engineSetupGeneration) handleTtsInit(status) }
        }
        tts = if (requested == null) TextToSpeech(applicationContext, callback)
            else TextToSpeech(applicationContext, callback, requested)
    }
    return false
}

internal fun PlaybackService.handleTtsInit(status: Int) {
    if (status != TextToSpeech.SUCCESS) {
        ++engineSetupGeneration
        tts?.shutdown(); tts = null; ttsReady = false
        releaseVoiceShaping()
        handleTtsFailure("The selected voice engine could not initialize. Open Voice Studio to choose another engine.")
        return
    }
    ttsReady = true
    attachListener()
    if (pendingSpeak && PlaybackStateStore.isPlaying) speakCurrent()
    else pendingSelectionText?.let(::speakSelectionText)
}

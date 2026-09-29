package com.veritas.reader

import android.speech.tts.TextToSpeech
import android.util.Log
import com.veritas.reader.PlaybackService.Companion.TAG
import com.veritas.reader.tts.OfflineEngineType
import com.veritas.reader.tts.VoiceModelManager

internal fun PlaybackService.ensureTtsReadyAndSpeak() {
    val voiceSettings = repository.loadVoiceSettings()
    var requestedEngine = voiceSettings.enginePackage.ifBlank { null }
    val detectedVoiceEngine = VoiceManager.engineForVoice(voiceSettings.voiceName)
    if (requestedEngine == null || !VoiceManager.isVeritasEngine(requestedEngine)) {
        if (VoiceManager.isVeritasEngine(detectedVoiceEngine)) {
            requestedEngine = detectedVoiceEngine
        }
    }

    if (VoiceManager.isVeritasEngine(requestedEngine)) {
        var voiceToUse = voiceSettings.voiceName
        var installed = VoiceModelManager.isVoiceInstalled(
            applicationContext,
            voiceToUse
        )
        if (!installed) {
            val engineType = if (requestedEngine == VoiceManager.VERITAS_LITE) {
                OfflineEngineType.PIPER
            } else {
                OfflineEngineType.KOKORO
            }
            var fallbackVoice = VoiceModelManager.availableVoices
                .firstOrNull { it.engineType == engineType && VoiceModelManager.isVoiceInstalled(applicationContext, it.id) }
            if (fallbackVoice == null) {
                fallbackVoice = VoiceModelManager.availableVoices
                    .firstOrNull { VoiceModelManager.isVoiceInstalled(applicationContext, it.id) }
            }

            if (fallbackVoice != null) {
                val resolvedEngine = if (fallbackVoice.engineType == OfflineEngineType.PIPER) {
                    VoiceManager.VERITAS_LITE
                } else {
                    VoiceManager.VERITAS_STUDIO
                }
                val updatedSettings = voiceSettings.copy(
                    voiceName = fallbackVoice.id,
                    voiceLabel = fallbackVoice.name,
                    localeTag = fallbackVoice.localeTag,
                    enginePackage = resolvedEngine,
                    engineLabel = if (resolvedEngine == VoiceManager.VERITAS_LITE) "Vern Lite" else "Vern Studio"
                )
                repository.saveVoiceSettings(updatedSettings)
                PlaybackStateStore.statusMessage = "Switched to installed voice: ${fallbackVoice.name}"
                requestedEngine = resolvedEngine
                voiceToUse = fallbackVoice.id
                installed = true
            } else {
                Log.w(TAG, "No Vern offline voices are downloaded. Temporarily falling back to system TTS.")
                PlaybackStateStore.statusMessage = "Voice not downloaded. Falling back to system voice."
                runCatching { veritasAudioBuffer?.flush() }
                runCatching { veritasAudioBuffer?.shutdown() }
                veritasAudioBuffer = null
                activeEnginePackage = null
                ttsReady = false
                pendingSpeak = true
                tts = TextToSpeech(applicationContext) { status -> handleTtsInit(status) }
                return
            }
        }
        if (activeEnginePackage != requestedEngine) {
            runCatching { veritasAudioBuffer?.flush() }
            runCatching { veritasAudioBuffer?.shutdown() }
            veritasAudioBuffer = null
            runCatching { tts?.stop() }
            runCatching { tts?.shutdown() }
            tts = null
            ttsReady = false
        }
        activeEnginePackage = requestedEngine
        ttsReady = true
        speakCurrent()
        return
    }
    runCatching { veritasAudioBuffer?.flush() }
    runCatching { veritasAudioBuffer?.shutdown() }
    veritasAudioBuffer = null

    if (tts != null && activeEnginePackage != requestedEngine) {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        ttsReady = false
        activeTtsRate = Float.NaN
        activeTtsPitch = Float.NaN
    }

    if (ttsReady && tts != null) {
        tts?.let { VoiceConfigurator.apply(it, voiceSettings) }
        speakCurrent()
        return
    }

    pendingSpeak = true
    if (tts == null) {
        activeEnginePackage = requestedEngine
        activeTtsRate = Float.NaN
        activeTtsPitch = Float.NaN
        tts = if (requestedEngine == null) {
            TextToSpeech(applicationContext) { status -> handleTtsInit(status) }
        } else {
            TextToSpeech(applicationContext, { status -> handleTtsInit(status) }, requestedEngine)
        }
    }
}

internal fun PlaybackService.ensureTtsReadyAndSpeakSelection(text: String) {
    val voiceSettings = repository.loadVoiceSettings()
    var requestedEngine = voiceSettings.enginePackage.ifBlank { null }
    val detectedVoiceEngine = VoiceManager.engineForVoice(voiceSettings.voiceName)
    if (requestedEngine == null || !VoiceManager.isVeritasEngine(requestedEngine)) {
        if (VoiceManager.isVeritasEngine(detectedVoiceEngine)) {
            requestedEngine = detectedVoiceEngine
        }
    }

    if (VoiceManager.isVeritasEngine(requestedEngine)) {
        var voiceToUse = voiceSettings.voiceName
        var installed = VoiceModelManager.isVoiceInstalled(
            applicationContext,
            voiceToUse
        )
        if (!installed) {
            val engineType = if (requestedEngine == VoiceManager.VERITAS_LITE) {
                OfflineEngineType.PIPER
            } else {
                OfflineEngineType.KOKORO
            }
            var fallbackVoice = VoiceModelManager.availableVoices
                .firstOrNull { it.engineType == engineType && VoiceModelManager.isVoiceInstalled(applicationContext, it.id) }
            if (fallbackVoice == null) {
                fallbackVoice = VoiceModelManager.availableVoices
                    .firstOrNull { VoiceModelManager.isVoiceInstalled(applicationContext, it.id) }
            }

            if (fallbackVoice != null) {
                val resolvedEngine = if (fallbackVoice.engineType == OfflineEngineType.PIPER) {
                    VoiceManager.VERITAS_LITE
                } else {
                    VoiceManager.VERITAS_STUDIO
                }
                val updatedSettings = voiceSettings.copy(
                    voiceName = fallbackVoice.id,
                    voiceLabel = fallbackVoice.name,
                    localeTag = fallbackVoice.localeTag,
                    enginePackage = resolvedEngine,
                    engineLabel = if (resolvedEngine == VoiceManager.VERITAS_LITE) "Vern Lite" else "Vern Studio"
                )
                repository.saveVoiceSettings(updatedSettings)
                PlaybackStateStore.statusMessage = "Switched to installed voice: ${fallbackVoice.name}"
                requestedEngine = resolvedEngine
                voiceToUse = fallbackVoice.id
                installed = true
            } else {
                Log.w(TAG, "No Vern offline voices are downloaded. Temporarily falling back to system TTS.")
                PlaybackStateStore.statusMessage = "Voice not downloaded. Falling back to system voice."
                runCatching { veritasAudioBuffer?.flush() }
                runCatching { veritasAudioBuffer?.shutdown() }
                veritasAudioBuffer = null
                activeEnginePackage = null
                ttsReady = false
                pendingSpeak = false
                pendingSelectionText = text
                tts = TextToSpeech(applicationContext) { status -> handleTtsInit(status) }
                return
            }
        }
        if (activeEnginePackage != requestedEngine) {
            runCatching { veritasAudioBuffer?.flush() }
            runCatching { veritasAudioBuffer?.shutdown() }
            veritasAudioBuffer = null
            runCatching { tts?.stop() }
            runCatching { tts?.shutdown() }
            tts = null
            ttsReady = false
        }
        activeEnginePackage = requestedEngine
        ttsReady = true
        speakSelectionText(text)
        return
    }
    runCatching { veritasAudioBuffer?.flush() }
    runCatching { veritasAudioBuffer?.shutdown() }
    veritasAudioBuffer = null

    if (tts != null && activeEnginePackage != requestedEngine) {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        ttsReady = false
        activeTtsRate = Float.NaN
        activeTtsPitch = Float.NaN
    }

    if (ttsReady && tts != null) {
        tts?.let { VoiceConfigurator.apply(it, voiceSettings) }
        speakSelectionText(text)
        return
    }

    pendingSpeak = false
    pendingSelectionText = text
    if (tts == null) {
        activeEnginePackage = requestedEngine
        activeTtsRate = Float.NaN
        activeTtsPitch = Float.NaN
        tts = if (requestedEngine == null) {
            TextToSpeech(applicationContext) { status -> handleTtsInit(status) }
        } else {
            TextToSpeech(applicationContext, { status -> handleTtsInit(status) }, requestedEngine)
        }
    }
}

internal fun PlaybackService.handleTtsInit(status: Int) {
    if (status == TextToSpeech.SUCCESS) {
        ttsReady = true
        attachListener()
        val voiceSettings = repository.loadVoiceSettings()
        tts?.let { VoiceConfigurator.apply(it, voiceSettings) }
        if (pendingSpeak) {
            pendingSpeak = false
            speakCurrent()
        } else {
            val selection = pendingSelectionText
            if (!selection.isNullOrBlank()) {
                pendingSelectionText = null
                speakSelectionText(selection)
            }
        }
    } else {
        ttsReady = false
        PlaybackStateStore.isPlaying = false
        PlaybackStateStore.statusMessage = "TTS engine failed to initialize."
        updateMediaSessionState()
        refreshForegroundNotification()
    }
}

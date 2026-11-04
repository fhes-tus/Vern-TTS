package com.veritas.reader

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import com.veritas.reader.PlaybackService.Companion.RESUME_SKIP_CHARS
import com.veritas.reader.PlaybackService.Companion.RESUME_WORD_THRESHOLD
import com.veritas.reader.tts.KokoroTtsEngine
import com.veritas.reader.tts.PiperEngine
import com.veritas.reader.tts.VeritasAudioBuffer
import com.veritas.reader.tts.VoiceModelManager

internal fun PlaybackService.veritasBufferFor(voiceSettings: VoiceSettings): VeritasAudioBuffer {
    val voiceName = voiceSettings.voiceName
    require(VoiceModelManager.isVoiceInstalled(applicationContext, voiceName)) { "Download the selected voice in Voice Studio." }
    // A change from Piper to Kokoro (or to a different local model) must rebuild the
    // native engine. The service otherwise keeps using the model selected before restart.
    if (veritasAudioBuffer != null && (veritasAudioVoiceId != voiceName || veritasVoiceShaping != voiceSettings.voiceShaping)) {
        veritasAudioBuffer?.shutdown()
        veritasAudioBuffer = null
    }
    return veritasAudioBuffer ?: run {
        val isPiper = voiceName.startsWith("piper_", ignoreCase = true) ||
            VoiceManager.engineForVoice(voiceName) == VoiceManager.VERITAS_LITE
        val isKitten = voiceName.startsWith("kitten_")
        val graphPool = if (isPiper || isKitten) null else kokoroGraphPool ?: com.veritas.reader.tts.KokoroGraphPool(applicationContext, com.veritas.reader.tts.SpeechModelRole.LIVE).also {
            kokoroGraphPool = it
        }
        val engine = com.veritas.reader.tts.DeferredTtsEngine {
            if (isPiper) PiperEngine(applicationContext, voiceName, com.veritas.reader.tts.SpeechModelRole.LIVE)
            else if(isKitten) com.veritas.reader.tts.KittenEngine(applicationContext,voiceName,com.veritas.reader.tts.SpeechModelRole.LIVE)
            else requireNotNull(graphPool).voice(voiceName, voiceSettings.voiceShaping)
        }
        val output = veritasAudioOutput ?: com.veritas.reader.tts.PcmAudioOutput().also { veritasAudioOutput = it }
        VeritasAudioBuffer(applicationContext, engine, output = output).also {
            veritasAudioBuffer = it
            veritasAudioVoiceId = voiceName
            veritasVoiceShaping = voiceSettings.voiceShaping
        }
    }
}

internal fun PlaybackService.ttsParams(utteranceId: String?): Bundle {
    val params = Bundle()
    utteranceId?.let { params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, it) }
    params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, sleepFadeVolume)
    if (ttsSessionId > 0) {
        params.putInt(TextToSpeech.Engine.KEY_PARAM_SESSION_ID, ttsSessionId)
    }
    return params
}

/** Final service teardown; model switches retire their buffer while retaining this output. */
internal fun PlaybackService.releaseLocalSpeech() {
    veritasAudioBuffer?.shutdown()
    retiringAudioBuffer?.shutdown()
    veritasAudioBuffer = null
    retiringAudioBuffer = null
    veritasAudioVoiceId = null
    veritasVoiceShaping = null
    kokoroGraphPool?.let { retiringKokoroGraphJob = it.closeAsync() }
    kokoroGraphPool = null
    veritasAudioOutput?.close()
    veritasAudioOutput = null
}

internal fun PlaybackService.attachVoiceShaping() {
    if (!repository.loadVoiceSettings().voiceShaping) { releaseVoiceShaping(); return }
    if (ttsEqualizer != null) return
    runCatching {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        if (ttsSessionId <= 0) {
            ttsSessionId = audioManager.generateAudioSessionId()
        }
        ttsEqualizer = SpeechEqualizer.create(ttsSessionId)
    }.onFailure { Log.w(TAG_TTS, "Voice shaping unavailable", it) }
}

internal fun PlaybackService.releaseVoiceShaping() {
    val effect = ttsEqualizer
    ttsEqualizer = null
    ttsSessionId = android.media.AudioManager.ERROR
    runCatching { effect?.release() }.onFailure { Log.w(TAG_TTS, "Voice shaping release failed", it) }
}

internal fun PlaybackService.rememberPausePoint() {
    val docId = activeDocument?.id ?: return clearResumePoint()
    val index = PlaybackStateStore.currentIndex
    val text = activeChunkSpeechText
    val canResume = activeChunkIndex == index &&
        text.isNotBlank() &&
        spokenWordCount >= RESUME_WORD_THRESHOLD &&
        spokenCharOffset in 1 until text.length
    if (!canResume) {
        clearResumePoint()
        return
    }
    resumeDocumentId = docId
    resumeChunkIndex = index
    resumeCharOffset = normalizeResumeOffset(text, spokenCharOffset)
    resumeWordCount = spokenWordCount
    repository.savePersistedResumePoint(docId, index, resumeCharOffset, spokenWordCount)
}

internal fun PlaybackService.resumeOffsetForCurrentChunk(text: String, index: Int): Int {
    val docId = activeDocument?.id ?: return 0
    if (resumeDocumentId == null) {
        repository.loadPersistedResumePoint()?.let { persisted ->
            if (persisted.documentId == docId && persisted.chunkIndex == index && persisted.wordCount >= RESUME_WORD_THRESHOLD) {
                resumeDocumentId = persisted.documentId
                resumeChunkIndex = persisted.chunkIndex
                resumeCharOffset = persisted.charOffset
                resumeWordCount = persisted.wordCount
            }
        }
    }
    if (docId != resumeDocumentId || index != resumeChunkIndex || resumeWordCount < RESUME_WORD_THRESHOLD) return 0
    return normalizeResumeOffset(text, resumeCharOffset).takeIf { it in 1 until text.length } ?: 0
}

internal fun PlaybackService.normalizeResumeOffset(text: String, offset: Int): Int {
    var safeOffset = offset.coerceIn(0, text.length)
    // Skip whitespace and trailing punctuation so resume never starts on a
    // stray period/comma left over from the previous word boundary.
    while (safeOffset < text.length &&
        (text[safeOffset].isWhitespace() || text[safeOffset] in RESUME_SKIP_CHARS)
    ) safeOffset++
    return if (safeOffset >= text.length) 0 else safeOffset
}

internal fun PlaybackService.wordCountBefore(text: String, charOffset: Int): Int {
    val safeOffset = charOffset.coerceIn(0, text.length)
    if (safeOffset <= 0) return 0
    return Regex("\\S+").findAll(text.take(safeOffset)).count()
}

internal fun PlaybackService.updateCurrentSentenceBounds(charOffset: Int) {
    val text = activeChunkSpeechText
    if (text.isBlank()) {
        PlaybackStateStore.currentSentenceStart = 0
        PlaybackStateStore.currentSentenceEnd = 0
        return
    }
    // Playback chunks and the reader use the same sentence index. A second punctuation
    // scan here used to stop at Dr./initials and disagree with the on-page highlight.
    PlaybackStateStore.currentSentenceStart = 0
    PlaybackStateStore.currentSentenceEnd = text.length
}

internal fun PlaybackService.clearQueuedChunk() {
    queuedChunkUtteranceId = null
    queuedChunkIndex = -1
    queuedChunkSpeechText = ""
    queuedChunkBaseOffset = 0
    queuedSpokenText = null
}

internal fun PlaybackService.clearResumePoint() {
    cancelSpeechParts()
    PlaybackStateStore.speechPosition = null
    activeChunkUtteranceId = null
    activeChunkIndex = -1
    activeChunkSpeechText = ""
    activeChunkBaseOffset = 0
    activeSpokenText = null
    spokenCharOffset = 0
    spokenWordCount = 0
    resumeDocumentId = null
    resumeChunkIndex = -1
    resumeCharOffset = 0
    resumeWordCount = 0
    PlaybackStateStore.currentSentenceStart = 0
    PlaybackStateStore.currentSentenceEnd = 0
    PlaybackStateStore.activeTableColumnIndex = -1
    repository.clearPersistedResumePoint()
    clearQueuedChunk()
}

internal fun PlaybackService.leadingSilenceMsFor(index: Int): Long =
    maxOf(PlaybackService.leadingSilenceMs(chunkPageNumbers, index, chunks.getOrNull(index)), literaryTurnPauseMs(index))

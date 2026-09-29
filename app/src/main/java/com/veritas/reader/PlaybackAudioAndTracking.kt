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
    var voiceName = voiceSettings.voiceName
    if (!VoiceModelManager.isVoiceInstalled(applicationContext, voiceName)) {
        val installed = VoiceModelManager.getInstalledVoices(applicationContext).firstOrNull()
        if (installed != null) {
            voiceName = installed.id
        }
    }
    // A change from Piper to Kokoro (or to a different local model) must rebuild the
    // native engine. The service otherwise keeps using the model selected before restart.
    if (veritasAudioBuffer != null && veritasAudioVoiceId != voiceName) {
        veritasAudioBuffer?.shutdown()
        veritasAudioBuffer = null
    }
    return veritasAudioBuffer ?: run {
        val isPiper = voiceName.startsWith("piper_", ignoreCase = true) ||
            VoiceManager.engineForVoice(voiceName) == VoiceManager.VERITAS_LITE
        val engine = if (isPiper) {
            PiperEngine(applicationContext, voiceName)
        } else {
            KokoroTtsEngine(applicationContext, voiceName)
        }
        VeritasAudioBuffer(applicationContext, engine).also {
            veritasAudioBuffer = it
            veritasAudioVoiceId = voiceName
        }
    }
}

internal fun PlaybackService.ttsParams(utteranceId: String?): Bundle {
    val params = Bundle()
    utteranceId?.let { params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, it) }
    params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, sleepFadeVolume)
    if (ttsSessionId != android.media.AudioManager.ERROR) {
        params.putInt(TextToSpeech.Engine.KEY_PARAM_SESSION_ID, ttsSessionId)
    }
    return params
}

internal fun PlaybackService.attachVoiceShaping() {
    if (ttsEqualizer != null) return
    runCatching {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        if (ttsSessionId == android.media.AudioManager.ERROR) {
            ttsSessionId = audioManager.generateAudioSessionId()
        }
        val eq = android.media.audiofx.Equalizer(0, ttsSessionId)
        val minLevel = eq.bandLevelRange[0].toInt()
        val maxLevel = eq.bandLevelRange[1].toInt()
        for (band in 0 until eq.numberOfBands.toInt()) {
            val centreHz = eq.getCenterFreq(band.toShort()) / 1000
            val millibels = when {
                centreHz < 120 -> -100     // rumble the voice never uses
                centreHz < 500 -> 250      // chest warmth
                centreHz < 1500 -> 0       // leave the vowels alone
                centreHz < 5000 -> 300     // presence: consonant clarity
                else -> -150               // take the edge off sibilance
            }
            eq.setBandLevel(band.toShort(), millibels.coerceIn(minLevel, maxLevel).toShort())
        }
        eq.enabled = true
        ttsEqualizer = eq
        Log.i(TAG_TTS, "Voice shaping attached to session $ttsSessionId")
    }.onFailure { Log.w(TAG_TTS, "Voice shaping unavailable", it) }
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
    val safeOffset = charOffset.coerceIn(0, text.length)
    val sentenceStart = text
        .lastIndexOfAny(charArrayOf('.', '!', '?', '\n'), (safeOffset - 1).coerceAtLeast(0))
        .let { if (it < 0) 0 else (it + 1).coerceAtMost(text.length) }
        .let { start ->
            var adjusted = start
            while (adjusted < text.length && text[adjusted].isWhitespace()) adjusted++
            adjusted
        }
    val sentenceEnd = text
        .indexOfAny(charArrayOf('.', '!', '?', '\n'), safeOffset)
        .let { if (it < 0) text.length else (it + 1).coerceAtMost(text.length) }
    if (sentenceEnd > sentenceStart) {
        PlaybackStateStore.currentSentenceStart = sentenceStart
        PlaybackStateStore.currentSentenceEnd = sentenceEnd
    } else {
        PlaybackStateStore.currentSentenceStart = safeOffset
        PlaybackStateStore.currentSentenceEnd = safeOffset
    }
}

internal fun PlaybackService.clearQueuedChunk() {
    queuedChunkUtteranceId = null
    queuedChunkIndex = -1
    queuedChunkSpeechText = ""
    queuedChunkBaseOffset = 0
}

internal fun PlaybackService.clearResumePoint() {
    activeChunkUtteranceId = null
    activeChunkIndex = -1
    activeChunkSpeechText = ""
    activeChunkBaseOffset = 0
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
    PlaybackService.leadingSilenceMs(chunkPageNumbers, index, chunks.getOrNull(index))

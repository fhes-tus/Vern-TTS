package com.veritas.desktop.audio

import com.veritas.desktop.models.DesktopDocument
import com.veritas.desktop.models.PronunciationRule
import com.veritas.desktop.models.ReaderSettings
import com.veritas.desktop.models.VoiceProfile
import com.veritas.desktop.models.VoiceSettings
import com.veritas.desktop.storage.DesktopStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlaybackState(
    val activeDocument: DesktopDocument? = null,
    val currentIndex: Int = 0,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentSentenceText: String = "",
    val voiceSettings: VoiceSettings = VoiceSettings(),
    val readerSettings: ReaderSettings = ReaderSettings(),
    val availableVoices: List<VoiceProfile> = emptyList(),
    val pronunciationRules: List<PronunciationRule> = emptyList(),
    val sleepTimerMinutesRemaining: Int? = null,
    val errorMessage: String? = null
)

class DesktopPlaybackController(
    private val coroutineScope: CoroutineScope
) {
    private val speechEngine = WindowsSpeechEngine()

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var sleepTimerJob: Job? = null

    init {
        val (rSettings, vSettings) = DesktopStorage.loadSettings()
        val rules = DesktopStorage.loadPronunciationRules()
        val defaultVoices = speechEngine.getDefaultVoices()

        val selectedVoice = if (vSettings.voiceName.isNotBlank()) {
            vSettings.voiceName
        } else {
            defaultVoices.firstOrNull()?.name ?: ""
        }

        _state.value = _state.value.copy(
            readerSettings = rSettings,
            voiceSettings = vSettings.copy(voiceName = selectedVoice),
            availableVoices = defaultVoices,
            pronunciationRules = rules
        )

        // Asynchronously query all installed system voices in background
        coroutineScope.launch(Dispatchers.IO) {
            val installed = speechEngine.getInstalledVoicesAsync()
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(availableVoices = installed)
            }
        }

        speechEngine.onSentenceStarted = { idx ->
            _state.value = _state.value.copy(
                currentIndex = idx,
                isPlaying = true,
                isBuffering = false,
                currentSentenceText = _state.value.activeDocument?.chunks?.getOrNull(idx) ?: ""
            )
            updateDocumentProgress(idx)
        }

        speechEngine.onSentenceCompleted = { idx ->
            val doc = _state.value.activeDocument
            if (doc != null && _state.value.isPlaying && _state.value.voiceSettings.autoAdvance) {
                if (idx + 1 < doc.chunks.size) {
                    jumpToSentence(idx + 1, autoPlay = true)
                } else {
                    pause()
                }
            }
        }

        speechEngine.onSpeechError = { error ->
            _state.value = _state.value.copy(
                isPlaying = false,
                isBuffering = false,
                errorMessage = error
            )
        }
    }

    fun loadDocument(document: DesktopDocument, startIndex: Int = document.currentIndex) {
        val safeIndex = startIndex.coerceIn(0, (document.chunks.size - 1).coerceAtLeast(0))
        val currentText = document.chunks.getOrNull(safeIndex) ?: ""
        _state.value = _state.value.copy(
            activeDocument = document,
            currentIndex = safeIndex,
            currentSentenceText = currentText,
            errorMessage = null
        )
    }

    fun play() {
        val doc = _state.value.activeDocument ?: return
        if (doc.chunks.isEmpty()) return

        val index = _state.value.currentIndex.coerceIn(0, doc.chunks.size - 1)
        val text = doc.chunks[index]

        _state.value = _state.value.copy(
            isPlaying = true,
            isBuffering = false,
            currentIndex = index,
            currentSentenceText = text
        )

        speechEngine.speakSentence(
            index = index,
            rawText = text,
            settings = _state.value.voiceSettings,
            rules = _state.value.pronunciationRules
        )
    }

    fun pause() {
        speechEngine.stop()
        _state.value = _state.value.copy(
            isPlaying = false,
            isBuffering = false
        )
    }

    fun togglePlay() {
        if (_state.value.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun jumpToSentence(index: Int, autoPlay: Boolean = _state.value.isPlaying) {
        val doc = _state.value.activeDocument ?: return
        if (doc.chunks.isEmpty()) return
        val safeIndex = index.coerceIn(0, doc.chunks.size - 1)

        speechEngine.stop()
        _state.value = _state.value.copy(
            currentIndex = safeIndex,
            currentSentenceText = doc.chunks[safeIndex]
        )
        updateDocumentProgress(safeIndex)

        if (autoPlay) {
            play()
        } else {
            _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
        }
    }

    fun nextSentence() {
        val doc = _state.value.activeDocument ?: return
        val nextIdx = _state.value.currentIndex + 1
        if (nextIdx < doc.chunks.size) {
            jumpToSentence(nextIdx)
        }
    }

    fun previousSentence() {
        val prevIdx = _state.value.currentIndex - 1
        if (prevIdx >= 0) {
            jumpToSentence(prevIdx)
        }
    }

    fun setSpeed(rate: Float) {
        val safeRate = rate.coerceIn(0.5f, 2.5f)
        val newVoiceSettings = _state.value.voiceSettings.copy(rate = safeRate)
        _state.value = _state.value.copy(voiceSettings = newVoiceSettings)
        DesktopStorage.saveSettings(_state.value.readerSettings, newVoiceSettings)
        if (_state.value.isPlaying) {
            play()
        }
    }

    fun adjustSpeed(delta: Float) {
        setSpeed(_state.value.voiceSettings.rate + delta)
    }

    fun setVoice(voiceName: String) {
        val newVoiceSettings = _state.value.voiceSettings.copy(voiceName = voiceName)
        _state.value = _state.value.copy(voiceSettings = newVoiceSettings)
        DesktopStorage.saveSettings(_state.value.readerSettings, newVoiceSettings)
        if (_state.value.isPlaying) {
            play()
        }
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        if (minutes == null || minutes <= 0) {
            _state.value = _state.value.copy(sleepTimerMinutesRemaining = null)
            return
        }

        _state.value = _state.value.copy(sleepTimerMinutesRemaining = minutes)
        sleepTimerJob = coroutineScope.launch(Dispatchers.Default) {
            var remaining = minutes
            while (remaining > 0) {
                delay(60_000L)
                remaining--
                _state.value = _state.value.copy(sleepTimerMinutesRemaining = if (remaining > 0) remaining else null)
            }
            withContext(Dispatchers.Main) {
                pause()
            }
        }
    }

    fun updateReaderSettings(settings: ReaderSettings) {
        _state.value = _state.value.copy(readerSettings = settings)
        DesktopStorage.saveSettings(settings, _state.value.voiceSettings)
    }

    fun updatePronunciationRules(rules: List<PronunciationRule>) {
        _state.value = _state.value.copy(pronunciationRules = rules)
        DesktopStorage.savePronunciationRules(rules)
    }

    private fun updateDocumentProgress(index: Int) {
        val doc = _state.value.activeDocument ?: return
        val updated = doc.copy(currentIndex = index, lastReadAt = System.currentTimeMillis())
        _state.value = _state.value.copy(activeDocument = updated)

        // Save progress in background
        coroutineScope.launch(Dispatchers.IO) {
            val all = DesktopStorage.loadLibrary().toMutableList()
            val existingIdx = all.indexOfFirst { it.id == doc.id }
            if (existingIdx >= 0) {
                all[existingIdx] = updated
                DesktopStorage.saveLibrary(all)
            }
        }
    }
}

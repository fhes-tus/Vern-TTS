package com.veritas.reader.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.viewModelScope
import com.veritas.reader.AskAiSettings
import com.veritas.reader.DocumentRepository
import com.veritas.reader.NarrationSettings
import com.veritas.reader.PlaybackActions
import com.veritas.reader.PlaybackStateStore
import com.veritas.reader.PronunciationRule
import com.veritas.reader.ReaderSettings
import com.veritas.reader.TtsVoiceOption
import com.veritas.reader.VeritasThemeState
import com.veritas.reader.VoiceManager
import com.veritas.reader.VoiceSettings
import com.veritas.reader.addAiPromptHistory
import com.veritas.reader.addAiPromptTemplate
import com.veritas.reader.addPronunciationRule
import com.veritas.reader.clearAiPromptHistory
import com.veritas.reader.deleteAiPromptTemplate
import com.veritas.reader.loadPronunciationRules
import com.veritas.reader.removePronunciationRule
import com.veritas.reader.sendPlaybackIntent
import com.veritas.reader.togglePronunciationRule
import com.veritas.reader.updateVeritasWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

fun ReaderViewModel.saveReaderSettings(update: ReaderSettings) {
    val previous = uiState.value.readerSettings
    val revision = ++readerSettingsSaveRevision
    val normalized = update.copy(fontSizeSp = update.fontSizeSp.coerceIn(10, 28),
        sectionSpacingDp = update.sectionSpacingDp.coerceIn(6, 24),
        themeId = com.veritas.reader.VeritasThemeCatalog.normalizeThemeId(update.themeId),
        themePackId = com.veritas.reader.VeritasThemePackCatalog.normalizePackId(update.themePackId))
    _uiState.update { it.copy(readerSettings = normalized) }
    PlaybackStateStore.autoPlayQueue = normalized.autoPlayQueue
    viewModelScope.launch {
        try {
            val saved = withContext(Dispatchers.IO) {
                synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
                    if (revision != readerSettingsSaveRevision) return@synchronized null
                    repository.saveReaderSettings(normalized)
                }
            } ?: return@launch
            if (revision == readerSettingsSaveRevision) _uiState.update { it.copy(readerSettings = saved) }
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            if (revision == readerSettingsSaveRevision) {
                _uiState.update { it.copy(readerSettings = previous, importMessage = "Could not save display preferences. Please try again.") }
                PlaybackStateStore.autoPlayQueue = previous.autoPlayQueue
            }
        }
    }
}

fun ReaderViewModel.reloadReaderSettings() {
    val revision = readerSettingsSaveRevision
    viewModelScope.launch(Dispatchers.IO) {
        val loaded = repository.loadReaderSettings()
        withContext(Dispatchers.Main) {
            if (revision == readerSettingsSaveRevision) _uiState.update { it.copy(readerSettings = loaded) }
        }
    }
}

fun ReaderViewModel.saveNotesSettings(update: NotesSettings) {
    val previous = uiState.value.notesSettings
    val normalized = update.normalized()
    _uiState.update { it.copy(notesSettings = normalized) }
    viewModelScope.launch(Dispatchers.IO) {
        notesSettingsSaveMutex.withLock {
            if (uiState.value.notesSettings != normalized) return@withLock
            runCatching { NotesSettingsStore.save(getApplication(), normalized) }.onFailure {
                _uiState.update { state ->
                    if (state.notesSettings == normalized) state.copy(notesSettings = previous) else state
                }
                withContext(Dispatchers.Main) { android.widget.Toast.makeText(getApplication(), "Could not save Notes preferences. Please try again.", android.widget.Toast.LENGTH_LONG).show() }
            }
        }
    }
}

fun ReaderViewModel.refreshPronunciationRules() {
    viewModelScope.launch(Dispatchers.IO) {
        val rules = repository.loadPronunciationRules()
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(pronunciationRules = rules) }
        }
    }
}

fun ReaderViewModel.addPronunciationRule() {
    val find = uiState.value.newRuleFind.trim()
    val replaceWith = uiState.value.newRuleReplaceWith.trim()
    if (find.isBlank()) return
    viewModelScope.launch(Dispatchers.IO) {
        val rules = repository.addPronunciationRule(find, replaceWith)
        withContext(Dispatchers.Main) {
            _uiState.update {
                it.copy(
                    pronunciationRules = rules,
                    newRuleFind = if (it.newRuleFind.trim() == find && it.newRuleReplaceWith.trim() == replaceWith) "" else it.newRuleFind,
                    newRuleReplaceWith = if (it.newRuleFind.trim() == find && it.newRuleReplaceWith.trim() == replaceWith) "" else it.newRuleReplaceWith
                )
            }
            restartCurrentSectionIfPlaying()
        }
    }
}

fun ReaderViewModel.togglePronunciationRule(rule: PronunciationRule) {
    viewModelScope.launch(Dispatchers.IO) {
        val rules = repository.togglePronunciationRule(rule.id)
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(pronunciationRules = rules) }
            restartCurrentSectionIfPlaying()
        }
    }
}

fun ReaderViewModel.removePronunciationRule(rule: PronunciationRule) {
    viewModelScope.launch(Dispatchers.IO) {
        val rules = repository.removePronunciationRule(rule.id)
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(pronunciationRules = rules) }
            restartCurrentSectionIfPlaying()
        }
    }
}

fun ReaderViewModel.restartCurrentSectionIfPlaying() {
    val doc = uiState.value.activeDocument ?: return
    val docId = doc.id ?: return
    if (isReaderPlaying) {
        requestNotificationPermissionForPlayback()
        sendPlaybackIntent(
            context = getApplication(),
            action = PlaybackActions.ACTION_PLAY,
            documentId = docId,
            startIndex = currentReaderIndex
        )
    }
}

fun ReaderViewModel.saveVoiceSettings(update: VoiceSettings) {
    val revision = ++voiceSettingsSaveRevision
    voiceSettingsSaveJob?.cancel()
    PlaybackStateStore.pendingVoiceSettings = true
    // Show every gesture immediately; apply only the latest settled value to
    // speech, rather than flushing the sentence for each slider movement.
    _uiState.update { it.copy(voiceSettings = update) }
    PlaybackStateStore.rate = update.preferredRate
    PlaybackStateStore.pitch = update.preferredPitch
    voiceSettingsSaveJob = viewModelScope.launch {
        try {
            kotlinx.coroutines.delay(150)
            val docId = uiState.value.activeDocument?.id
            val saved = withContext(Dispatchers.IO) {
                synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
                    if (revision != voiceSettingsSaveRevision) return@synchronized repository.loadVoiceSettings()
                    val result = repository.saveVoiceSettings(update)
                    if (docId != null) repository.saveDocVoiceMemory(docId, result.preferredRate, result.preferredPitch)
                    result
                }
            }
            if (revision != voiceSettingsSaveRevision) return@launch
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(voiceSettings = saved) }
                PlaybackStateStore.rate = saved.preferredRate
                PlaybackStateStore.pitch = saved.preferredPitch
                PlaybackStateStore.statusMessage = "Voice updated: ${saved.displayName}."
                sendPlaybackIntent(
                    context = getApplication(),
                    action = PlaybackActions.ACTION_UPDATE_PLAYBACK_SETTINGS,
                    rate = saved.preferredRate,
                    pitch = saved.preferredPitch
                )
                completeQuestSpeed()
            }
        } finally {
            if (revision == voiceSettingsSaveRevision) PlaybackStateStore.pendingVoiceSettings = false
        }
    }
}

fun ReaderViewModel.saveNarrationSettings(update: NarrationSettings) {
    _uiState.update { it.copy(narrationSettings = update) }
    viewModelScope.launch(Dispatchers.IO) {
        val saved = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
            if (uiState.value.narrationSettings != update) return@synchronized null
            repository.saveNarrationSettings(update)
        } ?: return@launch
        withContext(Dispatchers.Main) {
            if (uiState.value.narrationSettings != update) return@withContext
            _uiState.update { it.copy(narrationSettings = saved) }
            PlaybackStateStore.statusMessage = if (saved.enabled) "Narration mode enabled." else "Narration mode disabled."
            restartCurrentSectionIfPlaying()
        }
    }
}

fun ReaderViewModel.saveAskAiSettings(update: AskAiSettings) {
    _uiState.update { it.copy(askAiSettings = update) }
    viewModelScope.launch(Dispatchers.IO) {
        val saved = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
            if (uiState.value.askAiSettings != update) return@synchronized null
            repository.saveAskAiSettings(update)
        } ?: return@launch
        withContext(Dispatchers.Main) {
            _uiState.update { 
                it.copy(
                    askAiSettings = if (it.askAiSettings == update) saved else it.askAiSettings,
                    importMessage = "Ask AI preference updated: ${saved.assistantLabel}."
                )
            }
        }
    }
}

fun ReaderViewModel.saveAiPromptTemplate(title: String, instruction: String) {
    viewModelScope.launch(Dispatchers.IO) {
        val templates = repository.addAiPromptTemplate(title, instruction)
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(aiPromptTemplates = templates) }
        }
    }
}

fun ReaderViewModel.deleteAiPromptTemplate(id: String) {
    viewModelScope.launch(Dispatchers.IO) {
        val templates = repository.deleteAiPromptTemplate(id)
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(aiPromptTemplates = templates) }
        }
    }
}

fun ReaderViewModel.recordAiPrompt(documentTitle: String, promptType: String, scope: String, prompt: String) {
    viewModelScope.launch(Dispatchers.IO) {
        val history = repository.addAiPromptHistory(documentTitle, promptType, scope, prompt)
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(aiPromptHistory = history) }
        }
    }
}

fun ReaderViewModel.clearAiPromptHistory() {
    viewModelScope.launch(Dispatchers.IO) {
        val history = repository.clearAiPromptHistory()
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(aiPromptHistory = history) }
        }
    }
}

fun ReaderViewModel.loadVoicesForEngine(enginePackage: String = uiState.value.voiceSettings.enginePackage) {
    _uiState.update { it.copy(voiceLoadInProgress = true, voiceMessage = null) }
    voiceJob?.cancel()
    voiceJob = viewModelScope.launch(Dispatchers.IO) {
        val result = runCatching { VoiceManager.loadVoices(getApplication(), enginePackage) }
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(voiceLoadInProgress = false) }
            result.onSuccess { options ->
                _uiState.update { state ->
                    state.copy(
                        ttsVoices = options,
                        voiceMessage = if (options.isEmpty()) "No selectable voices were reported by this TTS engine. Try installing voice data or choosing another engine." else null
                    )
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(
                        ttsVoices = emptyList(),
                        voiceMessage = "Could not load voices: ${error.message ?: "unknown error"}"
                    )
                }
            }
        }
    }
}

fun ReaderViewModel.previewVoice(voice: TtsVoiceOption) {
    val detectedEngine = VoiceManager.engineForVoice(voice.name)
    val enginePackage = if (detectedEngine != null) {
        detectedEngine
    } else if (VoiceManager.isVeritasEngine(uiState.value.voiceSettings.enginePackage)) {
        ""
    } else {
        uiState.value.voiceSettings.enginePackage
    }
    VoiceManager.previewVoice(
        context = getApplication(),
        enginePackage = enginePackage,
        voiceName = voice.name,
        text = "There is surely a future for you, and your hope will not be cut off",
        rate = 1.0f,
        pitch = 1.0f
    )
}

fun ReaderViewModel.previewActiveVoiceWithPreset() {
    val settings = uiState.value.voiceSettings
    val voiceName = settings.voiceName
    val detectedEngine = VoiceManager.engineForVoice(voiceName)
    val enginePackage = if (detectedEngine != null) {
        detectedEngine
    } else if (VoiceManager.isVeritasEngine(settings.enginePackage)) {
        ""
    } else {
        settings.enginePackage
    }
    val sampleText = "Brethren, whatever things are true, noble, just, pure, lovely, whatever things are of good report, - if anything is excellent or praiseworthy, - think about such things"
    VoiceManager.previewVoice(
        context = getApplication(),
        enginePackage = enginePackage,
        voiceName = voiceName,
        text = sampleText,
        rate = settings.preferredRate,
        pitch = settings.preferredPitch
    )
}

fun ReaderViewModel.openSystemTtsSettings() {
    val intent = Intent("com.android.settings.TTS_SETTINGS").apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
    val fallback = Intent(android.provider.Settings.ACTION_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
    runCatching { getApplication<Application>().startActivity(intent) }.onFailure {
        runCatching { getApplication<Application>().startActivity(fallback) }
    }
}

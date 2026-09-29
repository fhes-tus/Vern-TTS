package com.veritas.reader.ui

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.veritas.reader.CoverExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

fun ReaderViewModel.finishTutorial() {
    finishOnboarding(uiState.value.userName)
}

fun ReaderViewModel.updateUserNameInMemory(name: String) {
    _uiState.update { it.copy(userName = name) }
}

fun ReaderViewModel.saveUserName(name: String) {
    val cleanName = name.trim().ifBlank { "Reader" }
    viewModelScope.launch(Dispatchers.IO) {
        repository.saveUserName(cleanName)
        val savedName = repository.loadUserName()
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(userName = savedName) }
        }
    }
}

fun ReaderViewModel.finishOnboarding(name: String) {
    val cleanName = name.trim().ifBlank { "Reader" }
    viewModelScope.launch(Dispatchers.IO) {
        repository.markOnboardingComplete(cleanName)
        val savedName = repository.loadUserName()
        withContext(Dispatchers.Main) {
            _uiState.update {
                it.copy(
                    showTutorial = false,
                    userName = savedName,
                    hasCompletedOnboarding = true
                )
            }
        }
    }
}

fun ReaderViewModel.completeRevampedOnboarding(
    name: String,
    interest: String,
    aiAssistant: String,
    speedRate: Float,
    pitchRate: Float
) {
    val cleanName = name.trim().ifBlank { "Reader" }
    val cleanInterest = interest.trim()
    viewModelScope.launch(Dispatchers.IO) {
        repository.markOnboardingComplete(cleanName)
        repository.saveReadingInterest(cleanInterest)
        val currentAi = repository.loadAskAiSettings()
        val updatedAi = currentAi.copy(
            assistantId = aiAssistant,
            assistantLabel = when (aiAssistant.lowercase(Locale.getDefault())) {
                "gemini" -> "Google Gemini"
                "chatgpt" -> "ChatGPT"
                "claude" -> "Claude"
                "copilot" -> "Microsoft Copilot"
                "perplexity" -> "Perplexity"
                "grok" -> "xAI Grok"
                else -> currentAi.assistantLabel
            }
        )
        repository.saveAskAiSettings(updatedAi)
        val updatedVoice = repository.loadVoiceSettings().copy(
            preferredRate = speedRate,
            preferredPitch = pitchRate
        )
        repository.saveVoiceSettings(updatedVoice)
        val savedName = repository.loadUserName()
        withContext(Dispatchers.Main) {
            _uiState.update {
                it.copy(
                    showTutorial = true,
                    userName = savedName,
                    readingInterest = cleanInterest,
                    hasCompletedOnboarding = true,
                    askAiSettings = updatedAi,
                    voiceSettings = updatedVoice,
                    questTourDone = false
                )
            }
        }
    }
}

fun ReaderViewModel.resetQuestProgress() {
    viewModelScope.launch(Dispatchers.IO) {
        repository.saveQuestProgress(tour = false, import = false, speed = false, bookmark = false)
        repository.resetOnboardingState()
        withContext(Dispatchers.Main) {
            _uiState.update {
                it.copy(
                    questTourDone = false,
                    questImportDone = false,
                    questSpeedDone = false,
                    questBookmarkDone = false,
                    showTutorial = true,
                    hasCompletedOnboarding = false
                )
            }
        }
    }
}

fun ReaderViewModel.completeQuestTour() {
    if (uiState.value.questTourDone) return
    viewModelScope.launch(Dispatchers.IO) {
        val state = uiState.value
        repository.saveQuestProgress(
            tour = true,
            import = state.questImportDone,
            speed = state.questSpeedDone,
            bookmark = state.questBookmarkDone
        )
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(questTourDone = true) }
            checkOnboardingOverallCompletion()
        }
    }
}

fun ReaderViewModel.dismissQuestChecklist() {
    repository.setQuestChecklistDismissed(true)
    _uiState.update { it.copy(questChecklistDismissed = true) }
}

fun ReaderViewModel.reopenQuestChecklist() {
    repository.setQuestChecklistDismissed(false)
    _uiState.update { it.copy(questChecklistDismissed = false) }
}

fun ReaderViewModel.completeQuestImport() {
    if (uiState.value.questImportDone) return
    viewModelScope.launch(Dispatchers.IO) {
        val state = uiState.value
        repository.saveQuestProgress(
            tour = state.questTourDone,
            import = true,
            speed = state.questSpeedDone,
            bookmark = state.questBookmarkDone
        )
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(questImportDone = true) }
            checkOnboardingOverallCompletion()
        }
    }
}

fun ReaderViewModel.completeQuestSpeed() {
    if (uiState.value.questSpeedDone) return
    viewModelScope.launch(Dispatchers.IO) {
        val state = uiState.value
        repository.saveQuestProgress(
            tour = state.questTourDone,
            import = state.questImportDone,
            speed = true,
            bookmark = state.questBookmarkDone
        )
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(questSpeedDone = true) }
            checkOnboardingOverallCompletion()
        }
    }
}

fun ReaderViewModel.completeQuestBookmark() {
    if (uiState.value.questBookmarkDone) return
    viewModelScope.launch(Dispatchers.IO) {
        val state = uiState.value
        repository.saveQuestProgress(
            tour = state.questTourDone,
            import = state.questImportDone,
            speed = state.questSpeedDone,
            bookmark = true
        )
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(questBookmarkDone = true) }
            checkOnboardingOverallCompletion()
        }
    }
}

internal fun ReaderViewModel.checkOnboardingOverallCompletion() {
    val state = uiState.value
    if (state.questTourDone && state.questImportDone && state.questSpeedDone && state.questBookmarkDone) {
        if (!repository.hasCelebratedQuests()) {
            repository.markQuestsCelebrated()
            repository.setQuestChecklistDismissed(true)
            _uiState.update { it.copy(showConfetti = true, questChecklistDismissed = true) }
        }
    }
}

fun ReaderViewModel.finishConfettiCelebration() {
    _uiState.update { it.copy(showConfetti = false) }
    finishOnboarding(uiState.value.userName.ifBlank { "Reader" })
}

fun ReaderViewModel.createWelcomeDocumentSilently() {
    viewModelScope.launch(Dispatchers.IO) {
        val existingDocs = repository.loadDocuments()
        if (existingDocs.isEmpty()) {
            val context = getApplication<Application>()
            
            // 1. Seed default classic book: "Who Moved My Cheese?"
            val cheeseText = runCatching {
                context.assets.open("books/who_moved_my_cheese.txt").bufferedReader().use { it.readText() }
            }.getOrNull()
            
            if (!cheeseText.isNullOrBlank()) {
                val cheeseDoc = repository.createDocument(
                    title = "Who Moved My Cheese?",
                    text = cheeseText,
                    sourceLabel = "Spencer Johnson, M.D.",
                    originalDisplayName = "Who Moved My Cheese? - Spencer Johnson, M.D."
                )
                // Copy default cover image from assets
                runCatching {
                    context.assets.open("covers/who_moved_my_cheese.jpg").use { input ->
                        val coversDir = CoverExtractor.coversDir(context)
                        coversDir.mkdirs()
                        val coverFile = File(coversDir, "${cheeseDoc.id}.cover.jpg")
                        coverFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }

            // 2. Seed interactive Vern Welcome Guide
            repository.createDocument(
                title = "Vern Welcome Guide",
                text = "Welcome to Vern! This is a sample document designed to help you explore the reading environment. Vern lets you convert research papers, textbooks, EPUBs, docx files, web articles, and images into high-quality spoken audio. Long-press any sentence in this guide to try highlighting, bookmarking, adding study notes, or asking the AI Assistant a question. Adjust the voice speed or select premium voices in the expandable player panel below. Toggle different layout modes like TEXT for clean reading or LISTEN to follow along sentence-by-sentence. Enjoy your reading journey!",
                sourceLabel = "System"
            )
            withContext(Dispatchers.Main) {
                refreshAll()
            }
        }
    }
}


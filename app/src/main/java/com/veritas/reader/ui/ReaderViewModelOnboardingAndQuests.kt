package com.veritas.reader.ui

import com.veritas.reader.VeritasScreen
import android.app.Application
import androidx.lifecycle.viewModelScope
import com.veritas.reader.CoverExtractor
import com.veritas.reader.FirstChapterMilestone
import com.veritas.reader.ui.screens.VeritasHomeTab
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
                it.withVisibility(VeritasScreen.TUTORIAL, false).copy(
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
    pitchRate: Float,
    showFirstChapterPrompt: Boolean
) {
    val cleanName = name.trim().ifBlank { "Reader" }
    val cleanInterest = interest.trim()
    viewModelScope.launch(Dispatchers.IO) {
        repository.saveReadingInterest(cleanInterest)
        val currentAi = repository.loadAskAiSettings()
        val updatedAi = currentAi.copy(
            assistantId = aiAssistant,
            packageName = if (currentAi.assistantId == aiAssistant) currentAi.packageName else "",
            assistantLabel = when (aiAssistant.lowercase(Locale.ROOT)) {
                "chooser" -> "Choose each time"
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
        repository.markOnboardingComplete(cleanName)
        val savedName = repository.loadUserName()
        withContext(Dispatchers.Main) {
            _uiState.update {
                it.withVisibility(VeritasScreen.TUTORIAL, false).copy(
                    userName = savedName,
                    readingInterest = cleanInterest,
                    hasCompletedOnboarding = true,
                    askAiSettings = updatedAi,
                    voiceSettings = updatedVoice,
                    showFirstChapterPrompt = showFirstChapterPrompt && !it.questChecklistDismissed,
                    targetHomeTab = if (showFirstChapterPrompt) VeritasHomeTab.HOME else it.targetHomeTab,
                    targetLibrarySection = if (showFirstChapterPrompt) null else it.targetLibrarySection
                )
            }
        }
    }
}

fun ReaderViewModel.resetQuestProgress() {
    viewModelScope.launch {
        repository.saveQuestProgress(tour = false, import = false, speed = false, bookmark = false)
        repository.resetOnboardingState()
        _uiState.update {
            it.withVisibility(VeritasScreen.TUTORIAL, true).copy(
                questTourDone = false,
                questImportDone = false,
                questSpeedDone = false,
                questBookmarkDone = false,
                questNoteDone = false,
                questChecklistDismissed = false,
                showFirstChapterPrompt = false,
                showConfetti = false,
                hasCompletedOnboarding = false
            )
        }
    }
}

fun ReaderViewModel.completeQuestTour() = completeFirstChapterMilestone(FirstChapterMilestone.TOUR)

fun ReaderViewModel.completeQuestImport() = completeFirstChapterMilestone(FirstChapterMilestone.IMPORT)

fun ReaderViewModel.completeQuestSpeed() = completeFirstChapterMilestone(FirstChapterMilestone.PACE)

fun ReaderViewModel.completeQuestBookmark() = completeFirstChapterMilestone(FirstChapterMilestone.BOOKMARK)

internal fun ReaderViewModel.completeQuestNote() = completeFirstChapterMilestone(FirstChapterMilestone.NOTE)

private fun ReaderViewModel.completeFirstChapterMilestone(milestone: FirstChapterMilestone) {
    viewModelScope.launch {
        repository.completeFirstChapterMilestone(milestone)
        _uiState.update { state ->
            when (milestone) {
                FirstChapterMilestone.IMPORT -> state.copy(questImportDone = true)
                FirstChapterMilestone.PACE -> state.copy(questSpeedDone = true)
                FirstChapterMilestone.BOOKMARK -> state.copy(questBookmarkDone = true)
                FirstChapterMilestone.NOTE -> state.copy(questNoteDone = true)
                FirstChapterMilestone.TOUR -> state.copy(questTourDone = true)
            }
        }
        checkOnboardingOverallCompletion()
    }
}

fun ReaderViewModel.dismissQuestChecklist() {
    repository.setQuestChecklistDismissed(true)
    _uiState.update { it.copy(questChecklistDismissed = true, showFirstChapterPrompt = false) }
}

fun ReaderViewModel.reopenQuestChecklist() {
    repository.setQuestChecklistDismissed(false)
    _uiState.update { it.copy(questChecklistDismissed = false) }
}

internal fun ReaderViewModel.checkOnboardingOverallCompletion() {
    val state = uiState.value
    if (state.hasCompletedOnboarding && !state.questChecklistDismissed &&
        state.questImportDone && state.questSpeedDone && state.questBookmarkDone && state.questNoteDone &&
        !repository.hasCelebratedQuests()
    ) {
        repository.markQuestsCelebrated()
        _uiState.update { it.copy(showConfetti = true) }
    }
}

fun ReaderViewModel.finishConfettiCelebration() {
    _uiState.update { it.copy(showConfetti = false) }
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

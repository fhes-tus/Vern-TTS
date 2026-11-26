package com.veritas.reader.ui

import androidx.lifecycle.viewModelScope
import com.veritas.reader.AiPromptType
import com.veritas.reader.Flashcard
import com.veritas.reader.FlashcardProgress
import com.veritas.reader.GeminiStudyService
import com.veritas.reader.studyRequestText
import com.veritas.reader.GeneralNote
import com.veritas.reader.mutateGeneralNotes
import com.veritas.reader.QuizSet
import com.veritas.reader.ReaderDocument
import com.veritas.reader.SavedDocument
import com.veritas.reader.SpacedRepetitionScheduler
import com.veritas.reader.TextChunker
import com.veritas.reader.deleteFlashcardSet
import com.veritas.reader.deleteQuiz
import com.veritas.reader.loadAllFlashcards
import com.veritas.reader.loadAllQuizzes
import com.veritas.reader.renameFlashcardSet
import com.veritas.reader.saveQuiz
import com.veritas.reader.mutateStudyFlashcards
import com.veritas.reader.mutateStudyQuizzes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import java.util.UUID

fun ReaderViewModel.importFlashcards(documentId: String, setName: String, cards: List<Flashcard>) {
    if (cards.isEmpty()) return
    launchStudyMutation { persistFlashcards(documentId, setName, cards) }
}

private suspend fun ReaderViewModel.persistFlashcards(documentId: String, setName: String, cards: List<Flashcard>) {
    val cleanCards = cards.map { Flashcard(it.front.trim(), it.back.trim()) }
        .filter { it.front.isNotBlank() && it.back.isNotBlank() }
    if (cleanCards.isEmpty()) return
    val setId = UUID.randomUUID().toString()
    val cleanName = setName.trim().ifBlank { "Untitled set" }
    val allCards = repository.mutateStudyFlashcards { current ->
        val existing = current.toMutableList()
        cleanCards.forEach { card ->
            existing.add(
                FlashcardProgress(
                    id = UUID.randomUUID().toString(),
                    documentId = documentId,
                    front = card.front,
                    back = card.back,
                    setId = setId,
                    setName = cleanName
                )
            )
        }
        existing
    }
    withContext(Dispatchers.Main) {
        _uiState.update { it.copy(flashcards = allCards) }
    }
}


/** Records the recall rating for a card using SM-2 Lite spaced repetition scheduling. */
fun ReaderViewModel.rateFlashcardRecall(cardId: String, recall: String, onComplete: (Boolean) -> Unit = {}) {
    viewModelScope.launch {
        try {
            var found = false
            runStudyMutation {
                val updated = repository.mutateStudyFlashcards { cards -> cards.map {
                    if (it.id != cardId) it else {
                        found = true
                        SpacedRepetitionScheduler.rateCard(it, recall)
                    }
                } }
                withContext(Dispatchers.Main) { _uiState.update { it.copy(flashcards = updated) } }
            }
            onComplete(found)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            android.util.Log.e("Study", "Could not record flashcard recall", failure)
            onComplete(false)
        }
    }
}

fun ReaderViewModel.saveQuiz(quiz: QuizSet) {
    launchStudyMutation { persistQuiz(quiz) }
}

private suspend fun ReaderViewModel.persistQuiz(quiz: QuizSet) {
    val updated = repository.saveQuiz(quiz)
    withContext(Dispatchers.Main) {
        _uiState.update { it.copy(quizzes = updated) }
    }
}


fun ReaderViewModel.deleteQuiz(quizId: String) {
    launchStudyMutation {
        val remaining = repository.deleteQuiz(quizId)
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(quizzes = remaining) }
        }
    }
}

fun ReaderViewModel.recordQuizScore(quizId: String, score: Int) {
    launchStudyMutation {
        val all = repository.mutateStudyQuizzes { quizzes -> quizzes.map {
            if (it.id == quizId) it.copy(bestScore = maxOf(it.calibratedBestScore, score.coerceIn(0, it.questions.size))) else it
        } }
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(quizzes = all) }
        }
    }
}

fun ReaderViewModel.generateInAppFlashcards(
    document: ReaderDocument,
    count: Int = 10,
    scopeText: String? = null,
    setName: String? = null,
    onComplete: ((Boolean, String) -> Unit)? = null
) = startFlashcardGeneration({ document }, count, scopeText, setName, onComplete)

private fun ReaderViewModel.startFlashcardGeneration(
    loadDocument: suspend () -> ReaderDocument,
    count: Int,
    scopeText: String?,
    setName: String?,
    onComplete: ((Boolean, String) -> Unit)?
) {
    val profile = GeminiStudyService.requestProfile(getApplication())
    if (profile.apiKey.isBlank()) {
        if (onComplete == null) reportStudyFailure("Add an API key for your selected provider in AI settings.")
        onComplete?.invoke(false, "Add an API key for your selected provider in AI settings.")
        return
    }

    launchAiStudy("Creating flashcards", profile.provider.label, onFailure = { message ->
        if (onComplete == null) reportStudyFailure(message) else onComplete(false, message)
    }) {
        val document = loadDocument()
        val textToAnalyze = studyRequestText(scopeText) { document.rawText.ifBlank { document.chunks.joinToString(" ") } }
        val result = GeminiStudyService.generateFlashcards(
            profile = profile,
            documentTitle = document.title,
            textContext = textToAnalyze,
            cardCount = count
        )
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        result.onSuccess { cards ->
            if (cards.isEmpty()) error("The provider returned no usable flashcards. Try a shorter passage.")
            if (onComplete == null) runStudyMutation { persistFlashcards(document.id.orEmpty(), setName ?: "${document.title} Flashcards", cards) }
            val cardJson = org.json.JSONArray().apply { cards.forEach { put(org.json.JSONObject().put("front", it.front).put("back", it.back)) } }.toString()
            withContext(Dispatchers.Main) {
                onComplete?.invoke(true, cardJson)
            }
        }.onFailure { throw it }
    }
}

fun ReaderViewModel.generateInAppQuiz(
    document: ReaderDocument,
    count: Int = 5,
    scopeText: String? = null,
    quizTitle: String? = null,
    onComplete: ((Boolean, String, QuizSet?) -> Unit)? = null
) = startQuizGeneration({ document }, count, scopeText, quizTitle, onComplete)

private fun ReaderViewModel.startQuizGeneration(
    loadDocument: suspend () -> ReaderDocument,
    count: Int,
    scopeText: String?,
    quizTitle: String?,
    onComplete: ((Boolean, String, QuizSet?) -> Unit)?
) {
    val profile = GeminiStudyService.requestProfile(getApplication())
    if (profile.apiKey.isBlank()) {
        if (onComplete == null) reportStudyFailure("Add an API key for your selected provider in AI settings.")
        onComplete?.invoke(false, "Add an API key for your selected provider in AI settings.", null)
        return
    }

    launchAiStudy("Creating a quiz", profile.provider.label, onFailure = { message ->
        if (onComplete == null) reportStudyFailure(message) else onComplete(false, message, null)
    }) {
        val document = loadDocument()
        val textToAnalyze = studyRequestText(scopeText) { document.rawText.ifBlank { document.chunks.joinToString(" ") } }
        val result = GeminiStudyService.generateQuiz(
            profile = profile,
            documentTitle = document.title,
            textContext = textToAnalyze,
            questionCount = count
        )
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        result.onSuccess { questions ->
            if (questions.isEmpty()) error("The provider returned no usable questions. Try another passage.")
            val newQuiz = QuizSet(
                title = quizTitle ?: "${document.title} Quiz",
                documentId = document.id.orEmpty(),
                questions = questions
            )
            if (onComplete == null) runStudyMutation { persistQuiz(newQuiz) }
            withContext(Dispatchers.Main) {
                onComplete?.invoke(true, "Created quiz with ${questions.size} questions!", newQuiz)
            }
        }.onFailure { throw it }
    }
}

fun ReaderViewModel.generateInAppQuiz(
    document: SavedDocument,
    prompt: String? = null
) {
    startQuizGeneration({ loadStudyDocument(document) }, 5, prompt, null, null)
}

internal fun ReaderViewModel.loadStudyDocument(document: SavedDocument): ReaderDocument {
    val text = repository.readText(document)
    check(text.isNotBlank()) { "This book has no readable text for study tools." }
    return ReaderDocument(document.id, document.title, document.sourceLabel, text, TextChunker.chunk(text), document.pageCount)
}

internal fun ReaderViewModel.saveAiStudyNote(documentTitle: String, content: String, onComplete: (Boolean) -> Unit) {
    if (content.isBlank()) {
        onComplete(false)
        return
    }
    viewModelScope.launch {
        try {
            val note = GeneralNote(java.util.UUID.randomUUID().toString(), documentTitle, content.trim(), System.currentTimeMillis())
            val notes = withContext(Dispatchers.IO) { repository.mutateGeneralNotes { listOf(note) + it } }
            _uiState.update { it.copy(generalNotes = notes) }
            onComplete(true)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            android.util.Log.e("Study", "Could not save an AI study note", failure)
            onComplete(false)
        }
    }
}

internal fun ReaderViewModel.generateInAppStudyAnswer(
    document: ReaderDocument,
    type: AiPromptType,
    scopeText: String,
    questionOrFocus: String,
    onPartial: (String) -> Unit,
    onComplete: (Boolean, String) -> Unit
) {
    val profile = GeminiStudyService.requestProfile(getApplication())
    if (profile.apiKey.isBlank()) {
        onComplete(false, "Add an API key for your selected provider in AI settings.")
        return
    }
    launchAiStudy("Writing an answer", profile.provider.label, onFailure = { message -> onComplete(false, message) }) {
        val source = studyRequestText(scopeText) { error("A passage is required.") }
        val answer = StringBuilder()
        var nextUpdate = 0L
        GeminiStudyService.studyAnswer(profile, type, document.title, source, questionOrFocus).collect { delta ->
            check(delta.length <= 80_000 - answer.length) { "The provider returned an oversized answer. Try a shorter passage." }
            answer.append(delta)
            val now = android.os.SystemClock.elapsedRealtime()
            if (now >= nextUpdate) {
                val preview = answer.toString()
                withContext(Dispatchers.Main) { onPartial(preview) }
                nextUpdate = now + 80
            }
        }
        val result = answer.toString().trim()
        check(result.isNotBlank()) { "The provider returned an empty answer. Try again." }
        withContext(Dispatchers.Main) { onComplete(true, result) }
    }
}

fun ReaderViewModel.deleteFlashcard(cardId: String) {
    launchStudyMutation {
        val remaining = repository.mutateStudyFlashcards { cards -> cards.filterNot { it.id == cardId } }
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(flashcards = remaining) }
        }
    }
}

fun ReaderViewModel.renameFlashcardSet(setId: String, newName: String) {
    val cleanName = newName.trim().ifBlank { "Untitled set" }
    launchStudyMutation {
        val updated = repository.renameFlashcardSet(setId, cleanName)
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(flashcards = updated) }
        }
    }
}

fun ReaderViewModel.deleteFlashcardSet(setId: String) {
    launchStudyMutation {
        val remaining = repository.deleteFlashcardSet(setId)
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(flashcards = remaining) }
        }
    }
}

/** Keep each database change and its UI publication together, in tap order. */
private suspend fun ReaderViewModel.runStudyMutation(action: suspend () -> Unit) {
    studyMutationMutex.withLock { withContext(Dispatchers.IO) { action() } }
}

internal suspend fun ReaderViewModel.refreshStudyState() = runStudyMutation {
    val flashcards = repository.loadAllFlashcards()
    val quizzes = repository.loadAllQuizzes()
    withContext(Dispatchers.Main) { _uiState.update { it.copy(flashcards = flashcards, quizzes = quizzes) } }
}

private fun ReaderViewModel.launchStudyMutation(action: suspend () -> Unit) {
    viewModelScope.launch {
        try { runStudyMutation(action) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) {
            android.util.Log.e("Study", "Could not save study changes", failure)
            _uiState.update { it.copy(importMessage = "Could not save study changes. Please try again.") }
        }
    }
}

private fun ReaderViewModel.reportStudyFailure(message: String) {
    _uiState.update { it.copy(importMessage = message) }
}


/** One generation owns the shared loading state; canceled work cannot publish a late result. */
private fun ReaderViewModel.launchAiStudy(status: String, providerLabel: String, onFailure: (String) -> Unit, action: suspend () -> Unit) {
    viewModelScope.launch(Dispatchers.Main.immediate) {
        if (aiStudyJob?.isActive == true) {
            onFailure("A study request is already running. Wait or cancel it first.")
            return@launch
        }
        val revision = ++aiStudyRevision
        _uiState.update { it.copy(isGeneratingAiStudy = true, aiStudyStatusMessage = "$status with $providerLabel…") }
        aiStudyJob = viewModelScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            try { action() }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (failure: Exception) { withContext(Dispatchers.Main) { if (revision == aiStudyRevision) onFailure(failure.message ?: "Could not complete this request. Try again.") } }
            finally {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) {
                    if (revision == aiStudyRevision) _uiState.update { it.copy(isGeneratingAiStudy = false, aiStudyStatusMessage = null) }
                }
            }
        }.also { it.start() }
    }
}

fun ReaderViewModel.cancelAiStudyGeneration() {
    viewModelScope.launch(Dispatchers.Main.immediate) {
        aiStudyRevision++
        aiStudyJob?.cancel()
        aiStudyJob = null
        _uiState.update { it.copy(isGeneratingAiStudy = false, aiStudyStatusMessage = null) }
    }
}

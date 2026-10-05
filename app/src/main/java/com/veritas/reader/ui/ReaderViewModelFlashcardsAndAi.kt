package com.veritas.reader.ui

import androidx.lifecycle.viewModelScope
import com.veritas.reader.Flashcard
import com.veritas.reader.FlashcardProgress
import com.veritas.reader.GeminiStudyService
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
import com.veritas.reader.saveAllFlashcards
import com.veritas.reader.saveAllQuizzes
import com.veritas.reader.saveQuiz
import com.veritas.reader.mutateStudyFlashcards
import com.veritas.reader.mutateStudyQuizzes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

fun ReaderViewModel.importFlashcards(documentId: String, setName: String, cards: List<Flashcard>) {
    if (cards.isEmpty()) return
    viewModelScope.launch(Dispatchers.IO) {
        val setId = UUID.randomUUID().toString()
        val cleanName = setName.trim().ifBlank { "Untitled set" }
        val allCards = repository.mutateStudyFlashcards { current ->
        val existing = current.toMutableList()
        cards.forEach { card ->
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
}

/** Records the recall rating for a card using SM-2 Lite spaced repetition scheduling. */
fun ReaderViewModel.rateFlashcardRecall(cardId: String, recall: String) {
    viewModelScope.launch(Dispatchers.IO) {
        val updated = repository.mutateStudyFlashcards { cards -> cards.map {
            if (it.id == cardId) SpacedRepetitionScheduler.rateCard(it, recall) else it
        } }
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(flashcards = updated) }
        }
    }
}

fun ReaderViewModel.saveQuiz(quiz: QuizSet) {
    viewModelScope.launch(Dispatchers.IO) {
        val updated = repository.saveQuiz(quiz)
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(quizzes = updated) }
        }
    }
}

fun ReaderViewModel.deleteQuiz(quizId: String) {
    viewModelScope.launch(Dispatchers.IO) {
        val remaining = repository.deleteQuiz(quizId)
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(quizzes = remaining) }
        }
    }
}

fun ReaderViewModel.recordQuizScore(quizId: String, score: Int) {
    viewModelScope.launch(Dispatchers.IO) {
        val all = repository.mutateStudyQuizzes { quizzes -> quizzes.map {
            if (it.id == quizId) it.copy(bestScore = maxOf(it.bestScore, score.coerceIn(0, it.questions.size))) else it
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
) {
    val apiKey = GeminiStudyService.getApiKey(getApplication())
    if (apiKey.isBlank()) {
        onComplete?.invoke(false, "Add an API key for your selected provider in AI settings.")
        return
    }

    launchAiStudy("Creating flashcards", onFailure = { message -> onComplete?.invoke(false, message) }) {
        val textToAnalyze = scopeText?.ifBlank { null } ?: document.rawText.ifBlank { document.chunks.joinToString(" ") }
        val result = GeminiStudyService.generateFlashcards(
            apiKey = apiKey,
            documentTitle = document.title,
            textContext = textToAnalyze,
            cardCount = count
        )
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        result.onSuccess { cards ->
            if (cards.isEmpty()) error("The provider returned no usable flashcards. Try a shorter passage.")
            if (onComplete == null) importFlashcards(document.id.orEmpty(), setName ?: "${document.title} Flashcards", cards)
            val cardJson = org.json.JSONArray().apply { cards.forEach { put(org.json.JSONObject().put("front", it.front).put("back", it.back)) } }.toString()
            withContext(Dispatchers.Main) {
                onComplete?.invoke(true, cardJson)
            }
        }.onFailure { err ->
            withContext(Dispatchers.Main) {
                onComplete?.invoke(false, err.message ?: "Failed to generate flashcards.")
            }
        }
    }
}

fun ReaderViewModel.generateInAppQuiz(
    document: ReaderDocument,
    count: Int = 5,
    scopeText: String? = null,
    quizTitle: String? = null,
    onComplete: ((Boolean, String, QuizSet?) -> Unit)? = null
) {
    val apiKey = GeminiStudyService.getApiKey(getApplication())
    if (apiKey.isBlank()) {
        onComplete?.invoke(false, "Add an API key for your selected provider in AI settings.", null)
        return
    }

    launchAiStudy("Creating a quiz", onFailure = { message -> onComplete?.invoke(false, message, null) }) {
        val textToAnalyze = scopeText?.ifBlank { null } ?: document.rawText.ifBlank { document.chunks.joinToString(" ") }
        val result = GeminiStudyService.generateQuiz(
            apiKey = apiKey,
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
            if (onComplete == null) saveQuiz(newQuiz)
            withContext(Dispatchers.Main) {
                onComplete?.invoke(true, "Created quiz with ${questions.size} questions!", newQuiz)
            }
        }.onFailure { err ->
            withContext(Dispatchers.Main) {
                onComplete?.invoke(false, err.message ?: "Failed to create quiz.", null)
            }
        }
    }
}

fun ReaderViewModel.generateInAppFlashcards(
    document: SavedDocument,
    prompt: String? = null
) {
    viewModelScope.launch(Dispatchers.IO) {
        val text = repository.readText(document)
        if (text.isBlank()) return@launch
        val readerDoc = ReaderDocument(
            id = document.id,
            title = document.title,
            sourceLabel = document.sourceLabel,
            rawText = text,
            sentences = TextChunker.chunk(text)
        )
        generateInAppFlashcards(readerDoc, count = 10, scopeText = prompt)
    }
}

fun ReaderViewModel.generateInAppQuiz(
    document: SavedDocument,
    prompt: String? = null
) {
    viewModelScope.launch(Dispatchers.IO) {
        val text = repository.readText(document)
        if (text.isBlank()) return@launch
        val readerDoc = ReaderDocument(
            id = document.id,
            title = document.title,
            sourceLabel = document.sourceLabel,
            rawText = text,
            sentences = TextChunker.chunk(text)
        )
        generateInAppQuiz(readerDoc, count = 5, scopeText = prompt)
    }
}

fun ReaderViewModel.generateInAppStudySummary(
    document: ReaderDocument,
    scopeText: String? = null,
    onComplete: ((Boolean, String) -> Unit)? = null
) {
    val apiKey = GeminiStudyService.getApiKey(getApplication())
    if (apiKey.isBlank()) {
        onComplete?.invoke(false, "Add an API key for your selected provider in AI settings.")
        return
    }

    launchAiStudy("Writing a summary", onFailure = { message -> onComplete?.invoke(false, message) }) {
        val textToAnalyze = scopeText?.ifBlank { null } ?: document.rawText.ifBlank { document.chunks.joinToString(" ") }
        val result = GeminiStudyService.generateStudySummary(
            apiKey = apiKey,
            documentTitle = document.title,
            textContext = textToAnalyze
        )
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        result.onSuccess { summary ->
            withContext(Dispatchers.Main) {
                onComplete?.invoke(true, summary)
            }
        }.onFailure { err ->
            withContext(Dispatchers.Main) {
                onComplete?.invoke(false, err.message ?: "Failed to generate summary.")
            }
        }
    }
}

fun ReaderViewModel.generateInAppExplanation(
    document: ReaderDocument,
    scopeText: String? = null,
    targetPassage: String = "",
    onComplete: ((Boolean, String) -> Unit)? = null
) {
    val apiKey = GeminiStudyService.getApiKey(getApplication())
    if (apiKey.isBlank()) {
        onComplete?.invoke(false, "Add an API key for your selected provider in AI settings.")
        return
    }

    launchAiStudy("Explaining the passage", onFailure = { message -> onComplete?.invoke(false, message) }) {
        val textToAnalyze = scopeText?.ifBlank { null } ?: document.rawText.ifBlank { document.chunks.joinToString(" ") }
        val result = GeminiStudyService.generateExplanation(
            apiKey = apiKey,
            documentTitle = document.title,
            textContext = textToAnalyze,
            targetPassage = targetPassage
        )
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        result.onSuccess { explanation ->
            withContext(Dispatchers.Main) {
                onComplete?.invoke(true, explanation)
            }
        }.onFailure { err ->
            withContext(Dispatchers.Main) {
                onComplete?.invoke(false, err.message ?: "Failed to explain passage.")
            }
        }
    }
}

fun ReaderViewModel.generateInAppStudyGuide(
    document: ReaderDocument,
    scopeText: String? = null,
    onComplete: ((Boolean, String) -> Unit)? = null
) {
    val apiKey = GeminiStudyService.getApiKey(getApplication())
    if (apiKey.isBlank()) {
        onComplete?.invoke(false, "Add an API key for your selected provider in AI settings.")
        return
    }

    launchAiStudy("Creating a study guide", onFailure = { message -> onComplete?.invoke(false, message) }) {
        val textToAnalyze = scopeText?.ifBlank { null } ?: document.rawText.ifBlank { document.chunks.joinToString(" ") }
        val result = GeminiStudyService.generateStudyGuide(
            apiKey = apiKey,
            documentTitle = document.title,
            textContext = textToAnalyze
        )
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        result.onSuccess { guide ->
            withContext(Dispatchers.Main) {
                onComplete?.invoke(true, guide)
            }
        }.onFailure { err ->
            withContext(Dispatchers.Main) {
                onComplete?.invoke(false, err.message ?: "Failed to generate study guide.")
            }
        }
    }
}

fun ReaderViewModel.deleteFlashcard(cardId: String) {
    viewModelScope.launch(Dispatchers.IO) {
        val remaining = repository.mutateStudyFlashcards { cards -> cards.filterNot { it.id == cardId } }
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(flashcards = remaining) }
        }
    }
}

fun ReaderViewModel.renameFlashcardSet(setId: String, newName: String) {
    val cleanName = newName.trim().ifBlank { "Untitled set" }
    viewModelScope.launch(Dispatchers.IO) {
        val updated = repository.renameFlashcardSet(setId, cleanName)
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(flashcards = updated) }
        }
    }
}

fun ReaderViewModel.deleteFlashcardSet(setId: String) {
    viewModelScope.launch(Dispatchers.IO) {
        val remaining = repository.deleteFlashcardSet(setId)
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(flashcards = remaining) }
        }
    }
}


/** One generation owns the shared loading state; canceled work cannot publish a late result. */
private fun ReaderViewModel.launchAiStudy(status: String, onFailure: (String) -> Unit, action: suspend () -> Unit) {
    if (aiStudyJob?.isActive == true) {
        onFailure("A study request is already running. Wait or cancel it first.")
        return
    }
    val revision = ++aiStudyRevision
    _uiState.update { it.copy(isGeneratingAiStudy = true, aiStudyStatusMessage = "$status with ${GeminiStudyService.getProvider(getApplication()).label}…") }
    aiStudyJob = viewModelScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
        try { action() }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) { withContext(Dispatchers.Main) { if (revision == aiStudyRevision) onFailure(failure.message ?: "Could not complete this request. Try again.") } }
        finally { if (revision == aiStudyRevision) _uiState.update { it.copy(isGeneratingAiStudy = false, aiStudyStatusMessage = null) } }
    }.also { it.start() }
}

fun ReaderViewModel.cancelAiStudyGeneration() {
    aiStudyRevision++
    aiStudyJob?.cancel()
    aiStudyJob = null
    _uiState.update { it.copy(isGeneratingAiStudy = false, aiStudyStatusMessage = null) }
}

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

fun ReaderViewModel.importFlashcards(documentId: String, setName: String, cards: List<Flashcard>) {
    if (cards.isEmpty()) return
    viewModelScope.launch(Dispatchers.IO) {
        val existing = repository.loadAllFlashcards().toMutableList()
        val setId = UUID.randomUUID().toString()
        val cleanName = setName.trim().ifBlank { "Untitled set" }
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
        repository.saveAllFlashcards(existing)
        val allCards = repository.loadAllFlashcards()
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(flashcards = allCards) }
        }
    }
}

/** Records the recall rating for a card using SM-2 Lite spaced repetition scheduling. */
fun ReaderViewModel.rateFlashcardRecall(cardId: String, recall: String) {
    viewModelScope.launch(Dispatchers.IO) {
        val updated = repository.loadAllFlashcards().map {
            if (it.id == cardId) SpacedRepetitionScheduler.rateCard(it, recall) else it
        }
        repository.saveAllFlashcards(updated)
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
        val all = repository.loadAllQuizzes().map {
            if (it.id == quizId) it.copy(bestScore = maxOf(it.bestScore, score)) else it
        }
        repository.saveAllQuizzes(all)
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
        onComplete?.invoke(false, "Please configure your Gemini API Key in Study Hub settings.")
        return
    }

    viewModelScope.launch(Dispatchers.IO) {
        _uiState.update { it.copy(isGeneratingAiStudy = true, aiStudyStatusMessage = "Generating flashcards with Gemini...") }
        val textToAnalyze = scopeText?.ifBlank { null } ?: document.rawText.ifBlank { document.chunks.joinToString(" ") }
        val result = GeminiStudyService.generateFlashcards(
            apiKey = apiKey,
            documentTitle = document.title,
            textContext = textToAnalyze,
            cardCount = count
        )
        result.onSuccess { cards ->
            importFlashcards(
                documentId = document.id.orEmpty(),
                setName = setName ?: "${document.title} Flashcards",
                cards = cards
            )
            _uiState.update { it.copy(isGeneratingAiStudy = false, aiStudyStatusMessage = null) }
            withContext(Dispatchers.Main) {
                onComplete?.invoke(true, "Successfully generated ${cards.size} flashcards!")
            }
        }.onFailure { err ->
            _uiState.update { it.copy(isGeneratingAiStudy = false, aiStudyStatusMessage = null) }
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
        onComplete?.invoke(false, "Please configure your Gemini API Key in Study Hub settings.", null)
        return
    }

    viewModelScope.launch(Dispatchers.IO) {
        _uiState.update { it.copy(isGeneratingAiStudy = true, aiStudyStatusMessage = "Creating exam quiz with Gemini...") }
        val textToAnalyze = scopeText?.ifBlank { null } ?: document.rawText.ifBlank { document.chunks.joinToString(" ") }
        val result = GeminiStudyService.generateQuiz(
            apiKey = apiKey,
            documentTitle = document.title,
            textContext = textToAnalyze,
            questionCount = count
        )
        result.onSuccess { questions ->
            val newQuiz = QuizSet(
                title = quizTitle ?: "${document.title} Quiz",
                documentId = document.id.orEmpty(),
                questions = questions
            )
            saveQuiz(newQuiz)
            _uiState.update { it.copy(isGeneratingAiStudy = false, aiStudyStatusMessage = null) }
            withContext(Dispatchers.Main) {
                onComplete?.invoke(true, "Created quiz with ${questions.size} questions!", newQuiz)
            }
        }.onFailure { err ->
            _uiState.update { it.copy(isGeneratingAiStudy = false, aiStudyStatusMessage = null) }
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
        onComplete?.invoke(false, "Please configure your Gemini API Key in Study Hub settings.")
        return
    }

    viewModelScope.launch(Dispatchers.IO) {
        _uiState.update { it.copy(isGeneratingAiStudy = true, aiStudyStatusMessage = "Synthesizing executive summary with Gemini...") }
        val textToAnalyze = scopeText?.ifBlank { null } ?: document.rawText.ifBlank { document.chunks.joinToString(" ") }
        val result = GeminiStudyService.generateStudySummary(
            apiKey = apiKey,
            documentTitle = document.title,
            textContext = textToAnalyze
        )
        result.onSuccess { summary ->
            _uiState.update { it.copy(isGeneratingAiStudy = false, aiStudyStatusMessage = null) }
            withContext(Dispatchers.Main) {
                onComplete?.invoke(true, summary)
            }
        }.onFailure { err ->
            _uiState.update { it.copy(isGeneratingAiStudy = false, aiStudyStatusMessage = null) }
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
        onComplete?.invoke(false, "Please configure your Gemini API Key in Study Hub settings.")
        return
    }

    viewModelScope.launch(Dispatchers.IO) {
        _uiState.update { it.copy(isGeneratingAiStudy = true, aiStudyStatusMessage = "Deconstructing concepts with Feynman explanation...") }
        val textToAnalyze = scopeText?.ifBlank { null } ?: document.rawText.ifBlank { document.chunks.joinToString(" ") }
        val result = GeminiStudyService.generateExplanation(
            apiKey = apiKey,
            documentTitle = document.title,
            textContext = textToAnalyze,
            targetPassage = targetPassage
        )
        result.onSuccess { explanation ->
            _uiState.update { it.copy(isGeneratingAiStudy = false, aiStudyStatusMessage = null) }
            withContext(Dispatchers.Main) {
                onComplete?.invoke(true, explanation)
            }
        }.onFailure { err ->
            _uiState.update { it.copy(isGeneratingAiStudy = false, aiStudyStatusMessage = null) }
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
        onComplete?.invoke(false, "Please configure your Gemini API Key in Study Hub settings.")
        return
    }

    viewModelScope.launch(Dispatchers.IO) {
        _uiState.update { it.copy(isGeneratingAiStudy = true, aiStudyStatusMessage = "Building structured study guide...") }
        val textToAnalyze = scopeText?.ifBlank { null } ?: document.rawText.ifBlank { document.chunks.joinToString(" ") }
        val result = GeminiStudyService.generateStudyGuide(
            apiKey = apiKey,
            documentTitle = document.title,
            textContext = textToAnalyze
        )
        result.onSuccess { guide ->
            _uiState.update { it.copy(isGeneratingAiStudy = false, aiStudyStatusMessage = null) }
            withContext(Dispatchers.Main) {
                onComplete?.invoke(true, guide)
            }
        }.onFailure { err ->
            _uiState.update { it.copy(isGeneratingAiStudy = false, aiStudyStatusMessage = null) }
            withContext(Dispatchers.Main) {
                onComplete?.invoke(false, err.message ?: "Failed to generate study guide.")
            }
        }
    }
}

fun ReaderViewModel.deleteFlashcard(cardId: String) {
    viewModelScope.launch(Dispatchers.IO) {
        val remaining = repository.loadAllFlashcards().filterNot { it.id == cardId }
        repository.saveAllFlashcards(remaining)
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


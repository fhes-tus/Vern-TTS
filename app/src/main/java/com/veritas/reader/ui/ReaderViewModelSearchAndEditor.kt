package com.veritas.reader.ui

import com.veritas.reader.VeritasScreen
import android.app.Application
import android.content.Intent
import androidx.lifecycle.viewModelScope
import com.veritas.reader.PlaybackStateStore
import com.veritas.reader.ReaderDocument
import com.veritas.reader.ReaderTextIndex
import com.veritas.reader.ReaderTextModelCache
import com.veritas.reader.VeritasTextEditTarget
import com.veritas.reader.buildReaderDocument
import com.veritas.reader.loadAnnotationCount
import com.veritas.reader.loadAnnotations
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun ReaderViewModel.updateSearchQuery(query: String) {
    val doc = uiState.value.activeDocument
    val cleanQuery = query.take(120)
    val matches = buildSearchMatches(doc, cleanQuery)
    val currentIndex = currentReaderIndex
    val cursor = if (matches.isEmpty()) {
        0
    } else {
        matches.indexOfFirst { it >= currentIndex }.takeIf { it >= 0 } ?: 0
    }
    _uiState.update {
        it.copy(
            searchQuery = cleanQuery,
            searchMatches = matches,
            searchCursor = cursor
        )
    }
}

fun ReaderViewModel.moveToNextSearchMatch() {
    moveToSearchMatch(offset = 1)
}

fun ReaderViewModel.moveToPreviousSearchMatch() {
    moveToSearchMatch(offset = -1)
}

internal fun ReaderViewModel.moveToSearchMatch(offset: Int) {
    val state = uiState.value
    val doc = state.activeDocument ?: return
    val matches = state.searchMatches.ifEmpty { buildSearchMatches(doc, state.searchQuery) }
    if (matches.isEmpty()) {
        _uiState.update { it.copy(searchMatches = emptyList(), searchCursor = 0) }
        return
    }
    val currentIndex = currentReaderIndex
    val currentMatchCursor = matches.indexOf(currentIndex)
    val nextCursor = when {
        currentMatchCursor >= 0 -> Math.floorMod(currentMatchCursor + offset, matches.size)
        offset > 0 -> matches.indexOfFirst { it > currentIndex }.takeIf { it >= 0 } ?: 0
        else -> matches.indexOfLast { it < currentIndex }.takeIf { it >= 0 } ?: matches.lastIndex
    }
    _uiState.update {
        it.copy(
            searchMatches = matches,
            searchCursor = nextCursor
        )
    }
    moveTo(matches[nextCursor], autoPlay = false)
}

internal fun ReaderViewModel.buildSearchMatches(document: ReaderDocument?, query: String): List<Int> {
    val needle = query.trim()
    if (document == null || needle.isBlank()) return emptyList()
    return document.chunks.mapIndexedNotNull { index, chunk ->
        index.takeIf { chunk.contains(needle, ignoreCase = true) }
    }
}

fun ReaderViewModel.openCurrentPartTextEditor() {
    val doc = uiState.value.activeDocument ?: return
    val documentId = doc.id ?: return
    val model = ReaderTextModelCache.get(doc.id, doc.rawText, doc.pageCount)
    val part = model.partForSentence(currentReaderIndex) ?: model.parts.firstOrNull() ?: return
    _uiState.update {
        it.withVisibility(VeritasScreen.TEXT_EDITOR, true).copy(
            editorText = part.text,
            editorDocumentId = documentId,
            editorSaving = false,
            editorSaveError = null,
            editorTarget = VeritasTextEditTarget.Part(
                partIndex = part.index,
                label = "part ${part.index + 1}"
            )
        )
    }
}

fun ReaderViewModel.openSelectionTextEditor(indexes: List<Int>) {
    val doc = uiState.value.activeDocument ?: return
    val documentId = doc.id ?: return
    val sorted = indexes
        .filter { it in doc.chunks.indices }
        .distinct()
        .sorted()
    if (sorted.isEmpty()) return
    val start = sorted.first()
    val endExclusive = sorted.last() + 1
    val label = if (endExclusive - start == 1) {
        "sentence ${start + 1}"
    } else {
        "sentences ${start + 1}-$endExclusive"
    }
    _uiState.update {
        it.withVisibility(VeritasScreen.TEXT_EDITOR, true).copy(
            editorText = doc.chunks.subList(start, endExclusive).joinToString("\n\n"),
            editorDocumentId = documentId,
            editorSaving = false,
            editorSaveError = null,
            editorTarget = VeritasTextEditTarget.SentenceRange(
                startSentenceIndex = start,
                endSentenceIndexExclusive = endExclusive,
                label = label
            )
        )
    }
}

fun ReaderViewModel.saveTextEditorChanges(partIndex: Int? = null, text: String? = null) {
    viewModelScope.launch {
        val state = uiState.value
        val doc = state.activeDocument ?: return@launch
        val docId = doc.id ?: return@launch
        val owner = state.editorTarget ?: return@launch
        if (!state.ownsTextEdit(doc, owner) || state.editorSaving) return@launch
        val replacement = (text ?: state.editorText).trim()
        if (doc.partial) {
            _uiState.update { it.copy(editorSaveError = "Wait for the import to finish before saving changes to this reading.") }
            return@launch
        }
        val target = if (partIndex != null && owner is VeritasTextEditTarget.Part) owner.copy(partIndex = partIndex) else owner
        val previousIndex = currentReaderIndex
        _uiState.update { it.copy(editorSaving = true, editorSaveError = null) }
        try {
            if (PlaybackStateStore.activeDocumentId == docId) stopServicePlayback()
            withContext(Dispatchers.IO) persist@{
                val updatedText = when (target) {
                    is VeritasTextEditTarget.Part -> ReaderTextIndex.replacePart(doc.rawText, doc.pageCount, target.partIndex, replacement)
                    is VeritasTextEditTarget.SentenceRange -> ReaderTextIndex.replaceSentenceRange(
                        doc.rawText, doc.pageCount, target.startSentenceIndex, target.endSentenceIndexExclusive, replacement
                    )
                }.trim()
                val updated = repository.saveEditedDocumentText(docId, doc.rawText, updatedText)
                if (updated == null) {
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            if (it.ownsTextEdit(doc, owner)) it.copy(editorSaving = false,
                                editorSaveError = "This reading changed while the editor was open. Copy or export your draft before reopening it.")
                            else if (it.editorTarget === owner) it.copy(editorSaving = false) else it
                        }
                    }
                    return@persist
                }
                val updatedDocument = buildReaderDocument(updated, updatedText)
                val annotations = repository.loadAnnotations(docId)
                val documents = repository.loadDocuments()
                val annotationCount = repository.loadAnnotationCount()
                val outline = repository.loadDocumentOutline(updated, updatedDocument.chunks)
                val startIndex = when (target) {
                    is VeritasTextEditTarget.Part -> ReaderTextModelCache.get(updatedDocument.id, updatedText, updatedDocument.pageCount)
                        .parts.getOrNull(target.partIndex)?.sentenceStartIndex ?: previousIndex
                    is VeritasTextEditTarget.SentenceRange -> target.startSentenceIndex
                }.coerceIn(0, (updatedDocument.chunks.size - 1).coerceAtLeast(0))
                withContext(Dispatchers.Main) publish@{
                    if (!uiState.value.ownsTextEdit(doc, owner)) {
                        _uiState.update { if (it.editorTarget === owner) it.copy(editorSaving = false) else it }
                        refreshAll()
                        return@publish
                    }
                    _uiState.update {
                        it.withVisibility(VeritasScreen.TEXT_EDITOR, false).copy(
                            documents = documents,
                            activeDocument = updatedDocument,
                            annotations = annotations,
                            annotationCount = annotationCount,
                            documentOutline = outline,
                            editorText = "",
                            editorTarget = null,
                            editorDocumentId = null,
                            editorSaving = false,
                            editorSaveError = null,
                            searchMatches = buildSearchMatches(updatedDocument, it.searchQuery),
                            searchCursor = 0,
                            importMessage = "Extracted text updated."
                        )
                    }
                    syncPlaybackStateForDocument(updatedDocument, startIndex)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.w("TextEditor", "Could not finish saving the extracted-text edit.", error)
            _uiState.update {
                if (it.ownsTextEdit(doc, owner)) it.copy(editorSaving = false,
                    editorSaveError = "The edit could not be confirmed. Your draft is still here. Try saving again.")
                else if (it.editorTarget === owner) it.copy(editorSaving = false) else it
            }
        }
    }
}

internal fun ReaderUiState.ownsTextEdit(document: ReaderDocument, target: VeritasTextEditTarget): Boolean =
    showTextEditor && !isOpeningDocument && editorDocumentId == document.id && activeDocument === document && editorTarget === target

fun ReaderViewModel.dismissTextEditor() {
    _uiState.update { it.withVisibility(VeritasScreen.TEXT_EDITOR, false).copy(
        editorText = "",
        editorTarget = null,
        editorDocumentId = null,
        editorSaving = false,
        editorSaveError = null
    ) }
}

fun ReaderViewModel.openTtsDataInstaller() {
    val intent = Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    runCatching { getApplication<Application>().startActivity(intent) }
}

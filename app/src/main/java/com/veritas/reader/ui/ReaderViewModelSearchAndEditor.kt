package com.veritas.reader.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.viewModelScope
import com.veritas.reader.PlaybackStateStore
import com.veritas.reader.ReaderDocument
import com.veritas.reader.ReaderTextIndex
import com.veritas.reader.ReaderTextModelCache
import com.veritas.reader.VeritasTextEditTarget
import com.veritas.reader.loadAnnotationCount
import com.veritas.reader.loadAnnotations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun ReaderViewModel.updateSearchQuery(query: String) {
    val doc = uiState.value.activeDocument
    val cleanQuery = query.take(120)
    val matches = buildSearchMatches(doc, cleanQuery)
    val currentIndex = PlaybackStateStore.currentIndex
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
    val currentIndex = PlaybackStateStore.currentIndex
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
    val model = ReaderTextModelCache.get(doc.id, doc.rawText, doc.pageCount)
    val part = model.partForSentence(PlaybackStateStore.currentIndex) ?: model.parts.firstOrNull() ?: return
    _uiState.update {
        it.copy(
            showTextEditor = true,
            editorText = part.text,
            editorTarget = VeritasTextEditTarget.Part(
                partIndex = part.index,
                label = "part ${part.index + 1}"
            )
        )
    }
}

fun ReaderViewModel.openSelectionTextEditor(indexes: List<Int>) {
    val doc = uiState.value.activeDocument ?: return
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
        it.copy(
            showTextEditor = true,
            editorText = doc.chunks.subList(start, endExclusive).joinToString("\n\n"),
            editorTarget = VeritasTextEditTarget.SentenceRange(
                startSentenceIndex = start,
                endSentenceIndexExclusive = endExclusive,
                label = label
            )
        )
    }
}

fun ReaderViewModel.saveTextEditorChanges(partIndex: Int? = null, text: String? = null) {
    val doc = uiState.value.activeDocument ?: return
    val baseTarget = uiState.value.editorTarget ?: return
    val docId = doc.id ?: return
    val target = if (partIndex != null && baseTarget is VeritasTextEditTarget.Part) {
        VeritasTextEditTarget.Part(partIndex = partIndex, label = "part ${partIndex + 1}")
    } else {
        baseTarget
    }
    val replacement = (text ?: uiState.value.editorText).trim()
    if (replacement.isBlank()) return

    stopServicePlayback()
    viewModelScope.launch(Dispatchers.IO) {
        val updatedText = when (target) {
            is VeritasTextEditTarget.Part -> ReaderTextIndex.replacePart(
                rawText = doc.rawText,
                storedPageCount = doc.pageCount,
                partIndex = target.partIndex,
                replacement = replacement
            )
            is VeritasTextEditTarget.SentenceRange -> ReaderTextIndex.replaceSentenceRange(
                rawText = doc.rawText,
                storedPageCount = doc.pageCount,
                startSentenceIndex = target.startSentenceIndex,
                endSentenceIndexExclusive = target.endSentenceIndexExclusive,
                replacement = replacement
            )
        }
        val updated = repository.updateDocumentText(docId, updatedText) ?: return@launch
        val updatedDocument = loadReaderDocument(updated)
        val annotations = repository.loadAnnotations(docId)
        val documents = repository.loadDocuments()
        val annotationCount = repository.loadAnnotationCount()
        val outline = repository.loadDocumentOutline(updated, updatedDocument.chunks)
        val startIndex = when (target) {
            is VeritasTextEditTarget.Part -> {
                ReaderTextModelCache.get(updatedDocument.id, updatedDocument.rawText, updatedDocument.pageCount)
                    .parts
                    .getOrNull(target.partIndex)
                    ?.sentenceStartIndex
                    ?: PlaybackStateStore.currentIndex
            }
            is VeritasTextEditTarget.SentenceRange -> target.startSentenceIndex
        }.coerceIn(0, (updatedDocument.chunks.size - 1).coerceAtLeast(0))
        withContext(Dispatchers.Main) {
            syncPlaybackStateForDocument(updatedDocument, startIndex)
            _uiState.update {
                it.copy(
                    documents = documents,
                    activeDocument = updatedDocument,
                    annotations = annotations,
                    annotationCount = annotationCount,
                    documentOutline = outline,
                    showTextEditor = false,
                    editorText = "",
                    editorTarget = null,
                    searchMatches = buildSearchMatches(updatedDocument, it.searchQuery),
                    searchCursor = 0,
                    importMessage = "Extracted text updated."
                )
            }
        }
    }
}

fun ReaderViewModel.dismissTextEditor() {
    _uiState.update { it.copy(showTextEditor = false, editorText = "", editorTarget = null) }
}

fun ReaderViewModel.openTtsDataInstaller() {
    val intent = Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    runCatching { getApplication<Application>().startActivity(intent) }
}


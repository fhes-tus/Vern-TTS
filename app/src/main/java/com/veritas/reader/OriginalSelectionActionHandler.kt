package com.veritas.reader

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.veritas.reader.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class OriginalSelectionRequest(val documentId: String, val action: ReaderSelectionAction,
    val text: String, val pageNumber: Int, val pageIsExact: Boolean = true)
internal data class OriginalBookmarkSelection(val documentId: String, val indexes: List<Int>)

/** Routes document selections to the same editor, notes, speech and search operations as text view. */
internal fun ReaderViewModel.handleOriginalSelection(context: Context, request: OriginalSelectionRequest) {
    val state = uiState.value
    val document = state.activeDocument?.takeIf { it.id == request.documentId } ?: return
    val text = request.text.trim()
    if (text.isEmpty()) return
    val action = request.action
    when (action) {
        ReaderSelectionAction.COPY -> { copyTextToClipboard(context, "Vern selection", text); return }
        ReaderSelectionAction.SHARE -> { sharePlainText(context, "Vern selection", text); return }
        ReaderSelectionAction.GOOGLE -> { openGoogleSearch(context, text); return }
        ReaderSelectionAction.READ_ALOUD -> { sendSelectionSpeechIntent(context, text); return }
        ReaderSelectionAction.EDIT_SPEECH -> {
            updateState { it.copy(showPronunciationRules = true,
                newRuleFind = text.replace(Regex("\\s+"), " ").take(120), newRuleReplaceWith = "", editingRuleId = null,
                newRuleKind = SpeechRuleKind.LITERAL, newRuleCaseSensitive = false, newRuleSkip = false, newRuleError = null) }
            return
        }
        ReaderSelectionAction.SEARCH -> {
            updateState { it.copy(showCanvasView = false) }
            updateSearchQuery(text.take(80))
            return
        }
        else -> Unit
    }
    val model = ReaderTextModelCache.get(document.id, document.rawText, document.pageCount)
    val range = PdfSelectionLocator.findExactRange(text, model, request.pageNumber, allowAdjacentPages = !request.pageIsExact)
    val match = range?.start
    if (action == ReaderSelectionAction.DEFINE) {
        showDictionaryLookup(context, text, DictionaryLookupAnchor(document.id, match?.chunkIndex ?: -1,
            match?.let { model.sentences.getOrNull(it.chunkIndex)?.text }))
        return
    }
    if (action == ReaderSelectionAction.TRANSLATE) {
        saveOriginalSelectionVocabulary(context, request.documentId, text, "Looked up translation.", match)
        openGoogleTranslate(context, text)
        return
    }
    if (action == ReaderSelectionAction.ASK_AI) {
        saveOriginalSelectionVocabulary(context, request.documentId, text, "Asked AI for explanation.", match)
        askAiWithSelection(context, state.askAiSettings, text)
        return
    }
    if (range == null) {
        Toast.makeText(context, R.string.pdf_selection_anchor_unavailable, Toast.LENGTH_SHORT).show()
        return
    }
    if (action == ReaderSelectionAction.READ_FROM_HERE) {
        moveTo(range.start.chunkIndex, autoPlay = true, forcePlaybackStart = true, charOffset = range.start.charOffset)
        return
    }
    val indexes = (range.start.chunkIndex..range.endChunkIndex).toList()
    when (action) {
        ReaderSelectionAction.BOOKMARK -> PlaybackStateStore.pendingBookmarkSelection = OriginalBookmarkSelection(request.documentId, indexes)
        ReaderSelectionAction.NOTE -> beginSentenceNote(indexes)
        ReaderSelectionAction.EDIT_TEXT -> openSelectionTextEditor(indexes)
        else -> Unit
    }
}

private fun ReaderViewModel.saveOriginalSelectionVocabulary(
    context: Context, documentId: String, text: String, explanation: String, match: PdfSelectionMatch?
) {
    if (match != null) {
        appendVocabularyWord(text, explanation, selectedSentenceIndex = match.chunkIndex)
        return
    }
    viewModelScope.launch(Dispatchers.IO) {
        val notes = repository.saveVocabularyLookup(context.applicationContext, documentId,
            VocabularyEntry(text, explanation, "(looked up: source location unavailable)", -1, null))
        withContext(Dispatchers.Main) { updateState { it.copy(generalNotes = notes) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun OriginalSelectionBookmarkSheet(viewModel: ReaderViewModel) {
    val selection = PlaybackStateStore.pendingBookmarkSelection ?: return
    val state by viewModel.uiState.collectAsState()
    if (state.activeDocument?.id != selection.documentId) return
    val indexes = selection.indexes
    val existing = state.annotations.filter { it.chunkIndex in indexes && it.type == AnnotationType.BOOKMARK }
    ModalBottomSheet(onDismissRequest = { PlaybackStateStore.pendingBookmarkSelection = null },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (existing.isEmpty()) "Highlight passage" else "Bookmark options", style = MaterialTheme.typography.titleMedium)
            Text("Select a highlight color")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("#FFE082" to "Yellow", "#A5D6A7" to "Green", "#90CAF9" to "Blue", "#F48FB1" to "Pink",
                    "#B39DDB" to "Purple", "#FFCC80" to "Orange").forEach { (hex, name) ->
                    TextButton(onClick = {
                        viewModel.addBookmarkGroup(indexes, hex)
                        PlaybackStateStore.pendingBookmarkSelection = null
                    }) {
                        Box(Modifier.size(24.dp).background(Color(android.graphics.Color.parseColor(hex)), CircleShape))
                        Spacer(Modifier.width(6.dp)); Text(name)
                    }
                }
            }
            if (existing.isNotEmpty()) TextButton(onClick = {
                existing.map { it.chunkIndex }.distinct().forEach { viewModel.toggleBookmark(it) }
                PlaybackStateStore.pendingBookmarkSelection = null
            }) { Text("Remove bookmark") }
        }
    }
}

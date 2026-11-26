package com.veritas.reader

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.pdf.selection.LinkSelection
import androidx.pdf.selection.model.TextSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun VeritasPdfViewerActivity.handleActionModeStarted(mode: android.view.ActionMode?) {
    val menu = mode?.menu ?: return
    if (pdfView?.currentSelection == null || currentFocus is android.widget.EditText) return
    val menuContext = pdfView?.context ?: this
    val selectAllTitle = menuContext.getString(android.R.string.selectAll)
    val copyTitle = menuContext.getString(android.R.string.copy)

    // AndroidX uses Menu.NONE for these items and its own click listeners. Keep
    // explicit Copy and Select all intact instead of dispatching a shared item ID.
    val nativeCopy = (menu.size() - 1 downTo 0).asSequence().map(menu::getItem).firstOrNull {
        it.itemId == android.R.id.copy || it.title?.toString() == copyTitle
    }
    for (index in menu.size() - 1 downTo 0) {
        val item = menu.getItem(index)
        when {
            item.itemId == android.R.id.selectAll || item.title?.toString() == selectAllTitle ->
                item.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
            item === nativeCopy ->
                item.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER)
            else -> item.isVisible = false
        }
    }
    menu.addReaderSelectionActions(this, document != null) { action ->
        val selection = pdfView?.currentSelection
        val text = when (selection) {
            is TextSelection -> selection.text
            is LinkSelection -> selection.linkText
            else -> null
        }?.toString()
        if (text.isNullOrBlank()) {
            Toast.makeText(activity, R.string.pdf_selection_text_unavailable, Toast.LENGTH_LONG).show()
            return@addReaderSelectionActions
        }

        val selectedPage = selection?.bounds?.firstOrNull()?.pageNum?.plus(1)
        val selectionPage = selectedPage ?: pdfView?.firstVisiblePage?.plus(1) ?: 1
        val match by lazy {
            readerTextModel?.let {
                PdfSelectionLocator.findExactRange(text, it, selectionPage, allowAdjacentPages = selectedPage == null)?.start
            }
        }
        if (action in listOf(ReaderSelectionAction.BOOKMARK, ReaderSelectionAction.NOTE, ReaderSelectionAction.EDIT_TEXT) && match == null) {
            Toast.makeText(activity, R.string.pdf_selection_anchor_unavailable, Toast.LENGTH_SHORT).show()
            return@addReaderSelectionActions
        }
        mode.finish()
        when (action) {
            ReaderSelectionAction.COPY -> copyTextToClipboard(activity, "Vern selection", text)
            ReaderSelectionAction.SHARE -> sharePlainText(activity, "Vern selection", text)
            ReaderSelectionAction.GOOGLE -> openGoogleSearch(activity, text)
            ReaderSelectionAction.DEFINE -> {
                val index = match?.chunkIndex ?: -1
                showDictionaryLookup(activity, text, DictionaryLookupAnchor(document?.id, index,
                    readerTextModel?.sentences?.getOrNull(index)?.text))
            }
            ReaderSelectionAction.TRANSLATE -> {
                appendVocabularyWord(text, "Looked up translation.", match)
                openGoogleTranslate(activity, text)
            }
            ReaderSelectionAction.ASK_AI -> {
                appendVocabularyWord(text, "Asked AI for explanation.", match)
                lifecycleScope.launch(Dispatchers.IO) {
                    val settings = repository.loadAskAiSettings()
                    withContext(Dispatchers.Main) { askAiWithSelection(activity, settings, text) }
                }
            }
            ReaderSelectionAction.READ_FROM_HERE -> jumpToSelection(match)
            ReaderSelectionAction.READ_ALOUD -> sendSelectionSpeechIntent(activity, text)
            else -> {
                val docId = document?.id ?: return@addReaderSelectionActions
                PlaybackStateStore.pendingOriginalSelection = OriginalSelectionRequest(
                    docId, action, text, selectionPage, pageIsExact = selectedPage != null
                )
                PlaybackStateStore.readerMode = ReaderMode.TEXT
                startActivity(Intent(activity, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                })
                finish()
            }
        }
    }
    if (nativeCopy != null) menu.removeItem(ReaderSelectionAction.COPY.menuId)
    mode.invalidate()
}

private fun VeritasPdfViewerActivity.appendVocabularyWord(word: String, explanation: String, match: PdfSelectionMatch?) {
    val docId = document?.id ?: return
    val index = match?.chunkIndex
    val textModel = readerTextModel
    val contextSentence = index?.let { textModel?.sentences?.getOrNull(it)?.text }
    val section = index?.let { textModel?.partForSentence(it)?.index?.plus(1) }
    val location = if (index != null && section != null) "(looked up: Section $section, sentence ${index + 1})"
        else "(looked up: source location unavailable)"
    lifecycleScope.launch(Dispatchers.IO) {
        repository.saveVocabularyLookup(activity, docId, VocabularyEntry(
            word.trim(), explanation, location, index ?: -1, contextSentence
        ))
    }
}

    private fun VeritasPdfViewerActivity.showAddNoteDialog(sentenceIndex: Int) {
        val docId = document?.id ?: return
        val input = android.widget.EditText(this).apply {
            stylePdfInput(this)
            hint = "Write sentence note..."
            setTextColor(colorTextPrimary)
            setHintTextColor(colorTextSecondary)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(16.dp, 12.dp, 16.dp, 12.dp)
        }
        val container = FrameLayout(this).apply {
            val padding = 16.dp
            setPadding(padding, 8.dp, padding, 8.dp)
            addView(input)
        }
        val titleView = TextView(this).apply {
            text = "Add note to sentence ${sentenceIndex + 1}"
            setTextColor(colorTextPrimary)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(20.dp, 16.dp, 20.dp, 4.dp)
        }
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setCustomTitle(titleView)
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotBlank()) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        repository.upsertAnnotation(
                            documentId = docId,
                            chunkIndex = sentenceIndex,
                            type = AnnotationType.NOTE,
                            note = text
                        )
                        withContext(Dispatchers.Main) {
                            Toast.makeText(activity, "Note saved.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(colorSurface))
        dialog.show()
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(colorPrimary)
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)?.setTextColor(colorTextSecondary)
    }

private fun VeritasPdfViewerActivity.jumpToSelection(match: PdfSelectionMatch?) {
    val docId = document?.id ?: return
    if (match == null) {
        Toast.makeText(this, R.string.pdf_selection_anchor_unavailable, Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(this, PlaybackService::class.java).apply {
        action = PlaybackActions.ACTION_JUMP_TO
        putExtra(PlaybackActions.EXTRA_DOCUMENT_ID, docId)
        putExtra(PlaybackActions.EXTRA_START_INDEX, match.chunkIndex)
        putExtra(PlaybackActions.EXTRA_CHAR_OFFSET, match.charOffset)
    }
    startService(PlaybackCommandSecurity.authorize(this, intent))
    Toast.makeText(this, "Reading from selection", Toast.LENGTH_SHORT).show()
    updatePlaybackControls()
}

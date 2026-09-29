package com.veritas.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun VeritasPdfViewerActivity.handleActionModeStarted(mode: android.view.ActionMode?) {
                val menu = mode?.menu ?: return

        val readItem = menu.findItem(1001) ?: menu.add(0, 1001, 1, "Read from here")
        readItem.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS or android.view.MenuItem.SHOW_AS_ACTION_WITH_TEXT)
        readItem.titleCondensed = "Read"
        readItem.isVisible = true
        readItem.isEnabled = true
        readItem.setOnMenuItemClickListener {
            performActionOnCopiedSelection(menu, mode) { text ->
                jumpToText(text)
            }
            true
        }

        val fixPronunciationItem = menu.findItem(1002) ?: menu.add(0, 1002, 2, "Fix pronunciation")
        fixPronunciationItem.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS or android.view.MenuItem.SHOW_AS_ACTION_WITH_TEXT)
        fixPronunciationItem.isVisible = true
        fixPronunciationItem.isEnabled = true
        fixPronunciationItem.setOnMenuItemClickListener {
            performActionOnCopiedSelection(menu, mode) { text ->
                PlaybackStateStore.pendingPronunciationFixWord = text.replace(Regex("\\s+"), " ").trim().take(120)
                val intent = Intent(activity, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(intent)
                finish()
            }
            true
        }

        val bookmarkItem = menu.findItem(1003) ?: menu.add(0, 1003, 3, "Add bookmark")
        bookmarkItem.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM or android.view.MenuItem.SHOW_AS_ACTION_WITH_TEXT)
        bookmarkItem.isVisible = true
        bookmarkItem.isEnabled = true
        bookmarkItem.setOnMenuItemClickListener {
            performActionOnCopiedSelection(menu, mode) { text ->
                val index = findSentenceIndexForText(text)
                lifecycleScope.launch(Dispatchers.IO) {
                    val docId = document?.id ?: return@launch
                    repository.toggleAnnotation(docId, index, AnnotationType.BOOKMARK)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(activity, "Bookmark toggled.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            true
        }

        val noteItem = menu.findItem(1004) ?: menu.add(0, 1004, 4, "Add note")
        noteItem.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM or android.view.MenuItem.SHOW_AS_ACTION_WITH_TEXT)
        noteItem.isVisible = true
        noteItem.isEnabled = true
        noteItem.setOnMenuItemClickListener {
            performActionOnCopiedSelection(menu, mode) { text ->
                val index = findSentenceIndexForText(text)
                showAddNoteDialog(index)
            }
            true
        }

        val askAiItem = menu.findItem(1005) ?: menu.add(0, 1005, 5, "Ask AI")
        askAiItem.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM or android.view.MenuItem.SHOW_AS_ACTION_WITH_TEXT)
        askAiItem.isVisible = true
        askAiItem.isEnabled = true
        askAiItem.setOnMenuItemClickListener {
            performActionOnCopiedSelection(menu, mode) { text ->
                val cleanText = text.replace(Regex("\\s+"), " ").trim()
                appendVocabularyWord(cleanText, "Asked AI for explanation.")
                lifecycleScope.launch(Dispatchers.IO) {
                    val settings = repository.loadAskAiSettings()
                    withContext(Dispatchers.Main) {
                        askAiWithSelection(activity, settings, cleanText)
                    }
                }
            }
            true
        }

        val copyItem = menu.findItem(1006) ?: menu.add(0, 1006, 6, "Copy")
        copyItem.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER)
        copyItem.isVisible = true
        copyItem.isEnabled = true
        copyItem.setOnMenuItemClickListener {
            performActionOnCopiedSelection(menu, mode) { text ->
                copyTextToClipboard(activity, "Vern selection", text)
            }
            true
        }

        val shareItem = menu.findItem(1007) ?: menu.add(0, 1007, 7, "Share")
        shareItem.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER)
        shareItem.isVisible = true
        shareItem.isEnabled = true
        shareItem.setOnMenuItemClickListener {
            performActionOnCopiedSelection(menu, mode) { text ->
                sharePlainText(activity, "Vern selection", text)
            }
            true
        }

        mode.invalidate()
    }

    private fun VeritasPdfViewerActivity.performActionOnCopiedSelection(
        menu: android.view.Menu,
        mode: android.view.ActionMode,
        onTextRetrieved: (String) -> Unit
    ) {
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val oldClip = clipboard.primaryClip
        val sentinel = "vern-pdf-selection-${System.nanoTime()}"
        clipboard.setPrimaryClip(ClipData.newPlainText("Vern selection marker", sentinel))

        val copyItem = findCopyMenuItem(menu)
        if (copyItem != null) {
            val copyStarted = performCopyMenuAction(menu, copyItem)
            if (!copyStarted) {
                restoreClipboard(clipboard, oldClip)
                Toast.makeText(activity, "Copy action did not start.", Toast.LENGTH_SHORT).show()
                mode.finish()
                return
            }
            pollCopiedSelection(clipboard, oldClip, sentinel, mode, 0, onTextRetrieved)
        } else {
            Toast.makeText(activity, "Copy action is not available.", Toast.LENGTH_SHORT).show()
            restoreClipboard(clipboard, oldClip)
            mode.finish()
        }
    }

    private fun VeritasPdfViewerActivity.pollCopiedSelection(
        clipboard: ClipboardManager,
        previousClip: ClipData?,
        sentinel: String,
        mode: android.view.ActionMode,
        attempt: Int,
        onTextRetrieved: (String) -> Unit
    ) {
        val delayMs = when (attempt) {
            0 -> 180L
            1 -> 260L
            else -> 360L
        }
        window.decorView.postDelayed({
            val newClip = clipboard.primaryClip
            val selectedText = newClip?.getItemAt(0)?.coerceToText(activity)?.toString()
            if (!selectedText.isNullOrBlank() && selectedText != sentinel) {
                restoreClipboard(clipboard, previousClip)
                mode.finish()
                onTextRetrieved(selectedText)
            } else if (attempt < 2) {
                pollCopiedSelection(clipboard, previousClip, sentinel, mode, attempt + 1, onTextRetrieved)
            } else {
                Toast.makeText(activity, "Could not extract selected text.", Toast.LENGTH_SHORT).show()
                restoreClipboard(clipboard, previousClip)
                mode.finish()
            }
        }, delayMs)
    }

    private fun VeritasPdfViewerActivity.findSentenceIndexForText(selectedText: String): Int {
        val clean = selectedText.trim()
        if (clean.isBlank()) return PlaybackStateStore.currentIndex
        val model = readerTextModel ?: return PlaybackStateStore.currentIndex
        val index = model.sentences.indexOfFirst { it.text.contains(clean, ignoreCase = true) }
        if (index != -1) return index
        val bestMatch = model.sentences.maxByOrNull { sentence ->
            val common = sentence.text.split(" ").filter { it.length > 3 && clean.contains(it, ignoreCase = true) }
            common.size
        }
        return bestMatch?.index ?: PlaybackStateStore.currentIndex
    }

    private fun VeritasPdfViewerActivity.appendVocabularyWord(word: String, explanation: String) {
        val docId = document?.id ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val existing = repository.loadGeneralNotes().toMutableList()
            val targetTitle = "__vocab__$docId"
            val vocabIndex = existing.indexOfFirst { it.title == targetTitle }
            val now = System.currentTimeMillis()
            val formattedTime = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(now))

            val currentIndex = findSentenceIndexForText(word)
            val textModel = readerTextModel
            val part = textModel?.partForSentence(currentIndex)
            val sectionNum = (part?.index ?: 0) + 1

            val entryText = buildString {
                appendLine(word.trim())
                appendLine("  $explanation")
                appendLine("  (looked up: Section $sectionNum, sentence ${currentIndex + 1})")
                append("  [$formattedTime]")
            }

            if (vocabIndex != -1) {
                val oldNote = existing[vocabIndex]
                val newContent = if (oldNote.content.isBlank()) entryText else oldNote.content + "\n\n" + entryText
                existing[vocabIndex] = oldNote.copy(content = newContent, updatedAt = now)
            } else {
                val newNote = GeneralNote(
                    id = java.util.UUID.randomUUID().toString(),
                    title = targetTitle,
                    content = entryText,
                    updatedAt = now
                )
                existing.add(0, newNote)
            }

            repository.saveGeneralNotes(existing)
        }
    }

    private fun VeritasPdfViewerActivity.showAddNoteDialog(sentenceIndex: Int) {
        val docId = document?.id ?: return
        val input = android.widget.EditText(this).apply {
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

    private fun findCopyMenuItem(menu: android.view.Menu): android.view.MenuItem? {
        menu.findItem(android.R.id.copy)?.let { return it }
        for (index in 0 until menu.size()) {
            val item = menu.getItem(index)
            val title = item.title?.toString().orEmpty()
            val description = item.contentDescription?.toString().orEmpty()
            if (title.contains("copy", ignoreCase = true) || description.contains("copy", ignoreCase = true)) {
                return item
            }
            item.subMenu?.let { subMenu ->
                findCopyMenuItem(subMenu)?.let { return it }
            }
        }
        return null
    }

    private fun performCopyMenuAction(menu: android.view.Menu, item: android.view.MenuItem): Boolean {
        if (menu.performIdentifierAction(item.itemId, 0)) return true
        if (item.itemId != android.R.id.copy && menu.performIdentifierAction(android.R.id.copy, 0)) return true
        item.subMenu?.let { subMenu ->
            findCopyMenuItem(subMenu)?.let { nested ->
                if (subMenu.performIdentifierAction(nested.itemId, 0)) return true
            }
        }
        return false
    }

    private fun restoreClipboard(clipboard: ClipboardManager, previousClip: ClipData?) {
        if (previousClip != null) {
            clipboard.setPrimaryClip(previousClip)
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            clipboard.clearPrimaryClip()
        }
    }

    private fun VeritasPdfViewerActivity.jumpToText(selectedText: String) {
        val docId = document?.id ?: return
        val currentPage = pdfView?.firstVisiblePage?.plus(1)
        val model = readerTextModel
        val match = if (model != null) {
            PdfSelectionLocator.findMatch(
                selectedText = selectedText,
                model = model,
                currentPage = currentPage,
                preferredSentenceIndex = PlaybackStateStore.currentIndex
            )
        } else {
            PdfSelectionLocator.findMatch(selectedText, extractedChunks)
        }

        if (match != null) {
            val intent = Intent(this, PlaybackService::class.java).apply {
                action = PlaybackActions.ACTION_JUMP_TO
                putExtra(PlaybackActions.EXTRA_DOCUMENT_ID, docId)
                putExtra(PlaybackActions.EXTRA_START_INDEX, match.chunkIndex)
                putExtra(PlaybackActions.EXTRA_CHAR_OFFSET, 0)
            }
            startService(intent)
            Toast.makeText(this, "Reading from selection", Toast.LENGTH_SHORT).show()
            updatePlaybackControls()
        } else {
            Toast.makeText(this, "Text not found in extracted document.", Toast.LENGTH_SHORT).show()
        }
    }



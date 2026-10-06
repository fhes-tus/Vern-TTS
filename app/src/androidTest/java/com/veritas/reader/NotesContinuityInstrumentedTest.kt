package com.veritas.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.screens.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NotesContinuityInstrumentedTest {
    @get:Rule val rule = createComposeRule()
    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = java.io.File(context.getExternalFilesDir(null), "stage3-review").apply { mkdirs() }
        java.io.File(directory, name).outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun openingSelectingAndClosingAnUnchangedNoteNeverSaves() {
        var saves = 0
        var closed = false
        rule.setContent { MaterialTheme {
            GeneralNotesEditor(GeneralNote("unchanged", "Old note", "Original paragraph", 123),
                onSave = { _, _, _, _, _, _, _, _, _, _ -> saves++ }, onDelete = {}, onDismiss = { closed = true })
        } }
        rule.onNodeWithText("Original paragraph").performClick()
        rule.onNodeWithText("Original paragraph").performTextInputSelection(androidx.compose.ui.text.TextRange(2))
        rule.mainClock.advanceTimeBy(1200)
        rule.onNodeWithContentDescription("Save Note").performClick()
        rule.runOnIdle { assertEquals(0, saves); assertTrue(closed) }
    }

    @Test fun retainedLinkCardSurvivesTextDeletionAndUndoSurvivesReopening() {
        val url = "https://example.org/reflection"
        val noteState = mutableStateOf(GeneralNote("continuity", "Reflection", NoteLinks.write("Words $url", listOf(NoteLink(url, "A reflection"))), 123))
        val visible = mutableStateOf(true)
        rule.setContent { MaterialTheme {
            if (visible.value) GeneralNotesEditor(noteState.value,
                onSave = { title, content, _, _, _, _, _, _, close, _ ->
                    noteState.value = noteState.value.copy(title = title, content = content)
                    if (close) visible.value = false
                }, onDelete = {}, onDismiss = { visible.value = false })
        } }
        rule.onNodeWithText("Words $url").performTextReplacement("Words")
        rule.onNodeWithTag("note_link_preview").assertExists()
        rule.onNodeWithTag("note_link_preview").performScrollTo()
        capture("notes-retained-link.png")
        rule.onNodeWithContentDescription("Save Note").performClick()
        rule.runOnIdle { assertEquals(url, NoteLinks.read(noteState.value.content).single().url); visible.value = true }
        rule.onNodeWithContentDescription("Undo").assertIsEnabled().performClick()
        rule.onNodeWithText("Words $url").assertExists()
        rule.onNodeWithContentDescription("Redo").performClick()
        rule.onNodeWithText("Words").assertExists()
    }

    @Test fun editingAChecklistRetainsItsDocumentAttachment() {
        val file = NoteBlock.File("/data/notes_media/owned.pdf", "Attached document.pdf", 50)
        val body = VeritasNoteEditing.serializeNoteBlocks(listOf(file)) + "\n[ ] Original task"
        var saved = ""
        rule.setContent { MaterialTheme {
            GeneralNotesEditor(GeneralNote("checklist-file", "Tasks", body, 123, isChecklist = true),
                onSave = { _, content, _, _, _, _, _, _, _, _ -> saved = content }, onDelete = {}, onDismiss = {})
        } }
        rule.onNodeWithText("Original task").performTextReplacement("Edited task")
        rule.onNodeWithContentDescription("Save Note").performClick()
        rule.runOnIdle {
            assertEquals(file, VeritasNoteEditing.parseNoteBlocks(saved).filterIsInstance<NoteBlock.File>().single())
            assertTrue(saved.contains("[ ] Edited task"))
        }
    }

    @Test fun fileWithLegacyBracketsRendersAsAnIconWithoutPathText() {
        val file = "[file:Report [final] (2).pdf:1125506](/data/user/0/com.veritas.reader/files/notes_media/owned.pdf)"
        rule.setContent { MaterialTheme {
            GeneralNotesEditor(GeneralNote("file", "File note", file, 123), onSave = { _, _, _, _, _, _, _, _, _, _ -> }, onDelete = {}, onDismiss = {})
        } }
        rule.onNodeWithText("Report [final] (2).pdf").assertExists()
        rule.onAllNodes(hasText("/data/user/0", substring = true)).assertCountEquals(0)
        capture("notes-file-icon.png")
    }

    @Test fun removingALinkCardDoesNotRecreateItOnReopening() {
        val url = "https://example.org/reflection"
        val note = mutableStateOf(GeneralNote("dismiss-link", "Reflection", NoteLinks.write(url, listOf(NoteLink(url, "Reflection"))), 123))
        val visible = mutableStateOf(true)
        rule.setContent { MaterialTheme {
            if (visible.value) GeneralNotesEditor(note.value,
                onSave = { title, content, _, _, _, _, _, _, close, _ ->
                    note.value = note.value.copy(title = title, content = content)
                    if (close) visible.value = false
                }, onDelete = {}, onDismiss = { visible.value = false })
        } }
        rule.onNodeWithContentDescription("Link preview options").performClick()
        InstrumentationRegistry.getInstrumentation().let { instrumentation ->
            val directory = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "stage3-review").apply { mkdirs() }
            instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                java.io.File(directory, "ui-polish-link-overflow.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
        }
        rule.onNodeWithText("Copy URL").performClick()
        rule.runOnIdle {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            assertEquals(url, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        }
        rule.onNodeWithContentDescription("Link preview options").performClick()
        rule.onNodeWithText("Remove").performClick()
        rule.onNodeWithContentDescription("Save Note").performClick()
        rule.runOnIdle { visible.value = true }
        rule.onNodeWithText(url).assertExists()
        rule.onAllNodesWithTag("note_link_preview").assertCountEquals(0)
    }
}

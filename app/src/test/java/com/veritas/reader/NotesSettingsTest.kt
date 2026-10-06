package com.veritas.reader

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.veritas.reader.ui.NotesPaperTemplate
import com.veritas.reader.ui.NotesSettings
import com.veritas.reader.ui.QuoteCitationStyle
import com.veritas.reader.ui.screens.VeritasNoteEditing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesSettingsTest {

    @Test fun noteCreationTimeSurvivesEditsAndLegacyJsonUsesLastKnownTime() {
        val note = GeneralNote("sort-fixture", "Note", "Original", 100L)
        val edited = note.copy(content = "Edited", updatedAt = 500L)
        assertEquals(100L, GeneralNote.fromJson(edited.toJson()).createdAt)
        val oldJson = edited.toJson().apply { remove("createdAt") }
        assertEquals(500L, GeneralNote.fromJson(oldJson).createdAt)
    }

    @Test fun preferencesRoundTripAndInvalidValuesAreBounded() {
        val configured = NotesSettings(isGridView = false, defaultSortOrder = "title", previewLines = 0,
            editorFontScale = 1.25f, editorLineSpacing = 1.5f, newNoteIsChecklist = true,
            richAttachmentPreviews = false, paperTemplate = NotesPaperTemplate.DOTS)
        assertEquals(configured, NotesSettings.fromJson(configured.toJson()))
        val invalid = NotesSettings(editorFontScale = Float.NaN, editorLineSpacing = 99f,
            previewLines = -20, defaultSortOrder = "unknown").normalized()
        assertEquals(1f, invalid.editorFontScale, 0.001f)
        assertEquals(1.5f, invalid.editorLineSpacing, 0.001f)
        assertEquals(0, invalid.previewLines)
        assertEquals("date", invalid.defaultSortOrder)
        assertEquals(NotesSettings(), NotesSettings.fromJson(org.json.JSONObject("{}")))
    }

    @Test
    fun testNotesSettingsDefaults() {
        val settings = NotesSettings()
        assertEquals(NotesPaperTemplate.BLANK, settings.paperTemplate)
        assertEquals(1.0f, settings.editorFontScale, 0.001f)
        assertEquals(5, settings.previewLines)
        assertTrue(settings.moveCheckedToBottom)
        assertFalse(settings.addNewItemsToTop)
        assertTrue(settings.showRichLinkPreviews)
        assertTrue(settings.showBookSourceBadges)
        assertTrue(settings.voiceMemoWaveformPreview)
        assertEquals(QuoteCitationStyle.MARKDOWN, settings.quoteCitationFormat)
        assertTrue(settings.isGridView)
        assertEquals("date", settings.defaultSortOrder)
        assertTrue(settings.showAttachmentBadges)
        assertTrue(settings.richAttachmentPreviews)
    }

    @Test
    fun testNotesSettingsEnums() {
        assertEquals(4, NotesPaperTemplate.values().size)
        assertTrue(NotesPaperTemplate.values().contains(NotesPaperTemplate.BLANK))
        assertTrue(NotesPaperTemplate.values().contains(NotesPaperTemplate.RULED))
        assertTrue(NotesPaperTemplate.values().contains(NotesPaperTemplate.GRID))
        assertTrue(NotesPaperTemplate.values().contains(NotesPaperTemplate.DOTS))

        assertEquals(3, QuoteCitationStyle.values().size)
        assertTrue(QuoteCitationStyle.values().contains(QuoteCitationStyle.MARKDOWN))
        assertTrue(QuoteCitationStyle.values().contains(QuoteCitationStyle.CLEAN))
        assertTrue(QuoteCitationStyle.values().contains(QuoteCitationStyle.ACADEMIC))
    }

    @Test
    fun testNotesSettingsCopy() {
        val initial = NotesSettings()
        val modified = initial.copy(
            paperTemplate = NotesPaperTemplate.GRID,
            editorFontScale = 1.25f,
            previewLines = 3,
            moveCheckedToBottom = false,
            voiceMemoWaveformPreview = false,
            isGridView = false
        )
        assertEquals(NotesPaperTemplate.GRID, modified.paperTemplate)
        assertEquals(1.25f, modified.editorFontScale, 0.001f)
        assertEquals(3, modified.previewLines)
        assertFalse(modified.moveCheckedToBottom)
        assertFalse(modified.voiceMemoWaveformPreview)
        assertFalse(modified.isGridView)
        // Unmodified fields retained
        assertTrue(modified.showBookSourceBadges)
    }

    // ── VeritasNoteEditing: Checklist Reordering & Insertion ─────────────────

    @Test
    fun testToggleChecklistItemMoveCheckedToBottom() {
        val list = mutableListOf(
            false to TextFieldValue("First", TextRange(5)),
            false to TextFieldValue("Second", TextRange(6)),
            false to TextFieldValue("Third", TextRange(5))
        )

        // Check the first item; it should be moved to the bottom (index 2)
        val newIdx = VeritasNoteEditing.toggleChecklistItem(
            items = list,
            index = 0,
            isChecked = true,
            moveCheckedToBottom = true
        )
        assertEquals(2, newIdx)
        assertEquals("Second", list[0].second.text)
        assertFalse(list[0].first)
        assertEquals("Third", list[1].second.text)
        assertFalse(list[1].first)
        assertEquals("First", list[2].second.text)
        assertTrue(list[2].first)
    }

    @Test
    fun testToggleChecklistItemUncheckMovesAboveChecked() {
        val list = mutableListOf(
            false to TextFieldValue("Active 1", TextRange(8)),
            true to TextFieldValue("Done 1", TextRange(6)),
            true to TextFieldValue("Done 2", TextRange(6))
        )

        // Uncheck "Done 2" (at index 2); it should move before "Done 1" (at index 1)
        val newIdx = VeritasNoteEditing.toggleChecklistItem(
            items = list,
            index = 2,
            isChecked = false,
            moveCheckedToBottom = true
        )
        assertEquals(1, newIdx)
        assertEquals("Active 1", list[0].second.text)
        assertFalse(list[0].first)
        assertEquals("Done 2", list[1].second.text)
        assertFalse(list[1].first)
        assertEquals("Done 1", list[2].second.text)
        assertTrue(list[2].first)
    }

    @Test
    fun testToggleChecklistItemStayInPlaceWhenMoveCheckedDisabled() {
        val list = mutableListOf(
            false to TextFieldValue("Task A", TextRange(6)),
            false to TextFieldValue("Task B", TextRange(6))
        )

        val newIdx = VeritasNoteEditing.toggleChecklistItem(
            items = list,
            index = 0,
            isChecked = true,
            moveCheckedToBottom = false
        )
        assertEquals(0, newIdx)
        assertEquals("Task A", list[0].second.text)
        assertTrue(list[0].first)
        assertEquals("Task B", list[1].second.text)
        assertFalse(list[1].first)
    }

    @Test
    fun testToggleChecklistItemOutOfBoundsSafe() {
        val list = mutableListOf(
            false to TextFieldValue("Only item", TextRange(9))
        )
        val idx = VeritasNoteEditing.toggleChecklistItem(list, 5, true, true)
        assertEquals(5, idx)
        assertFalse(list[0].first)
    }

    @Test
    fun testAddNewChecklistItemToTop() {
        val list = mutableListOf(
            false to TextFieldValue("Item 1", TextRange(6)),
            false to TextFieldValue("Item 2", TextRange(6))
        )

        val insertedIdx = VeritasNoteEditing.addNewChecklistItem(
            items = list,
            addNewItemsToTop = true,
            moveCheckedToBottom = true,
            text = "Top item"
        )
        assertEquals(0, insertedIdx)
        assertEquals(3, list.size)
        assertEquals("Top item", list[0].second.text)
        assertEquals("Item 1", list[1].second.text)
    }

    @Test
    fun testAddNewChecklistItemToBottomBeforeChecked() {
        val list = mutableListOf(
            false to TextFieldValue("Unchecked 1", TextRange(11)),
            true to TextFieldValue("Checked 1", TextRange(9))
        )

        val insertedIdx = VeritasNoteEditing.addNewChecklistItem(
            items = list,
            addNewItemsToTop = false,
            moveCheckedToBottom = true,
            text = "New item"
        )
        assertEquals(1, insertedIdx)
        assertEquals("New item", list[1].second.text)
        assertFalse(list[1].first)
        assertEquals("Checked 1", list[2].second.text)
        assertTrue(list[2].first)
    }

    @Test
    fun testAddNewChecklistItemAppendsToEndWhenNoChecked() {
        val list = mutableListOf(
            false to TextFieldValue("Unchecked 1", TextRange(11)),
            false to TextFieldValue("Unchecked 2", TextRange(11))
        )

        val insertedIdx = VeritasNoteEditing.addNewChecklistItem(
            items = list,
            addNewItemsToTop = false,
            moveCheckedToBottom = true,
            text = "End item"
        )
        assertEquals(2, insertedIdx)
        assertEquals("End item", list[2].second.text)
    }

    // ── VeritasNoteEditing: Quote Citation Formatting ────────────────────────

    @Test
    fun testFormatQuoteCitationMarkdown() {
        val formatted = VeritasNoteEditing.formatQuoteCitation(
            quote = "Knowledge is power.",
            bookTitle = "Meditations",
            author = "Marcus Aurelius",
            style = QuoteCitationStyle.MARKDOWN
        )
        assertEquals("> \"Knowledge is power.\"\n> — Marcus Aurelius, Meditations", formatted)
    }

    @Test
    fun testFormatQuoteCitationClean() {
        val formatted = VeritasNoteEditing.formatQuoteCitation(
            quote = "To be or not to be.",
            bookTitle = "Hamlet",
            author = "Shakespeare",
            style = QuoteCitationStyle.CLEAN
        )
        assertEquals("\"To be or not to be.\"\n— Shakespeare, Hamlet", formatted)
    }

    @Test
    fun testFormatQuoteCitationAcademic() {
        val formatted = VeritasNoteEditing.formatQuoteCitation(
            quote = "The unexamined life is not worth living.",
            bookTitle = "Apology",
            author = "Plato",
            style = QuoteCitationStyle.ACADEMIC
        )
        assertEquals("\"The unexamined life is not worth living.\" (In: Apology by Plato)", formatted)
    }

    @Test
    fun testFormatQuoteCitationTrimsExistingQuotes() {
        val formatted = VeritasNoteEditing.formatQuoteCitation(
            quote = "“Hello world”",
            bookTitle = "Book",
            author = "",
            style = QuoteCitationStyle.CLEAN
        )
        assertEquals("\"Hello world\"\n— Book", formatted)
    }

    @Test
    fun testFormatQuoteCitationWithoutMetadata() {
        val formatted = VeritasNoteEditing.formatQuoteCitation(
            quote = "Standalone insight.",
            bookTitle = "",
            author = "",
            style = QuoteCitationStyle.MARKDOWN
        )
        assertEquals("> \"Standalone insight.\"", formatted)
    }
}

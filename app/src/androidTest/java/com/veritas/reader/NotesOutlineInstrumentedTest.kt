package com.veritas.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.veritas.reader.ui.screens.GeneralNotesEditor
import com.veritas.reader.ui.screens.SmartOutlineDialog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NotesOutlineInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    @Test fun undoRedoAndRecreationRetainTheActualSavedDraft() {
        var saved = ""
        val note = GeneralNote("editor-regression", "Example", "Original paragraph", 1)
        val restoration = StateRestorationTester(rule)
        restoration.setContent { MaterialTheme {
            GeneralNotesEditor(note, onSave = { _, content, _, _, _, _, _, _, _, _ -> saved = content }, onDelete = {}, onDismiss = {})
        } }
        rule.onNodeWithText("Original paragraph").performTextReplacement("Edited paragraph")
        rule.onNodeWithContentDescription("Undo").performClick()
        rule.onNodeWithText("Original paragraph").assertExists()
        rule.onNodeWithContentDescription("Redo").performClick()
        rule.onNodeWithText("Edited paragraph").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("Edited paragraph").assertExists()
        rule.onNodeWithContentDescription("Save Note").performClick()
        rule.runOnIdle { assertEquals("Edited paragraph", saved) }
    }

    @Test fun checklistUndoPreservesCheckedStateAndSwitchingRetainsRecentText() {
        var saved = ""
        val note = GeneralNote("checklist-regression", "List", "[x] Completed\n[ ] Pending", 1, isChecklist = true)
        rule.setContent { MaterialTheme {
            GeneralNotesEditor(note, onSave = { _, content, _, _, _, _, _, _, _, _ -> saved = content }, onDelete = {}, onDismiss = {})
        } }
        rule.onAllNodes(isToggleable())[0].assertIsOn().performClick()
        rule.onNodeWithContentDescription("Undo").performClick()
        rule.onAllNodes(isToggleable())[0].assertIsOn()
        rule.onNodeWithText("Pending").performTextReplacement("Latest item")
        rule.onNodeWithContentDescription("Save Note").performClick()
        rule.runOnIdle { assertEquals("[x] Completed\n[ ] Latest item", saved) }
    }

    @Test fun outlinePreservesEmbeddedNumeralsAndAllowsAValidatedPageJump() {
        val raw = "[[VERITAS_PAGE:1]]\nChapter seven.\n\n[[VERITAS_PAGE:12]]\nLast page."
        val model = ReaderTextIndex.build(raw, storedPageCount = 12)
        val document = ReaderDocument("outline-test", "Test book", "PDF", raw, model.sentences.map { it.text }, pageCount = 12)
        var page: Int? = null
        rule.setContent { MaterialTheme {
            SmartOutlineDialog(document, listOf(VeritasDocumentOutlineEntry("VII", 0, 1, 0, "PDF table of contents")), 0,
                onJumpToDestination = { target, _ -> page = target }, onDismiss = {}, readerModel = model)
        } }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("VII").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("VII").assertExists()
        rule.onNodeWithText("Jump to page (1–12)").performTextInput("13")
        rule.onNodeWithText("Go").assertIsNotEnabled()
        rule.onNodeWithText("Jump to page (1–12)").performTextReplacement("12")
        rule.onNodeWithText("Go").performClick()
        rule.runOnIdle { assertEquals(12, page) }
    }
}

package com.veritas.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.veritas.reader.ui.ReaderUiState
import com.veritas.reader.ui.screens.StudyBookPickerDialog
import com.veritas.reader.ui.screens.StudyHubOverview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StudyHubOverviewUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun overviewShowsSavedCountsAndOpensEveryCollection() {
        var selected = ""
        val cards = listOf(FlashcardProgress("one", front = "Q", back = "A"))
        rule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    StudyHubOverview(
                        state = ReaderUiState(flashcards = cards), sets = emptyList(), vocabularyCount = 5,
                        review = StudyReviewSummary.from(cards, 100), onSelectSection = { selected = it },
                        onStartReview = {}, onOpenDeck = {}, onImportFlashcards = {}, onImportQuiz = {},
                        onCreateWithAi = {}, onSetupAi = {}
                    )
                }
            }
        }
        rule.onNodeWithText("1 flashcard due").assertExists()
        rule.onNodeWithText("1 card").assertExists()
        rule.onNodeWithText("5 words").assertExists()
        listOf("Flashcards", "Quizzes", "Vocabulary", "Bookmarks", "Booknotes", "History").forEach { title ->
            rule.onNodeWithText(title).performScrollTo().performClick()
            assertEquals(if (title == "Vocabulary") "Vocab" else title, selected)
        }
        rule.onNodeWithText("Your decks").assertDoesNotExist()
    }

    @Test fun aiBookPickerUsesTheExplicitlySelectedBook() {
        fun book(id: String, title: String) = SavedDocument(id, title, "$id.txt", "TXT", 1, 1, 0, 1, 10, "")
        val first = book("one", "First book")
        val second = book("two", "Chosen book")
        var selected: SavedDocument? = null
        rule.setContent {
            MaterialTheme { StudyBookPickerDialog(listOf(first, second), { selected = it }, {}, {}) }
        }
        rule.onNodeWithText("Chosen book").performClick()
        assertEquals(second, selected)
    }
}

package com.veritas.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class OriginalCanvasInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    @Test fun positionedSlidesKeepNavigationNotesAndTextActionsUsable() {
        val current = mutableIntStateOf(1)
        val notes = mutableStateOf(false)
        var selected = ""
        rule.setContent { MaterialTheme {
            val number = current.intValue
            PresentationSlideCanvas(
                PptxSlideContent(number, listOf("Title $number"), emptyList(), listOf("Notes for slide $number"), emptyList(),
                    PptxSlideLayout(16f / 9, 0xffffffff, listOf(PptxVisualElement(.1f, .1f, .8f, .4f, "Title $number", fontWidthFraction = .06f)))),
                2, emptyList(), showNotes = notes.value, onToggleNotes = { notes.value = !notes.value },
                onNextSlide = { current.intValue++ }, onPrevSlide = { current.intValue-- }, onToggleBars = {},
                onSelectText = { selected = it }, modifier = Modifier.fillMaxSize())
        } }
        rule.onNodeWithText("Title 1").performClick()
        rule.runOnIdle { assertEquals("Title 1", selected) }
        rule.onNodeWithText("Notes").performClick()
        rule.onNodeWithText("Notes for slide 1").assertIsDisplayed()
        rule.onNodeWithContentDescription("Next slide").performClick()
        rule.onNodeWithText("Title 2").assertIsDisplayed()
        rule.onNodeWithText("Notes for slide 2").assertIsDisplayed()
        rule.onNodeWithContentDescription("Next slide").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Previous slide").performClick()
        rule.onNodeWithText("Title 1").assertIsDisplayed()
    }

    @Test fun wordPagesScrollLongContentAndResetAtTheNextPage() {
        val current = mutableIntStateOf(1)
        rule.setContent { MaterialTheme {
            val number = current.intValue
            val blocks = if (number == 1) List(30) { DocxBlock.Paragraph("Paragraph $it: " + "Readable content. ".repeat(10)) }
                else listOf(DocxBlock.Heading(1, "Second page heading"), DocxBlock.Table(listOf(listOf("Column one", "Column two"), listOf("Value one", "Value two"))))
            DocxDocumentCanvas("Original Word test", DocxPage(number, blocks), 2,
                onNextPage = { current.intValue++ }, onPrevPage = { current.intValue-- }, onToggleBars = {}, modifier = Modifier.fillMaxSize())
        } }
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(29)
        rule.onNodeWithText("Paragraph 29:", substring = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("Next Page").performClick()
        rule.onNodeWithText("Second page heading").assertIsDisplayed()
        rule.onNodeWithText("Column one").assertIsDisplayed()
    }
}

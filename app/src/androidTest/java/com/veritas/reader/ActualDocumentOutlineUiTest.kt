package com.veritas.reader

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ActualDocumentOutlineUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun closingOutlinePreservesOriginalDocumentAndPageAndJumpStaysInOriginalView() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks"))
        val repository = DocumentRepository(context)
        val source = File(context.cacheDir, "original-outline.pdf")
        val pdf = PdfDocument()
        try {
            repeat(3) { index ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(400, 800, index + 1).create())
                page.canvas.drawText("Chapter ${index + 1}", 40f, 100f, Paint().apply { textSize = 24f })
                pdf.finishPage(page)
            }
            source.outputStream().use(pdf::writeTo)
        } finally { pdf.close() }
        val saved = repository.createDocument("Original reader outline test",
            (1..3).joinToString("\n\n") { "[[VERITAS_PAGE:$it]]\nCHAPTER $it\nThis is the body of chapter $it." },
            "PDF", Uri.fromFile(source), source.name, "application/pdf", 3)
        var readerClosed = false
        try {
            rule.setContent { VeritasTheme {
                ActualDocumentView(saved, repository, activeSentencePage = 2, activeSentenceText = "", isPlaying = false,
                    statusMessage = "", queueCount = 0, rate = 1f, pitch = 1f, fontSizeSp = 18,
                    canGoPrevious = true, canGoNext = true, onPrevious = {}, onPlayPause = {}, onNext = {},
                    onPageChanged = { _, _ -> }, onOpenExternal = {}, onRateChange = {}, onPitchChange = {},
                    onFontSizeChange = {}, onOpenVoiceStudio = {}, voices = emptyList(), voiceSettings = VoiceSettings(), onVoiceSelected = {},
                    onClose = { readerClosed = true })
            } }
            rule.waitUntil(15_000) { rule.onAllNodesWithText("Page 2 of 3").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithContentDescription("Table of Contents").performClick()
            rule.onNodeWithTag("reader-outline-screen").assertIsDisplayed()
            rule.onNodeWithContentDescription("Close outline").performClick()
            rule.onNodeWithText("Page 2 of 3").assertIsDisplayed()
            rule.onNodeWithContentDescription("Table of Contents").performClick()
            androidx.test.espresso.Espresso.pressBack()
            rule.onNodeWithTag("reader-outline-screen").assertDoesNotExist()
            rule.onNodeWithText("Page 2 of 3").assertIsDisplayed()
            rule.onNodeWithContentDescription("Table of Contents").performClick()
            rule.onNodeWithText("Page (1–3)").performTextInput("3")
            rule.onNodeWithText("Go").performClick()
            rule.onNodeWithText("Page 3 of 3").assertIsDisplayed()
            rule.runOnIdle { assertFalse("Outline dismissal must not close the original reader", readerClosed) }
        } finally {
            repository.deleteDocument(saved.id)
            source.delete()
        }
    }
}

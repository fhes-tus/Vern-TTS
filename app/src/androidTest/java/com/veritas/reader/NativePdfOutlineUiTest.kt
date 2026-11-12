package com.veritas.reader

import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class NativePdfOutlineUiTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun pdfOutlineClosesBackToTheSameNativeViewportAndNavigatesNestedDestinations() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".checks"))
        val repository = DocumentRepository(context)
        val old = repository.loadReaderSettings()
        val file = File(context.cacheDir, "outline-navigation.pdf")
        val pdf = PdfDocument()
        try {
            repeat(4) { index ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(400, 800, index + 1).create())
                page.canvas.drawText("Outline page ${index + 1}", 40f, 100f, Paint().apply { textSize = 24f })
                pdf.finishPage(page)
            }
            file.outputStream().use(pdf::writeTo)
        } finally { pdf.close() }
        var saved: SavedDocument? = null
        fun await(label: String, predicate: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + 15_000
            while (!predicate()) { check(SystemClock.elapsedRealtime() < deadline) { label }; SystemClock.sleep(100) }
        }
        try {
            val fixture = repository.createDocument("Native outline test", (1..4).joinToString("\n\n") { "[[VERITAS_PAGE:$it]]\nOutline page $it." },
                "PDF", Uri.fromFile(file), file.name, "application/pdf", 4)
            saved = fixture
            repository.saveReaderSettings(old.copy(pdfNavigationMode = "swipe", reduceMotion = false))
            ActivityScenario.launch<VeritasPdfViewerActivity>(Intent(context, VeritasPdfViewerActivity::class.java)
                .putExtra("document_id", fixture.id)).use { scenario ->
                var ready = false
                await("Native PDF viewport and metadata ready") {
                    scenario.onActivity { ready = it.pdfView?.pdfDocument?.pageCount == 4 && it.pdfFitWidthZoom > 0f && !it.isExtractingToc }
                    ready
                }
                var viewport: androidx.pdf.view.PdfView? = null
                scenario.onActivity {
                    viewport = it.pdfView
                    it.pdfTocItems = listOf(PdfTocItem("Chapter one", 1, 0), PdfTocItem("PDF topic", 2, 1, 1),
                        PdfTocItem("Nested PDF section", 3, 2, 2), PdfTocItem("Chapter two", 4, 3))
                    it.showChrome(keepVisible = true)
                    requireNotNull(it.outlineButton).performClick()
                }
                rule.onNodeWithTag("reader-outline-screen").assertIsDisplayed()
                rule.onNodeWithContentDescription("Collapse PDF topic").performClick()
                rule.onNodeWithText("Nested PDF section").assertDoesNotExist()
                rule.onNodeWithContentDescription("Expand PDF topic").performClick()
                rule.onNodeWithContentDescription("Close outline").performClick()
                rule.onNodeWithTag("reader-outline-screen").assertDoesNotExist()
                scenario.onActivity {
                    assertSame(viewport, it.pdfView)
                    assertNull(it.outlineHost)
                    assertEquals(0, it.pdfNavigationPage)
                    requireNotNull(it.outlineButton).performClick()
                }
                androidx.test.espresso.Espresso.pressBack()
                rule.onNodeWithTag("reader-outline-screen").assertDoesNotExist()
                scenario.onActivity {
                    assertSame(viewport, it.pdfView)
                    assertFalse(it.isFinishing)
                    requireNotNull(it.outlineButton).performClick()
                }
                rule.waitForIdle()
                SystemClock.sleep(150)
                val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                try {
                    val target = File(context.getExternalFilesDir(null), "ReaderOutline/native-pdf-outline.png")
                    target.parentFile!!.mkdirs()
                    target.outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                } finally { screenshot.recycle() }
                rule.onNodeWithText("Nested PDF section").performClick()
                rule.waitForIdle()
                scenario.onActivity { assertNull("The outline closes before native navigation is checked", it.outlineHost) }
                await("Nested outline destination navigates the real PDF") {
                    scenario.onActivity { ready = it.pdfNavigationPage == 2 && it.outlineHost == null }
                    ready
                }
                scenario.onActivity { assertSame(viewport, it.pdfView) }
            }
        } finally {
            saved?.let { repository.deleteDocument(it.id) }
            repository.saveReaderSettings(old)
            file.delete()
        }
    }
}

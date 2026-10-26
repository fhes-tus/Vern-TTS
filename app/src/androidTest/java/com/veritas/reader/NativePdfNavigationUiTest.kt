package com.veritas.reader

import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.SystemClock
import androidx.pdf.view.PdfView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class NativePdfNavigationUiTest {
    @Test fun pdfOwnSettingsPersistAndSwipesLeaveZoomAndSelectionIntact() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".checks"))
        val repository = DocumentRepository(context)
        val old = repository.loadReaderSettings()
        val file = File(context.cacheDir, "native-navigation.pdf")
        val pdf = PdfDocument()
        try {
            repeat(4) { index ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(400, 800, index + 1).create())
                page.canvas.drawText("Page ${index + 1}", 40f, 100f, Paint().apply { textSize = 24f })
                pdf.finishPage(page)
            }
            file.outputStream().use(pdf::writeTo)
        } finally { pdf.close() }
        var saved: SavedDocument? = null
        var phase = "fixture"
        fun await(label: String, predicate: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + 15_000
            while (!predicate()) { check(SystemClock.elapsedRealtime() < deadline) { label }; SystemClock.sleep(100) }
        }
        try {
            val fixture = repository.createDocument("Native PDF navigation", (1..4).joinToString("\n\n") { "[[VERITAS_PAGE:$it]]\nPage $it." },
                "PDF", Uri.fromFile(file), "native-navigation.pdf", "application/pdf", 4)
            saved = fixture
            repository.saveReaderSettings(old.copy(pdfNavigationMode = "scroll"))
            ActivityScenario.launch<VeritasPdfViewerActivity>(Intent(context, VeritasPdfViewerActivity::class.java).putExtra("document_id", fixture.id)).use { scenario ->
                phase = "viewport"
                var ready = false
                await("Native PDF viewport ready") {
                    scenario.onActivity { ready = it.pdfView?.pdfDocument?.pageCount == 4 && it.pdfFitWidthZoom > 0f }
                    ready
                }
                scenario.onActivity { it.showTopMenu(requireNotNull(it.toolbarChrome)) }
                phase = "settings menu"
                onView(withText("PDF settings")).perform(click())
                onView(withText("Swipe")).perform(click())
                onView(withText("Done")).perform(click())
                await("Swipe preference persisted") { repository.loadReaderSettings().pdfNavigationMode == "swipe" }
                phase = "swipe left"
                onView(isAssignableFrom(PdfView::class.java)).perform(swipeLeft())
                var page = 0
                await("Horizontal swipe advances actual native PDF") { scenario.onActivity { page = it.pdfNavigationPage }; page == 1 }
                onView(isAssignableFrom(PdfView::class.java)).perform(swipeRight())
                phase = "swipe right"
                await("Horizontal swipe returns") { scenario.onActivity { page = it.pdfNavigationPage }; page == 0 }
                scenario.onActivity { it.pdfView!!.zoom = it.pdfFitWidthZoom * 1.6f }
                phase = "zoom"
                onView(isAssignableFrom(PdfView::class.java)).perform(swipeLeft())
                scenario.onActivity { assertEquals("Zoomed horizontal drag pans instead of paging", 0, it.pdfNavigationPage)
                    it.pdfView!!.zoom = it.pdfFitWidthZoom; it.pdfView!!.scrollToPage(0); it.pdfSelectionActive = true }
                onView(isAssignableFrom(PdfView::class.java)).perform(swipeLeft())
                scenario.onActivity { assertEquals("Selection gestures do not turn pages", 0, it.pdfNavigationPage)
                    it.pdfSelectionActive = false; it.setPdfNavigationMode("scroll") }
                await("Scroll preference persisted") { repository.loadReaderSettings().pdfNavigationMode == "scroll" }
                onView(isAssignableFrom(PdfView::class.java)).perform(swipeUp())
                phase = "scroll"
                await("Native continuous scroll still advances") { scenario.onActivity { page = it.pdfNavigationPage }; page > 0 }
                scenario.recreate()
                phase = "recreate"
                scenario.onActivity { assertEquals("scroll", it.pdfChromeSettings.pdfNavigationMode) }
            }
        } catch (error: Throwable) {
            throw AssertionError("Native PDF check failed during $phase", error)
        } finally {
            saved?.let { repository.deleteDocument(it.id) }
            repository.saveReaderSettings(old)
            file.delete()
        }
    }
}

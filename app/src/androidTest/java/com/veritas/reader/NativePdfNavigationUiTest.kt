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
                page.canvas.drawRect(8f, 8f, 392f, 792f, Paint().apply {
                    color = android.graphics.Color.rgb(35, 90, 150); style = Paint.Style.STROKE; strokeWidth = 3f
                })
                page.canvas.drawText("Page ${index + 1}", 40f, 100f, Paint().apply { textSize = 24f })
                page.canvas.drawText("End of page ${index + 1}", 40f, 760f, Paint().apply { textSize = 18f })
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
        fun capture(name: String) {
            SystemClock.sleep(350)
            val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                val target = File(context.getExternalFilesDir(null), "ReaderOrganization/$name.png")
                target.parentFile!!.mkdirs()
                target.outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            } finally { screenshot.recycle() }
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
                onView(withText("Reading layout")).perform(click())
                capture("pdf-inline-layout")
                onView(withText("Page swipe")).perform(click())
                onView(withText("Swipe to turn pages")).check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()))
                androidx.test.espresso.Espresso.pressBack()
                await("Swipe preference persisted") { repository.loadReaderSettings().pdfNavigationMode == "swipe" }
                await("Swipe mode focuses one complete native page") {
                    scenario.onActivity { ready = it.pdfFocusInitialized && it.pdfVisiblePagesCount == 1 }
                    ready
                }
                fun assertWholePageFits(page: Int) {
                    var geometry = ""
                    await("All four page edges fit the native viewport: $geometry") {
                        scenario.onActivity {
                            val view = requireNotNull(it.pdfView)
                            val top = view.pdfToViewPoint(androidx.pdf.PdfPoint(page, 0f, 0f))
                            val bottom = view.pdfToViewPoint(androidx.pdf.PdfPoint(page, 400f, 800f))
                            geometry = "top=$top, bottom=$bottom, size=${view.width}x${view.height}, zoom=${view.zoom}"
                            ready = top != null && bottom != null && top.x >= -2 && top.y >= -2 &&
                                bottom.x <= view.width + 2 && bottom.y <= view.height + 2
                        }
                        ready
                    }
                    scenario.onActivity {
                        val location = IntArray(2)
                        it.pdfView!!.getLocationOnScreen(location)
                        val chrome = IntArray(2)
                        it.toolbarChrome!!.getLocationOnScreen(chrome)
                        assertTrue("The page begins below the toolbar", location[1] >= chrome[1] + it.toolbarChrome!!.height)
                        it.bottomChrome!!.getLocationOnScreen(chrome)
                        assertTrue("The page ends above the player", location[1] + it.pdfView!!.height <= chrome[1])
                    }
                }
                scenario.onActivity {
                    assertTrue("Focused mode adds room outside the page", it.pdfView!!.verticalPageSpacing > it.pdfDefaultPageSpacing!!)
                    assertEquals(it.pdfView!!.minZoom, it.pdfView!!.zoom, .01f)
                }
                SystemClock.sleep(1200)
                assertWholePageFits(0)
                capture("pdf-focused-swipe")
                phase = "swipe left"
                onView(isAssignableFrom(PdfView::class.java)).perform(swipeLeft())
                var page = 0
                await("Horizontal swipe advances actual native PDF") { scenario.onActivity { page = it.pdfNavigationPage }; page == 1 }
                await("Only the new page is visible") { scenario.onActivity { ready = it.pdfVisiblePagesCount == 1 }; ready }
                assertWholePageFits(1)
                onView(isAssignableFrom(PdfView::class.java)).perform(swipeUp())
                scenario.onActivity { assertEquals("Vertical drag cannot expose the page stack in Swipe mode", 1, it.pdfNavigationPage) }
                onView(isAssignableFrom(PdfView::class.java)).perform(swipeRight())
                phase = "swipe right"
                await("Horizontal swipe returns") { scenario.onActivity { page = it.pdfNavigationPage }; page == 0 }
                scenario.onActivity { it.navigateToPdfPage(0) }
                assertWholePageFits(0)
                scenario.onActivity { it.pdfView!!.zoom = it.pdfFitWidthZoom * 1.6f }
                phase = "zoom"
                onView(isAssignableFrom(PdfView::class.java)).perform(swipeLeft())
                scenario.onActivity { assertEquals("Zoomed horizontal drag pans instead of paging", 0, it.pdfNavigationPage)
                    it.pdfView!!.zoom = it.pdfFitWidthZoom; it.navigateToPdfPage(0); it.pdfSelectionActive = true }
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

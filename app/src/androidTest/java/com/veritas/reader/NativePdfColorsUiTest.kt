package com.veritas.reader

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.SystemClock
import android.widget.EditText
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class NativePdfColorsUiTest {
    @Test fun nativePdfAndItsDialogUseResolvedDarkAmoledAndAccessibilityColors() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".checks"))
        val repository = DocumentRepository(context)
        val original = repository.loadReaderSettings()
        val source = File(context.cacheDir, "appearance-paper.pdf")
        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(600, 900, 1).create())
            page.canvas.drawColor(android.graphics.Color.WHITE)
            page.canvas.drawText("Resolved PDF paper", 30f, 60f, Paint().apply { textSize = 24f; color = android.graphics.Color.BLACK })
            pdf.finishPage(page)
            source.outputStream().use(pdf::writeTo)
        } finally { pdf.close() }
        var saved: SavedDocument? = null
        try {
            val fixture = repository.createDocument("Appearance PDF", "Resolved PDF paper.", "PDF", Uri.fromFile(source), source.name, "application/pdf", 1)
            saved = fixture
            for ((theme, amoled) in listOf("midnight_dark" to false, "midnight_dark" to true, "dracula" to true,
                "amoled" to false, "dark_high_contrast" to true, "midnight_light" to true)) {
                val settings = original.copy(themeId = theme, themePackId = "material_you", amoledMode = amoled,
                    adaptiveCover = false, reduceTransparency = true, glassFloatingControls = false,
                    paperToneMode = PaperToneMode.ACTIVE_THEME.name.lowercase())
                repository.saveReaderSettings(settings)
                val scheme = veritasReaderColorScheme(settings, context)
                ActivityScenario.launch<VeritasPdfViewerActivity>(Intent(context, VeritasPdfViewerActivity::class.java)
                    .putExtra("document_id", fixture.id)).use { scenario ->
                    await("PDF must render") {
                        var loaded = false
                        scenario.onActivity { loaded = it.pdfView?.pdfDocument?.pageCount == 1 }
                        loaded
                    }
                    val center = IntArray(2)
                    scenario.onActivity { activity ->
                        assertEquals(scheme.background.toArgb(), activity.colorBackground)
                        if ((amoled && theme != "midnight_light") || theme == "amoled") assertEquals(Color.Black.toArgb(), activity.colorBackground)
                        assertEquals(VeritasPackStyle.headerColorForPack(scheme, settings.themePackId).toArgb(), activity.colorToolbar)
                        val paper = requireNotNull(activity.fragmentContainer)
                        paper.getLocationOnScreen(center)
                        center[0] += paper.width / 2
                        center[1] += paper.height / 2
                        activity.showChrome(keepVisible = true)
                    }
                    await("$theme/$amoled active-theme PDF paper must match the resolved surface") {
                        val bitmap = instrumentation.uiAutomation.takeScreenshot()
                        val matches = near(scheme.surface.toArgb(), bitmap.getPixel(center[0], center[1]))
                        bitmap.recycle()
                        matches
                    }
                    val capture = instrumentation.uiAutomation.takeScreenshot()
                    val image = File(context.getExternalFilesDir(null), "dark-reader-colors/native-$theme-$amoled.png").apply { parentFile!!.mkdirs() }
                    image.outputStream().use { capture.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    capture.recycle()
                    scenario.onActivity {
                        it.cyclePaperToneMode() // Active theme -> Dark slate -> Natural White, including persistence.
                        it.cyclePaperToneMode()
                    }
                    var observed = 0
                    await("$theme/$amoled Natural White must remove the PDF color filter") {
                        val bitmap = instrumentation.uiAutomation.takeScreenshot()
                        observed = bitmap.getPixel(center[0], center[1])
                        val matches = near(Color.White.toArgb(), observed)
                        if (!matches) {
                            val failure = File(context.getExternalFilesDir(null), "dark-reader-colors/native-white-$theme-$amoled.png")
                            failure.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        }
                        bitmap.recycle()
                        matches
                    }
                    // Measure paper before opening a dialog: its dim scrim changes screenshot pixels.
                    scenario.onActivity { it.showJumpToPageDialog() }
                    onView(isAssignableFrom(EditText::class.java)).inRoot(isDialog()).check { view, error ->
                        if (error != null) throw error
                        val input = view as EditText
                        assertEquals(scheme.onSurface.toArgb(), input.currentTextColor)
                        assertEquals(scheme.primary.toArgb(), input.backgroundTintList!!.getColorForState(intArrayOf(android.R.attr.state_focused), 0))
                    }
                    onView(withText("Cancel")).inRoot(isDialog()).check { view, error ->
                        if (error != null) throw error
                        assertTrue(view.performClick())
                    }
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { it.showTopMenu(requireNotNull(it.toolbarChrome)) }
                    onView(withText("Reading layout")).check { view, error ->
                        if (error != null) throw error
                        assertEquals(scheme.onSurface.toArgb(), (view as android.widget.TextView).currentTextColor)
                        assertTrue((view.parent.parent as android.view.View).performClick())
                    }
                    onView(withText("Page swipe")).check { view, error ->
                        if (error != null) throw error
                        val choice = view as android.widget.TextView
                        assertEquals(scheme.onSurface.toArgb(), choice.currentTextColor)
                        val row = view.parent as android.widget.LinearLayout
                        val radio = row.getChildAt(0) as android.widget.RadioButton
                        assertEquals(scheme.primary.toArgb(), radio.buttonTintList!!.defaultColor)
                    }
                    androidx.test.espresso.Espresso.pressBack()
                    instrumentation.waitForIdleSync()
                }
            }
        } finally {
            saved?.let { repository.deleteDocument(it.id) }
            repository.saveReaderSettings(original)
            source.delete()
        }
    }

    private fun await(message: String, predicate: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 15_000
        while (!predicate()) {
            check(SystemClock.elapsedRealtime() < end) { message }
            SystemClock.sleep(100)
        }
    }

    private fun near(expected: Int, actual: Int) = listOf(0, 8, 16).all {
        kotlin.math.abs((expected shr it and 255) - (actual shr it and 255)) <= 4
    }
}

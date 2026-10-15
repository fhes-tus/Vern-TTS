package com.veritas.reader

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PdfGlassInstrumentedTest {
    @Test fun nativePdfPlayerRendersAndExpandsInBothGlassModes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".checks"))
        val repository = DocumentRepository(context)
        val originalSettings = repository.loadReaderSettings()
        val pdf = File(context.cacheDir, "glass-fixture.pdf")
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(600, 900, 1).create())
            page.canvas.drawColor(Color.WHITE)
            val paint = Paint().apply { textSize = 24f; color = Color.BLACK }
            page.canvas.drawText("PDF glass playback check", 30f, 60f, paint)
            paint.color = Color.rgb(30, 170, 220)
            page.canvas.drawRect(0f, 700f, 600f, 900f, paint)
            document.finishPage(page)
            pdf.outputStream().use(document::writeTo)
        } finally { document.close() }
        var saved: SavedDocument? = null
        try {
            val fixture = repository.createDocument("Glass PDF check", "PDF glass playback check.", "PDF",
                Uri.fromFile(pdf), "glass-fixture.pdf", "application/pdf", 1)
            saved = fixture
            listOf("liquid_glass", "material_you").forEach { mode ->
                repository.saveReaderSettings(originalSettings.copy(themeId = "light", themePackId = mode,
                    glassFloatingControls = mode != "liquid_glass", reduceTransparency = false, adaptiveCover = false))
                val intent = Intent(context, VeritasPdfViewerActivity::class.java).putExtra("document_id", fixture.id)
                ActivityScenario.launch<VeritasPdfViewerActivity>(intent).use { scenario ->
                    scenario.onActivity { it.showChrome(keepVisible = true) }
                    Thread.sleep(1800) // Native PDF pages render asynchronously.
                    scenario.onActivity { activity ->
                        val outer = activity.bottomChrome as LinearLayout
                        val material = outer.getChildAt(0) as FrameLayout
                        assertTrue(material.height > 0)
                        assertNull("Glass cannot have an opaque gradient underneath", material.background)
                        assertEquals(0f, material.elevation, 0f)
                        assertNotNull(activity.viewerFragment)
                        assertNotNull(activity.pdfView)
                        PlaybackStateStore.beginPreparing(fixture.id)
                        activity.updatePlaybackControls()
                        assertTrue(activity.playPauseControl?.foreground is PlaybackLoadingDrawable)
                        assertEquals("Preparing playback. Tap to cancel", activity.playPauseControl?.contentDescription)
                        PlaybackStateStore.finishPreparing()
                        activity.updatePlaybackControls()
                        assertFalse(activity.playPauseControl?.foreground is PlaybackLoadingDrawable)
                        activity.togglePanelExpand()
                    }
                    Thread.sleep(600)
                    scenario.onActivity { activity ->
                        assertTrue(activity.panelExpanded)
                        assertEquals(View.VISIBLE, activity.expandedPanelContent?.visibility)
                    }
                    instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                        File(context.getExternalFilesDir(null), "pdf-$mode-expanded.png").outputStream().use {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                        bitmap.recycle()
                    }
                }
            }
        } finally {
            saved?.let { repository.deleteDocument(it.id) }
            repository.saveReaderSettings(originalSettings)
            pdf.delete()
        }
    }
}

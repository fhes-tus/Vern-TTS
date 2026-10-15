package com.veritas.reader

import android.app.usage.StorageStatsManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Process
import android.os.storage.StorageManager
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class WidgetAndQuestAcceptanceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext.also { check(it.packageName.endsWith(".checks")) }

    @OptIn(ExperimentalGlanceApi::class)
    @Test fun widgetsRenderAtCompactAndExpandedSizesInBothSystemThemes() = runBlocking {
        val widgets = listOf(ReadingProgressWidget(), FlashcardWidget(), StudyDashboardWidget(), QuickNoteWidget(), QuickCaptureWidget())
        for (night in listOf(false, true)) {
            val config = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            val themed = context.createConfigurationContext(config)
            for (widget in widgets) for (size in listOf(DpSize(240.dp, 120.dp), DpSize(320.dp, 240.dp))) {
                val remote = widget.compose(themed, size = size)
                instrumentation.runOnMainSync {
                    val parent = FrameLayout(themed)
                    val view = remote.apply(themed, parent)
                    val width = (size.width.value * themed.resources.displayMetrics.density).toInt()
                    val height = (size.height.value * themed.resources.displayMetrics.density).toInt()
                    view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    view.layout(0, 0, width, height)
                    assertEquals(width, view.measuredWidth); assertEquals(height, view.measuredHeight)
                    val texts = mutableListOf<TextView>()
                    val labels = mutableListOf<String>()
                    fun collect(node: View) {
                        node.contentDescription?.toString()?.takeIf { it.isNotBlank() && node.visibility == View.VISIBLE }?.let(labels::add)
                        if (node is TextView && node.visibility == View.VISIBLE && node.text.isNotBlank()) texts.add(node)
                        if (node is ViewGroup) for (i in 0 until node.childCount) collect(node.getChildAt(i)) }
                    collect(view)
                    assertTrue("${widget.javaClass.simpleName} must have readable content", texts.isNotEmpty() || labels.isNotEmpty())
                    texts.forEach { text ->
                        val bounds = android.graphics.Rect(0, 0, text.width, text.height)
                        (view as ViewGroup).offsetDescendantRectToMyCoords(text, bounds)
                        assertTrue("${widget.javaClass.simpleName}: ${text.text} outside $size at $bounds", bounds.top >= 0 && bounds.bottom <= height + 2 && bounds.left >= 0 && bounds.right <= width + 2)
                    }
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap))
                    val file = File(context.getExternalFilesDir(null), "remaining-fixes/widget-${widget.javaClass.simpleName}-${if (night) "dark" else "light"}-${size.height.value.toInt()}.png").apply { parentFile!!.mkdirs() }
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
                }
            }
        }
    }

    @Test fun dismissingQuestsPreservesIncompleteProgressAndCompletionSurvivesReload() {
        val repo = DocumentRepository(context)
        val before = repo.prefs.all.filterKeys { it.contains("quest") }
        try {
            repo.saveQuestProgress(true, false, true, false); repo.setQuestChecklistDismissed(false)
            val incomplete = DocumentRepository(context).loadQuestProgress()
            assertTrue(incomplete.tourDone); assertFalse(incomplete.importDone)
            repo.setQuestChecklistDismissed(true)
            assertEquals(incomplete, DocumentRepository(context).loadQuestProgress())
            assertTrue(DocumentRepository(context).isQuestChecklistDismissed())
            repo.setQuestChecklistDismissed(false); repo.saveQuestProgress(true, true, true, true); repo.markQuestsCelebrated()
            val complete = DocumentRepository(context)
            assertTrue(complete.loadQuestProgress().let { it.tourDone && it.importDone && it.speedDone && it.bookmarkDone })
            assertTrue(complete.hasCelebratedQuests()); assertFalse(complete.isQuestChecklistDismissed())
        } finally {
            val edit = repo.prefs.edit()
            repo.prefs.all.keys.filter { it.contains("quest") }.forEach(edit::remove)
            before.forEach { (key, value) -> if (value is Boolean) edit.putBoolean(key, value) }
            assertTrue(edit.commit())
        }
    }

    @Test fun reportOwnInstalledStorageForPackagingExperiment() {
        val manager = context.getSystemService(StorageStatsManager::class.java)
        val stats = manager.queryStatsForPackage(StorageManager.UUID_DEFAULT, context.packageName, Process.myUserHandle())
        println("VERN_STORAGE=" + JSONObject().put("appBytes", stats.appBytes).put("dataBytes", stats.dataBytes).put("cacheBytes", stats.cacheBytes))
        assertTrue(stats.appBytes > 0)
    }
}

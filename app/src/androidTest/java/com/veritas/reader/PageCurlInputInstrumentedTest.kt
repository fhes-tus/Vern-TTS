package com.veritas.reader

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.ReaderViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PageCurlInputInstrumentedTest {
    private val test = InstrumentationRegistry.getInstrumentation()

    @Test fun diagonalScrollAndRapidTurnsInBothDirectionsKeepTheCorrectPage() {
        val context = test.targetContext
        assumeTrue(context.packageName.endsWith(".checks"))
        val repository = DocumentRepository(context)
        val previousSettings = repository.loadReaderSettings()
        repository.markOnboardingTutorialSeen()
        repository.saveReaderSettings(previousSettings.copy(
            pageTurnStyle = "flip", readingLayout = "pages", reduceMotion = false,
            showGestureTips = false, collapsibleReaderBars = false
        ))
        val text = (1..5).joinToString("\n\n") { page ->
            ReaderTextIndex.pageMarker(page) + "\n" + (1..20).joinToString("\n\n") {
                "Curl fixture page $page contains enough selectable prose for a vertical drag to stay inside the page."
            }
        }
        val saved = repository.createDocument("Curl fixture " + UUID.randomUUID(), text, "TXT", pageCount = 5)
        val model = ReaderTextIndex.build(text, 5)
        val previousMode = PlaybackStateStore.readerMode
        PlaybackStateStore.readerMode = ReaderMode.TEXT
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                lateinit var viewModel: ReaderViewModel
                scenario.onActivity { activity ->
                    viewModel = ViewModelProvider(activity)[ReaderViewModel::class.java]
                    viewModel.updateState { it.copy(showReleaseNotesDialog = false, showTutorial = false) }
                    viewModel.openSavedDocument(saved, model.sentences.first { it.pageNumber == 2 }.index)
                }
                fun currentPage() = model.sentences.getOrNull(viewModel.uiState.value.readerPosition)?.pageNumber
                val bounds = Rect()
                var textTop = 0
                fun locateSecondPage(): Boolean {
                    var found = false
                    scenario.onActivity { activity ->
                        val page = visibleProse(activity.window.decorView, "Curl fixture page 2")
                        if (page != null) {
                            val location = IntArray(2)
                            page.getLocationOnScreen(location)
                            textTop = location[1]
                            found = page.getGlobalVisibleRect(bounds)
                        }
                    }
                    return found
                }
                await("Page 2 is rendered") { currentPage() == 2 && locateSecondPage() }
                val initialTop = textTop
                val viewport = Rect(bounds)
                drag(viewport.centerX().toFloat(), viewport.top + viewport.height() * .75f,
                    viewport.width() * .14f, -viewport.height() * .47f, steps = 14, interval = 16)
                await("A vertical drag scrolls the text") { locateSecondPage() && textTop < initialTop - 20 }
                assertEquals(2, currentPage())

                fun turn(forward: Boolean, expected: Int) {
                    val start = viewport.left + viewport.width() * if (forward) .86f else .14f
                    val dx = viewport.width() * if (forward) -.72f else .72f
                    drag(start, viewport.centerY().toFloat(), dx, 0f, steps = 5, interval = 14)
                    await("Fast turn settles on page $expected") { currentPage() == expected }
                }
                turn(true, 3)
                turn(true, 4)
                turn(false, 3)
                turn(false, 2)

                if (InstrumentationRegistry.getArguments().getString("capturePageCurl") == "true") {
                    drag(viewport.right - viewport.width() * .12f, viewport.centerY().toFloat(),
                        -viewport.width() * .73f, 0f, steps = 18, interval = 24, atMidpoint = {
                            val bitmap = requireNotNull(test.uiAutomation.takeScreenshot())
                            try {
                                File(context.cacheDir, "page-curl-native-check.png").outputStream().use {
                                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                                }
                            } finally {
                                bitmap.recycle()
                            }
                        })
                    await("Captured turn settles on page 3") { currentPage() == 3 }
                }
            }
        } finally {
            PlaybackStateStore.readerMode = previousMode
            repository.saveReaderSettings(previousSettings)
            repository.deleteDocument(saved.id)
        }
    }

    private fun visibleProse(view: View, text: String): TextView? {
        if (view is TextView && view.text.contains(text) && view.getGlobalVisibleRect(Rect())) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) visibleProse(view.getChildAt(i), text)?.let { return it }
        }
        return null
    }

    private fun await(label: String, until: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 6_000
        while (!until()) {
            assertTrue(label, SystemClock.elapsedRealtime() < deadline)
            SystemClock.sleep(35)
        }
    }

    private fun drag(
        x: Float, y: Float, dx: Float, dy: Float, steps: Int, interval: Long,
        atMidpoint: (() -> Unit)? = null
    ) {
        val start = SystemClock.uptimeMillis()
        fun inject(action: Int, px: Float, py: Float) {
            val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, px, py, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(test.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        inject(MotionEvent.ACTION_DOWN, x, y)
        try {
            for (step in 1..steps) {
                SystemClock.sleep(interval)
                inject(MotionEvent.ACTION_MOVE, x + dx * step / steps, y + dy * step / steps)
                if (step == steps / 2) atMidpoint?.invoke()
            }
        } finally {
            inject(MotionEvent.ACTION_UP, x + dx, y + dy)
        }
    }
}

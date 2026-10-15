package com.veritas.reader

import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import com.veritas.reader.ui.ReaderViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import java.util.UUID

/** Real extracted-page integration, including native long-press and selection ownership. */
class ExtractedCurlGestureInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun await(label: String, predicate: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 15000
        while (true) {
            // ActivityScenario/native-view polling must also advance Compose's
            // test frame clock so the reader's entry transition can finish.
            compose.waitForIdle()
            if (predicate()) return
            check(SystemClock.elapsedRealtime() < end) { label }
            SystemClock.sleep(100)
        }
    }
    private fun visibleText(view: View, page: Int): TextView? {
        if (view is TextView && view.text.contains("Gesture page $page") && view.getGlobalVisibleRect(Rect())) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) visibleText(view.getChildAt(i), page)?.let { return it }
        return null
    }
    private fun touch(action: Int, down: Long, x: Float, y: Float) {
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        try { check(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
    }
    private fun drag(x: Float, y: Float, toX: Float, toY: Float, advanceFrames: Boolean = false) {
        val down = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, down, x, y)
        try {
            for (i in 1..18) {
                SystemClock.sleep(20)
                touch(MotionEvent.ACTION_MOVE, down, x + (toX - x) * i / 18f, y + (toY - y) * i / 18f)
                if (advanceFrames) compose.waitForIdle()
            }
        } finally { touch(MotionEvent.ACTION_UP, down, toX, toY) }
    }
    private fun longPress(x: Float, y: Float) {
        val down = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, down, x, y)
        try { SystemClock.sleep(android.view.ViewConfiguration.getLongPressTimeout().toLong() + 200) }
        finally { touch(MotionEvent.ACTION_UP, down, x, y) }
    }

    @Test fun extractedCurlScrollsSelectsKeepsSelectionOnPageAndSwipesAfterDismissal() = runGesturesWithStyle("flip")

    @Test fun slideLightFlipAndNoneRetainTheirNativeGesturesAndPagePositions() {
        for (style in listOf("slide", "hinge", "none")) runGesturesWithStyle(style)
    }

    @Test fun reducedMotionCurlRetainsNativeGesturesAndPagePositions() = runGesturesWithStyle("flip", reduceMotion = true)

    @Test fun singleTapShowsBothBarsAfterScrollAndDoesNotUndoItself() = runGesturesWithStyle("flip", verifyBarTap = true)

    private fun runGesturesWithStyle(style: String, reduceMotion: Boolean = false, verifyBarTap: Boolean = false) {
        val repository = DocumentRepository(context)
        val oldSettings = repository.loadReaderSettings()
        repository.saveReaderSettings(oldSettings.copy(pageTurnStyle = style, reduceMotion = reduceMotion,
            fontSizeSp = 18, showGestureTips = false, collapsibleReaderBars = verifyBarTap))
        repository.markOnboardingComplete("Gesture preview")
        val text = (1..3).joinToString("\n\n") { page ->
            "[[VERITAS_PAGE:$page]]\n" + (1..45).joinToString("\n\n") {
                "Gesture page $page has selectable prose with words that remain readable when scrolling and turning the page."
            }
        }
        val saved = repository.createDocument("$style gesture check ${UUID.randomUUID()}", text, "TXT", pageCount = 3)
        val model = ReaderTextIndex.build(text)
        val middle = model.sentences.indexOfFirst { it.pageNumber == 2 }
        val previousAudioId = PlaybackStateStore.activeDocumentId
        val previousAudioIndex = PlaybackStateStore.currentIndex
        // Reproduce opening a different reading book while an audio position
        // exists elsewhere. The reader must use its own middle-page position.
        PlaybackStateStore.activeDocumentId = "other-audio-book-${UUID.randomUUID()}"
        PlaybackStateStore.currentIndex = 378
        PlaybackStateStore.readerMode = ReaderMode.TEXT
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        lateinit var vm: ReaderViewModel
        var native: TextView? = null
        fun locate(page: Int): TextView? { scenario.onActivity { native = visibleText(it.window.decorView, page) }; return native }
        try {
            scenario.onActivity { vm = ViewModelProvider(it)[ReaderViewModel::class.java] }
            await("Initial library startup completes") { vm.uiState.value.documents.any { it.id == saved.id } }
            scenario.onActivity {
                vm.updateState { it.copy(showReleaseNotesDialog = false, showTutorial = false, navStack = emptyList()) }
                vm.openSavedDocument(saved, middle)
            }
            await("Saved reader opens") { vm.uiState.value.activeDocument?.id == saved.id }
            await("Extracted middle page appears") { locate(2) != null }
            var visible = Rect()
            scenario.onActivity { native!!.getGlobalVisibleRect(visible) }
            val scrollNode = compose.onNodeWithTag("reader-page-scroll-2")
            fun scrollPosition() = scrollNode.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            fun tapPage(margin: Int = 0) {
                val bounds = scrollNode.fetchSemanticsNode().boundsInRoot
                val tapPoint = androidx.compose.ui.geometry.Offset(
                    when (margin) { -1 -> bounds.left - 12f; 1 -> bounds.right + 12f; else -> bounds.center.x },
                    bounds.center.y)
                val down = SystemClock.uptimeMillis()
                touch(MotionEvent.ACTION_DOWN, down, tapPoint.x, tapPoint.y)
                SystemClock.sleep(60)
                touch(MotionEvent.ACTION_UP, down, tapPoint.x, tapPoint.y)
                SystemClock.sleep(550)
                compose.waitForIdle()
            }
            if (verifyBarTap) {
                tapPage()
                compose.onNodeWithTag("reader-top-controls").assertIsNotDisplayed()
                compose.onNodeWithTag("reader-bottom-controls").assertIsNotDisplayed()
            }
            val beforeScroll = scrollPosition()
            drag(visible.centerX().toFloat(), visible.top + visible.height() * .65f,
                visible.centerX().toFloat(), visible.top + visible.height() * .25f)
            if (verifyBarTap) {
                // A tap just after a scroll must reveal the controls too,
                // even when the first touch stops a remaining fling.
                tapPage()
                compose.onNodeWithTag("reader-top-controls").assertIsDisplayed()
                compose.onNodeWithTag("reader-bottom-controls").assertIsDisplayed()
                val dragBounds = scrollNode.fetchSemanticsNode().boundsInRoot
                drag(dragBounds.center.x, dragBounds.top + dragBounds.height * .65f,
                    dragBounds.center.x, dragBounds.top + dragBounds.height * .25f, advanceFrames = true)
                compose.waitForIdle()
                compose.onNodeWithTag("reader-top-controls").assertIsNotDisplayed()
                compose.onNodeWithTag("reader-bottom-controls").assertIsNotDisplayed()
                tapPage()
                compose.onNodeWithTag("reader-top-controls").assertIsDisplayed()
                compose.onNodeWithTag("reader-bottom-controls").assertIsDisplayed()
            }
            await("Vertical scrolling reaches extracted prose") {
                scrollPosition() > beforeScroll + 20
            }
            // Let the vertical fling settle before calculating a word target.
            SystemClock.sleep(800)
            if (verifyBarTap) {
                compose.waitForIdle()
                // Native input and the Compose test clock can finish the fling
                // before the scroll observer sees it. Start from the actual
                // bar state, then require each tap to change both bars once.
                val initiallyVisible = runCatching {
                    compose.onNodeWithTag("reader-top-controls").assertIsDisplayed()
                }.isSuccess
                val states = if (initiallyVisible) listOf(false, true, false, true) else listOf(true, false, true)
                for (show in states) {
                    // Wait past native double-tap confirmation; each tap should
                    // toggle once and remain there, rather than toggle twice.
                    tapPage()
                    if (show) {
                        compose.onNodeWithTag("reader-top-controls").assertIsDisplayed()
                        compose.onNodeWithTag("reader-bottom-controls").assertIsDisplayed()
                    } else {
                        compose.onNodeWithTag("reader-top-controls").assertIsNotDisplayed()
                        compose.onNodeWithTag("reader-bottom-controls").assertIsNotDisplayed()
                    }
                }
                tapPage(-1)
                compose.onNodeWithTag("reader-top-controls").assertIsNotDisplayed()
                compose.onNodeWithTag("reader-bottom-controls").assertIsNotDisplayed()
                tapPage(1)
                compose.onNodeWithTag("reader-top-controls").assertIsDisplayed()
                compose.onNodeWithTag("reader-bottom-controls").assertIsDisplayed()
                // Following a reading position is a programmatic scroll, not a
                // new user drag. It must not undo the preceding reveal tap.
                scenario.onActivity { vm.updateState { it.copy(readerPosition = middle + 32) } }
                compose.waitForIdle()
                compose.onNodeWithTag("reader-top-controls").assertIsDisplayed()
                compose.onNodeWithTag("reader-bottom-controls").assertIsDisplayed()
            }
            var x = 0f; var y = 0f
            val viewport = scrollNode.fetchSemanticsNode().boundsInRoot
            val proseOrigin = compose.onNodeWithTag("reader-prose-2-0").fetchSemanticsNode().layoutInfo.coordinates
                .localToRoot(androidx.compose.ui.geometry.Offset.Zero)
            scenario.onActivity {
                val tv = native!!
                tv.getGlobalVisibleRect(visible)
                var line = tv.layout.getLineForVertical((viewport.center.y - proseOrigin.y - tv.totalPaddingTop).toInt())
                while (line < tv.layout.lineCount - 1 && tv.text.subSequence(tv.layout.getLineStart(line), tv.layout.getLineEnd(line)).isBlank()) line++
                val word = tv.layout.getLineStart(line) + 3
                x = proseOrigin.x + tv.totalPaddingLeft + tv.layout.getPrimaryHorizontal(word)
                y = proseOrigin.y + tv.totalPaddingTop + (tv.layout.getLineTop(line) + tv.layout.getLineBottom(line)) / 2f
            }
            longPress(x, y)
            await("Native long-press selects extracted text") { var selected = false; scenario.onActivity { selected = native!!.hasSelection() }; selected }
            val indexBeforeDrag = vm.uiState.value.readerPosition
            drag(visible.centerX().toFloat(), y, visible.left + 30f, y)
            SystemClock.sleep(1200)
            assertEquals("Selection gesture must not advance the reader", indexBeforeDrag, vm.uiState.value.readerPosition)
            assertNotNull(locate(2))
            // A drag in the text can itself dismiss Android's selection. Ensure
            // selection is active before Back, otherwise Back leaves the book.
            var selectedAfterDrag = false
            scenario.onActivity { selectedAfterDrag = native!!.hasSelection() }
            if (!selectedAfterDrag) {
                longPress(x, y)
                await("Select again before dismissal") { var selected = false; scenario.onActivity { selected = native!!.hasSelection() }; selected }
            }
            androidx.test.espresso.Espresso.pressBack()
            await("Back dismisses native selection") { var cleared = false; scenario.onActivity { cleared = !native!!.hasSelection() }; cleared }
            assertEquals("Dismissing selection must keep the reader open", saved.id, vm.uiState.value.activeDocument?.id)
            scenario.onActivity { native!!.getGlobalVisibleRect(visible) }
            // Stay out of Android's edge-Back gesture area.
            drag(visible.left + visible.width() * .85f, visible.centerY().toFloat(),
                visible.left + visible.width() * .15f, visible.centerY().toFloat())
            await("Horizontal turn reaches next extracted page") { locate(3) != null && vm.uiState.value.readerPosition > indexBeforeDrag }
        } catch (error: Throwable) {
            val directory = java.io.File(context.getExternalFilesDir(null), "VernUiChecks").apply { mkdirs() }
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                java.io.File(directory, "extracted-curl-failure.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            val details = StringBuilder("active=${vm.uiState.value.activeDocument?.title} position=${vm.uiState.value.readerPosition} playback=${PlaybackStateStore.currentIndex} mode=${PlaybackStateStore.readerMode} stack=${vm.uiState.value.navStack}\n")
            scenario.onActivity { activity ->
                fun describe(view: View) {
                    if (view is TextView) details.append("${view.javaClass.name} shown=${view.isShown} visible=${view.getGlobalVisibleRect(Rect())} text=${view.text.take(90)}\n")
                    if (view is ViewGroup) for (i in 0 until view.childCount) describe(view.getChildAt(i))
                }
                describe(activity.window.decorView)
            }
            java.io.File(directory, "extracted-curl-failure.txt").writeText(details.toString())
            throw error
        } finally {
            scenario.close()
            repository.deleteDocument(saved.id)
            repository.saveReaderSettings(oldSettings)
            PlaybackStateStore.activeDocumentId = previousAudioId
            PlaybackStateStore.currentIndex = previousAudioIndex
        }
    }
}

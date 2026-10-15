package com.veritas.reader

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.screens.BookPageCurl
import com.veritas.reader.ui.screens.CurledPageMesh
import com.veritas.reader.ui.screens.readerFontPinch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BookPageCurlUiTest {
    @get:Rule val rule = createComposeRule()
    private fun capture(name: String) {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "VernUiChecks").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("No screenshot")
        java.io.File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun middlePageReceivesTapScrollDoubleTapAndNativeSelectionWithCurlEnabled() {
        lateinit var pager: PagerState
        val scrolls = mutableMapOf<Int, androidx.compose.foundation.ScrollState>()
        val texts = mutableMapOf<Int, android.widget.TextView>()
        val tapped = mutableListOf<Int>()
        val doubleTapped = mutableListOf<Int>()
        var scope: CoroutineScope? = null
        rule.setContent {
            pager = rememberPagerState(initialPage = 1, pageCount = { 3 })
            scope = rememberCoroutineScope()
            MaterialTheme {
                HorizontalPager(pager, Modifier.fillMaxSize(), pageSpacing = 0.dp, beyondViewportPageCount = 1) { page ->
                    BookPageCurl(true, pager.isScrollInProgress, { (pager.currentPage - page) + pager.currentPageOffsetFraction }, Color.White) {
                        Column(Modifier.fillMaxSize().background(Color.White)) {
                            androidx.compose.material3.Button({ tapped.add(page) }, Modifier.testTag("curl-tap-$page")) { Text("Tap page $page") }
                            val scroll = androidx.compose.foundation.rememberScrollState()
                            scrolls[page] = scroll
                            Column(Modifier.weight(1f).fillMaxWidth().testTag("curl-scroll-$page").verticalScroll(scroll)) {
                                androidx.compose.ui.viewinterop.AndroidView(factory = { context ->
                                    android.widget.TextView(context).apply {
                                        text = ("Page $page has selectable words and paragraphs that scroll.\n\n").repeat(45)
                                        textSize = 20f
                                        setTextColor(android.graphics.Color.BLACK)
                                        setTextIsSelectable(true)
                                        var doubleHandled = false
                                        val detector = android.view.GestureDetector(context, object : android.view.GestureDetector.SimpleOnGestureListener() {
                                            override fun onDoubleTap(event: android.view.MotionEvent): Boolean {
                                                doubleTapped.add(page)
                                                doubleHandled = true
                                                return true
                                            }
                                        })
                                        setOnTouchListener { _, event ->
                                            if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) doubleHandled = false
                                            detector.onTouchEvent(event)
                                            doubleHandled
                                        }
                                        texts[page] = this
                                    }
                                }, modifier = Modifier.fillMaxWidth().testTag("curl-text-$page"))
                            }
                        }
                    }
                }
            }
        }
        rule.onNodeWithTag("curl-tap-1").performTouchInput { click() }
        rule.runOnIdle { assertEquals(listOf(1), tapped) }
        rule.onNodeWithTag("curl-scroll-1").performTouchInput { doubleClick(androidx.compose.ui.geometry.Offset(centerX, 40f)) }
        rule.runOnIdle { assertEquals(listOf(1), doubleTapped) }
        rule.onNodeWithTag("curl-scroll-1").performTouchInput { swipeUp() }
        rule.runOnIdle { assertTrue("Vertical scrolling reaches the live page", scrolls.getValue(1).value > 0) }
        var wordPoint = androidx.compose.ui.geometry.Offset.Zero
        rule.runOnIdle {
            val view = texts.getValue(1)
            val layout = view.layout
            val location = IntArray(2).also(view::getLocationOnScreen)
            val visible = android.graphics.Rect()
            assertTrue(view.getGlobalVisibleRect(visible))
            // Native global bounds include the Compose-clipped portion above
            // this scroll viewport. The screen centre is inside visible prose.
            var line = layout.getLineForVertical(visible.centerY() - location[1] - view.totalPaddingTop)
            while (line < layout.lineCount - 1 && view.text.subSequence(layout.getLineStart(line), layout.getLineEnd(line)).isBlank()) line++
            val word = (layout.getLineStart(line) + 3).coerceAtMost(layout.getLineEnd(line) - 1)
            wordPoint = androidx.compose.ui.geometry.Offset(location[0] + view.totalPaddingLeft + layout.getPrimaryHorizontal(word),
                location[1] + view.totalPaddingTop + (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f)
            assertTrue(visible.contains(wordPoint.x.toInt(), wordPoint.y.toInt()))
        }
        // Native TextView long-press uses real uptime; virtual Compose touch
        // duration alone sends UP before Android's delayed long-press callback.
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = android.os.SystemClock.uptimeMillis()
        fun inject(action: Int) {
            val event = android.view.MotionEvent.obtain(downTime, android.os.SystemClock.uptimeMillis(), action, wordPoint.x, wordPoint.y, 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        inject(android.view.MotionEvent.ACTION_DOWN)
        try { android.os.SystemClock.sleep(android.view.ViewConfiguration.getLongPressTimeout().toLong() + 180) }
        finally { inject(android.view.MotionEvent.ACTION_UP) }
        rule.waitUntil(5000) { texts.getValue(1).hasSelection() }
        rule.runOnIdle {
            assertFalse(texts.getValue(0).hasSelection())
            texts.getValue(1).clearFocus()
            android.text.Selection.removeSelection(texts.getValue(1).text as android.text.Spannable)
            scope!!.launch { pager.animateScrollToPage(2, animationSpec = tween(950)) }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("curl-tap-2").performTouchInput { click() }
        rule.runOnIdle { assertEquals(listOf(1, 2), tapped) }
    }

    @Test fun pinchKeepsItsOwnershipAcrossFontUpdatesAndCancelledCurlReturnsToLivePage() {
        lateinit var pager: PagerState
        var size by mutableStateOf(18)
        var pinching by mutableStateOf(false)
        var updates = 0
        rule.setContent {
            pager = rememberPagerState(initialPage = 1, pageCount = { 3 })
            MaterialTheme {
                HorizontalPager(pager, Modifier.fillMaxSize().testTag("pinch-curl-pager"),
                    userScrollEnabled = !pinching, pageSpacing = 0.dp, beyondViewportPageCount = 1) { page ->
                    BookPageCurl(true, pager.isScrollInProgress, { (pager.currentPage - page) + pager.currentPageOffsetFraction }, Color.White) {
                        Box(Modifier.fillMaxSize().testTag("pinch-curl-page-$page")
                            .readerFontPinch(size, { size = it; updates++ }, { pinching = it })) {
                            androidx.compose.ui.viewinterop.AndroidView(factory = { context -> android.widget.TextView(context).apply {
                                text = ("Page $page remains live and selectable while pinching or scrolling.\n\n").repeat(40)
                                setTextIsSelectable(true)
                            } }, update = { it.textSize = size.toFloat() }, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
        val node = rule.onNodeWithTag("pinch-curl-page-1")
        node.performTouchInput {
            down(0, androidx.compose.ui.geometry.Offset(centerX - 80f, centerY))
            down(1, androidx.compose.ui.geometry.Offset(centerX + 80f, centerY))
            moveTo(0, androidx.compose.ui.geometry.Offset(centerX - 90f, centerY))
            moveTo(1, androidx.compose.ui.geometry.Offset(centerX + 90f, centerY))
        }
        rule.runOnIdle { assertTrue(pinching); assertTrue(size > 18); assertEquals(1, pager.currentPage) }
        node.performTouchInput {
            moveTo(0, androidx.compose.ui.geometry.Offset(centerX - 110f, centerY))
            moveTo(1, androidx.compose.ui.geometry.Offset(centerX + 110f, centerY))
            up(1)
            moveTo(0, androidx.compose.ui.geometry.Offset(centerX + 60f, centerY))
            up(0)
        }
        rule.runOnIdle { assertFalse(pinching); assertTrue(updates >= 2); assertEquals(1, pager.settledPage) }
        node.performTouchInput {
            down(center)
            moveTo(androidx.compose.ui.geometry.Offset(centerX - width * .18f, centerY), delayMillis = 200)
            moveTo(center, delayMillis = 200)
            advanceEventTime(100)
            up()
        }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(1, pager.settledPage); assertFalse(pager.isScrollInProgress) }
        rule.onAllNodesWithTag("book-curl-snapshot").assertCountEquals(0)
        node.performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(2, pager.settledPage) }
    }

    @Test fun curvedPagesRenderForwardAndBackAndReturnToLiveText() {
        lateinit var pager: PagerState
        lateinit var scope: CoroutineScope
        var enabled by mutableStateOf(true)
        rule.setContent {
            pager = rememberPagerState(pageCount = { 2 })
            scope = rememberCoroutineScope()
            MaterialTheme {
                HorizontalPager(pager, Modifier.fillMaxSize(), pageSpacing = 0.dp, beyondViewportPageCount = 1) { page ->
                    BookPageCurl(enabled, pager.isScrollInProgress, { (pager.currentPage - page) + pager.currentPageOffsetFraction }, Color(0xFFFFF6E6)) {
                        Column(Modifier.fillMaxSize().background(if (page == 0) Color(0xFFFFF6E6) else Color(0xFFE6F0FF)).padding(24.dp)) {
                            Text("Chapter ${page + 1}", style = MaterialTheme.typography.headlineLarge)
                            Spacer(Modifier.height(24.dp))
                            // The real reader uses selectable Android TextViews inside Compose.
                            androidx.compose.ui.viewinterop.AndroidView(factory = { context ->
                                android.widget.TextView(context).apply {
                                    text = ("Native selectable words bend with the page, while the reader returns to live text after each turn.\n\n").repeat(8)
                                    textSize = 20f
                                    setTextColor(0xFF252534.toInt())
                                    setTextIsSelectable(true)
                                }
                            }, modifier = Modifier.fillMaxWidth().weight(1f))
                        }
                    }
                }
            }
        }
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { scope.launch { pager.animateScrollToPage(1, animationSpec = tween(1000)) } }
        rule.mainClock.advanceTimeBy(200)
        rule.waitUntil(5000) { rule.onAllNodesWithTag("book-curl-snapshot").fetchSemanticsNodes().isNotEmpty() }
        rule.mainClock.advanceTimeBy(220)
        capture("page-curl-forward")
        rule.mainClock.advanceTimeBy(1000)
        rule.runOnIdle { assertEquals(1, pager.currentPage) }
        rule.runOnIdle { scope.launch { pager.animateScrollToPage(0, animationSpec = tween(1000)) } }
        rule.mainClock.advanceTimeBy(250)
        rule.waitUntil(5000) { rule.onAllNodesWithTag("book-curl-snapshot").fetchSemanticsNodes().isNotEmpty() }
        rule.mainClock.advanceTimeBy(180)
        capture("page-curl-backward")
        rule.mainClock.advanceTimeBy(1000)
        rule.runOnIdle { assertEquals(0, pager.currentPage) }
        rule.waitUntil(5000) { rule.onAllNodesWithTag("book-curl-snapshot").fetchSemanticsNodes().isEmpty() }
        rule.runOnIdle { enabled = false }
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        rule.onAllNodesWithTag("book-curl-live").assertCountEquals(0)
        rule.onNodeWithText("Chapter 1").assertIsDisplayed()
        rule.mainClock.autoAdvance = true
    }

    @Test fun meshAcceptsTurnEndpointsAndProducesPaperReverse() {
        val source = android.graphics.Bitmap.createBitmap(360, 520, android.graphics.Bitmap.Config.ARGB_8888)
        val sourceCanvas = android.graphics.Canvas(source)
        sourceCanvas.drawColor(0xFFFFF6E6.toInt())
        val text = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; textSize = 24f }
        for (y in 60..480 step 45) sourceCanvas.drawText("Words across the page", 20f, y.toFloat(), text)
        val target = android.graphics.Bitmap.createBitmap(360, 520, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(target)
        val renderer = CurledPageMesh(0xFFFFF6E6.toInt())
        try {
            for (position in listOf(0f, .25f, .5f, .75f, .999f, 1f)) {
                canvas.drawColor(android.graphics.Color.BLUE)
                renderer.draw(canvas, source, 360f, 520f, position)
                if (position == 0f) assertEquals(source.getPixel(10, 10), target.getPixel(10, 10))
                if (position >= .75f) assertEquals("Next page is revealed", android.graphics.Color.BLUE, target.getPixel(350, 260))
            }
        } finally { source.recycle(); target.recycle() }
    }
}

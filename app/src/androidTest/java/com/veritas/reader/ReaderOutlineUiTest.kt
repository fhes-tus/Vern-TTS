package com.veritas.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.screens.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReaderOutlineUiTest {
    @get:Rule val rule = createComposeRule()
    private val entries = listOf(
        SmartOutlineEntry(0, "Chapter one", "", true, 0, 1),
        SmartOutlineEntry(3, "First topic", "", true, 1, 2),
        SmartOutlineEntry(6, "Nested detail", "", true, 2, 3),
        SmartOutlineEntry(9, "Second topic", "", true, 1, 4),
        SmartOutlineEntry(12, "Chapter two", "", true, 0, 5)
    )

    private fun capture(name: String) {
        rule.waitForIdle()
        android.os.SystemClock.sleep(150)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "ReaderOutline/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try { file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }

    @Test fun bloomStartsAtTheMeasuredButtonAndFillsTheWindow() {
        org.junit.Assume.assumeTrue(android.animation.ValueAnimator.areAnimatorsEnabled())
        var open by mutableStateOf(false)
        var origin = Offset.Unspecified
        val readerColor = Color(0xFF243B53)
        val outlineColor = Color(0xFFF7F5F0)
        rule.setContent { MaterialTheme(colorScheme = lightColorScheme(surface = outlineColor)) {
            Box(Modifier.fillMaxSize().background(readerColor)) {
                Button({ open = true }, Modifier.padding(24.dp).outlineRevealAnchor { origin = it }) { Text("Open outline") }
            }
            if (open) ReaderOutlineScreen("Table of contents", "Bloom test", entries, 0, 8, { _, _ -> },
                onDismiss = { open = false }, origin = origin, reduceMotion = false)
        } }
        rule.mainClock.autoAdvance = false
        try {
            rule.onNodeWithText("Open outline").performClick()
            rule.mainClock.advanceTimeBy(96)
            rule.waitForIdle()
            android.os.SystemClock.sleep(150)
            val screenshot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            try {
                assertTrue("Reveal begins at the actual button", (-8..8).any { dx ->
                    (-8..8).any { dy -> screenshot.getPixel(origin.x.toInt() + dx, origin.y.toInt() + dy) == outlineColor.toArgb() }
                })
                assertEquals("Distant reader area is still visible during the bloom", readerColor.toArgb(),
                    screenshot.getPixel(8, (screenshot.height * .8f).toInt()))
            } finally { screenshot.recycle() }
            capture("bloom-from-button")
            rule.mainClock.advanceTimeBy(500)
            rule.waitForIdle()
            android.os.SystemClock.sleep(150)
            val complete = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            try {
                assertEquals("Bloom reaches the far edge", outlineColor.toArgb(), complete.getPixel(8, (complete.height * .8f).toInt()))
            } finally { complete.recycle() }
        } finally { rule.mainClock.autoAdvance = true }
    }

    @Test fun nestedBranchesCollapseIndependentlyAndSearchRevealsTheirParents() {
        rule.setContent { VeritasTheme {
            ReaderOutlineScreen("Table of contents", "The outline test book", entries, 0, 8, { _, _ -> }, {}, reduceMotion = true)
        } }
        val bounds = rule.onNodeWithTag("reader-outline-screen").fetchSemanticsNode().boundsInRoot
        val metrics = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics
        assertTrue("Outline fills the window width", bounds.width >= metrics.widthPixels - 2)
        assertTrue("Outline fills the window height", bounds.height >= metrics.heightPixels - 2)
        rule.onNodeWithContentDescription("Collapse First topic").performClick()
        rule.onNodeWithText("Nested detail").assertDoesNotExist()
        rule.onNodeWithText("Second topic").assertIsDisplayed()
        rule.onNodeWithContentDescription("Collapse Chapter one").performClick()
        rule.onNodeWithText("First topic").assertDoesNotExist()
        rule.onNodeWithContentDescription("Expand Chapter one").performClick()
        rule.onNodeWithText("First topic").assertIsDisplayed()
        rule.onNodeWithText("Nested detail").assertDoesNotExist()
        rule.onNodeWithText("Collapse all").performClick()
        rule.onNodeWithText("Filter outline").performTextInput("Nested detail")
        rule.onNodeWithText("Chapter one").assertIsDisplayed()
        rule.onNodeWithText("First topic").assertIsDisplayed()
        rule.onNodeWithTag("outline-row-2").assertIsDisplayed()
        capture("search-nested-path")
        rule.onNodeWithContentDescription("Clear filter").performClick()
        rule.onNodeWithText("First topic").assertDoesNotExist()
        rule.onNodeWithText("Expand all").performClick()
        rule.onNodeWithText("Nested detail").assertIsDisplayed()
        capture("full-screen-hierarchy")
    }

    @Test fun bloomCloseBackAndDestinationsReturnToTheSameReaderMode() {
        var mode by mutableStateOf("Actual document")
        var open by mutableStateOf(false)
        var destination: Pair<Int?, Int>? = null
        rule.setContent { VeritasTheme {
            var origin by remember { mutableStateOf(Offset.Unspecified) }
            Column(Modifier.fillMaxSize().padding(24.dp)) {
                Text("Reader: $mode")
                Button({ open = true }, Modifier.outlineRevealAnchor { origin = it }) { Text("Open outline") }
            }
            if (open) ReaderOutlineScreen("Table of contents", "The outline test book", entries, 0, 8,
                onJumpToDestination = { page, index -> destination = page to index; open = false },
                onDismiss = { open = false }, origin = origin, reduceMotion = false,
                sentenceForPage = { (it - 1) * 3 })
        } }
        for (reader in listOf("Actual document", "PDF", "Extracted text")) {
            rule.runOnIdle { mode = reader }
            rule.onNodeWithText("Open outline").performClick()
            rule.onNodeWithContentDescription("Close outline").performClick()
            rule.onNodeWithTag("reader-outline-screen").assertDoesNotExist()
            rule.onNodeWithText("Reader: $reader").assertIsDisplayed()
            rule.onNodeWithText("Open outline").performClick()
            androidx.test.espresso.Espresso.pressBack()
            rule.onNodeWithTag("reader-outline-screen").assertDoesNotExist()
            rule.onNodeWithText("Reader: $reader").assertIsDisplayed()
        }
        rule.onNodeWithText("Open outline").performClick()
        rule.onNodeWithText("Nested detail").performClick()
        rule.runOnIdle { assertEquals(3 to 6, destination) }
        rule.onNodeWithText("Reader: Extracted text").assertIsDisplayed()
        rule.onNodeWithText("Open outline").performClick()
        rule.onNodeWithText("Page (1–8)").performTextInput("99")
        rule.onNodeWithText("Go").assertIsNotEnabled()
        rule.onNodeWithText("Page (1–8)").performTextReplacement("4")
        rule.onNodeWithText("Go").performClick()
        rule.runOnIdle { assertEquals(4 to 9, destination) }
    }
}

package com.veritas.reader

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.OnboardingController
import com.veritas.reader.ui.OnboardingStep
import com.veritas.reader.ui.screens.AudioModeScreen
import com.veritas.reader.ui.screens.BatchImportFloater
import com.veritas.reader.ui.screens.OnboardingSpotlightOverlay
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Local component constraints exercise resizing without claiming hardware posture coverage. */
class ResponsiveBoundsUiTest {
    @get:Rule val rule = createComposeRule()
    private var pixelsPerDp = 1f
    private val dismissLabel get() = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.ui_dismiss_tour)

    @Before fun requireIsolatedProfile() {
        val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        check(packageName.endsWith(".responsiveaudit") || packageName.endsWith(".checks") || packageName.endsWith(".iconcheck"))
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // Other screen fixtures also record anchors; every component case starts independently.
            OnboardingController.componentBounds.clear()
        }
    }

    @Composable private fun Frame(width: Dp, height: Dp, fontScale: Float = 1f, content: @Composable () -> Unit) {
        val base = LocalDensity.current
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val scale = minOf(1f, maxWidth / width, maxHeight / height)
            val density = Density(base.density * scale, fontScale)
            pixelsPerDp = density.density
            CompositionLocalProvider(LocalDensity provides density) {
                MaterialTheme {
                    Box(Modifier.requiredSize(width, height).testTag("fixture")) { content() }
                }
            }
        }
    }

    private fun bounds(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    private fun inside(inner: Rect, outer: Rect) {
        assertTrue("left: $inner in $outer", inner.left >= outer.left - 1f)
        assertTrue("top: $inner in $outer", inner.top >= outer.top - 1f)
        assertTrue("right: $inner in $outer", inner.right <= outer.right + 1f)
        assertTrue("bottom: $inner in $outer", inner.bottom <= outer.bottom + 1f)
        assertTrue(inner.width > 0f && inner.height > 0f)
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "responsive-first-batch/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use {
            rule.onNodeWithTag("fixture").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun centeredParentFloaterStaysReachableAndDragsAfterResize() {
        var width by mutableStateOf(380.dp)
        var height by mutableStateOf(568.dp)
        rule.setContent {
            Frame(width, height) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    BatchImportFloater(true, 2, 6, modifier = Modifier.padding(bottom = 72.dp).testTag("floater-host"))
                }
            }
        }
        rule.onNodeWithTag("batch_import_floater").assertIsDisplayed()
        inside(bounds("batch_import_floater"), bounds("floater-host"))
        capture("floater-before-drag")
        rule.onNodeWithTag("batch_import_floater").performTouchInput {
            swipe(center, center + Offset(-600f, 0f), 400)
        }
        rule.waitForIdle()
        capture("floater-after-left-drag")
        assertEquals(bounds("floater-host").left + 16 * pixelsPerDp, bounds("batch_import_floater").left, 2f)
        rule.runOnIdle { width = 240.dp; height = 280.dp }
        inside(bounds("batch_import_floater"), bounds("floater-host"))
        rule.onNodeWithTag("batch_import_floater").performTouchInput {
            swipe(center, center + Offset(900f, 900f), 400)
        }
        rule.waitForIdle()
        inside(bounds("batch_import_floater"), bounds("floater-host"))
        assertEquals(bounds("floater-host").right - 16 * pixelsPerDp, bounds("batch_import_floater").right, 2f)
        rule.runOnIdle { width = 380.dp; height = 568.dp }
        inside(bounds("batch_import_floater"), bounds("floater-host"))
        assertEquals(bounds("floater-host").right - 16 * pixelsPerDp, bounds("batch_import_floater").right, 2f)
        capture("floater-resized-right")
    }

    @Test fun spotlightMeasuresAboveAndBelowTargetsInANestedRoot() {
        var targetAtTop by mutableStateOf(false)
        var wide by mutableStateOf(false)
        try {
            rule.setContent {
                Frame(if (wide) 891.dp else 411.dp, if (wide) 411.dp else 800.dp) {
                    Box(Modifier.fillMaxSize().padding(start = 12.dp, top = 36.dp)) {
                        Box(Modifier.align(if (targetAtTop) Alignment.TopEnd else Alignment.BottomEnd)
                            .padding(16.dp).size(56.dp).testTag("target")
                            .onGloballyPositioned { OnboardingController.updateBounds("add_fab", it) })
                        OnboardingSpotlightOverlay(OnboardingStep.FAB_SPOTLIGHT, "", {}, {}, {}, {})
                    }
                }
            }
            rule.waitForIdle()
            inside(bounds("spotlight-card"), bounds("fixture"))
            assertTrue(bounds("spotlight-card").bottom < bounds("target").top)
            capture("spotlight-above")
            rule.runOnIdle { targetAtTop = true }
            rule.waitForIdle()
            assertTrue(bounds("spotlight-card").top > bounds("target").bottom)
            inside(bounds("spotlight-card"), bounds("fixture"))
            capture("spotlight-below")
            rule.runOnIdle { wide = true }
            rule.waitForIdle()
            inside(bounds("spotlight-card"), bounds("fixture"))
            assertTrue(bounds("spotlight-card").width <= 380 * pixelsPerDp + 1)
            rule.onNodeWithTag("spotlight-next").performScrollTo().assertIsDisplayed()
            capture("spotlight-wide-capped")
        } finally {
            rule.runOnIdle { OnboardingController.componentBounds.remove("add_fab") }
        }
    }

    @Test fun shortSpotlightWithDoubleTextKeepsDismissAndNextReachable() {
        var next = 0
        var dismissed = 0
        rule.setContent {
            Frame(320.dp, 568.dp, 2f) {
                OnboardingSpotlightOverlay(OnboardingStep.FAB_SPOTLIGHT, "", {}, { next++ }, {}, { dismissed++ })
            }
        }
        inside(bounds("spotlight-card"), bounds("fixture"))
        rule.onNodeWithContentDescription(dismissLabel).assertIsDisplayed()
        capture("spotlight-short-font2-top")
        rule.onNodeWithTag("spotlight-next").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, next)
        capture("spotlight-short-font2-actions")
        rule.onNodeWithContentDescription(dismissLabel).performScrollTo().performClick()
        assertEquals(1, dismissed)
    }

    @Test fun shortListenPreservesTranscriptAndScrollsToEveryControl() {
        var index by mutableIntStateOf(1)
        var fontScale by mutableFloatStateOf(1f)
        var timers = 0
        val sentences = listOf("Start here.", "The active sentence remains readable in a short phone window.", "Finish here.")
        rule.setContent {
            Frame(320.dp, 568.dp, fontScale) {
                AudioModeScreen("A longer responsive test book title", index, sentences.size, sentences[index], false,
                    onPlayPause = {}, onNext = { index = (index + 1).coerceAtMost(2) }, onPrevious = {}, onDismiss = {},
                    hasCanvas = true, documentChunks = sentences, onSentenceClick = { index = it },
                    onOpenSleepTimer = { timers++ }, contentWindowInsets = WindowInsets(0, 0, 0, 0))
            }
        }
        rule.onNodeWithText("Original").assertIsDisplayed()
        rule.onNodeWithText("Read", useUnmergedTree = true).performClick()
        rule.onNodeWithText(sentences[1]).assertIsSelected().assertIsDisplayed()
        assertTrue(bounds("listen-sentences").height >= 150 * pixelsPerDp)
        capture("listen-short-read")
        rule.onNodeWithContentDescription("Next sentence").performScrollTo().performClick()
        assertEquals(2, index)
        rule.onNodeWithContentDescription("Sleep timer").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, timers)
        capture("listen-short-controls")
        rule.onNodeWithTag("listen-sentences").performScrollTo()
        rule.onNodeWithText(sentences[2]).assertIsSelected().assertIsDisplayed()
        rule.runOnIdle { fontScale = 2f }
        capture("listen-short-font2-before-layout-check")
        rule.onNodeWithText("Original").assertIsDisplayed().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { action ->
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            action(layouts)
            val layout = layouts.single()
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            File(context.getExternalFilesDir(null), "responsive-first-batch/mode-label-layout.txt").writeText(
                "size=${layout.size}, constraints=${layout.layoutInput.constraints}, widthOverflow=${layout.didOverflowWidth}, heightOverflow=${layout.didOverflowHeight}, lines=${layout.lineCount}, paragraphWidth=${layout.multiParagraph.width}, paragraphHeight=${layout.multiParagraph.height}"
            )
            // A scrollable row gives the paragraph infinite width; compare the actual line extent.
            assertFalse("Mode label must fit vertically", layout.didOverflowHeight)
            assertEquals(1, layout.lineCount)
            assertFalse(layout.isLineEllipsized(0))
            assertTrue("Whole mode label must fit its measured box", layout.getLineRight(0) <= layout.size.width + 1f)
        }
        inside(rule.onNodeWithText("Original").fetchSemanticsNode().boundsInRoot, bounds("listen-mode-toggle"))
        rule.onNodeWithTag("listen-sentences").performScrollTo().assertIsDisplayed()
        capture("listen-short-font2-read")
        rule.onNodeWithContentDescription("Sleep timer").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(2, timers)
        capture("listen-short-font2-controls")
    }

    @Test fun wideShortListenKeepsMediaBesideBoundedTransport() {
        var played = 0
        var timers = 0
        val sentences = listOf("The transcript has a usable column beside playback controls.", "Another sentence.")
        rule.setContent {
            Frame(891.dp, 411.dp) {
                AudioModeScreen("Landscape fixture", 0, sentences.size, sentences[0], false,
                    onPlayPause = { played++ }, onNext = {}, onPrevious = {}, onDismiss = {}, hasCanvas = true,
                    documentChunks = sentences, onOpenSleepTimer = { timers++ }, contentWindowInsets = WindowInsets(0, 0, 0, 0))
            }
        }
        rule.onNodeWithTag("listen-artwork").assertIsDisplayed()
        assertTrue(bounds("listen-mode-toggle").width <= 360 * pixelsPerDp + 1)
        rule.onNodeWithText("Read", useUnmergedTree = true).performClick()
        rule.onNodeWithText(sentences[0]).assertIsSelected().assertIsDisplayed()
        val transcript = bounds("listen-sentences")
        val play = rule.onNodeWithContentDescription("Play").performScrollTo().fetchSemanticsNode().boundsInRoot
        assertTrue(transcript.height >= 150 * pixelsPerDp)
        assertTrue(transcript.right < play.left)
        assertTrue(play.width <= 240 * pixelsPerDp + 1)
        rule.onNodeWithContentDescription("Play").performClick()
        assertEquals(1, played)
        rule.onNodeWithContentDescription("Sleep timer").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, timers)
        capture("listen-landscape-read")
    }
}

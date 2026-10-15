package com.veritas.reader

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.LocalVeritasMotion
import com.veritas.reader.ui.VeritasMotionScheme
import com.veritas.reader.ui.screens.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner

class BrandAndMotionUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun rotatingReadingStatsKeepCardBoundsAndActions() {
        var dark by mutableStateOf(false)
        var tab by mutableStateOf(VeritasHomeTab.HOME)
        rule.setContent {
            // Snapshot fixture stays active even if the user opens another app during capture.
            val owner = remember { object : LifecycleOwner {
                override val lifecycle = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
            } }
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                    Surface {
                        Column(Modifier.width(360.dp)) {
                            VeritasWordmark(Modifier.padding(16.dp))
                            HomeTodayReadingCard(9 * 60_000L, 30, 3, {}, {})
                            LibraryBottomNavBar(tab, true, { tab = it })
                        }
                    }
                }
            }
        }
        val before = rule.onNodeWithTag("home-today-reading-card").fetchSemanticsNode().boundsInRoot
        capture("light")
        rule.waitUntil(30_000) { rule.onAllNodesWithText("This week").fetchSemanticsNodes().isNotEmpty() }
        val after = rule.onNodeWithTag("home-today-reading-card").fetchSemanticsNode().boundsInRoot
        assertEquals(before.height, after.height, .5f)
        assertEquals(before.width, after.width, .5f)
        rule.onNodeWithText("Edit goal").assertIsDisplayed()
        rule.onNodeWithText("View insights →").assertIsDisplayed()
        rule.runOnIdle { dark = true; tab = VeritasHomeTab.LIBRARY }
        rule.onNodeWithContentDescription("Library").assertIsSelected()
        capture("dark-library")
        rule.runOnIdle { tab = VeritasHomeTab.NOTES }
        rule.onNodeWithContentDescription("Notes").assertIsSelected()
        capture("dark-notes")
        rule.waitUntil(30_000) { rule.onAllNodesWithText("Daily goal").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("To your target").assertIsDisplayed()
        capture("daily-goal")
        rule.waitUntil(30_000) { rule.onAllNodesWithText("Weekly reading").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Total since Monday").assertIsDisplayed()
        capture("weekly-time")
        assertEquals(before.height, rule.onNodeWithTag("home-today-reading-card").fetchSemanticsNode().boundsInRoot.height, .5f)
    }

    @Test fun reducedMotionKeepsReadingSummaryStaticAndSkipsStartup() {
        rule.setContent {
            CompositionLocalProvider(LocalVeritasMotion provides VeritasMotionScheme(true)) {
                MaterialTheme {
                    Box {
                        HomeTodayReadingCard(9 * 60_000L, 30, 3, {}, {})
                        VernStartupAnimation(true)
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(13_000)
        rule.onNodeWithText("Today’s reading").assertIsDisplayed()
        rule.onNodeWithText("This week").assertDoesNotExist()
        rule.onNodeWithTag("vern-startup-animation").assertDoesNotExist()
    }

    @Test fun notesSelectionTravelsLeftToLibraryAndStudy() {
        var tab by mutableStateOf(VeritasHomeTab.NOTES)
        rule.mainClock.autoAdvance = false
        rule.setContent { MaterialTheme { LibraryBottomNavBar(tab, true, { tab = it }) } }
        for (destination in listOf(VeritasHomeTab.LIBRARY, VeritasHomeTab.STUDY)) {
            rule.runOnIdle { tab = VeritasHomeTab.NOTES }
            rule.mainClock.advanceTimeBy(2_000)
            rule.runOnIdle { tab = destination }
            rule.mainClock.advanceTimeBy(80)
            val early = rule.onNodeWithTag("navigation-selection-HOME", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.center.x
            rule.mainClock.advanceTimeBy(2_000)
            val settled = rule.onNodeWithTag("navigation-selection-HOME", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.center.x
            assertTrue("Selection must arrive from Notes on the right", early > settled)
        }
    }

    @Test fun startupVExitsAndDoesNotReplayAfterRecomposition() {
        var enabled by mutableStateOf(true)
        rule.mainClock.autoAdvance = false
        rule.setContent { MaterialTheme { VernStartupAnimation(enabled) } }
        rule.mainClock.advanceTimeBy(450)
        rule.onNodeWithTag("vern-startup-animation").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithTag("vern-startup-animation").assertDoesNotExist()
        rule.runOnIdle { enabled = false }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { enabled = true }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("vern-startup-animation").assertDoesNotExist()
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "brand-motion/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}

package com.veritas.reader

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.*
import com.veritas.reader.ui.screens.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class MobilePolishUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun readingCardIsCompactAndEachSwipeHasItsOwnGraphic() {
        var large by mutableStateOf(false)
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 1.5f else 1f),
                LocalVeritasMotion provides VeritasMotionScheme(true)) {
                MaterialTheme(typography = veritasTypography(VeritasUiFont.CRISP)) {
                    Box(Modifier.width(if (large) 320.dp else 400.dp)) {
                        HomeTodayReadingCard(240_000, 15, 2, {}, {}, weeklyMillis = 600_000,
                            weekReadingMillis = listOf(360_000, 240_000, 0, 0, 0, 0, 0))
                    }
                }
            }
        }
        val card = rule.onNodeWithTag("home-today-reading-card").fetchSemanticsNode().boundsInRoot
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        assertTrue("Reading card stays compact", card.height / density <= 180f)
        repeat(4) { page ->
            rule.onNodeWithTag("reading-stat-graphic-$page").assertIsDisplayed()
            capture("reading-card-$page")
            rule.onNodeWithTag("home-today-reading-card").performTouchInput { swipeLeft() }
        }
        rule.onNodeWithTag("reading-stat-graphic-0").assertIsDisplayed()
        rule.runOnIdle { large = true }
        rule.onNodeWithText("View insights →").assertIsDisplayed()
        capture("reading-card-large")
    }

    @Test fun ringsShareARowAndStackForLargeText() {
        var large by mutableStateOf(false)
        val dates = listOf("2026-10-25", "2026-10-26")
        val snapshot = ReaderTrackerSnapshot(measuredReadingMillisByDate = dates.associateWith { 300_000L },
            listeningMillisByDate = mapOf(dates[1] to 120_000L))
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 1.5f else 1f),
                LocalVeritasMotion provides VeritasMotionScheme(true)) {
                MaterialTheme(typography = veritasTypography(VeritasUiFont.CRISP)) {
                    Box(Modifier.width(360.dp).background(MaterialTheme.colorScheme.background).padding(16.dp)) {
                        InsightsReadingRings(snapshot, dates.takeLast(1), dates, "October 2026")
                    }
                }
            }
        }
        rule.onNodeWithTag("insights-rings-side-by-side").assertIsDisplayed()
        capture("rings-side-by-side")
        rule.runOnIdle { large = true }
        rule.onNodeWithTag("insights-rings-stacked").assertIsDisplayed()
        capture("rings-large-text")
    }

    @Test fun addedTilesKeepBaseAndActionsWithEdgeToEdgeCoverBackground() {
        var dark by mutableStateOf(false)
        var opens = 0
        val documents = listOf(
            SavedDocument("polish-book", "October — Reading notes", "polish.txt", "DOCX", 1, 1, 3, 10, 200, "Reading notes"),
            SavedDocument("polish-classic", "A Tale of Two Cities", "polish-classic.txt", "Classic Book", 1, 1, 0, 100, 2000, "A classic"))
        rule.setContent {
            CompositionLocalProvider(LocalVeritasPackId provides "material_you", LocalVeritasMotion provides VeritasMotionScheme(true)) {
                MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme(), typography = veritasTypography(VeritasUiFont.CRISP)) {
                    Row(Modifier.width(412.dp).background(MaterialTheme.colorScheme.background).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        documents.forEachIndexed { index, doc ->
                            DocumentTileCard(doc, false, false, false, { opens++ }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                                modifier = Modifier.weight(1f).testTag("polish-tile-$index"))
                        }
                    }
                }
            }
        }
        rule.onNodeWithTag("polish-tile-0").performTouchInput { click(center) }
        assertEquals(1, opens)
        capture("added-joined-light")
        rule.runOnIdle { dark = true }
        capture("added-joined-dark")
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "mobile-polish/$name.png").apply { parentFile!!.mkdirs() }
            .outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

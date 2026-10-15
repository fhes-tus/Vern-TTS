package com.veritas.reader

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.AutoGraph
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
import com.veritas.reader.ui.screens.*
import org.junit.Assert.*
import com.veritas.reader.ui.LocalVeritasMotion
import com.veritas.reader.ui.VeritasMotionScheme
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

class ReadingInsightsGraphicsUiTest {
    @get:Rule val rule = createComposeRule()
    @Test fun emblemsAndRingMoveButReducedMotionIsStill() {
        var reduced by mutableStateOf(false)
        val today = LocalDate.now().toString()
        val snapshot = ReaderTrackerSnapshot(currentStreak = 2, longestStreak = 9,
            activeDateKeys = setOf(today), measuredReadingMillisByDate = mapOf(today to 900_000L),
            listeningMillisByDate = mapOf(today to 300_000L))
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalVeritasMotion provides VeritasMotionScheme(reduced)) {
                MaterialTheme {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        InsightsStreaksCard(snapshot, List(7) { LocalDate.now().minusDays(it.toLong()).toString() })
                        InsightsReadingRing(snapshot, listOf(today))
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1500)
        val tags = listOf("insights-fire", "insights-trophy", "insights-reading-ring")
        fun capture() = tags.map { rule.onNodeWithTag(it).captureToImage().asAndroidBitmap() }
        val before = capture()
        rule.mainClock.advanceTimeBy(1400)
        val after = capture()
        before.zip(after).forEachIndexed { i, pair -> assertFalse("${tags[i]} should animate", pair.first.sameAs(pair.second)) }
        rule.runOnIdle { reduced = true }
        rule.mainClock.advanceTimeBy(2000)
        val still = capture()
        rule.mainClock.advanceTimeBy(1400)
        still.zip(capture()).forEachIndexed { i, pair -> assertTrue("${tags[i]} should respect reduced motion", pair.first.sameAs(pair.second)) }
    }

    @Test fun overviewGraphicsAndWeekNavigationFitCompactLargeText() {
        val today = LocalDate.now()
        val monday = today.minusDays(today.dayOfWeek.value.toLong() - 1)
        val keys = List(7) { monday.plusDays(it.toLong()).toString() }
        val days = listOf(300_000L, 600_000L, 0L, 900_000L, 0L, 1_200_000L, 0L)
        val snapshot = ReaderTrackerSnapshot(currentStreak = 2, longestStreak = 9,
            activeDateKeys = setOf(today.toString(), today.minusDays(1).toString()),
            weeklyUsageByDay = days,
            weeklyHistory = listOf(WeekBars("Last week", days.map { it / 2 }, days.sum() / 2, false), WeekBars("This week", days, days.sum(), true)),
            readingMillisByDate = mapOf(today.toString() to 1_200_000L),
            measuredReadingMillisByDate = mapOf(today.toString() to 900_000L), listeningMillisByDate = mapOf(today.toString() to 300_000L))
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                MaterialTheme {
                    Column(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState()).testTag("compact-insights").padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        InsightsStreaksCard(snapshot, keys)
                        InsightsWeeklyAppCard(snapshot, monday)
                        CalendarHeatMap(snapshot.activeDateKeys, readingMillisByDate = snapshot.readingMillisByDate, summary = { InsightsReadingRing(snapshot, keys) })
                    }
                }
            }
        }
        rule.onNodeWithText("Current streak").assertIsDisplayed()
        rule.onNodeWithContentDescription("Previous week").performScrollTo().performClick()
        rule.onNodeWithText("Last week").assertIsDisplayed()
        rule.onNodeWithContentDescription("Next week").performClick()
        rule.onAllNodesWithText("This week")[0].assertIsDisplayed()
        rule.onNodeWithText("Reading Activity").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Listening").assertIsDisplayed()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.getExternalFilesDir(null), "remaining-fixes/insights-compact-graphics.png").apply { parentFile!!.mkdirs() }
        output.outputStream().use { rule.onNodeWithTag("compact-insights").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun booksAndHistoryGraphicsAnimateAndRespectReducedMotion() {
        var reduced by mutableStateOf(false)
        val now = System.currentTimeMillis()
        val doc = SavedDocument("animated-book", "The Pilgrim's Progress", "fixture.txt", "EPUB", now, now,
            39, 100, 1000, "Fixture", catalogId = "classic_pilgrims_progress")
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalVeritasMotion provides VeritasMotionScheme(reduced)) {
                MaterialTheme {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        InsightsBookCard(doc, emptyList(), null, {}, {}, {})
                        InsightsGoalRing(1_200_000, 30)
                        InsightsDetailCard("Today", androidx.compose.material.icons.Icons.Default.AutoGraph) {
                            InsightsModeBars(900_000, 300_000, "today")
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1500)
        val tags = listOf("insights-book-cover-${doc.id}", "insights-goal-ring", "insights-mode-today-Reading")
        fun capture() = tags.map { rule.onNodeWithTag(it).captureToImage().asAndroidBitmap() }
        val before = capture()
        rule.mainClock.advanceTimeBy(1400)
        before.zip(capture()).forEachIndexed { i, pair -> assertFalse("${tags[i]} should animate", pair.first.sameAs(pair.second)) }
        rule.runOnIdle { reduced = true }
        rule.mainClock.advanceTimeBy(1800)
        val still = capture()
        rule.mainClock.advanceTimeBy(1400)
        still.zip(capture()).forEachIndexed { i, pair -> assertTrue("${tags[i]} should stop with reduced motion", pair.first.sameAs(pair.second)) }
    }

    @Test fun booksAndHistoryFitCompactLargeTextAndRetainActions() {
        val now = System.currentTimeMillis()
        val doc = SavedDocument("compact-book", "The Pilgrim's Progress", "fixture.txt", "EPUB", now, now,
            39, 100, 1000, "Fixture", catalogId = "classic_pilgrims_progress")
        val session = ReadingSession("compact-session", doc.id, doc.title, "rev", ReadingSessionMode.LISTENING,
            now - 300_000, now, 2, 39, listOf(ReadingSessionSpan(now - 300_000, 300_000, "UTC", 0)), "paused", correctedMillis = 240_000)
        var continued = false
        var corrected = false
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                MaterialTheme {
                    Column(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState())
                        .testTag("compact-details").padding(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        InsightsBookCard(doc, listOf(session), null, { continued = true }, {}, {})
                        InsightsGoalRing(1_200_000, 30)
                        InsightsSessionCard(session, doc) { corrected = true }
                    }
                }
            }
        }
        rule.onNodeWithContentDescription("Book position 40 percent").assertExists()
        rule.onNodeWithText("Continue").performScrollTo().performClick()
        assertTrue(continued)
        fun screenshot(name: String) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val output = File(context.getExternalFilesDir(null), "remaining-fixes/$name.png").apply { parentFile!!.mkdirs() }
            output.outputStream().use { rule.onNodeWithTag("compact-details").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        screenshot("insights-books-compact")
        rule.onNodeWithText("Correct session").performScrollTo().performClick()
        assertTrue(corrected)
        rule.onNodeWithText("Corrected · original 5m").assertIsDisplayed()
        screenshot("insights-history-compact")
    }

}

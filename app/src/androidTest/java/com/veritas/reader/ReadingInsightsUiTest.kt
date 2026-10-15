package com.veritas.reader

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.screens.ReadingStatsDashboardDialog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ReadingInsightsUiTest {
    @get:Rule val rule = createComposeRule()
    @Test fun measuredOverviewBooksAndHistoryExposeSeparateModesAndContinuation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks"))
        val now = System.currentTimeMillis()
        val key = trackerDateKey(now)
        val doc = SavedDocument("insights-ui", "Insights fixture", "fixture.txt", "TXT", now, now, 3, 10, 500, "Fixture")
        val session = ReadingSession("insights-session", doc.id, doc.title, "rev", ReadingSessionMode.READING,
            now - 900_000, now, 0, 3, listOf(ReadingSessionSpan(now - 900_000, 900_000, "UTC", 0)), "reader hidden")
        val snapshot = ReaderTrackerSnapshot(sessions = listOf(session), measuredReadingMillisByDate = mapOf(key to 900_000),
            listeningMillisByDate = mapOf(key to 300_000), readingMillisByDate = mapOf(key to 1_200_000), activeDateKeys = setOf(key), currentStreak = 1, longestStreak = 3,
            weeklyUsageByDay = listOf(300_000, 600_000, 0, 900_000, 0, 1_200_000, 0))
        var opened: SavedDocument? = null
        rule.setContent { MaterialTheme {
            ReadingStatsDashboardDialog(snapshot, listOf(doc), emptyMap(), onDismiss = {}, onOpenDocument = { opened = it })
        } }
        rule.onNodeWithText("Reading Insights").assertIsDisplayed()
        rule.onNodeWithText("Streaks").assertIsDisplayed()
        rule.onNodeWithText("Current streak").assertIsDisplayed()
        rule.onNodeWithText("Weekly App Activity").assertIsDisplayed()
        val top = File(context.getExternalFilesDir(null), "remaining-fixes/insights-overview-top.png").apply { parentFile!!.mkdirs() }
        top.outputStream().use { rule.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        rule.onNodeWithText("Reading Activity").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Reading", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Listening", useUnmergedTree = true).assertIsDisplayed()
        rule.onAllNodesWithText("15m")[0].assertIsDisplayed()
        rule.onNodeWithText("5m").assertIsDisplayed()
        rule.onNodeWithText("Reading timer").assertDoesNotExist()
        rule.onNodeWithText("Avg WPM").assertDoesNotExist()
        val output = File(context.getExternalFilesDir(null), "remaining-fixes/insights-overview.png").apply { parentFile!!.mkdirs() }
        output.outputStream().use { rule.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        rule.onNodeWithText("History").performClick()
        rule.onNodeWithText("Today total 20m").performScrollTo().assertIsDisplayed()
        val historyImage = File(context.getExternalFilesDir(null), "remaining-fixes/insights-history-top.png")
        historyImage.outputStream().use { rule.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        rule.onNodeWithTag("insights-content").performScrollToNode(hasText("Add manual session"))
        rule.onNodeWithText("Add manual session").assertIsDisplayed()
        rule.onNodeWithTag("insights-content").performScrollToNode(hasText("Correct session"))
        rule.onNodeWithText("Correct session").performClick()
        rule.onNodeWithText("Correct duration").assertIsDisplayed()
        rule.onNodeWithText("Cancel").performClick()
        rule.onNodeWithText("Books").performClick()
        rule.onNodeWithText("Insights fixture").assertIsDisplayed()
        val booksImage = File(context.getExternalFilesDir(null), "remaining-fixes/insights-books.png")
        booksImage.outputStream().use { rule.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        rule.onNodeWithText("Mark finished").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Continue").performClick()
        assertEquals(doc, opened)
    }
}

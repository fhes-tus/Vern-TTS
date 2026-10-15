package com.veritas.reader

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.screens.CalendarHeatMap
import com.veritas.reader.ui.screens.InsightsStreaksCard
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

class StreakCompatibilityUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun legacyReadingMarkersRestoreStreaksAndCalendarWithoutInventingMinutes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks"))
        val repository = DocumentRepository(context)
        val prefs = repository.prefs
        val oldDays = prefs.getString(DocumentRepository.KEY_TRACKER_DAYS, null)
        val oldSessions = prefs.getString(READING_SESSIONS_KEY, null)
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val now = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val legacyDates = listOf(today, today.minusDays(2)) + (10L..14L).map(today::minusDays)
        // This is the old serialized shape: document markers, no readingMillis field.
        val legacyDays = JSONArray().apply {
            legacyDates.forEach { date -> put(JSONObject().put("dateKey", date.toString())
                .put("appOpenCount", 1).put("usageMillis", 300_000)
                .put("readDocumentIds", JSONArray().put("legacy-book"))) }
        }
        val sessionStart = today.minusDays(1).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val measured = ReadingSession("compatibility-session", "measured-book", "Measured book", "fixture",
            ReadingSessionMode.LISTENING, sessionStart, sessionStart + 120_000, 0, 1,
            listOf(ReadingSessionSpan(sessionStart, 120_000, zone.id, 0)), "paused")
        val excludedStart = today.minusDays(3).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val excluded = measured.copy(id = "excluded-session", startedAt = excludedStart,
            checkpointAt = excludedStart + 120_000, spans = listOf(ReadingSessionSpan(excludedStart, 120_000, zone.id, 0)), excluded = true)
        try {
            prefs.edit().putString(DocumentRepository.KEY_TRACKER_DAYS, legacyDays.toString()).commit()
            repository.saveReadingSessions(listOf(measured, excluded))
            fun assertCompatible(): ReaderTrackerSnapshot {
                val snapshot = repository.loadReaderTrackerSnapshot(now)
                assertEquals(3, snapshot.currentStreak)
                assertEquals(5, snapshot.longestStreak)
                assertTrue(snapshot.activeDateKeys.containsAll(legacyDates.map(LocalDate::toString)))
                assertFalse(snapshot.activeDateKeys.contains(today.minusDays(3).toString()))
                assertFalse(snapshot.readingMillisByDate.containsKey(today.toString()))
                assertTrue(snapshot.legacyReadingMillisByDate.isEmpty())
                assertEquals(120_000L, snapshot.readingMillisByDate.values.sum())
                return snapshot
            }
            assertCompatible()
            val backup = JSONObject().put("schema", "veritas.reader.backup.v1")
                .put("documents", JSONArray()).put("trackerDays", legacyDays).toString()
            // Re-importing old markers must neither lose streaks nor multiply minutes.
            repeat(2) { repository.restoreBackupJson(backup, false); assertCompatible() }
            repository.recordAppOpen(today.minusDays(3).atTime(12, 0).atZone(zone).toInstant().toEpochMilli())
            val snapshot = assertCompatible()
            rule.setContent {
                MaterialTheme {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .testTag("legacy-streak-fixture").padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        InsightsStreaksCard(snapshot, trackerWeekKeys(now))
                        CalendarHeatMap(snapshot.activeDateKeys, snapshot.openedDateKeys, snapshot.readingMillisByDate)
                    }
                }
            }
            rule.mainClock.advanceTimeBy(1500)
            rule.onNodeWithText("Current streak").assertIsDisplayed()
            rule.onAllNodesWithText("3")[0].assertExists()
            rule.onNodeWithContentDescription("${today}: reading recorded").performScrollTo().performClick()
            rule.onNodeWithText("Reading recorded; duration not available").performScrollTo().assertIsDisplayed()
            val file = File(context.getExternalFilesDir(null), "remaining-fixes/legacy-streak-calendar.png").apply { parentFile!!.mkdirs() }
            file.outputStream().use { rule.onNodeWithTag("legacy-streak-fixture").captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            prefs.edit().apply {
                if (oldDays == null) remove(DocumentRepository.KEY_TRACKER_DAYS) else putString(DocumentRepository.KEY_TRACKER_DAYS, oldDays)
                if (oldSessions == null) remove(READING_SESSIONS_KEY) else putString(READING_SESSIONS_KEY, oldSessions)
            }.commit()
        }
    }
}

package com.veritas.reader

import androidx.core.content.edit
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun DocumentRepository.recordAppOpen(nowMillis: Long = System.currentTimeMillis()): ReaderTrackerSnapshot {
    val key = trackerDateKey(nowMillis)
    val updated = loadTrackerDays().toMutableMap()
    val current = updated[key] ?: ReaderTrackerDay(dateKey = key)
    updated[key] = current.copy(appOpenCount = maxOf(current.appOpenCount, 1))
    saveTrackerDays(updated.values)
    return loadReaderTrackerSnapshot(nowMillis)
}

fun DocumentRepository.recordUsageDuration(durationMillis: Long, endMillis: Long = System.currentTimeMillis()): ReaderTrackerSnapshot {
    val cleanDuration = durationMillis.coerceIn(0L, DocumentRepository.MAX_TRACKER_SESSION_MILLIS)
    if (cleanDuration <= 0L) return loadReaderTrackerSnapshot(endMillis)
    val key = trackerDateKey(endMillis)
    val updated = loadTrackerDays().toMutableMap()
    val current = updated[key] ?: ReaderTrackerDay(dateKey = key, appOpenCount = 1)
    updated[key] = current.copy(
        appOpenCount = maxOf(current.appOpenCount, 1),
        usageMillis = current.usageMillis + cleanDuration
    )
    saveTrackerDays(updated.values)
    return loadReaderTrackerSnapshot(endMillis)
}

fun DocumentRepository.recordDocumentRead(documentId: String, title: String, nowMillis: Long = System.currentTimeMillis()): ReaderTrackerSnapshot {
    if (documentId.isBlank()) return loadReaderTrackerSnapshot(nowMillis)
    val key = trackerDateKey(nowMillis)
    val updated = loadTrackerDays().toMutableMap()
    val current = updated[key] ?: ReaderTrackerDay(dateKey = key, appOpenCount = 1)
    updated[key] = current.copy(
        appOpenCount = maxOf(current.appOpenCount, 1),
        readDocumentIds = current.readDocumentIds + documentId
    )
    saveTrackerDays(updated.values)
    return loadReaderTrackerSnapshot(nowMillis)
}

fun DocumentRepository.recordDocumentProgress(document: SavedDocument, nowMillis: Long = System.currentTimeMillis()): ReaderTrackerSnapshot {
    recordDocumentRead(document.id, document.title, nowMillis)
    if (document.chunkCount > 0) {
        val progress = ((document.currentIndex + 1).toFloat() / document.chunkCount.toFloat()).coerceIn(0f, 1f)
        if (progress >= ReaderTrackerMath.COMPLETION_THRESHOLD) {
            recordDocumentCompletion(document.id, document.title, nowMillis)
        }
    }
    return loadReaderTrackerSnapshot(nowMillis)
}

fun DocumentRepository.loadReaderTrackerSnapshot(nowMillis: Long = System.currentTimeMillis()): ReaderTrackerSnapshot {
    val days = loadTrackerDays().values.sortedBy { it.dateKey }
    val todayKey = trackerDateKey(nowMillis)
    // Streaks and the heatmap count days the user actually READ something (a document
    // was opened for reading that day), not days the app was merely launched. Merely
    // opening the app used to grant a "1-day streak", which cheapened the streak.
    val readingDateKeys = days
        .filter { it.readDocumentIds.isNotEmpty() }
        .map { it.dateKey }
        .toSet()
    val openedDateKeys = days
        .filter { it.appOpenCount > 0 || it.usageMillis > 0L }
        .map { it.dateKey }
        .toSet()
    val weekKeys = trackerWeekKeys(nowMillis)
    val (weeklyUsage, weeklyAverage) = ReaderTrackerMath.weeklyUsage(days, weekKeys)
    val daysByKey = days.associateBy { it.dateKey }
    val weeklyUsageByDay = weekKeys.map { key -> daysByKey[key]?.usageMillis ?: 0L }
    // Last 8 weeks of Monday-first daily totals for the swipeable weekly chart,
    // ordered oldest -> newest so the chart defaults to the current (last) week.
    val weeksToShow = 8
    val weeklyHistory = (weeksToShow - 1 downTo 0).map { offset ->
        val weekTime = nowMillis - offset * 7L * 24L * 60L * 60L * 1000L
        val keys = trackerWeekKeys(weekTime)
        val values = keys.map { daysByKey[it]?.usageMillis ?: 0L }
        val label = when (offset) {
            0 -> "This week"
            1 -> "Last week"
            else -> trackerWeekRangeLabel(keys.first(), keys.last())
        }
        WeekBars(label = label, values = values, totalMillis = values.sum(), isCurrentWeek = offset == 0)
    }
    val readThisWeek = weekKeys
        .flatMap { key -> daysByKey[key]?.readDocumentIds.orEmpty() }
        .toSet()
        .size
    val completions = loadTrackerCompletions()
    val completedThisMonth = ReaderTrackerMath.monthCompletionCount(
        completions = completions,
        monthPrefix = trackerDateKey(nowMillis).take(7),
        dateKeyFor = ::trackerDateKey
    )
    return ReaderTrackerSnapshot(
        currentStreak = ReaderTrackerMath.currentStreak(readingDateKeys, todayKey),
        longestStreak = ReaderTrackerMath.longestStreak(readingDateKeys),
        weeklyUsageMillis = weeklyUsage,
        weeklyAverageMillis = weeklyAverage,
        documentsReadThisWeek = readThisWeek,
        documentsCompletedThisMonth = completedThisMonth,
        weeklyUsageByDay = weeklyUsageByDay,
        weeklyHistory = weeklyHistory,
        recentCompletions = completions.sortedByDescending { it.completedAt }.take(8),
        activeDateKeys = readingDateKeys,
        openedDateKeys = openedDateKeys
    )
}

internal fun DocumentRepository.recordDocumentCompletion(documentId: String, title: String, nowMillis: Long) {
    if (documentId.isBlank()) return
    val completions = loadTrackerCompletions().toMutableList()
    if (completions.none { it.documentId == documentId }) {
        completions.add(ReaderTrackerCompletion(documentId = documentId, title = title, completedAt = nowMillis))
        saveTrackerCompletions(completions)
    }
}

fun DocumentRepository.loadTrackerDays(): Map<String, ReaderTrackerDay> {
    val raw = prefs.getString(DocumentRepository.KEY_TRACKER_DAYS, "[]") ?: "[]"
    val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    val days = linkedMapOf<String, ReaderTrackerDay>()
    for (i in 0 until array.length()) {
        val day = array.optJSONObject(i)?.let(ReaderTrackerDay::fromJson) ?: continue
        if (day.dateKey.isNotBlank()) days[day.dateKey] = day
    }
    return days
}

fun DocumentRepository.saveTrackerDays(days: Collection<ReaderTrackerDay>) {
    val array = JSONArray()
    days.sortedByDescending { it.dateKey }
        .take(DocumentRepository.MAX_TRACKER_DAYS)
        .sortedBy { it.dateKey }
        .forEach { array.put(it.toJson()) }
    prefs.edit { putString(DocumentRepository.KEY_TRACKER_DAYS, array.toString()) }
    updateVeritasWidgets(appContext)
}

internal fun DocumentRepository.loadTrackerCompletions(): List<ReaderTrackerCompletion> {
    val raw = prefs.getString(DocumentRepository.KEY_TRACKER_COMPLETIONS, "[]") ?: "[]"
    val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    val completions = mutableListOf<ReaderTrackerCompletion>()
    val seen = mutableSetOf<String>()
    for (i in 0 until array.length()) {
        val completion = array.optJSONObject(i)?.let(ReaderTrackerCompletion::fromJson) ?: continue
        if (completion.documentId.isNotBlank() && seen.add(completion.documentId)) completions.add(completion)
    }
    return completions
}

internal fun DocumentRepository.saveTrackerCompletions(completions: List<ReaderTrackerCompletion>) {
    val array = JSONArray()
    completions.sortedByDescending { it.completedAt }
        .distinctBy { it.documentId }
        .take(DocumentRepository.MAX_TRACKER_COMPLETIONS)
        .forEach { array.put(it.toJson()) }
    prefs.edit { putString(DocumentRepository.KEY_TRACKER_COMPLETIONS, array.toString()) }
    updateVeritasWidgets(appContext)
}

internal fun trackerDateKey(timestamp: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestamp))

internal fun trackerWeekRangeLabel(startKey: String, endKey: String): String {
    val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val out = SimpleDateFormat("MMM d", Locale.getDefault())
    return runCatching {
        val start = parser.parse(startKey)
        val end = parser.parse(endKey)
        if (start != null && end != null) "${out.format(start)} – ${out.format(end)}" else startKey
    }.getOrDefault(startKey)
}

internal fun trackerWeekKeys(timestamp: Long): List<String> {
    val cursor = Calendar.getInstance().apply {
        timeInMillis = timestamp
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        while (get(Calendar.DAY_OF_WEEK) != firstDayOfWeek) {
            add(Calendar.DAY_OF_YEAR, -1)
        }
    }
    return List(7) {
        val key = trackerDateKey(cursor.timeInMillis)
        cursor.add(Calendar.DAY_OF_YEAR, 1)
        key
    }
}
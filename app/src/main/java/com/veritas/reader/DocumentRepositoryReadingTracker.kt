package com.veritas.reader

import androidx.core.content.edit
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun DocumentRepository.recordAppOpen(nowMillis: Long = System.currentTimeMillis()): ReaderTrackerSnapshot = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    val key = trackerDateKey(nowMillis)
    val updated = loadTrackerDays().toMutableMap()
    val current = updated[key] ?: ReaderTrackerDay(dateKey = key)
    updated[key] = current.copy(appOpenCount = maxOf(current.appOpenCount, 1))
    saveTrackerDays(updated.values)
    return@synchronized loadReaderTrackerSnapshot(nowMillis)
}

fun DocumentRepository.recordUsageDuration(durationMillis: Long, endMillis: Long = System.currentTimeMillis()): ReaderTrackerSnapshot = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    val cleanDuration = durationMillis.coerceIn(0L, DocumentRepository.MAX_TRACKER_SESSION_MILLIS)
    if (cleanDuration <= 0L) return@synchronized loadReaderTrackerSnapshot(endMillis)
    val key = trackerDateKey(endMillis)
    val updated = loadTrackerDays().toMutableMap()
    val current = updated[key] ?: ReaderTrackerDay(dateKey = key, appOpenCount = 1)
    updated[key] = current.copy(
        appOpenCount = maxOf(current.appOpenCount, 1),
        usageMillis = current.usageMillis + cleanDuration
    )
    saveTrackerDays(updated.values)
    return@synchronized loadReaderTrackerSnapshot(endMillis)
}

fun DocumentRepository.recordDocumentRead(documentId: String, title: String, nowMillis: Long = System.currentTimeMillis()): ReaderTrackerSnapshot = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    if (documentId.isBlank()) return@synchronized loadReaderTrackerSnapshot(nowMillis)
    val key = trackerDateKey(nowMillis)
    val updated = loadTrackerDays().toMutableMap()
    val current = updated[key] ?: ReaderTrackerDay(dateKey = key, appOpenCount = 1)
    updated[key] = current.copy(
        appOpenCount = maxOf(current.appOpenCount, 1),
        readDocumentIds = current.readDocumentIds + documentId
    )
    saveTrackerDays(updated.values)
    return@synchronized loadReaderTrackerSnapshot(nowMillis)
}

fun DocumentRepository.recordDocumentProgress(document: SavedDocument, nowMillis: Long = System.currentTimeMillis()): ReaderTrackerSnapshot {
    // Progress/seek alone does not prove reading or completion. Completion is explicit or actual playback end.
    return loadReaderTrackerSnapshot(nowMillis)
}

fun DocumentRepository.loadReaderTrackerSnapshot(nowMillis: Long = System.currentTimeMillis()): ReaderTrackerSnapshot {
    val days = loadTrackerDays().values.sortedBy { it.dateKey }
    val todayKey = trackerDateKey(nowMillis)
    val sessions = loadReadingSessions()
    fun totals(mode: ReadingSessionMode): Map<String, Long> {
        val totals = mutableMapOf<String, Long>()
        sessions.filter { it.mode == mode }.forEach { session -> session.millisByDate().forEach { (day, millis) ->
            totals[day] = (totals[day] ?: 0) + millis
        } }
        return totals
    }
    val reading = totals(ReadingSessionMode.READING)
    val listening = totals(ReadingSessionMode.LISTENING)
    val legacy = days.filter { it.readingMillis > 0 }.associate { it.dateKey to it.readingMillis }
    val combined = (reading.keys + listening.keys + legacy.keys).associateWith { (reading[it] ?: 0) + (listening[it] ?: 0) + (legacy[it] ?: 0) }
    // Older releases recorded reading-day markers without a duration. Preserve
    // those earned days for streaks/the calendar; unknown minutes stay unknown.
    // App opens alone still do not qualify, and measured totals remain unchanged.
    val readingDateKeys = combined.filterValues { it > 0 }.keys +
        days.filter { it.readDocumentIds.isNotEmpty() }.map { it.dateKey }
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
        val weekTime = Calendar.getInstance().apply { timeInMillis = nowMillis; add(Calendar.WEEK_OF_YEAR, -offset) }.timeInMillis
        val keys = trackerWeekKeys(weekTime)
        val values = keys.map { daysByKey[it]?.usageMillis ?: 0L }
        val label = when (offset) {
            0 -> "This week"
            1 -> "Last week"
            else -> trackerWeekRangeLabel(keys.first(), keys.last())
        }
        WeekBars(label = label, values = values, totalMillis = values.sum(), isCurrentWeek = offset == 0)
    }
    val readThisWeek = (sessions.filter { it.millisByDate().any { (date, duration) -> date in weekKeys && duration > 0 } }
        .map { it.documentId } + weekKeys.flatMap { key -> daysByKey[key]?.readDocumentIds.orEmpty() }).toSet().size
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
        openedDateKeys = openedDateKeys,
        readingMillisByDate = combined,
        weeklyReadingMillis = weekKeys.sumOf { combined[it] ?: 0 },
        sessions = sessions,
        measuredReadingMillisByDate = reading,
        listeningMillisByDate = listening,
        legacyReadingMillisByDate = legacy,
        completionDates = completions.associate { it.documentId to it.completedAt }
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

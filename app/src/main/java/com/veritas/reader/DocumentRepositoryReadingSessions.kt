package com.veritas.reader

import org.json.JSONArray

internal const val READING_SESSIONS_KEY = "reading_sessions_v1"

fun DocumentRepository.loadReadingSessions(): List<ReadingSession> {
    val array = runCatching { JSONArray(prefs.getString(READING_SESSIONS_KEY, "[]")) }.getOrDefault(JSONArray())
    return (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(ReadingSession::fromJson) }
        .distinctBy { it.id }.sortedByDescending { it.startedAt }
}

internal fun DocumentRepository.saveReadingSessions(sessions: List<ReadingSession>) {
    check(prefs.edit().putString(READING_SESSIONS_KEY, JSONArray().apply {
        sessions.distinctBy { it.id }.forEach { put(it.toJson()) }
    }.toString()).commit()) { "Could not save reading sessions" }
}

internal fun DocumentRepository.putReadingSession(session: ReadingSession) = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    val sessions = loadReadingSessions().toMutableList()
    val index = sessions.indexOfFirst { it.id == session.id }
    if (index < 0) sessions.add(session) else {
        val existing = sessions[index]
        // Never overwrite a deliberate correction while a measured session is still running.
        sessions[index] = session.copy(correctedMillis = existing.correctedMillis, excluded = existing.excluded)
    }
    saveReadingSessions(sessions)
}

fun DocumentRepository.correctReadingSession(id: String, durationMillis: Long?, excluded: Boolean = false) = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    saveReadingSessions(loadReadingSessions().map { if (it.id == id) it.copy(
        correctedMillis = durationMillis?.coerceIn(0, 86_400_000), excluded = excluded, modifiedAt = System.currentTimeMillis()) else it })
}

fun DocumentRepository.addManualReadingSession(document: SavedDocument, mode: ReadingSessionMode, startedAt: Long, durationMillis: Long) {
    val duration = durationMillis.coerceIn(1, 86_400_000)
    putReadingSession(ReadingSession(java.util.UUID.randomUUID().toString(), document.id, document.title,
        "manual", mode, startedAt, startedAt + duration, document.currentIndex, document.currentIndex,
        listOf(ReadingSessionSpan(startedAt, duration, java.time.ZoneId.systemDefault().id, document.currentIndex)),
        closedReason = "manual", manual = true))
}

internal fun DocumentRepository.measuredReadingTimes(nowMillis: Long): Map<String, Long> {
    val month = trackerDateKey(nowMillis).take(7)
    return loadReadingSessions().groupBy { it.documentId }.mapValues { (_, sessions) ->
        sessions.sumOf { it.millisByDate().filterKeys { date -> date.startsWith(month) }.values.sum() }
    }
}

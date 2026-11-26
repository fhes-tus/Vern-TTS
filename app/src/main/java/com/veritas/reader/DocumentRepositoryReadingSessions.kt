package com.veritas.reader

import org.json.JSONArray
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

internal const val READING_SESSIONS_KEY = "reading_sessions_v1"

fun DocumentRepository.loadReadingSessions(): List<ReadingSession> {
    val array = runCatching { JSONArray(prefs.getString(READING_SESSIONS_KEY, "[]")) }.getOrDefault(JSONArray())
    return (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(ReadingSession::fromJson) }
        .distinctBy { it.id }.sortedByDescending { it.startedAt }
}

internal fun DocumentRepository.saveReadingSessions(sessions: List<ReadingSession>) = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    val previous = prefs.getString(READING_SESSIONS_KEY, null)
    val updated = JSONArray().apply {
        sessions.distinctBy { it.id }.forEach { put(it.toJson()) }
    }.toString()
    if (!prefs.edit().putString(READING_SESSIONS_KEY, updated).commit()) {
        // A failed commit can still publish the attempted value to in-process readers.
        prefs.edit().putString(READING_SESSIONS_KEY, previous).commit()
        error("Could not save reading sessions")
    }
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
    val sessions = loadReadingSessions().toMutableList()
    val index = sessions.indexOfFirst { it.id == id }
    check(index >= 0) { "This reading session is no longer available" }
    val session = sessions[index]
    val correction = durationMillis?.coerceIn(0, 86_400_000) ?: session.correctedMillis.takeIf { excluded }
    sessions[index] = session.copy(correctedMillis = correction,
        excluded = excluded, modifiedAt = System.currentTimeMillis())
    saveReadingSessions(sessions)
}

fun DocumentRepository.addManualReadingSession(document: SavedDocument, mode: ReadingSessionMode, startedAt: Long, durationMillis: Long) {
    val duration = durationMillis.coerceIn(1, 86_400_000)
    putReadingSession(ReadingSession(UUID.randomUUID().toString(), document.id, document.title,
        "manual", mode, startedAt, startedAt + duration, document.currentIndex, document.currentIndex,
        listOf(ReadingSessionSpan(startedAt, duration, ZoneId.systemDefault().id, document.currentIndex)),
        closedReason = "manual", manual = true))
}

internal fun DocumentRepository.addManualReadingSession(
    id: String,
    documentId: String,
    mode: ReadingSessionMode,
    date: LocalDate,
    durationMillis: Long
) = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    val zone = ZoneId.systemDefault()
    require(!date.isAfter(LocalDate.now(zone)))
    require(durationMillis in 1L..86_400_000L)
    val document = checkNotNull(findDocument(documentId)) { "This book is no longer available" }
    val startedAt = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    val location = document.currentIndex
    putReadingSession(ReadingSession(id, document.id, document.title, "manual", mode, startedAt,
        startedAt + durationMillis, location, location, listOf(ReadingSessionSpan(startedAt, durationMillis, zone.id, location)),
        closedReason = "manual", manual = true, modifiedAt = System.currentTimeMillis(), manualDate = date))
}

internal data class ReadingTimerSettings(val idleSeconds: Int, val explicitOnly: Boolean)

private const val READING_IDLE_SECONDS = "reading_idle_seconds"
private const val READING_EXPLICIT_ONLY = "reading_explicit_only"

internal fun DocumentRepository.loadReadingTimerSettings() = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    ReadingTimerSettings(prefs.getInt(READING_IDLE_SECONDS, 120).coerceIn(30, 900),
        prefs.getBoolean(READING_EXPLICIT_ONLY, false))
}

internal fun DocumentRepository.saveReadingTimerSettings(idleSeconds: Int?, explicitOnly: Boolean?) =
    synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
        val edit = prefs.edit()
        idleSeconds?.let { edit.putInt(READING_IDLE_SECONDS, it.coerceIn(30, 900)) }
        explicitOnly?.let { edit.putBoolean(READING_EXPLICIT_ONLY, it) }
        val previousIdle = prefs.getInt(READING_IDLE_SECONDS, 120).takeIf { prefs.contains(READING_IDLE_SECONDS) }
        val previousExplicit = prefs.getBoolean(READING_EXPLICIT_ONLY, false).takeIf { prefs.contains(READING_EXPLICIT_ONLY) }
        if (!edit.commit()) {
            prefs.edit().apply {
                if (idleSeconds != null) {
                    if (previousIdle == null) remove(READING_IDLE_SECONDS) else putInt(READING_IDLE_SECONDS, previousIdle)
                }
                if (explicitOnly != null) {
                    if (previousExplicit == null) remove(READING_EXPLICIT_ONLY)
                    else putBoolean(READING_EXPLICIT_ONLY, previousExplicit)
                }
            }.commit()
            error("Could not save reading timer settings")
        }
        loadReadingTimerSettings()
    }

internal fun DocumentRepository.measuredReadingTimes(nowMillis: Long): Map<String, Long> {
    val month = trackerDateKey(nowMillis).take(7)
    return loadReadingSessions().groupBy { it.documentId }.mapValues { (_, sessions) ->
        sessions.sumOf { it.millisByDate().filterKeys { date -> date.startsWith(month) }.values.sum() }
    }
}

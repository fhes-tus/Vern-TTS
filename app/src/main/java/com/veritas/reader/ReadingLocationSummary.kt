package com.veritas.reader

import java.io.Writer
import java.time.Instant

internal data class ReadingLocationSummary(val firstVisitMillis: Long = 0, val revisitMillis: Long = 0, val unknownMillis: Long = 0)

/** Only locations with actual positive spans are visited; seeks never fill skipped ranges. */
internal fun readingLocationSummaries(sessions: List<ReadingSession>): Map<String, ReadingLocationSummary> {
    val records = sessions.groupBy { it.id }.values.map { it.maxBy { record -> record.modifiedAt } }
    val byId = records.associateBy { it.id }
    data class Event(val session: ReadingSession, val span: ReadingSessionSpan)
    val events = records.filterNot { it.excluded || it.manual || it.revision.isBlank() }
        .flatMap { session -> session.spans.filter { it.durationMillis > 0 }.map { Event(session, it) } }
        .sortedWith(compareBy<Event> { it.span.startedAt }.thenBy { it.session.startedAt }.thenBy { it.session.id })
    val seen = mutableSetOf<Triple<String, String, Int>>()
    val lastVisit = mutableMapOf<String, Pair<Int, Boolean>>()
    val raw = mutableMapOf<String, LongArray>()
    events.forEach { event ->
        val session = event.session; val span = event.span
        val prior = lastVisit[session.id]
        val first = if (prior?.first == span.location) prior.second
            else seen.add(Triple(session.documentId, session.revision, span.location))
        lastVisit[session.id] = span.location to first
        val totals = raw.getOrPut(session.id) { LongArray(2) }
        totals[if (first) 0 else 1] += span.durationMillis
    }
    return byId.mapValues { (id, session) ->
        val amounts = raw[id]
        if (session.activeMillis <= 0) ReadingLocationSummary()
        else if (amounts == null || session.measuredMillis <= 0) ReadingLocationSummary(unknownMillis = session.activeMillis)
        else {
            val first = (amounts[0].toDouble() * session.activeMillis / session.measuredMillis).toLong().coerceIn(0, session.activeMillis)
            ReadingLocationSummary(first, session.activeMillis - first)
        }
    }
}

internal fun writeReadingSessionsCsv(sessions: List<ReadingSession>, output: Writer) {
    val summary = readingLocationSummaries(sessions)
    fun cell(value: String): String {
        val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'$value" else value
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }
    output.write("session_id,document_id,title,started_utc,mode,active_ms,measured_ms,first_visit_ms,revisit_ms,unknown_ms,start_location,end_location,manual,excluded,closed_reason,content_revision\r\n")
    sessions.sortedWith(compareBy<ReadingSession> { it.startedAt }.thenBy { it.id }).forEach { session ->
        val locations = summary[session.id] ?: ReadingLocationSummary()
        val row = listOf(session.id, session.documentId, session.title, Instant.ofEpochMilli(session.startedAt).toString(), session.mode.name,
            session.activeMillis.toString(), session.measuredMillis.toString(), locations.firstVisitMillis.toString(), locations.revisitMillis.toString(),
            locations.unknownMillis.toString(), (session.startLocation + 1).toString(), (session.endLocation + 1).toString(),
            session.manual.toString(), session.excluded.toString(), session.closedReason.orEmpty(), session.revision)
        output.write(row.joinToString(",", transform = ::cell) + "\r\n")
    }
}

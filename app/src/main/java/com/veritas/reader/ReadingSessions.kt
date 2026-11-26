package com.veritas.reader

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

enum class ReadingSessionMode { READING, LISTENING }

data class ReadingSessionSpan(val startedAt: Long, val durationMillis: Long, val zone: String, val location: Int) {
    fun toJson() = JSONObject().put("startedAt", startedAt).put("durationMillis", durationMillis)
        .put("zone", zone).put("location", location)
}

data class ReadingSession(
    val id: String, val documentId: String, val title: String, val revision: String,
    val mode: ReadingSessionMode, val startedAt: Long, val checkpointAt: Long,
    val startLocation: Int, val endLocation: Int, val spans: List<ReadingSessionSpan> = emptyList(),
    val closedReason: String? = null, val manual: Boolean = false,
    val correctedMillis: Long? = null, val excluded: Boolean = false, val modifiedAt: Long = checkpointAt,
    val startPage: Int? = null, val endPage: Int? = null,
    val manualDate: LocalDate? = null
) {
    val measuredMillis: Long get() = spans.sumOf { it.durationMillis }
    val activeMillis: Long get() = if (excluded) 0 else correctedMillis ?: measuredMillis
    fun toJson() = JSONObject().put("version", 1).put("id", id).put("documentId", documentId)
        .put("title", title).put("revision", revision).put("mode", mode.name).put("startedAt", startedAt)
        .put("checkpointAt", checkpointAt).put("startLocation", startLocation).put("endLocation", endLocation)
        .put("spans", JSONArray().apply { spans.forEach { put(it.toJson()) } })
        .put("closedReason", closedReason ?: JSONObject.NULL).put("manual", manual)
        .put("correctedMillis", correctedMillis ?: JSONObject.NULL).put("excluded", excluded).put("modifiedAt", modifiedAt)
        .put("startPage", startPage ?: JSONObject.NULL).put("endPage", endPage ?: JSONObject.NULL)
        .apply { manualDate?.let { put("manualDate", it.toString()) } }
    companion object {
        fun fromJson(obj: JSONObject): ReadingSession? = runCatching {
            require(obj.optInt("version", 1) == 1)
            val id = obj.getString("id"); val doc = obj.getString("documentId")
            require(id.isNotBlank() && doc.isNotBlank())
            val spans = obj.optJSONArray("spans") ?: JSONArray()
            val parts = (0 until spans.length()).mapNotNull { i ->
                val part = spans.optJSONObject(i) ?: return@mapNotNull null
                val duration = part.optLong("durationMillis").coerceAtLeast(0)
                require(duration <= 86_400_000L)
                val zone = part.optString("zone", "UTC"); ZoneId.of(zone)
                ReadingSessionSpan(part.getLong("startedAt"), duration, zone, part.optInt("location").coerceAtLeast(0))
            }
            val start = obj.getLong("startedAt")
            val manual = obj.optBoolean("manual")
            val manualDate = obj.optString("manualDate").takeIf { manual && it.isNotBlank() }
                ?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() }
            ReadingSession(id, doc, obj.optString("title", "Reading"), obj.optString("revision"),
                ReadingSessionMode.valueOf(obj.getString("mode")), start, obj.optLong("checkpointAt", start),
                obj.optInt("startLocation").coerceAtLeast(0), obj.optInt("endLocation").coerceAtLeast(0), parts,
                if (obj.isNull("closedReason")) null else obj.optString("closedReason").takeIf { it.isNotBlank() },
                manual, if (obj.isNull("correctedMillis")) null else obj.optLong("correctedMillis").coerceIn(0, 86_400_000),
                obj.optBoolean("excluded"), obj.optLong("modifiedAt", start),
                obj.optInt("startPage").takeIf { it > 0 }, obj.optInt("endPage").takeIf { it > 0 }, manualDate)
        }.getOrNull()
    }
}

data class ReadingSessionTarget(val documentId: String, val title: String, val revision: String,
    val mode: ReadingSessionMode, val location: Int, val page: Int? = null)

/** Monotonic durations; wall time is only used to label dates. No time accrues while suspended. */
internal class ReadingSessionClock(private val newId: () -> String = { UUID.randomUUID().toString() }) {
    var current: ReadingSession? = null; private set
    private val pendingWrites = linkedMapOf<String, ReadingSession>()
    private var running = false
    private var lastMono = 0L
    private var lastWall = 0L
    private var lastZone = "UTC"

    fun checkpoint(wall: Long, mono: Long, zone: String, capMono: Long = mono) {
        val session = current
        val delta = (minOf(mono, capMono) - lastMono).coerceAtLeast(0)
        if (session != null && running && delta > 0) {
            val part = ReadingSessionSpan(lastWall, delta, lastZone, session.endLocation)
            val tail = session.spans.lastOrNull()
            val spans = if (tail != null && tail.zone == part.zone && tail.location == part.location &&
                tail.startedAt + tail.durationMillis == part.startedAt && tail.durationMillis + delta <= 86_400_000) {
                session.spans.dropLast(1) + tail.copy(durationMillis = tail.durationMillis + delta)
            } else session.spans + part
            update(session.copy(spans = spans, checkpointAt = wall, modifiedAt = wall))
        }
        lastMono = mono; lastWall = wall; lastZone = zone
    }

    fun transition(target: ReadingSessionTarget?, active: Boolean, wall: Long, mono: Long, zone: String,
        reason: String = "changed", capMono: Long = mono) {
        checkpoint(wall, mono, zone, capMono)
        val previous = current
        if (previous != null && target != null && previous.documentId == target.documentId &&
            previous.revision == target.revision && previous.mode == target.mode
        ) {
            val page = target.page ?: previous.endPage
            if (target.location != previous.endLocation || page != previous.endPage) {
                update(previous.copy(endLocation = target.location, endPage = page, checkpointAt = wall, modifiedAt = wall))
            }
        } else {
            if (previous != null) {
                val closed = previous.copy(checkpointAt = wall, closedReason = reason, modifiedAt = wall)
                pendingWrites[closed.id] = closed
            }
            current = null
            if (target != null) {
                update(ReadingSession(newId(), target.documentId, target.title, target.revision,
                    target.mode, wall, wall, target.location, target.location, startPage = target.page, endPage = target.page))
            }
        }
        running = target != null && active
    }

    fun close(reason: String, wall: Long, mono: Long, zone: String) =
        transition(null, false, wall, mono, zone, reason)

    private fun update(session: ReadingSession) {
        current = session
        pendingWrites[session.id] = session
    }

    /** The timer advances independently of disk I/O. A failed write stays queued for retry. */
    fun flush(save: (ReadingSession) -> Unit) {
        val pending = pendingWrites.values.iterator()
        while (pending.hasNext()) {
            save(pending.next())
            pending.remove()
        }
    }
}

/** Date-only entries belong to the chosen day. Timed spans split at their recorded local midnight. */
internal fun ReadingSession.millisByDate(): Map<String, Long> {
    if (excluded) return emptyMap()
    manualDate?.let { return mapOf(it.toString() to activeMillis) }
    val totals = linkedMapOf<String, Long>()
    spans.forEach { span ->
        totals.addDurationByDate(span.startedAt, span.durationMillis, ZoneId.of(span.zone))
    }
    if (correctedMillis != null && measuredMillis > 0) {
        var allocated = 0L
        totals.keys.toList().forEachIndexed { index, date ->
            val value = if (index == totals.size - 1) correctedMillis - allocated
                else (totals.getValue(date).toDouble() * correctedMillis / measuredMillis).toLong()
            totals[date] = value; allocated += value
        }
    }
    return totals
}

internal fun MutableMap<String, Long>.addDurationByDate(startedAt: Long, durationMillis: Long, zone: ZoneId) {
    var cursor = startedAt
    val end = cursor + durationMillis
    while (cursor < end) {
        val date = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
        val boundary = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val stop = minOf(end, boundary)
        val key = date.toString()
        this[key] = (this[key] ?: 0L) + (stop - cursor)
        cursor = stop
    }
}

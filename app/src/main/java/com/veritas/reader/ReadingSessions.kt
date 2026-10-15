package com.veritas.reader

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
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
    val startPage: Int? = null, val endPage: Int? = null
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
            ReadingSession(id, doc, obj.optString("title", "Reading"), obj.optString("revision"),
                ReadingSessionMode.valueOf(obj.getString("mode")), start, obj.optLong("checkpointAt", start),
                obj.optInt("startLocation").coerceAtLeast(0), obj.optInt("endLocation").coerceAtLeast(0), parts,
                if (obj.isNull("closedReason")) null else obj.optString("closedReason").takeIf { it.isNotBlank() },
                obj.optBoolean("manual"), if (obj.isNull("correctedMillis")) null else obj.optLong("correctedMillis").coerceIn(0, 86_400_000),
                obj.optBoolean("excluded"), obj.optLong("modifiedAt", start),
                obj.optInt("startPage").takeIf { it > 0 }, obj.optInt("endPage").takeIf { it > 0 })
        }.getOrNull()
    }
}

data class ReadingSessionTarget(val documentId: String, val title: String, val revision: String,
    val mode: ReadingSessionMode, val location: Int, val page: Int? = null)

/** Monotonic durations; wall time is only used to label dates. No time accrues while suspended. */
internal class ReadingSessionClock(private val save: (ReadingSession) -> Unit, private val newId: () -> String = { UUID.randomUUID().toString() }) {
    var current: ReadingSession? = null; private set
    private var running = false
    private var lastMono = 0L
    private var lastWall = 0L
    private var lastZone = "UTC"
    private var location = 0

    fun checkpoint(wall: Long, mono: Long, zone: String, capMono: Long = mono) {
        val session = current
        if (session != null) {
            val delta = (minOf(mono, capMono) - lastMono).coerceAtLeast(0)
            var spans = session.spans
            if (running && delta > 0) {
                val part = ReadingSessionSpan(lastWall, delta, lastZone, location)
                val tail = spans.lastOrNull()
                spans = if (tail != null && tail.zone == part.zone && tail.location == part.location &&
                    tail.startedAt + tail.durationMillis == part.startedAt && tail.durationMillis + delta <= 86_400_000) {
                    spans.dropLast(1) + tail.copy(durationMillis = tail.durationMillis + delta)
                } else spans + part
            }
            current = session.copy(spans = spans, checkpointAt = wall, endLocation = location, modifiedAt = wall)
            save(current!!)
        }
        lastMono = mono; lastWall = wall; lastZone = zone
    }

    fun transition(target: ReadingSessionTarget?, active: Boolean, wall: Long, mono: Long, zone: String,
        reason: String = "changed", capMono: Long = mono) {
        checkpoint(wall, mono, zone, capMono)
        val previous = current
        val same = previous != null && target != null && previous.documentId == target.documentId &&
            previous.revision == target.revision && previous.mode == target.mode
        if (!same) {
            close(reason, wall, mono, zone)
            if (target != null) current = ReadingSession(newId(), target.documentId, target.title, target.revision,
                target.mode, wall, wall, target.location, target.location, startPage = target.page, endPage = target.page).also(save)
        }
        location = target?.location ?: location
        current = current?.copy(endLocation = location, endPage = target?.page ?: current?.endPage)
        running = target != null && active
        lastMono = mono; lastWall = wall; lastZone = zone
    }

    fun close(reason: String, wall: Long, mono: Long, zone: String) {
        checkpoint(wall, mono, zone)
        current?.copy(closedReason = reason, modifiedAt = wall)?.let(save)
        current = null; running = false
    }
}

/** Split each active span at midnight in the timezone recorded at that time, including DST. */
internal fun ReadingSession.millisByDate(): Map<String, Long> {
    if (excluded) return emptyMap()
    val totals = linkedMapOf<String, Long>()
    spans.forEach { span ->
        var cursor = span.startedAt
        val end = cursor + span.durationMillis
        val zone = ZoneId.of(span.zone)
        while (cursor < end) {
            val local = Instant.ofEpochMilli(cursor).atZone(zone)
            val boundary = local.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val stop = minOf(end, boundary)
            val date = local.toLocalDate().toString()
            totals[date] = (totals[date] ?: 0) + stop - cursor
            cursor = stop
        }
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

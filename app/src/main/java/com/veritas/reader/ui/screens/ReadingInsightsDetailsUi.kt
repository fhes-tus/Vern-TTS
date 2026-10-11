package com.veritas.reader.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veritas.reader.*
import com.veritas.reader.ui.LocalVeritasMotion
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.sin

/** Fixed card bounds; only artwork and measured progress animate. */
@Composable
internal fun InsightsDetailCard(title: String? = null, icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(Modifier.fillMaxWidth(), shape = VeritasPackStyle.cardShape(), border = VeritasPackStyle.cardBorder(scheme),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow)) {
        Column(Modifier.background(Brush.linearGradient(listOf(scheme.primaryContainer.copy(alpha = .22f),
            scheme.surfaceContainerLow, scheme.tertiaryContainer.copy(alpha = .12f)))).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (icon != null) InsightsDetailEmblem(icon, "insights-detail-$title")
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            content()
        }
    }
}

@Composable
private fun InsightsDetailEmblem(icon: ImageVector, tag: String) {
    val phase = rememberInsightsPhase(tag)
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.size(36.dp).testTag(tag), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val pulse = (sin(phase.value * 2f * PI.toFloat()) + 1f) / 2f
            drawCircle(scheme.primary.copy(alpha = .09f + pulse * .05f))
            drawArc(scheme.tertiary.copy(alpha = .4f), phase.value * 360f, 65f, false,
                Offset(2.dp.toPx(), 2.dp.toPx()), Size(size.width - 4.dp.toPx(), size.height - 4.dp.toPx()),
                style = Stroke(1.dp.toPx(), cap = StrokeCap.Round))
        }
        Icon(icon, null, Modifier.size(21.dp), tint = scheme.primary)
    }
}

@Composable
private fun rememberInsightsReveal(key: Any): State<Float> {
    val reduce = LocalVeritasMotion.current.reduceMotion
    val reveal = remember(key) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(key, reduce) {
        if (reduce) reveal.snapTo(1f)
        else reveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    return reveal.asState()
}

@Composable
internal fun InsightsBookCard(document: SavedDocument, sessions: List<ReadingSession>, finishedAt: Long?,
    onContinue: () -> Unit, onTimer: () -> Unit, onFinish: () -> Unit,
    locationSummary: com.veritas.reader.ReadingLocationSummary? = null) {
    val scheme = MaterialTheme.colorScheme
    val phase = rememberInsightsPhase("book-${document.id}")
    val reveal = rememberInsightsReveal(document.id)
    val percent = if (document.chunkCount > 0) ((document.currentIndex + 1) * 100L / document.chunkCount).coerceIn(0, 100).toInt() else 0
    val read = sessions.filter { it.mode == ReadingSessionMode.READING }.sumOf { it.activeMillis }
    val listen = sessions.filter { it.mode == ReadingSessionMode.LISTENING }.sumOf { it.activeMillis }
    InsightsDetailCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(76.dp, 110.dp), contentAlignment = Alignment.Center) {
                DocumentArtwork(document, Modifier.size(68.dp, 100.dp).testTag("insights-book-cover-${document.id}")
                    .semantics { contentDescription = "Cover of ${document.title}" }.graphicsLayer {
                        val wave = sin(phase.value * 2f * PI.toFloat())
                        translationY = wave * -1.5.dp.toPx()
                        rotationZ = wave * .65f
                        alpha = .6f + reveal.value * .4f
                    }, compact = true)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(document.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (finishedAt != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp), tint = scheme.primary)
                    Text("Finished ${insightsDate(finishedAt)}", style = MaterialTheme.typography.labelMedium, color = scheme.primary)
                }
                Text("$percent% · location ${document.currentIndex + 1} of ${document.chunkCount}",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                Canvas(Modifier.fillMaxWidth().height(7.dp).semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(percent / 100f, 0f..1f)
                    contentDescription = "Book position $percent percent"
                }) {
                    val stroke = size.height
                    drawLine(scheme.surfaceContainerHighest, Offset(0f, stroke / 2), Offset(size.width, stroke / 2), stroke, StrokeCap.Round)
                    val width = size.width * percent / 100f * reveal.value
                    if (width > 0) drawLine(Brush.horizontalGradient(listOf(scheme.primary, scheme.tertiary)),
                        Offset(0f, stroke / 2), Offset(width, stroke / 2), stroke, StrokeCap.Round)
                }
            }
        }
        InsightsModeBars(read, listen, "book-${document.id}")
        locationSummary?.let { InsightsLocationBars(it) }
        Text("${sessions.size} " + (if (sessions.size == 1) "session" else "sessions") + (sessions.maxByOrNull { it.startedAt }?.let { " · last ${insightsDate(it.startedAt)}" } ?: ""),
            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        sessions.maxByOrNull { it.startedAt }?.endPage?.let {
            Text("Last recorded source page $it", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onContinue) { Text("Continue") }
            TextButton(onClick = onTimer) { Text("Start timer") }
            TextButton(onClick = onFinish) { Text("Mark finished") }
        }
    }
}

/** Reading/listening lengths share a scale. Empty activity draws no coloured segment. */
@Composable
internal fun InsightsModeBars(reading: Long, listening: Long, key: String) {
    val scheme = MaterialTheme.colorScheme
    val reveal = rememberInsightsReveal(key)
    val phase = rememberInsightsPhase("mode-$key")
    val maximum = maxOf(reading, listening, 1L).toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(Triple("Reading", reading, scheme.primary), Triple("Listening", listening, scheme.tertiary)).forEach { (label, duration, tint) ->
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    Text(formatTrackerDuration(duration), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
                Canvas(Modifier.fillMaxWidth().height(9.dp).testTag("insights-mode-$key-$label").semantics {
                    contentDescription = "$label: ${formatTrackerDuration(duration)}"
                }) {
                    val y = size.height / 2f
                    drawLine(scheme.surfaceContainerHighest, Offset(0f, y), Offset(size.width, y), size.height, StrokeCap.Round)
                    val width = size.width * (duration / maximum) * reveal.value
                    if (width > 0f) {
                        drawLine(Brush.horizontalGradient(listOf(tint, tint.copy(alpha = .6f))), Offset(0f, y), Offset(width, y), size.height, StrokeCap.Round)
                        val x = width * (.10f + phase.value * .8f)
                        drawLine(tint.copy(alpha = .35f), Offset(x, y), Offset((x + 12.dp.toPx()).coerceAtMost(width), y),
                            size.height * .3f, StrokeCap.Round)
                    }
                }
            }
        }
    }
}

@Composable
internal fun InsightsGoalRing(totalMillis: Long, goalMinutes: Int) {
    val scheme = MaterialTheme.colorScheme
    val reveal = rememberInsightsReveal("daily-goal")
    val phase = rememberInsightsPhase("daily-goal")
    val progress = if (goalMinutes > 0) (totalMillis / (goalMinutes * 60_000f)).coerceIn(0f, 1f) else 0f
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(88.dp).testTag("insights-goal-ring").semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
            contentDescription = if (goalMinutes > 0) "Daily goal ${(progress * 100).toInt()} percent" else "Daily goal off"
        }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(6.dp)) {
                val stroke = Stroke(7.dp.toPx(), cap = StrokeCap.Round)
                drawArc(scheme.surfaceContainerHighest, -90f, 360f, false, style = stroke)
                if (progress > 0) {
                    drawArc(Brush.sweepGradient(listOf(scheme.primary, scheme.tertiary, scheme.primary)), -90f,
                        360f * progress * reveal.value, false, style = stroke)
                    // A small glint travels inside the earned arc; its length always reflects the recorded total.
                    val arc = 360f * progress * reveal.value
                    drawArc(scheme.onPrimaryContainer.copy(alpha = .24f), -90f + phase.value * (arc - 8f).coerceAtLeast(0f),
                        minOf(8f, arc), false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                }
            }
            Icon(if (progress >= 1f) Icons.Default.Check else Icons.Default.Flag, null, Modifier.size(26.dp), tint = scheme.primary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Today total ${formatTrackerDuration(totalMillis)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(if (goalMinutes > 0) "${formatTrackerDuration(totalMillis)} of ${goalMinutes}m" else "Choose your daily target",
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            if (progress >= 1f && goalMinutes > 0) Text("Goal reached", style = MaterialTheme.typography.labelMedium, color = scheme.primary)
        }
    }
}

@Composable
internal fun InsightsSessionCard(session: ReadingSession, document: SavedDocument?, onEdit: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val reading = session.mode == ReadingSessionMode.READING
    val tint = if (reading) scheme.primary else scheme.tertiary
    val mode = if (reading) "Reading" else "Listening"
    InsightsDetailCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (document != null) DocumentArtwork(document, Modifier.size(44.dp, 62.dp)
                .semantics { contentDescription = "Cover of ${document.title}" }, compact = true)
            else Box(Modifier.size(44.dp, 62.dp).background(tint.copy(alpha = .12f), VeritasPackStyle.chipShape()), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = tint)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(session.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("$mode · ${formatTrackerDuration(session.activeMillis)}", style = MaterialTheme.typography.bodyMedium, color = tint)
                Text(insightsTime(session.startedAt), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
            InsightsDetailEmblem(if (reading) Icons.AutoMirrored.Filled.MenuBook else Icons.Default.Headphones, "insights-session-${session.id}")
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Route, null, Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
            Text(if (session.startPage != null && session.endPage != null) "Source pages ${session.startPage} → ${session.endPage}"
                else "Locations ${session.startLocation + 1} → ${session.endLocation + 1}", style = MaterialTheme.typography.bodySmall)
        }
        if (session.closedReason == null || session.manual || session.excluded || session.correctedMillis != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (session.closedReason == null) SessionStatus("In progress", tint)
                if (session.manual) SessionStatus("Manual entry", scheme.onSurfaceVariant)
                if (session.excluded) SessionStatus("Excluded from totals", scheme.error)
                if (session.correctedMillis != null) SessionStatus("Corrected · original ${formatTrackerDuration(session.measuredMillis)}", scheme.onSurfaceVariant)
            }
        }
        TextButton(onClick = onEdit) { Text("Correct session") }
    }
}

@Composable
private fun SessionStatus(text: String, color: Color) {
    Surface(color = color.copy(alpha = .08f), shape = CircleShape) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

internal fun insightsDate(time: Long): String = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy"))
private fun insightsTime(time: Long): String = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a"))

@Composable
internal fun InsightsLocationBars(summary: com.veritas.reader.ReadingLocationSummary) {
    val scheme = MaterialTheme.colorScheme
    val total = summary.firstVisitMillis + summary.revisitMillis + summary.unknownMillis
    if (total <= 0) return
    val phase = rememberInsightsPhase("location-visits")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Time at reading locations", style = MaterialTheme.typography.labelLarge)
        Canvas(Modifier.fillMaxWidth().height(9.dp).semantics {
            contentDescription = "First visits ${formatTrackerDuration(summary.firstVisitMillis)}, revisits ${formatTrackerDuration(summary.revisitMillis)}, unknown ${formatTrackerDuration(summary.unknownMillis)}"
        }) {
            val values = listOf(summary.firstVisitMillis, summary.revisitMillis, summary.unknownMillis)
            val colors = listOf(scheme.primary, scheme.tertiary, scheme.outlineVariant)
            var x = 0f
            values.forEachIndexed { i, value ->
                val width = size.width * value.toFloat() / total
                if (width > 0) drawRoundRect(colors[i], Offset(x, 0f), Size(width, size.height), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                x += width
            }
            val dot = size.width * (.1f + phase.value * .8f)
            drawCircle(scheme.onPrimary.copy(alpha = .2f), 1.dp.toPx(), Offset(dot, size.height / 2))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("First visits ${formatTrackerDuration(summary.firstVisitMillis)}", style = MaterialTheme.typography.labelSmall, color = scheme.primary)
            Text("Revisits ${formatTrackerDuration(summary.revisitMillis)}", style = MaterialTheme.typography.labelSmall, color = scheme.tertiary)
            if (summary.unknownMillis > 0) Text("Unknown ${formatTrackerDuration(summary.unknownMillis)}", style = MaterialTheme.typography.labelSmall)
        }
    }
}

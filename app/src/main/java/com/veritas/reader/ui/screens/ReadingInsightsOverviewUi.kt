package com.veritas.reader.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.reader.ReaderTrackerSnapshot
import com.veritas.reader.WeekBars
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.ui.VeritasMotion
import com.veritas.reader.ui.LocalVeritasMotion
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.sin
import kotlin.math.PI

@Composable
internal fun InsightsStreaksCard(snapshot: ReaderTrackerSnapshot, weekKeys: List<String>) {
    val scheme = MaterialTheme.colorScheme
    val phase = rememberInsightsPhase("streakCardGraphic")
    val current by animateIntAsState(snapshot.currentStreak, VeritasMotion.effectsSlow(), label = "insightsStreak")
    val best by animateIntAsState(snapshot.longestStreak, VeritasMotion.effectsSlow(), label = "insightsBest")
    Card(Modifier.fillMaxWidth(), shape = VeritasPackStyle.cardShape(),
        colors = CardDefaults.cardColors(containerColor = scheme.primaryContainer.copy(alpha = .42f)), border = VeritasPackStyle.cardBorder(scheme)) {
        Column(Modifier.background(Brush.linearGradient(listOf(scheme.primaryContainer.copy(alpha = .65f), scheme.tertiaryContainer.copy(alpha = .28f))))
            .insightsCardGraphic(scheme.primary, scheme.tertiary, phase)
            .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Streaks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InsightsStreakEmblem(trophy = false)
                Column(Modifier.weight(1f)) {
                    Text("$current", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("Current streak", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    InsightsStreakEmblem(trophy = true)
                    Text("$best days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Best", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                weekKeys.forEach { key ->
                    val active = key in snapshot.activeDateKeys
                    val date = LocalDate.parse(key)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.semantics { contentDescription = "$key: " + if (active) "reading day" else "no reading recorded" }) {
                        Surface(shape = CircleShape, color = if (active) scheme.primary else scheme.surfaceContainerHigh, modifier = Modifier.size(30.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                if (active) Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = scheme.onPrimary)
                                else Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                            }
                        }
                        Text(date.dayOfWeek.name.take(1), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// Animate ink within fixed bounds so the labels and cards never move.
@Composable
internal fun rememberInsightsPhase(label: String): State<Float> {
    if (LocalVeritasMotion.current.reduceMotion) return remember { mutableFloatStateOf(0f) }
    val transition = rememberInfiniteTransition(label = label)
    val phase = transition.animateFloat(0f, 1f,
        infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart), label = "${label}Phase")
    return phase
}

@Composable
private fun InsightsStreakEmblem(trophy: Boolean) {
    val phase = rememberInsightsPhase(if (trophy) "insightsTrophy" else "insightsFire")
    val reduceMotion = LocalVeritasMotion.current.reduceMotion
    val scheme = MaterialTheme.colorScheme
    val gold = if (scheme.surface.luminance() > .5f) Color(0xFFB47A12) else Color(0xFFFFD166)
    Box(Modifier.size(if (trophy) 36.dp else 52.dp).testTag(if (trophy) "insights-trophy" else "insights-fire"),
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val wave = if (reduceMotion) 0f else sin(phase.value * 2f * PI.toFloat())
            if (!trophy) {
                val w = size.width; val h = size.height
                val ember = Color(0xFFFF8B45)
                drawCircle(Brush.radialGradient(listOf(ember.copy(alpha = .20f + (wave + 1f) * .025f), Color.Transparent)),
                    radius = w * .46f, center = Offset(w * .5f, h * .53f))
                val flame = Path().apply {
                    moveTo(w * (.56f + wave * .025f), h * .08f)
                    cubicTo(w * .52f, h * .34f, w * .87f, h * .42f, w * .81f, h * .65f)
                    cubicTo(w * .79f, h * .96f, w * .23f, h * .99f, w * .18f, h * .67f)
                    cubicTo(w * .13f, h * .46f, w * .30f, h * .40f, w * .32f, h * .26f)
                    cubicTo(w * .34f, h * .53f, w * .53f, h * .48f, w * (.56f + wave * .025f), h * .08f)
                    close()
                }
                drawPath(flame, Brush.verticalGradient(listOf(Color(0xFFFFCD64), Color(0xFFFF8B45), Color(0xFFF05B4F))))
                val core = Path().apply {
                    moveTo(w * .51f, h * (.44f + wave * .025f))
                    cubicTo(w * .49f, h * .63f, w * .69f, h * .65f, w * .62f, h * .80f)
                    cubicTo(w * .55f, h * .94f, w * .34f, h * .85f, w * .38f, h * .73f)
                    cubicTo(w * .40f, h * .61f, w * .49f, h * .58f, w * .51f, h * (.44f + wave * .025f))
                    close()
                }
                drawPath(core, Brush.verticalGradient(listOf(Color(0xFFFFF0B0), Color(0xFFFFC85C))))
                repeat(2) { i ->
                    val lift = if (reduceMotion) .5f else (phase.value + i * .5f) % 1f
                    drawCircle(ember.copy(alpha = (1f - lift) * .65f), radius = w * .019f,
                        center = Offset(w * (.26f + i * .49f + wave * .018f), h * (.30f - lift * .23f)))
                }
            } else {
                drawCircle(gold.copy(alpha = .10f), radius = size.minDimension * .44f)
                val sparkle = if (reduceMotion) .5f else (wave + 1f) / 2f
                val center = Offset(size.width * .88f, size.height * .16f)
                val arm = 2.5.dp.toPx() * (.6f + sparkle * .4f)
                val ink = gold.copy(alpha = .2f + sparkle * .65f)
                drawLine(ink, center.copy(x = center.x - arm), center.copy(x = center.x + arm), 1.dp.toPx(), StrokeCap.Round)
                drawLine(ink, center.copy(y = center.y - arm), center.copy(y = center.y + arm), 1.dp.toPx(), StrokeCap.Round)
            }
        }
        if (trophy) Icon(Icons.Default.EmojiEvents, null, Modifier.size(27.dp).graphicsLayer {
            val wave = if (reduceMotion) 0f else sin(phase.value * 2f * PI.toFloat())
            scaleX = 1f + wave * .035f; scaleY = scaleX
            rotationZ = wave * 5f; translationY = wave * -1.5.dp.toPx()
        }, tint = gold)
    }
}

@Composable
internal fun InsightsWeeklyAppCard(snapshot: ReaderTrackerSnapshot, weekStart: LocalDate) {
    val days = snapshot.weeklyUsageByDay.take(7).let { it + List((7 - it.size).coerceAtLeast(0)) { 0L } }
    val history = snapshot.weeklyHistory.ifEmpty { listOf(WeekBars("This week", days, days.sum(), true)) }
    var shown by remember { mutableStateOf(history.last()) }
    Card(Modifier.fillMaxWidth(), shape = VeritasPackStyle.cardShape(), border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .20f), MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .28f)))).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary)
                Text("Weekly App Activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text(formatTrackerDuration(shown.totalMillis), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("${formatTrackerDuration(shown.totalMillis / 7)} / day", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            WeeklyReadingBarsPager(history, barHeight = 82.dp, chartLabel = "Weekly app activity", accented = true, onWeekChanged = { shown = it })
        }
    }
}

@Composable
internal fun InsightsReadingRing(snapshot: ReaderTrackerSnapshot, weekKeys: List<String>) {
    val scheme = MaterialTheme.colorScheme
    val reading = weekKeys.sumOf { snapshot.measuredReadingMillisByDate[it] ?: 0 }
    val listening = weekKeys.sumOf { snapshot.listeningMillisByDate[it] ?: 0 }
    val legacy = weekKeys.sumOf { snapshot.legacyReadingMillisByDate[it] ?: 0 }
    val amounts = listOf(reading, listening, legacy)
    val colors = listOf(scheme.primary, lerp(scheme.tertiary, Color(0xFF24A89B), .65f), scheme.outline)
    val total = amounts.sum()
    val reduceMotion = LocalVeritasMotion.current.reduceMotion
    val reveal = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(reading, listening, legacy, reduceMotion) {
        if (reduceMotion) reveal.snapTo(1f)
        else { reveal.snapTo(0f); reveal.animateTo(1f, tween(1000, easing = FastOutSlowInEasing)) }
    }
    val phase = rememberInsightsPhase("insightsReadingRing")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(120.dp).testTag("insights-reading-ring").semantics { contentDescription = "This week: reading ${formatTrackerDuration(reading)}, listening ${formatTrackerDuration(listening)}" },
            contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(6.dp)) {
                val stroke = 10.dp.toPx()
                val diameter = size.minDimension - stroke
                val origin = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
                drawCircle(scheme.primary.copy(alpha = .09f), radius = size.minDimension / 2f - 1.dp.toPx(), style = Stroke(1.dp.toPx()))
                drawCircle(scheme.surfaceContainerLowest, radius = size.minDimension * .34f)
                drawArc(scheme.outlineVariant.copy(alpha = .35f), -90f, 360f, false, origin, Size(diameter, diameter), style = Stroke(stroke))
                var angle = -90f + if (reduceMotion) 0f else sin(phase.value * 2f * PI.toFloat()) * 3.5f
                amounts.forEachIndexed { index, value ->
                    if (value > 0 && total > 0) {
                        val sweep = value.toFloat() / total * 360f
                        val shownSweep = (sweep * reveal.value - 3f).coerceAtLeast(0f)
                        drawArc(colors[index].copy(alpha = .12f), angle, shownSweep, false, origin, Size(diameter, diameter), style = Stroke(stroke + 5.dp.toPx(), cap = StrokeCap.Round))
                        drawArc(Brush.sweepGradient(listOf(colors[index], lerp(colors[index], Color.White, .25f), colors[index]), center), angle, shownSweep, false, origin, Size(diameter, diameter), style = Stroke(stroke, cap = StrokeCap.Round))
                        angle += sweep
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatTrackerDuration(total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("This week", fontSize = 10.sp, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Reading", "Listening", "Earlier").forEachIndexed { index, label ->
                if (index < 2 || legacy > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(7.dp).background(colors[index], CircleShape))
                    Column {
                        Text(formatTrackerDuration(amounts[index]), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(label, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Shared Streaks/Home motif: layered corner discs, fine orbit lines and slow ink movement. */
internal fun Modifier.insightsCardGraphic(primary: Color, accent: Color, phase: State<Float>): Modifier = drawBehind {
    val wave = sin(phase.value * 2f * PI.toFloat())
    val corner = Offset(size.width * (.96f + wave * .008f), size.height * (.12f + wave * .015f))
    drawCircle(accent.copy(alpha = .055f), size.width * .30f, corner)
    drawCircle(primary.copy(alpha = .07f), size.width * .22f, corner, style = Stroke(1.dp.toPx()))
    drawCircle(primary.copy(alpha = .05f), size.width * .26f, corner, style = Stroke(1.dp.toPx()))
    val radius = size.width * .26f
    val angle = (-140f + wave * 5f) * PI.toFloat() / 180f
    val dot = Offset(corner.x + kotlin.math.cos(angle) * radius, corner.y + sin(angle) * radius)
    drawCircle(primary.copy(alpha = .13f), 2.dp.toPx(), dot)
}

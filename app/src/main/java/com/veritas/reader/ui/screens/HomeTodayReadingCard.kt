package com.veritas.reader.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.vernScatteredCard
import com.veritas.reader.ui.LocalVeritasMotion
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.math.ceil

/** Uses recorded reading duration, never app-open duration or invented listening totals. */
@Composable
internal fun HomeTodayReadingCard(
    todayMillis: Long,
    goalMinutes: Int,
    readingDaysThisWeek: Int,
    onOpenInsights: () -> Unit,
    onGoalMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    weeklyMillis: Long = 0L,
    weekReadingMillis: List<Long> = emptyList()
) {
    val scheme = MaterialTheme.colorScheme
    val goalMillis = goalMinutes.toLong() * 60_000L
    val hasGoal = goalMillis > 0L
    val reachedGoal = hasGoal && todayMillis >= goalMillis
    val fraction by animateFloatAsState(
        if (hasGoal) (todayMillis.toFloat() / goalMillis).coerceIn(0f, 1f) else 0f,
        animationSpec = com.veritas.reader.ui.VeritasMotion.spatial(), label = "homeGoalProgress"
    )
    val timeLabel = if (todayMillis in 1..59_999L) "<1" else "${todayMillis / 60_000L}"
    val remaining = ceil((goalMillis - todayMillis).coerceAtLeast(0L) / 60_000.0).toInt()
    val days = readingDaysThisWeek.coerceIn(0, 7)
    val stats = listOf(
        ReadingCardStat(if (hasGoal) "Today’s reading" else "Your reading today",
            "$timeLabel min",
            when {
                reachedGoal -> "Daily goal reached"
                hasGoal -> "$remaining min to your goal"
                days > 0 -> "$days reading ${if (days == 1) "day" else "days"} this week"
                else -> "Recorded reading time"
            }),
        ReadingCardStat("This week", "$days ${if (days == 1) "day" else "days"}", "With recorded reading")
    ) + listOf(ReadingCardStat("Daily goal", if (!hasGoal) "No goal" else if (reachedGoal) "Done" else "$remaining min",
        if (!hasGoal) "Read at your own pace" else if (reachedGoal) "Today’s target complete" else "To your target"),
        ReadingCardStat("Weekly reading", "${weeklyMillis.coerceAtLeast(todayMillis) / 60_000L} min", "Total since Monday"))
    val reduceMotion = LocalVeritasMotion.current.reduceMotion
    val pageVisible = LocalLibraryPageVisible.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var statIndex by remember(hasGoal) { mutableStateOf(0) }
    LaunchedEffect(lifecycle, reduceMotion, hasGoal, pageVisible) {
        statIndex = 0
        if (!reduceMotion && pageVisible) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(6_000)
                statIndex = (statIndex + 1) % stats.size
            }
        }
    }
    var showGoalPicker by remember { mutableStateOf(false) }
    Card(modifier.fillMaxWidth().vernScatteredCard("home-today-reading", amount = .5f).testTag("home-today-reading-card").pointerInput(stats.size) {
                    var drag = 0f
                    detectHorizontalDragGestures(onDragStart = { drag = 0f }, onDragCancel = { drag = 0f },
                        onDragEnd = {
                            if (kotlin.math.abs(drag) > 40.dp.toPx()) statIndex = (statIndex + if (drag < 0f) 1 else stats.size - 1) % stats.size
                        }, onHorizontalDrag = { change, distance -> change.consume(); drag += distance })}, shape = VeritasPackStyle.cardShape(),
        colors = CardDefaults.cardColors(containerColor = VeritasPackStyle.panelColor(scheme)),
        border = VeritasPackStyle.cardBorder(scheme)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 0.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(Modifier.fillMaxWidth().clipToBounds().semantics {
                    customActions = listOf(
                        CustomAccessibilityAction("Next reading statistic") { statIndex = (statIndex + 1) % stats.size; true },
                        CustomAccessibilityAction("Previous reading statistic") { statIndex = (statIndex + stats.size - 1) % stats.size; true })
                }) {
                    // Reserve the tallest page at the user's font scale, including wrapped text.
                    // Rotating statistics must not move the books underneath this card.
                    stats.forEachIndexed { index, stat ->
                        ReadingStatPage(stat, index, stats.size, fraction, hasGoal, goalMinutes, weekReadingMillis,
                            Modifier.graphicsLayer { alpha = 0f }.clearAndSetSemantics {})
                    }
                    AnimatedContent(targetState = statIndex, modifier = Modifier.matchParentSize(),
                        transitionSpec = {
                            if (reduceMotion) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                            else (slideInHorizontally(tween(550)) { it } + fadeIn(tween(350)))
                                .togetherWith(slideOutHorizontally(tween(550)) { -it } + fadeOut(tween(300)))
                                .using(null)
                        }, label = "todayReadingStats") { index ->
                        val page = index.coerceIn(stats.indices)
                        ReadingStatPage(stats[page], page, stats.size, fraction, hasGoal, goalMinutes, weekReadingMillis)
                    }
                }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showGoalPicker = true }, modifier = Modifier.weight(1f)) {
                    Text(if (hasGoal) "Edit goal" else "Set a goal")
                }
                TextButton(onClick = onOpenInsights, modifier = Modifier.weight(1f)) { Text("View insights →") }
            }
        }
    }
    if (showGoalPicker) HomeReadingGoalDialog(goalMinutes, onGoalMinutesChange) { showGoalPicker = false }
}

private data class ReadingCardStat(val title: String, val value: String, val detail: String)

@Composable
private fun ReadingStatPage(stat: ReadingCardStat, page: Int, pages: Int, fraction: Float,
    hasGoal: Boolean, goalMinutes: Int, weekReadingMillis: List<Long>, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stat.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Canvas(Modifier.width((pages * 12).dp).heightIn(min = 8.dp).semantics { contentDescription = "Statistic ${page + 1} of $pages" }) {
                repeat(pages) { i -> drawLine(if (i == page) scheme.primary else scheme.onSurface.copy(alpha = .18f),
                    Offset(i * 12.dp.toPx() + 2.dp.toPx(), size.height / 2),
                    Offset(i * 12.dp.toPx() + (if (i == page) 8 else 4).dp.toPx(), size.height / 2), 3.dp.toPx(), StrokeCap.Round) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(.56f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stat.value, style = MaterialTheme.typography.titleLarge, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stat.detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Column(Modifier.weight(.44f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ReadingStatIllustration(page, fraction, weekReadingMillis)
                Text(when (page) { 1 -> "Days with reading"; 3 -> "Minutes by day"; else -> if (hasGoal) "$goalMinutes min target" else "No daily target" },
                    style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ReadingStatIllustration(page: Int, fraction: Float, week: List<Long>) {
    val scheme = MaterialTheme.colorScheme
    val phase = rememberInsightsPhase("home-stat-$page")
    val reduced = LocalVeritasMotion.current.reduceMotion
    val values = List(7) { week.getOrElse(it) { 0L }.coerceAtLeast(0) }
    val description = when (page) {
        0 -> "Daily reading progress ${(fraction * 100).toInt()} percent"
        1 -> "Reading days, Monday to Sunday: " + values.joinToString(", ") { if (it > 0) "read" else "no reading" }
        2 -> "Daily goal progress ${(fraction * 100).toInt()} percent"
        else -> "Reading minutes, Monday to Sunday: " + values.joinToString(", ") { "${it / 60_000L}" }
    }
    Canvas(Modifier.fillMaxWidth().requiredHeight(50.dp).testTag("reading-stat-graphic-$page").semantics { contentDescription = description }) {
        val ink = scheme.primary
        val quiet = scheme.primary.copy(alpha = .12f)
        val drift = if (reduced) 0f else kotlin.math.sin(phase.value * kotlin.math.PI.toFloat() * 2) * 1.5.dp.toPx()
        when (page) {
            0 -> {
                val left = size.width * .06f
                val width = size.width * .88f
                val top = size.height * .35f
                val height = size.height * .3f
                val radius = androidx.compose.ui.geometry.CornerRadius(height / 2)
                drawRoundRect(quiet, Offset(left, top), androidx.compose.ui.geometry.Size(width, height), radius)
                if (fraction > 0f) drawRoundRect(ink, Offset(left, top), androidx.compose.ui.geometry.Size(width * fraction.coerceIn(0f, 1f), height), radius)
                val x = left + width * fraction.coerceIn(0f, 1f)
                drawCircle(scheme.surface, height * .48f, Offset(x, top + height / 2))
                drawCircle(ink, height * .3f, Offset(x, top + height / 2))
                drawLine(ink.copy(alpha = .4f), Offset(left + width, top - 5.dp.toPx()), Offset(left + width, top + height + 5.dp.toPx()), 1.5.dp.toPx(), StrokeCap.Round)
            }
            1 -> {
                val step = size.width / 7; val width = step * .76f
                values.forEachIndexed { i, value ->
                    val x = step * i + (step - width) / 2; val y = size.height * .18f + if (value > 0) drift else 0f
                    drawRoundRect(if (value > 0) ink.copy(alpha = .85f) else quiet, Offset(x, y), androidx.compose.ui.geometry.Size(width, size.height * .62f), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
                    if (value > 0) {
                        drawLine(scheme.onPrimary, Offset(x + width * .2f, y + size.height * .31f), Offset(x + width * .43f, y + size.height * .4f), 1.5.dp.toPx(), StrokeCap.Round)
                        drawLine(scheme.onPrimary, Offset(x + width * .43f, y + size.height * .4f), Offset(x + width * .8f, y + size.height * .22f), 1.5.dp.toPx(), StrokeCap.Round)
                    }
                }
            }
            2 -> {
                val radius = size.height * .36f; val c = center.copy(y = center.y + drift)
                drawCircle(quiet, radius, c, style = Stroke(6.dp.toPx()))
                drawArc(ink, -90f, fraction.coerceIn(0f, 1f) * 360, false, c - Offset(radius, radius), androidx.compose.ui.geometry.Size(radius * 2, radius * 2), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
                drawLine(ink.copy(alpha = .65f), c, c + Offset(0f, -radius * .5f), 2.dp.toPx(), StrokeCap.Round)
                drawLine(ink.copy(alpha = .65f), c, c + Offset(radius * .35f, 0f), 2.dp.toPx(), StrokeCap.Round)
            }
            else -> {
                val peak = values.maxOrNull()?.coerceAtLeast(60_000L) ?: 60_000L
                val step = size.width / 7; val bottom = size.height - 4.dp.toPx()
                values.forEachIndexed { i, value ->
                    val x = step * (i + .5f)
                    drawCircle(quiet, 2.dp.toPx(), Offset(x, bottom))
                    if (value > 0) drawLine(ink.copy(alpha = .85f), Offset(x, bottom), Offset(x, bottom - (size.height - 8.dp.toPx()) * value.toFloat() / peak), 6.dp.toPx(), StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
internal fun HomeReadingGoalDialog(goalMinutes: Int, onGoalMinutesChange: (Int) -> Unit, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily reading goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Choose a target, or track your reading without one.", color = scheme.onSurfaceVariant)
                val options = (listOf(0, 15, 30, 45, 60) + goalMinutes.takeIf { it > 0 }.let { listOfNotNull(it) }).distinct().sorted()
                options.forEach { minutes ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable {
                        onGoalMinutesChange(minutes)
                        onDismiss()
                    }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (minutes == 0) "No goal" else "$minutes minutes a day", Modifier.weight(1f),
                            color = if (minutes == goalMinutes) scheme.primary else scheme.onSurface)
                        if (minutes == goalMinutes) Icon(Icons.Filled.Check, "Selected", tint = scheme.primary)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

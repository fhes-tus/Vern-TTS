package com.veritas.reader.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.veritas.reader.WeekBars
import com.veritas.reader.ui.pressScale
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Monday-based index (Mon=0 … Sun=6) for today, used to emphasise today's capsule.
internal fun mondayBasedTodayIndex(): Int {
    val cal = Calendar.getInstance()
    return (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
}

/**
 * Resolves a Long milliseconds value into a day-of-week label (Mon–Sun).
 * `weekStartMonday` is the epoch-ms of Monday 00:00 for the displayed week.
 */
internal fun dayLabel(dayIndex: Int, weekStartMonday: Long): String {
    val cal = Calendar.getInstance()
    cal.timeInMillis = weekStartMonday + dayIndex * 86_400_000L
    return SimpleDateFormat("EEE, d MMM", Locale.getDefault())
        .format(Date(cal.timeInMillis))
}

/**
 * Floating tooltip shown above the selected day capsule.
 * Uses an `AnimatedVisibility` (fade + slide) for smooth entrance/exit.
 */
@Composable
internal fun UsageTooltip(visible: Boolean, label: String, duration: String) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(com.veritas.reader.ui.VeritasMotion.effectsFast()) +
                slideInVertically(com.veritas.reader.ui.VeritasMotion.spatialFast()) { it / 2 },
        exit = fadeOut(com.veritas.reader.ui.VeritasMotion.effectsFast()) +
               slideOutVertically(com.veritas.reader.ui.VeritasMotion.spatialFast()) { it / 2 }
    ) {
        Surface(
            shape = com.veritas.reader.VeritasPackStyle.chipShape(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)),
            tonalElevation = 6.dp,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = duration,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
internal fun MiniWeekBars(
    values: List<Long>,
    height: androidx.compose.ui.unit.Dp = 64.dp,
    todayIndex: Int = -1,
    selectedIndex: Int = -1,
    onSelectDay: (Int) -> Unit = {},
    weekStartMonday: Long = 0L
) {
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
    val max = (values.maxOrNull()?.coerceAtLeast(1L) ?: 1L) * 1.25f
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val primary = MaterialTheme.colorScheme.primary
    val fillBrush = Brush.verticalGradient(
        listOf(lerp(primary, Color.White, 0.45f), primary)
    )
    val dimBrush = Brush.verticalGradient(
        listOf(lerp(primary, Color.White, 0.25f).copy(alpha = 0.7f), primary.copy(alpha = 0.7f))
    )
    val selectedBrush = Brush.verticalGradient(
        listOf(lerp(primary, Color.White, 0.6f), primary)
    )
    val areaAlpha = if (MaterialTheme.colorScheme.primary.luminance() > 0.5f) 0.12f else 0.18f
    Column(modifier = Modifier.fillMaxWidth()) {
        val fracs = values.map { v ->
            if (v > 0L) (v.toFloat() / max).coerceIn(0.14f, 1f) else 0f
        }
        val animatedFracs = fracs.mapIndexed { i, f ->
            animateFloatAsState(f, com.veritas.reader.ui.VeritasMotion.spatialSlow(), label = "areaFrac$i").value
        }
        Box(modifier = Modifier.fillMaxWidth().height(height)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (animatedFracs.size == 7 && size.width > 0f) {
                    val barWidth = size.width / 7f
                    val path = Path()
                    val points = animatedFracs.mapIndexed { i, frac ->
                        Offset(barWidth * i + barWidth / 2f, size.height * (1f - frac))
                    }
                    if (points.isNotEmpty()) {
                        path.moveTo(0f, size.height)
                        path.lineTo(0f, points.first().y)
                        path.lineTo(points.first().x, points.first().y)
                        for (k in 1 until points.size) {
                            val prev = points[k - 1]
                            val curr = points[k]
                            val cx = (prev.x + curr.x) / 2f
                            path.cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
                        }
                        path.lineTo(size.width, points.last().y)
                        path.lineTo(size.width, size.height)
                        path.close()
                        drawPath(
                            path = path,
                            brush = Brush.verticalGradient(
                                colors = listOf(primary.copy(alpha = areaAlpha), Color.Transparent),
                                startY = 0f,
                                endY = size.height
                            )
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                values.forEachIndexed { i, value ->
                    val isToday = i == todayIndex
                    val isSelected = i == selectedIndex
                    val targetFrac = if (value > 0L) (value.toFloat() / max).coerceIn(0.14f, 1f) else 0f
                    val frac by animateFloatAsState(
                        targetValue = targetFrac,
                        animationSpec = com.veritas.reader.ui.VeritasMotion.spatialSlow(),
                        label = "barFrac$i"
                    )
                    val dayDesc = if (weekStartMonday > 0L && value > 0L)
                        "${labels.getOrElse(i) { "" }}: ${formatTrackerDuration(value)}"
                    else labels.getOrElse(i) { "" }
                    val barInteraction = remember { MutableInteractionSource() }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = dayDesc },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(if (isSelected) 18.dp else 14.dp)
                                .height(height)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else trackColor
                                )
                                .pressScale(barInteraction)
                                .clickable(
                                    enabled = todayIndex == -1 || i <= todayIndex,
                                    indication = null,
                                    interactionSource = barInteraction
                                ) { onSelectDay(if (isSelected) -1 else i) },
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            if (frac > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(frac)
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            when {
                                                isSelected -> selectedBrush
                                                isToday -> fillBrush
                                                else -> dimBrush
                                            }
                                        )
                                )
                            }
                        }
                        Text(
                            labels.getOrElse(i) { "" },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.primary
                                isToday -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        if (isToday) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            )
                        } else {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Swipeable weekly chart: pages through `history` (oldest→newest), defaulting to the current
 * (last) week, with a small caption naming the week and its total. Falls back to a single
 * static week when no history is available. Tapping a bar shows a floating usage tooltip.
 */
@Composable
internal fun WeeklyReadingBarsPager(
    history: List<WeekBars>,
    modifier: Modifier = Modifier,
    barHeight: androidx.compose.ui.unit.Dp = 64.dp
) {
    if (history.isEmpty()) {
        MiniWeekBars(List(7) { 0L }, height = barHeight)
        return
    }
    val pagerState = rememberPagerState(initialPage = history.lastIndex) { history.size }
    val todayIndex = remember { mondayBasedTodayIndex() }
    var selectedBarIndex by remember { mutableIntStateOf(-1) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) { selectedBarIndex = -1 }

    val visibleWeek = history[pagerState.currentPage]
    val chartDescription = "Weekly reading chart. ${visibleWeek.label}: " +
        "${formatTrackerDuration(visibleWeek.totalMillis)} total. Swipe left or right to change week."

    val weekStartMonday = remember(pagerState.currentPage) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val weeksBack = history.lastIndex - pagerState.currentPage
        cal.add(Calendar.WEEK_OF_YEAR, -weeksBack)
        cal.timeInMillis
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = chartDescription }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val tooltipVisible = selectedBarIndex >= 0 && selectedBarIndex < visibleWeek.values.size
            val tooltipLabel = if (tooltipVisible && weekStartMonday > 0L)
                dayLabel(selectedBarIndex, weekStartMonday) else ""
            val tooltipDuration = if (tooltipVisible)
                formatTrackerDuration(visibleWeek.values.getOrElse(selectedBarIndex) { 0L }) else ""

            // Week Selector Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (pagerState.currentPage > 0) {
                                coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                            }
                        },
                        enabled = pagerState.currentPage > 0,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous week",
                            tint = if (pagerState.currentPage > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = if (visibleWeek.isCurrentWeek) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = visibleWeek.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (visibleWeek.isCurrentWeek) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            if (pagerState.currentPage < history.lastIndex) {
                                coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            }
                        },
                        enabled = pagerState.currentPage < history.lastIndex,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next week",
                            tint = if (pagerState.currentPage < history.lastIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = "Total: ${formatTrackerDuration(visibleWeek.totalMillis)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopStart
            ) {
                HorizontalPager(state = pagerState) { page ->
                    val week = history[page]
                    MiniWeekBars(
                        values = week.values,
                        height = barHeight,
                        todayIndex = if (week.isCurrentWeek) todayIndex else -1,
                        selectedIndex = if (page == pagerState.currentPage) selectedBarIndex else -1,
                        onSelectDay = { idx -> selectedBarIndex = idx },
                        weekStartMonday = weekStartMonday
                    )
                }
            }

            if (tooltipVisible) {
                Surface(
                    shape = com.veritas.reader.VeritasPackStyle.compactShape(),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "$tooltipLabel: $tooltipDuration",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "✕",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable { selectedBarIndex = -1 }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

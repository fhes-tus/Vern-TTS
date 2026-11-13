package com.veritas.reader.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.ui.LocalVeritasMotion
import com.veritas.reader.vernScatteredCard
import java.text.DateFormatSymbols
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

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
        animationSpec = com.veritas.reader.ui.VeritasMotion.spatial(),
        label = "homeGoalProgress"
    )
    val resources = LocalContext.current.resources
    val locale = LocalConfiguration.current.locales[0]
    val weekdayLabels = remember(locale) {
        val raw = DateFormatSymbols.getInstance(locale).shortWeekdays
        val calendarOrder = intArrayOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY,
            Calendar.SATURDAY,
            Calendar.SUNDAY
        )
        calendarOrder.map { dayConst ->
            raw.getOrNull(dayConst)?.trim()?.removeSuffix(".")?.takeIf { it.isNotEmpty() } ?: ""
        }
    }

    fun formatDurationMillis(millis: Long): String {
        val safeMillis = millis.coerceAtLeast(0L)
        return if (safeMillis in 1..59_999L) {
            resources.getString(com.veritas.reader.R.string.reading_less_than_minute)
        } else {
            val mins = (safeMillis / 60_000L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            resources.getQuantityString(com.veritas.reader.R.plurals.reading_minutes, mins, mins)
        }
    }

    val effectiveWeeklyMillis = weeklyMillis.coerceAtLeast(todayMillis).coerceAtLeast(0L)
    val todayTimeLabel = formatDurationMillis(todayMillis)
    val weeklyTimeLabel = formatDurationMillis(effectiveWeeklyMillis)
    val remaining = ceil((goalMillis - todayMillis).coerceAtLeast(0L) / 60_000.0).toInt()
    val days = readingDaysThisWeek.coerceIn(0, 7)
    val weekValues = List(7) { weekReadingMillis.getOrElse(it) { 0L }.coerceAtLeast(0L) }

    // Slide 2 (3rd creative slide): Active-day pace & peak reading day from real recorded data
    val avgActiveMillis = if (days > 0) effectiveWeeklyMillis / days else 0L
    val avgActiveLabel = formatDurationMillis(avgActiveMillis)
    val peakDayIndex = weekValues.indices.maxByOrNull { weekValues[it] } ?: 0
    val peakDayMillis = weekValues.getOrElse(peakDayIndex) { 0L }
    val peakDayLabel = formatDurationMillis(peakDayMillis)
    val peakDaySubtitle = if (peakDayMillis > 0L) {
        val dayName = weekdayLabels.getOrNull(peakDayIndex).orEmpty()
        if (dayName.isNotEmpty()) {
            "$dayName · ${resources.getString(com.veritas.reader.R.string.ui_recorded_reading_time)}"
        } else {
            resources.getString(com.veritas.reader.R.string.ui_recorded_reading_time)
        }
    } else {
        resources.getString(com.veritas.reader.R.string.ui_read_at_your_own_pace)
    }

    val viewInsightsLabel = "${com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_insights)} →"
    val setOrEditGoalLabel = "${
        com.veritas.reader.ui.screens.menuString(
            if (hasGoal) com.veritas.reader.R.string.ui_edit_goal else com.veritas.reader.R.string.ui_set_a_goal
        )
    } →"

    val slides = listOf(
        // Slide 0: Weekly Reading (matches the compact 3-row blue reference card)
        ReadingCarouselSlide(
            title = resources.getString(com.veritas.reader.R.string.ui_weekly_reading),
            actionLabel = viewInsightsLabel,
            actionOpensGoalPicker = false,
            topPrimaryValue = weeklyTimeLabel,
            topSecondaryLabel = resources.getString(com.veritas.reader.R.string.ui_recorded_reading_time),
            topBadgeKind = StatBadgeKind.CLOCK,
            bottomPrimaryValue = resources.getQuantityString(com.veritas.reader.R.plurals.reading_days, days, days),
            bottomSecondaryLabel = resources.getString(com.veritas.reader.R.string.ui_with_recorded_reading),
            bottomBadgeKind = StatBadgeKind.CALENDAR_CHECK
        ),
        // Slide 1: Daily Goal & Today's Target (dedicated goal setup + today's progress)
        ReadingCarouselSlide(
            title = resources.getString(com.veritas.reader.R.string.ui_daily_goal),
            actionLabel = setOrEditGoalLabel,
            actionOpensGoalPicker = true,
            topPrimaryValue = todayTimeLabel,
            topSecondaryLabel = resources.getString(
                if (hasGoal) com.veritas.reader.R.string.ui_today_s_reading else com.veritas.reader.R.string.ui_your_reading_today
            ),
            topBadgeKind = StatBadgeKind.CLOCK,
            bottomPrimaryValue = when {
                !hasGoal -> resources.getString(com.veritas.reader.R.string.ui_no_goal)
                reachedGoal -> resources.getString(com.veritas.reader.R.string.ui_done)
                else -> resources.getQuantityString(com.veritas.reader.R.plurals.reading_minutes, remaining, remaining)
            },
            bottomSecondaryLabel = resources.getString(
                when {
                    !hasGoal -> com.veritas.reader.R.string.ui_read_at_your_own_pace
                    reachedGoal -> com.veritas.reader.R.string.ui_today_s_target_complete
                    else -> com.veritas.reader.R.string.ui_to_your_target
                }
            ),
            bottomBadgeKind = StatBadgeKind.TARGET
        ),
        // Slide 2: Reading Pace & Rhythm (active-day average + peak day + 7-day momentum curve)
        ReadingCarouselSlide(
            title = resources.getString(com.veritas.reader.R.string.ui_this_week),
            actionLabel = viewInsightsLabel,
            actionOpensGoalPicker = false,
            topPrimaryValue = avgActiveLabel,
            topSecondaryLabel = resources.getString(com.veritas.reader.R.string.ui_days_with_reading),
            topBadgeKind = StatBadgeKind.PACE,
            bottomPrimaryValue = peakDayLabel,
            bottomSecondaryLabel = peakDaySubtitle,
            bottomBadgeKind = StatBadgeKind.SPARK
        )
    )

    val nextStatistic = resources.getString(com.veritas.reader.R.string.reading_next_statistic)
    val previousStatistic = resources.getString(com.veritas.reader.R.string.reading_previous_statistic)
    val reduceMotion = LocalVeritasMotion.current.reduceMotion
    val pageVisible = LocalLibraryPageVisible.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var statIndex by remember(hasGoal) { mutableStateOf(0) }
    LaunchedEffect(lifecycle, reduceMotion, hasGoal, pageVisible) {
        statIndex = 0
        if (!reduceMotion && pageVisible) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(6_000)
                    statIndex = (statIndex + 1) % slides.size
                }
            }
        }
    }
    var showGoalPicker by remember { mutableStateOf(false) }
    val currentSlide = slides[statIndex.coerceIn(slides.indices)]

    Card(
        modifier = modifier
            .fillMaxWidth()
            .vernScatteredCard("home-today-reading", amount = .5f)
            .testTag("home-today-reading-card")
            .clickable(
                onClickLabel = if (currentSlide.actionOpensGoalPicker) setOrEditGoalLabel else viewInsightsLabel,
                onClick = {
                    if (currentSlide.actionOpensGoalPicker) {
                        showGoalPicker = true
                    } else {
                        onOpenInsights()
                    }
                }
            )
            .pointerInput(slides.size) {
                var drag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragCancel = { drag = 0f },
                    onDragEnd = {
                        if (abs(drag) > 40.dp.toPx()) {
                            statIndex = (statIndex + if (drag < 0f) 1 else slides.size - 1) % slides.size
                        }
                    },
                    onHorizontalDrag = { change, distance ->
                        change.consume()
                        drag += distance
                    }
                )
            },
        shape = VeritasPackStyle.cardShape(),
        colors = CardDefaults.cardColors(
            containerColor = com.veritas.reader.blendColors(
                VeritasPackStyle.panelColor(scheme),
                scheme.tertiaryContainer,
                if (isSystemInDarkTheme()) 0.40f else 0.55f
            )
        ),
        border = null
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clipToBounds()
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(nextStatistic) {
                            statIndex = (statIndex + 1) % slides.size
                            true
                        },
                        CustomAccessibilityAction(previousStatistic) {
                            statIndex = (statIndex + slides.size - 1) % slides.size
                            true
                        }
                    )
                }
        ) {
            // Reserve the tallest slide at the user's font scale so rotating slides never shift content below.
            slides.forEachIndexed { index, slide ->
                ReadingStatPage(
                    slide = slide,
                    page = index,
                    pages = slides.size,
                    fraction = fraction,
                    hasGoal = hasGoal,
                    goalMinutes = goalMinutes,
                    weekReadingMillis = weekValues,
                    weekdayLabels = weekdayLabels,
                    onActionClick = {
                        if (slide.actionOpensGoalPicker) showGoalPicker = true else onOpenInsights()
                    },
                    onGoalRowClick = if (slide.actionOpensGoalPicker) ({ showGoalPicker = true }) else null,
                    modifier = Modifier
                        .graphicsLayer { alpha = 0f }
                        .clearAndSetSemantics {}
                )
            }
            AnimatedContent(
                targetState = statIndex,
                modifier = Modifier.matchParentSize(),
                transitionSpec = {
                    if (reduceMotion) {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    } else {
                        (slideInHorizontally(tween(550)) { it } + fadeIn(tween(350)))
                            .togetherWith(slideOutHorizontally(tween(550)) { -it } + fadeOut(tween(300)))
                            .using(null)
                    }
                },
                label = "todayReadingStats"
            ) { index ->
                val page = index.coerceIn(slides.indices)
                val activeSlide = slides[page]
                ReadingStatPage(
                    slide = activeSlide,
                    page = page,
                    pages = slides.size,
                    fraction = fraction,
                    hasGoal = hasGoal,
                    goalMinutes = goalMinutes,
                    weekReadingMillis = weekValues,
                    weekdayLabels = weekdayLabels,
                    onActionClick = {
                        if (activeSlide.actionOpensGoalPicker) showGoalPicker = true else onOpenInsights()
                    },
                    onGoalRowClick = if (activeSlide.actionOpensGoalPicker) ({ showGoalPicker = true }) else null
                )
            }
        }
    }
    if (showGoalPicker) {
        HomeReadingGoalDialog(goalMinutes, onGoalMinutesChange) { showGoalPicker = false }
    }
}

private enum class StatBadgeKind {
    CLOCK,
    CALENDAR_CHECK,
    TARGET,
    PACE,
    SPARK
}

private data class ReadingCarouselSlide(
    val title: String,
    val actionLabel: String,
    val actionOpensGoalPicker: Boolean,
    val topPrimaryValue: String,
    val topSecondaryLabel: String,
    val topBadgeKind: StatBadgeKind,
    val bottomPrimaryValue: String,
    val bottomSecondaryLabel: String,
    val bottomBadgeKind: StatBadgeKind
)

@Composable
private fun ReadingStatPage(
    slide: ReadingCarouselSlide,
    page: Int,
    pages: Int,
    fraction: Float,
    hasGoal: Boolean,
    goalMinutes: Int,
    weekReadingMillis: List<Long>,
    weekdayLabels: List<String>,
    onActionClick: () -> Unit,
    onGoalRowClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val resources = LocalContext.current.resources
    val positionDescription = resources.getString(com.veritas.reader.R.string.reading_statistic_position, page + 1, pages)
    val goalLabel = if (hasGoal) {
        resources.getString(
            com.veritas.reader.R.string.reading_goal_target,
            resources.getQuantityString(com.veritas.reader.R.plurals.reading_minutes, goalMinutes, goalMinutes)
        )
    } else {
        resources.getString(com.veritas.reader.R.string.ui_no_goal)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Row 1: Compact Header (Icon + Title on left, Pager Dots + Action Link on right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HeaderSlideGlyph(page = page, modifier = Modifier.size(18.dp))
            Text(
                text = slide.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Canvas(
                Modifier
                    .width((pages * 12).dp)
                    .heightIn(min = 8.dp)
                    .semantics { contentDescription = positionDescription }
            ) {
                repeat(pages) { i ->
                    drawLine(
                        color = if (i == page) scheme.tertiary else scheme.onSurface.copy(alpha = .18f),
                        start = Offset(i * 12.dp.toPx() + 2.dp.toPx(), size.height / 2),
                        end = Offset(i * 12.dp.toPx() + (if (i == page) 8 else 4).dp.toPx(), size.height / 2),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
            Text(
                text = slide.actionLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.tertiary,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(
                        role = Role.Button,
                        onClick = onActionClick
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .testTag(if (slide.actionOpensGoalPicker) "home-today-reading-set-goal" else "home-today-reading-view-insights")
            )
        }

        // Rows 2 & 3: Two stacked metrics on left | subtle vertical divider | 7-day chart / visual on right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(0.50f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactStatMetricRow(
                    badgeKind = slide.topBadgeKind,
                    value = slide.topPrimaryValue,
                    subtitle = slide.topSecondaryLabel
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(scheme.onSurface.copy(alpha = 0.08f))
                )
                CompactStatMetricRow(
                    badgeKind = slide.bottomBadgeKind,
                    value = slide.bottomPrimaryValue,
                    subtitle = slide.bottomSecondaryLabel,
                    onClick = onGoalRowClick
                )
            }

            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .padding(vertical = 2.dp)
                    .background(scheme.onSurface.copy(alpha = 0.10f))
            )

            Column(
                modifier = Modifier.weight(0.50f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ReadingStatIllustration(
                    page = page,
                    fraction = fraction,
                    week = weekReadingMillis
                )
                if (page == 0 || page == 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        weekdayLabels.forEachIndexed { idx, dayLabel ->
                            val isActive = (weekReadingMillis.getOrElse(idx) { 0L }) > 0L
                            Text(
                                text = dayLabel.take(3),
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isActive) scheme.onSurface else scheme.onSurfaceVariant.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Clip
                            )
                        }
                    }
                } else {
                    Text(
                        text = goalLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactStatMetricRow(
    badgeKind: StatBadgeKind,
    value: String,
    subtitle: String,
    onClick: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val rowModifier = if (onClick != null) {
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier.fillMaxWidth()
    }
    Row(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(scheme.tertiary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            StatBadgeIcon(kind = badgeKind, modifier = Modifier.size(16.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HeaderSlideGlyph(page: Int, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.tertiary
    Canvas(modifier) {
        val stroke = 2.dp.toPx()
        when (page) {
            0 -> {
                // Bar chart glyph matching the blue reference header icon
                val w = size.width
                val h = size.height
                drawLine(ink, Offset(w * 0.12f, h * 0.88f), Offset(w * 0.88f, h * 0.88f), stroke, StrokeCap.Round)
                drawLine(ink, Offset(w * 0.26f, h * 0.82f), Offset(w * 0.26f, h * 0.45f), stroke, StrokeCap.Round)
                drawLine(ink, Offset(w * 0.50f, h * 0.82f), Offset(w * 0.50f, h * 0.20f), stroke, StrokeCap.Round)
                drawLine(ink, Offset(w * 0.74f, h * 0.82f), Offset(w * 0.74f, h * 0.58f), stroke, StrokeCap.Round)
            }
            1 -> {
                // Target / goal ring glyph
                val r = size.minDimension * 0.40f
                drawCircle(ink, r, center, style = Stroke(stroke))
                drawCircle(ink, r * 0.38f, center)
            }
            else -> {
                // Momentum / trend wave glyph
                val path = Path().apply {
                    moveTo(size.width * 0.12f, size.height * 0.72f)
                    lineTo(size.width * 0.40f, size.height * 0.42f)
                    lineTo(size.width * 0.62f, size.height * 0.58f)
                    lineTo(size.width * 0.88f, size.height * 0.24f)
                }
                drawPath(path, ink, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

@Composable
private fun StatBadgeIcon(kind: StatBadgeKind, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.tertiary
    Canvas(modifier) {
        val stroke = 1.75.dp.toPx()
        val w = size.width
        val h = size.height
        when (kind) {
            StatBadgeKind.CLOCK -> {
                val r = size.minDimension * 0.44f
                drawCircle(ink, r, center, style = Stroke(stroke))
                drawLine(ink, center, Offset(center.x, center.y - r * 0.52f), stroke, StrokeCap.Round)
                drawLine(ink, center, Offset(center.x + r * 0.40f, center.y), stroke, StrokeCap.Round)
            }
            StatBadgeKind.CALENDAR_CHECK -> {
                val top = h * 0.18f
                val left = w * 0.12f
                val boxW = w * 0.76f
                val boxH = h * 0.70f
                drawRoundRect(
                    color = ink,
                    topLeft = Offset(left, top),
                    size = Size(boxW, boxH),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                    style = Stroke(stroke)
                )
                drawLine(ink, Offset(w * 0.30f, h * 0.10f), Offset(w * 0.30f, h * 0.26f), stroke, StrokeCap.Round)
                drawLine(ink, Offset(w * 0.70f, h * 0.10f), Offset(w * 0.70f, h * 0.26f), stroke, StrokeCap.Round)
                drawLine(ink, Offset(w * 0.32f, h * 0.54f), Offset(w * 0.46f, h * 0.68f), stroke, StrokeCap.Round)
                drawLine(ink, Offset(w * 0.46f, h * 0.68f), Offset(w * 0.70f, h * 0.42f), stroke, StrokeCap.Round)
            }
            StatBadgeKind.TARGET -> {
                val r = size.minDimension * 0.44f
                drawCircle(ink, r, center, style = Stroke(stroke))
                drawCircle(ink, r * 0.42f, center)
            }
            StatBadgeKind.PACE -> {
                val r = size.minDimension * 0.44f
                drawArc(
                    color = ink,
                    startAngle = 150f,
                    sweepAngle = 240f,
                    useCenter = false,
                    topLeft = Offset(center.x - r, center.y - r),
                    size = Size(r * 2, r * 2),
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
                drawLine(ink, center, Offset(center.x + r * 0.45f, center.y - r * 0.35f), stroke, StrokeCap.Round)
            }
            StatBadgeKind.SPARK -> {
                val path = Path().apply {
                    moveTo(w * 0.14f, h * 0.74f)
                    lineTo(w * 0.42f, h * 0.42f)
                    lineTo(w * 0.62f, h * 0.58f)
                    lineTo(w * 0.86f, h * 0.24f)
                }
                drawPath(path, ink, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

@Composable
private fun ReadingStatIllustration(page: Int, fraction: Float, week: List<Long>) {
    val scheme = MaterialTheme.colorScheme
    val phase = rememberInsightsPhase("home-stat-$page")
    val reduced = LocalVeritasMotion.current.reduceMotion
    val values = List(7) { week.getOrElse(it) { 0L }.coerceAtLeast(0L) }
    val numbers = java.text.NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
    val read = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.a11y_day_read)
    val unread = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.a11y_day_unread)
    val description = when (page) {
        0 -> com.veritas.reader.ui.screens.menuString(
            com.veritas.reader.R.string.a11y_reading_minutes,
            values.joinToString(", ") { numbers.format(it / 60_000L) }
        )
        1 -> com.veritas.reader.ui.screens.menuString(
            com.veritas.reader.R.string.a11y_goal_progress,
            numbers.format((fraction * 100).toInt())
        )
        else -> com.veritas.reader.ui.screens.menuString(
            com.veritas.reader.R.string.a11y_reading_days,
            values.joinToString(", ") { if (it > 0L) read else unread }
        )
    }

    Canvas(
        Modifier
            .fillMaxWidth()
            .requiredHeight(52.dp)
            .testTag("reading-stat-graphic-$page")
            .semantics { contentDescription = description }
    ) {
        val ink = scheme.tertiary
        val quiet = scheme.onSurface.copy(alpha = .18f)
        val drift = if (reduced) 0f else (phase.value % 1f) * 1.5.dp.toPx()

        when (page) {
            0 -> {
                // Slide 0: 7-day vertical rounded pill bar chart matching the supplied blue reference
                val peak = values.maxOrNull()?.coerceAtLeast(60_000L) ?: 60_000L
                val step = size.width / 7f
                val barWidth = (step * 0.42f).coerceIn(6.dp.toPx(), 12.dp.toPx())
                val maxBarHeight = (size.height - 6.dp.toPx()).coerceAtLeast(16.dp.toPx())
                val minActiveHeight = 12.dp.toPx()
                val bottom = size.height - 2.dp.toPx()

                values.forEachIndexed { i, value ->
                    val centerX = step * (i + 0.5f)
                    if (value > 0L) {
                        val normalized = (value.toFloat() / peak.toFloat()).coerceIn(0f, 1f)
                        val barH = (minActiveHeight + (maxBarHeight - minActiveHeight) * normalized)
                            .coerceAtMost(maxBarHeight)
                        val topY = (bottom - barH - drift * 0.4f).coerceAtLeast(1.dp.toPx())
                        drawRoundRect(
                            color = ink,
                            topLeft = Offset(centerX - barWidth / 2f, topY),
                            size = Size(barWidth, bottom - topY),
                            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                        )
                    } else {
                        val dotW = (barWidth * 0.78f).coerceAtLeast(5.dp.toPx())
                        val dotH = 3.5.dp.toPx()
                        drawRoundRect(
                            color = quiet,
                            topLeft = Offset(centerX - dotW / 2f, bottom - dotH),
                            size = Size(dotW, dotH),
                            cornerRadius = CornerRadius(dotH / 2f, dotH / 2f)
                        )
                    }
                }
            }
            1 -> {
                // Slide 1: Daily Goal circular progress ring with clock hands
                val radius = size.height * .38f
                val c = center.copy(y = center.y + drift * 0.5f)
                drawCircle(quiet, radius, c, style = Stroke(5.5.dp.toPx()))
                val clamped = fraction.coerceIn(0f, 1f)
                if (clamped > 0f) {
                    drawArc(
                        color = ink,
                        startAngle = -90f,
                        sweepAngle = clamped * 360f,
                        useCenter = false,
                        topLeft = c - Offset(radius, radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(5.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                    val angleRad = (clamped * 360f - 90f) * (Math.PI / 180.0).toFloat()
                    val tipX = c.x + radius * cos(angleRad)
                    val tipY = c.y + radius * sin(angleRad)
                    drawCircle(ink, 3.5.dp.toPx(), Offset(tipX, tipY))
                }
                drawLine(ink.copy(alpha = .70f), c, c + Offset(0f, -radius * .48f), 2.dp.toPx(), StrokeCap.Round)
                drawLine(ink.copy(alpha = .70f), c, c + Offset(radius * .34f, 0f), 2.dp.toPx(), StrokeCap.Round)
            }
            else -> {
                // Slide 2: 7-day Rhythm Sparkline + Active-Day Check Badges
                val peak = values.maxOrNull()?.coerceAtLeast(60_000L) ?: 60_000L
                val step = size.width / 7f
                val curveTop = 6.dp.toPx()
                val curveBottom = size.height * 0.56f
                val curveHeight = (curveBottom - curveTop).coerceAtLeast(10.dp.toPx())
                val points = values.mapIndexed { i, value ->
                    val x = step * (i + 0.5f)
                    val ratio = if (value > 0L) (value.toFloat() / peak.toFloat()).coerceIn(0.15f, 1f) else 0f
                    val y = curveBottom - curveHeight * ratio
                    Offset(x, y)
                }

                val path = Path()
                points.forEachIndexed { idx, pt ->
                    if (idx == 0) {
                        path.moveTo(pt.x, pt.y)
                    } else {
                        val prev = points[idx - 1]
                        val midX = (prev.x + pt.x) / 2f
                        path.cubicTo(midX, prev.y, midX, pt.y, pt.x, pt.y)
                    }
                }
                drawPath(
                    path = path,
                    color = ink.copy(alpha = 0.72f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                val badgeTop = size.height * 0.68f
                val badgeH = (size.height - badgeTop - 2.dp.toPx()).coerceAtLeast(8.dp.toPx())
                val badgeW = (step * 0.64f).coerceAtLeast(8.dp.toPx())
                values.forEachIndexed { i, value ->
                    val pt = points[i]
                    val active = value > 0L
                    drawCircle(
                        color = if (active) ink else quiet,
                        radius = if (active) 3.dp.toPx() else 2.dp.toPx(),
                        center = pt
                    )
                    val bx = step * i + (step - badgeW) / 2f
                    drawRoundRect(
                        color = if (active) ink.copy(alpha = 0.85f) else quiet,
                        topLeft = Offset(bx, badgeTop),
                        size = Size(badgeW, badgeH),
                        cornerRadius = CornerRadius(badgeH / 2f, badgeH / 2f)
                    )
                    if (active) {
                        drawLine(
                            color = scheme.onTertiary,
                            start = Offset(bx + badgeW * 0.26f, badgeTop + badgeH * 0.52f),
                            end = Offset(bx + badgeW * 0.44f, badgeTop + badgeH * 0.70f),
                            strokeWidth = 1.4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = scheme.onTertiary,
                            start = Offset(bx + badgeW * 0.44f, badgeTop + badgeH * 0.70f),
                            end = Offset(bx + badgeW * 0.74f, badgeTop + badgeH * 0.32f),
                            strokeWidth = 1.4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
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
        title = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_daily_reading_goal)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_choose_a_target_or_track_your_reading_without_one),
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                val options = (listOf(0, 15, 30, 45, 60) + listOfNotNull(goalMinutes.takeIf { it > 0 })).distinct().sorted()
                options.forEach { minutes ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable {
                                onGoalMinutesChange(minutes)
                                onDismiss()
                            }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (minutes == 0) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_no_goal)
                            else androidx.compose.ui.res.pluralStringResource(com.veritas.reader.R.plurals.reading_minutes_daily, minutes, minutes),
                            Modifier.weight(1f),
                            color = if (minutes == goalMinutes) scheme.primary else scheme.onSurface
                        )
                        if (minutes == goalMinutes) {
                            Icon(
                                Icons.Filled.Check,
                                com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_selected),
                                tint = scheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_done))
            }
        }
    )
}
package com.veritas.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * A sleek, modern slider matching Veritas Design (Images 4 & 5):
 * - Slim 4.dp rounded track
 * - Theme primary color active track extending to the center of the thumb
 * - Soft muted outline variant inactive track
 * - Clean, sleek 18.dp circular thumb with 2.dp surface border and drop shadow
 * - Responsive tap and drag gestures with continuous or step haptics
 */
@Composable
fun VeritasSleekSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    stepIncrement: Float = 0f,
    enabled: Boolean = true,
    sliderHeight: Dp = 20.dp,
    trackHeight: Dp = 4.dp,
    thumbSize: Dp = 16.dp,
    activeTrackColor: Color = MaterialTheme.colorScheme.primary,
    inactiveTrackColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    thumbBorderColor: Color = MaterialTheme.colorScheme.surface,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val effectiveSteps = if (stepIncrement > 0f) {
        ((valueRange.endInclusive - valueRange.start) / stepIncrement).roundToInt().coerceAtLeast(1) - 1
    } else {
        steps
    }
    val hapticTick = rememberSliderHaptics(value, valueRange, effectiveSteps, onValueChange)
    val density = LocalDensity.current

    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val progress = ((value - valueRange.start) / span).coerceIn(0f, 1f)

    val latestFinished by rememberUpdatedState(onValueChangeFinished)

    fun quantize(rawProgress: Float): Float {
        if (stepIncrement > 0f) {
            val rawValue = valueRange.start + rawProgress * span
            val stepIndex = ((rawValue - valueRange.start) / stepIncrement).roundToInt()
            val snapped = valueRange.start + stepIndex * stepIncrement
            return snapped.coerceIn(valueRange.start, valueRange.endInclusive)
        }
        if (steps <= 0) {
            return (valueRange.start + rawProgress * span).coerceIn(valueRange.start, valueRange.endInclusive)
        }
        val notches = steps + 1
        val notchIndex = (rawProgress * notches).roundToInt().coerceIn(0, notches)
        val snappedProgress = notchIndex.toFloat() / notches.toFloat()
        return (valueRange.start + snappedProgress * span).coerceIn(valueRange.start, valueRange.endInclusive)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(sliderHeight)
            .pointerInput(enabled, valueRange, steps, stepIncrement) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val thumbPx = with(density) { thumbSize.toPx() }
                    val totalWidth = size.width.toFloat().coerceAtLeast(1f)
                    val usableWidth = (totalWidth - thumbPx).coerceAtLeast(1f)
                    val rawProgress = ((offset.x - thumbPx / 2f) / usableWidth).coerceIn(0f, 1f)
                    hapticTick(quantize(rawProgress))
                    latestFinished?.invoke()
                }
            }
            .pointerInput(enabled, valueRange, steps, stepIncrement) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val thumbPx = with(density) { thumbSize.toPx() }
                        val totalWidth = size.width.toFloat().coerceAtLeast(1f)
                        val usableWidth = (totalWidth - thumbPx).coerceAtLeast(1f)
                        val rawProgress = ((offset.x - thumbPx / 2f) / usableWidth).coerceIn(0f, 1f)
                        hapticTick(quantize(rawProgress))
                    },
                    onHorizontalDrag = { change, _ ->
                        val thumbPx = with(density) { thumbSize.toPx() }
                        val totalWidth = size.width.toFloat().coerceAtLeast(1f)
                        val usableWidth = (totalWidth - thumbPx).coerceAtLeast(1f)
                        val rawProgress = ((change.position.x - thumbPx / 2f) / usableWidth).coerceIn(0f, 1f)
                        hapticTick(quantize(rawProgress))
                        change.consume()
                    },
                    onDragEnd = {
                        latestFinished?.invoke()
                    },
                    onDragCancel = {
                        latestFinished?.invoke()
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val thumbPx = with(density) { thumbSize.toPx() }
        val trackWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(thumbPx)
        val usableWidthPx = (trackWidthPx - thumbPx).coerceAtLeast(1f)
        val thumbOffsetXPx = (usableWidthPx * progress).roundToInt()

        // 1. Inactive full track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .background(
                    color = if (enabled) inactiveTrackColor else inactiveTrackColor.copy(alpha = 0.25f),
                    shape = CircleShape
                )
        )

        // 2. Active track extending to the thumb center
        val activeWidthPx = (thumbPx / 2f + usableWidthPx * progress).coerceIn(0f, trackWidthPx)
        val activeWidthDp = with(density) { activeWidthPx.toDp() }
        if (activeWidthPx > 0f) {
            Box(
                modifier = Modifier
                    .width(activeWidthDp)
                    .height(trackHeight)
                    .background(
                        color = if (enabled) activeTrackColor else activeTrackColor.copy(alpha = 0.38f),
                        shape = CircleShape
                    )
            )
        }

        // 3. Sleek Circular Thumb with 2.dp surface border & subtle elevation
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffsetXPx, 0) }
                .size(thumbSize)
                .shadow(elevation = 2.dp, shape = CircleShape)
                .background(
                    color = if (enabled) thumbColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    shape = CircleShape
                )
                .border(
                    width = 2.dp,
                    color = thumbBorderColor,
                    shape = CircleShape
                )
        )
    }
}

@Composable
fun VeritasThinRoundSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    stepIncrement: Float = 0f,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trackHeight: Dp = 4.dp,
    thumbSize: Dp = 18.dp
) {
    VeritasSleekSlider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        stepIncrement = stepIncrement,
        modifier = modifier,
        enabled = enabled,
        trackHeight = trackHeight,
        thumbSize = thumbSize
    )
}


package com.veritas.reader.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
    wavy: Boolean = false,
    isPlaying: Boolean = false,
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

    val wavePhase = if (wavy) {
        val infiniteTransition = rememberInfiniteTransition(label = "wavySeekbar")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wavePhase"
        ).value
    } else 0f

    val targetAmplitudeDp = if (!wavy) 0.dp else if (isPlaying) 8.5.dp else 1.5.dp
    val animatedAmplitudePx by animateFloatAsState(
        targetValue = with(density) { targetAmplitudeDp.toPx() },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "wavyAmplitude"
    )

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

        // 2. Active track extending to the thumb center (with solid baseline + animated wavy strands above it)
        val activeWidthPx = (thumbPx / 2f + usableWidthPx * progress).coerceIn(0f, trackWidthPx)
        val activeWidthDp = with(density) { activeWidthPx.toDp() }
        if (activeWidthPx > 0f) {
            if (wavy) {
                val trackHeightPx = with(density) { trackHeight.toPx() }
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(sliderHeight)
                ) {
                    val yCenter = size.height / 2f
                    // Solid straight active baseline
                    drawLine(
                        color = if (enabled) activeTrackColor else activeTrackColor.copy(alpha = 0.38f),
                        start = Offset(0f, yCenter),
                        end = Offset(activeWidthPx, yCenter),
                        strokeWidth = trackHeightPx,
                        cap = StrokeCap.Round
                    )

                    // Three harmonic resonant strands floating and undulating ABOVE the baseline
                    if (activeWidthPx > thumbPx * 0.5f) {
                        val wavelength1 = 46.dp.toPx()
                        val wavelength2 = 64.dp.toPx()
                        val wavelength3 = 32.dp.toPx()
                        val k1 = (2 * Math.PI / wavelength1).toFloat()
                        val k2 = (2 * Math.PI / wavelength2).toFloat()
                        val k3 = (2 * Math.PI / wavelength3).toFloat()

                        val taperDistance = 16.dp.toPx().coerceAtMost(activeWidthPx * 0.35f)

                        val path1 = Path()
                        val path2 = Path()
                        val path3 = Path()
                        path1.moveTo(0f, yCenter)
                        path2.moveTo(0f, yCenter)
                        path3.moveTo(0f, yCenter)

                        val stepPx = 2f
                        var x = 0f
                        while (x < activeWidthPx) {
                            x = (x + stepPx).coerceAtMost(activeWidthPx)
                            val leftTaper = if (taperDistance > 0f) (x / taperDistance).coerceIn(0f, 1f) else 1f
                            val rightTaper = if (taperDistance > 0f) ((activeWidthPx - x) / taperDistance).coerceIn(0f, 1f) else 1f
                            val env = leftTaper * rightTaper

                            // Uplifted waveforms strictly above baseline
                            val wave1 = (0.5f + 0.5f * kotlin.math.sin(k1 * x - wavePhase)).coerceIn(0f, 1f)
                            val wave2 = (0.5f + 0.5f * kotlin.math.sin(k2 * x - wavePhase * 1.25f + 1.6f)).coerceIn(0f, 1f)
                            val wave3 = (0.5f + 0.5f * kotlin.math.sin(k3 * x - wavePhase * 0.85f + 3.0f)).coerceIn(0f, 1f)

                            val y1 = yCenter - animatedAmplitudePx * env * (wave1 * wave1)
                            val y2 = yCenter - (animatedAmplitudePx * 0.82f) * env * (wave2 * wave2)
                            val y3 = yCenter - (animatedAmplitudePx * 0.62f) * env * (wave3 * wave3)

                            path1.lineTo(x, y1)
                            path2.lineTo(x, y2)
                            path3.lineTo(x, y3)
                        }

                        // Strand 3: fine harmonic shimmer
                        drawPath(
                            path = path3,
                            color = activeTrackColor.copy(alpha = 0.40f),
                            style = Stroke(width = 1.3.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // Strand 2: warm amber secondary harmonic
                        drawPath(
                            path = path2,
                            color = activeTrackColor.copy(alpha = 0.65f),
                            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // Strand 1: crisp bright primary wave
                        drawPath(
                            path = path1,
                            color = activeTrackColor,
                            style = Stroke(width = 2.0.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
            } else {
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
        }

        // 3. Glowing Outer Halo when wavy
        if (wavy && enabled) {
            val haloExtra = thumbPx * 0.35f
            val haloSizeDp = with(density) { (thumbPx + haloExtra * 2f).toDp() }
            val haloOffsetDp = with(density) { (thumbOffsetXPx - haloExtra).toDp() }
            Box(
                modifier = Modifier
                    .offset(x = haloOffsetDp)
                    .size(haloSizeDp)
                    .background(
                        color = activeTrackColor.copy(alpha = 0.22f),
                        shape = CircleShape
                    )
            )
        }

        // 4. Sleek Circular Thumb with 2.dp surface border & subtle elevation
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


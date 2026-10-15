package com.veritas.reader.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Small, circular, movable batch import floater.
 * Draggable anywhere on the left or right sides of the screen.
 * Displays "importing" and "current/total" with a circular progress ring.
 * Automatically vanishes when batch importing completes.
 */
@Composable
internal fun BatchImportFloater(
    visible: Boolean,
    current: Int,
    total: Int,
    failed: Int = 0,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }
    val floaterSizePx = with(density) { 50.dp.toPx() }
    val marginPx = with(density) { 16.dp.toPx() }
    val topSafePx = with(density) { 80.dp.toPx() }
    val bottomSafePx = with(density) { 100.dp.toPx() }

    val minX = marginPx
    val maxX = (screenWidthPx - floaterSizePx - marginPx).coerceAtLeast(minX)
    val minY = topSafePx
    val maxY = (screenHeightPx - floaterSizePx - bottomSafePx).coerceAtLeast(minY)

    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(maxX) }
    var offsetY by remember { mutableFloatStateOf(topSafePx + with(density) { 48.dp.toPx() }) }

    // Keep floater within bounds upon rotation or window dimension changes
    LaunchedEffect(maxX, maxY) {
        if (offsetX.value > maxX) offsetX.snapTo(maxX)
        if (offsetY > maxY) offsetY = maxY
        if (offsetY < minY) offsetY = minY
    }

    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
        exit = scaleOut() + fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), offsetY.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                offsetX.snapTo((offsetX.value + dragAmount.x).coerceIn(0f, (screenWidthPx - floaterSizePx).coerceAtLeast(0f)))
                            }
                            offsetY = (offsetY + dragAmount.y).coerceIn(minY, maxY)
                        },
                        onDragEnd = {
                            val snapTargetX = if (offsetX.value + floaterSizePx / 2f < screenWidthPx / 2f) minX else maxX
                            coroutineScope.launch {
                                offsetX.animateTo(
                                    targetValue = snapTargetX,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    )
                                )
                            }
                        }
                    )
                }
                .testTag("batch_import_floater")
        ) {
            Surface(
                modifier = Modifier.size(50.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 6.dp,
                tonalElevation = 2.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val hasProgress = total > 0
                    val progressFraction = if (hasProgress) (current.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                    val countText = if (hasProgress) "$current/$total" else if (current > 0) "$current" else "..."

                    if (hasProgress) {
                        CircularProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(2.5.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(2.5.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "importing",
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 8.5.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = countText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

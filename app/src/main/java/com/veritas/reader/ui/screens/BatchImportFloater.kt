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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.absoluteOffset
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
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
    // A full-size host gives offsets a local top-left origin even inside a centered Box.
    // The caller reserves its navigation/player and applies the remaining safe insets.
    BoxWithConstraints(modifier.fillMaxSize()) {
    val hostWidthPx = constraints.maxWidth.toFloat()
    val hostHeightPx = constraints.maxHeight.toFloat()
    val floaterSizePx = with(density) { 50.dp.toPx() }
    val marginPx = with(density) { 16.dp.toPx() }
    val topSafePx = with(density) { 80.dp.toPx() }
    val availableX = (hostWidthPx - floaterSizePx).coerceAtLeast(0f)
    val availableY = (hostHeightPx - floaterSizePx).coerceAtLeast(0f)
    val minX = marginPx.coerceAtMost(availableX / 2f)
    val maxX = (availableX - marginPx).coerceAtLeast(minX)
    val minY = topSafePx.coerceAtMost(availableY)
    val maxY = (availableY - marginPx).coerceAtLeast(minY)

    val coroutineScope = rememberCoroutineScope()
    var offsetX by remember { mutableFloatStateOf(maxX) }
    val snapAnimation = remember { Animatable(maxX) }
    var snapJob by remember { mutableStateOf<Job?>(null) }
    var offsetY by remember { mutableFloatStateOf(topSafePx + with(density) { 48.dp.toPx() }) }
    var dockedRight by remember { mutableStateOf(true) }

    // Keep floater within bounds upon rotation or window dimension changes
    LaunchedEffect(minX, maxX, minY, maxY) {
        snapJob?.cancel()
        offsetX = if (dockedRight) maxX else minX
        offsetY = offsetY.coerceIn(minY, maxY)
    }

    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
        exit = scaleOut() + fadeOut(),
        modifier = Modifier.align(AbsoluteAlignment.TopLeft)
            .absoluteOffset { IntOffset(offsetX.coerceIn(minX, maxX).roundToInt(), offsetY.coerceIn(minY, maxY).roundToInt()) }
    ) {
        Box(
            modifier = Modifier
                .pointerInput(minX, maxX, minY, maxY) {
                    fun snapToEdge() {
                        dockedRight = offsetX + floaterSizePx / 2f >= hostWidthPx / 2f
                        val target = if (dockedRight) maxX else minX
                        snapJob = coroutineScope.launch {
                            snapAnimation.snapTo(offsetX)
                            snapAnimation.animateTo(target, spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )) { offsetX = value.coerceIn(minX, maxX) }
                        }
                    }
                    detectDragGestures(
                        onDragStart = { snapJob?.cancel() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            // Update synchronously so the final event determines the docking edge.
                            offsetX = (offsetX + dragAmount.x).coerceIn(minX, maxX)
                            offsetY = (offsetY + dragAmount.y).coerceIn(minY, maxY)
                        },
                        onDragEnd = { snapToEdge() },
                        onDragCancel = { snapToEdge() }
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
                            text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_importing),
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
}

package com.veritas.reader.ui.screens

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.withSaveLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.reader.BrandMark
import com.veritas.reader.ui.OnboardingController
import com.veritas.reader.ui.OnboardingStep

/**
 * Spotlight overlay that highlights UI elements during the guided onboarding tour.
 */
@Composable
fun OnboardingSpotlightOverlay(
    step: OnboardingStep,
    userName: String,
    onUserNameChanged: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    isTransitioning: Boolean = false
) {
    val targetBounds = OnboardingController.componentBounds[step.targetKey ?: ""]
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }

    var lastNonNullBounds by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(targetBounds) {
        if (targetBounds != null) {
            lastNonNullBounds = targetBounds
        }
    }

    val baseBounds = (targetBounds ?: lastNonNullBounds)?.translate(-overlayOrigin)
    val hasTarget = step.targetKey != null && baseBounds != null

    // Animate the cutout alpha to fade out during step transitions
    val cutoutAlpha by animateFloatAsState(
        targetValue = if (isTransitioning) 0f else 1f,
        animationSpec = tween(durationMillis = 250),
        label = "cutoutAlpha"
    )

    // Animate the cutout coordinates to slide smoothly between locations
    val animatedLeft = animateFloatAsState(
        targetValue = if (hasTarget) baseBounds.left else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "left"
    )
    val animatedTop = animateFloatAsState(
        targetValue = if (hasTarget) baseBounds.top else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "top"
    )
    val animatedRight = animateFloatAsState(
        targetValue = if (hasTarget) baseBounds.right else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "right"
    )
    val animatedBottom = animateFloatAsState(
        targetValue = if (hasTarget) baseBounds.bottom else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "bottom"
    )

    val animBounds = if (hasTarget) {
        Rect(
            left = animatedLeft.value,
            top = animatedTop.value,
            right = animatedRight.value,
            bottom = animatedBottom.value
        )
    } else {
        null
    }

    val density = LocalDensity.current

    // Convert dimensions outside the canvas to be safe
    val paddingPx = with(density) { 8.dp.toPx() }
    val cornerRadiusPx = with(density) { 16.dp.toPx() }
    val cardSpacingPx = with(density) { 20.dp.toPx() }

    // Pulsing cutout glow effect
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier.fillMaxSize().onGloballyPositioned { overlayOrigin = it.positionInRoot() }
    ) {
        // Fullscreen touch barrier to prevent clicks from bleeding through to underlying home screen tabs and buttons
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) {
                    // Consume background taps so underlying UI cannot be accidentally pressed
                }
        )

        // Transparent black layer with a clear cutout
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            drawIntoCanvas { canvas ->
                canvas.withSaveLayer(
                    bounds = Rect(0f, 0f, canvasWidth, canvasHeight),
                    paint = Paint()
                ) {
                    // Dark dimming overlay
                    drawRect(color = Color.Black.copy(alpha = 0.76f))

                    if (animBounds != null) {
                        val rect = animBounds.inflate(paddingPx)

                        // Clear the shape where the view lies
                        drawRoundRect(
                            color = Color.Transparent,
                            topLeft = rect.topLeft,
                            size = rect.size,
                            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                            blendMode = BlendMode.Clear
                        )

                        // Fade the cutout to dark when transitioning
                        if (cutoutAlpha < 1f) {
                            drawRoundRect(
                                color = Color.Black.copy(alpha = (1f - cutoutAlpha) * 0.76f),
                                topLeft = rect.topLeft,
                                size = rect.size,
                                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                            )
                        }
                    }
                }
            }
        }

        // Draw glowing neon stroke overlay on top of canvas for visual feedback
        if (animBounds != null) {
            val baseRect = animBounds.inflate(paddingPx)
            val glowWidth = baseRect.width * pulseScale
            val glowHeight = baseRect.height * pulseScale
            val dx = (glowWidth - baseRect.width) / 2
            val dy = (glowHeight - baseRect.height) / 2

            Box(
                modifier = Modifier
                    .absoluteOffset(
                        x = with(density) { (baseRect.left - dx).toDp() },
                        y = with(density) { (baseRect.top - dy).toDp() }
                    )
                    .size(
                        width = with(density) { glowWidth.toDp() },
                        height = with(density) { glowHeight.toDp() }
                    )
                    .graphicsLayer { alpha = cutoutAlpha }
                    .border(
                        width = 2.5.dp,
                        brush = Brush.sweepGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary,
                                MaterialTheme.colorScheme.primary
                            )
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
            )
        }

        // Measure the actual safe viewport and card; anchors are recorded in root coordinates.
        var safeOrigin by remember { mutableStateOf(Offset.Zero) }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
                .padding(horizontal = 24.dp, vertical = 10.dp)
                .onGloballyPositioned { safeOrigin = it.positionInRoot() },
            contentAlignment = Alignment.TopCenter
        ) {
            val heightPx = constraints.maxHeight.toFloat()
            val localTarget = animBounds?.translate(overlayOrigin - safeOrigin)
            val above = ((localTarget?.top ?: 0f) - cardSpacingPx).coerceIn(0f, heightPx)
            val below = (heightPx - (localTarget?.bottom ?: heightPx) - cardSpacingPx).coerceIn(0f, heightPx)
            val belowTarget = below >= above
            val availableSide = maxOf(above, below)
            // If neither side provides a useful reading area, use the whole safe viewport.
            val heightLimitPx = if (localTarget != null && availableSide >= with(density) { 240.dp.toPx() }) availableSide else heightPx
            var cardHeight by remember(step, maxWidth, density.fontScale) { mutableIntStateOf(0) }
            val maxY = (heightPx - cardHeight).coerceAtLeast(0f)
            val targetY = when {
                localTarget == null -> maxY / 2f
                belowTarget -> localTarget.bottom + cardSpacingPx
                else -> localTarget.top - cardSpacingPx - cardHeight
            }.coerceIn(0f, maxY)
            val yOffset by animateDpAsState(
                targetValue = with(density) { targetY.toDp() },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "yOffset"
            )

            Box(
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .fillMaxWidth()
                    .heightIn(max = with(density) { heightLimitPx.toDp() })
                    .offset { IntOffset(0, yOffset.roundToPx().coerceIn(0, maxY.toInt())) }
                    .onSizeChanged { cardHeight = it.height }
                    .graphicsLayer { alpha = if (cardHeight > 0) cutoutAlpha else 0f }
                    .testTag("spotlight-card")
            ) {
                OnboardingInfoCard(
                    step = step,
                    userName = userName,
                    onUserNameChanged = onUserNameChanged,
                    onNext = onNext,
                    onBack = onBack,
                    onDismiss = onDismiss,
                    showLiveTip = step.targetKey != null && targetBounds == null,
                    isTransitioning = isTransitioning
                )
            }
        }
    }
}

/**
 * Information card displayed alongside highlighted components in the tour.
 */
@Composable
fun OnboardingInfoCard(
    step: OnboardingStep,
    userName: String,
    onUserNameChanged: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    showLiveTip: Boolean,
    isTransitioning: Boolean = false
) {
    Card(
        modifier = Modifier
            .widthIn(max = 380.dp)
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            if (step == OnboardingStep.WELCOME) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BrandMark(compact = false)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = step.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_dismiss_tour),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = step.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (step == OnboardingStep.NAME_INPUT) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = userName,
                    onValueChange = onUserNameChanged,
                    label = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_your_name)) },
                    placeholder = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_enter_your_name)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (showLiveTip) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_tip),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_tip_open_a_reading_to_see_this_live_in_the_reader),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Back button
                if (step != OnboardingStep.WELCOME) {
                    TextButton(
                        onClick = onBack,
                        enabled = !isTransitioning
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_back),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_back), style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                // Next / Finish button
                Button(
                    onClick = onNext,
                    modifier = Modifier.testTag("spotlight-next"),
                    enabled = !isTransitioning,
                    shape = RoundedCornerShape(50)
                ) {
                    val label = if (step == OnboardingStep.CONGRATULATIONS) "Finish" else "Next"
                    Text(label, style = MaterialTheme.typography.labelMedium)
                    if (step != OnboardingStep.CONGRATULATIONS) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_next),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

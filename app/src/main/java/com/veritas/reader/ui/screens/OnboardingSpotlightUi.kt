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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.withSaveLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
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
    
    var lastNonNullBounds by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(targetBounds) {
        if (targetBounds != null) {
            lastNonNullBounds = targetBounds
        }
    }

    val baseBounds = targetBounds ?: lastNonNullBounds
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
    val screenHeightPx = LocalWindowInfo.current.containerSize.height.toFloat()

    // Convert dimensions outside the canvas to be safe
    val paddingPx = with(density) { 8.dp.toPx() }
    val cornerRadiusPx = with(density) { 16.dp.toPx() }
    val cardSpacingPx = with(density) { 20.dp.toPx() }
    val cardHeightEstPx = with(density) { 200.dp.toPx() }
    val minTopMarginPx = with(density) { 10.dp.toPx() }

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
        modifier = Modifier.fillMaxSize()
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
                    .offset(
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

        // Display explanation card
        val isCardBelow = if (animBounds != null) {
            animBounds.center.y < screenHeightPx / 2
        } else {
            true // centered default fallback
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            val targetYOffset = if (animBounds != null) {
                if (isCardBelow) {
                    val bottomPx = animBounds.bottom + cardSpacingPx
                    with(density) { bottomPx.toDp() }
                } else {
                    val topPx = animBounds.top - cardHeightEstPx
                    with(density) { topPx.coerceAtLeast(minTopMarginPx).toDp() }
                }
            } else {
                // Center it vertically when there is no target
                val estCardHeightDp = 220.dp
                val screenHeightDp = with(density) { screenHeightPx.toDp() }
                ((screenHeightDp - estCardHeightDp) / 2f).coerceAtLeast(0.dp)
            }
            val yOffset by animateDpAsState(
                targetValue = targetYOffset,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "yOffset"
            )

            Box(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .offset { IntOffset(0, yOffset.roundToPx()) }
                    .graphicsLayer { alpha = cutoutAlpha }
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
            .fillMaxWidth()
            .widthIn(max = 380.dp)
            .shadow(16.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
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
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss Tour",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = step.body,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (step == OnboardingStep.NAME_INPUT) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = userName,
                    onValueChange = onUserNameChanged,
                    label = { Text("Your Name") },
                    placeholder = { Text("Enter your name") },
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
                        contentDescription = "Tip",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Tip: Open a reading to see this live in the reader!",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button
                if (step != OnboardingStep.WELCOME) {
                    TextButton(
                        onClick = onBack,
                        enabled = !isTransitioning
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Back", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                // Next / Finish button
                Button(
                    onClick = onNext,
                    enabled = !isTransitioning,
                    shape = RoundedCornerShape(50)
                ) {
                    val label = if (step == OnboardingStep.CONGRATULATIONS) "Finish" else "Next"
                    Text(label, style = MaterialTheme.typography.labelMedium)
                    if (step != OnboardingStep.CONGRATULATIONS) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

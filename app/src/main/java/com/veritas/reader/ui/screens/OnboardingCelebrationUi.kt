package com.veritas.reader.ui.screens

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Random

/**
 * Animated celebration badge with glowing pulsing rings and rotating sparkles.
 */
@Composable
fun CelebrationAnimatedBadge(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "celebrationAnim")

    val ringScale1 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(2000, easing = EaseInOutSine), RepeatMode.Restart),
        label = "ring1"
    )
    val ringAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(tween(2000, easing = EaseInOutSine), RepeatMode.Restart),
        label = "ringAlpha1"
    )

    val ringScale2 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(2000, delayMillis = 1000, easing = EaseInOutSine), RepeatMode.Restart),
        label = "ring2"
    )
    val ringAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(tween(2000, delayMillis = 1000, easing = EaseInOutSine), RepeatMode.Restart),
        label = "ringAlpha2"
    )

    val bounceScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1200, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "bounce"
    )
    val tiltAngle by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(1600, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "tilt"
    )
    val sparkleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(700, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "sparkle"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = modifier
            .size(140.dp)
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer pulsing ring 1
        Box(
            modifier = Modifier
                .size(110.dp)
                .scale(ringScale1)
                .graphicsLayer { alpha = ringAlpha1 }
                .border(
                    width = 3.dp,
                    brush = Brush.radialGradient(listOf(primaryColor, Color.Transparent)),
                    shape = CircleShape
                )
        )

        // Outer pulsing ring 2 (delayed phase)
        Box(
            modifier = Modifier
                .size(110.dp)
                .scale(ringScale2)
                .graphicsLayer { alpha = ringAlpha2 }
                .border(
                    width = 2.dp,
                    brush = Brush.radialGradient(listOf(tertiaryColor, Color.Transparent)),
                    shape = CircleShape
                )
        )

        // Main core sphere
        Box(
            modifier = Modifier
                .size(86.dp)
                .scale(bounceScale)
                .graphicsLayer { rotationZ = tiltAngle }
                .background(
                    brush = Brush.sweepGradient(
                        listOf(primaryColor, secondaryColor, tertiaryColor, primaryColor)
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(74.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.primary
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Success",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        // Sparkle 1: Top-Right
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = tertiaryColor,
            modifier = Modifier
                .size(24.dp)
                .align(Alignment.TopEnd)
                .graphicsLayer { alpha = sparkleAlpha }
        )

        // Sparkle 2: Bottom-Left
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = secondaryColor,
            modifier = Modifier
                .size(18.dp)
                .align(Alignment.BottomStart)
                .graphicsLayer { alpha = (1.3f - sparkleAlpha).coerceIn(0f, 1f) }
        )
    }
}

/**
 * Onboarding screen displaying configured profile summary before tour start.
 */
@Composable
fun OnboardingReadyCelebrationScreen(
    userName: String,
    personaTitle: String = "Student",
    interest: String,
    voiceTitle: String,
    aiTitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        CelebrationAnimatedBadge()

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (userName.isNotBlank()) "You're Ready, ${userName.trim()}!" else "You're Ready to Read!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Your personalized profile is configured. We're about to take a quick guided tour through your new reading environment.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Profile Summary Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Configured Reading Profile",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                OnboardingSummaryRow(label = "Reader Name", value = userName.ifBlank { "Vern" })
                OnboardingSummaryRow(label = "Reader Persona", value = personaTitle)
                OnboardingSummaryRow(label = "Primary Focus", value = interest)
                OnboardingSummaryRow(label = "Voice Preset", value = voiceTitle)
                OnboardingSummaryRow(label = "AI Assistant", value = aiTitle)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

/**
 * Single label-value summary row in the Onboarding celebration screen.
 */
@Composable
fun OnboardingSummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Particle data model for confetti rendering.
 */
data class ConfettiParticle(
    val x: Float,
    val y: Float,
    val speedY: Float,
    val speedX: Float,
    val color: Color,
    val size: Float,
    val rotation: Float,
    val rotationSpeed: Float,
    val shapeType: Int // 0: circle, 1: rectangle, 2: triangle
)

/**
 * Animated confetti overlay for triumphant moments.
 */
@Composable
fun ConfettiOverlay(
    modifier: Modifier = Modifier,
    onFinished: () -> Unit
) {
    var particles by remember { mutableStateOf(emptyList<ConfettiParticle>()) }
    var durationCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val random = Random()
        val colors = listOf(
            Color(0xFFFFC107), // Yellow
            Color(0xFFFF5722), // Orange
            Color(0xFF4CAF50), // Green
            Color(0xFF2196F3), // Blue
            Color(0xFF9C27B0), // Purple
            Color(0xFFE91E63), // Pink
            Color(0xFF00BCD4)  // Cyan
        )
        
        particles = List(120) {
            ConfettiParticle(
                x = 0.1f + random.nextFloat() * 0.8f,
                y = -0.2f - random.nextFloat() * 0.4f,
                speedY = 0.008f + random.nextFloat() * 0.015f,
                speedX = -0.006f + random.nextFloat() * 0.012f,
                color = colors[random.nextInt(colors.size)],
                size = 12f + random.nextFloat() * 18f,
                rotation = random.nextFloat() * 360f,
                rotationSpeed = -6f + random.nextFloat() * 12f,
                shapeType = random.nextInt(3)
            )
        }

        while (durationCount < 240) { // ~4 seconds at 60fps
            withFrameMillis {
                particles = particles.map { p ->
                    val newY = p.y + p.speedY
                    val newX = p.x + p.speedX
                    val newRotation = p.rotation + p.rotationSpeed
                    // Gravity and wind drift simulated
                    p.copy(
                        x = if (newX < -0.05f) 1.05f else if (newX > 1.05f) -0.05f else newX,
                        y = if (newY > 1.1f) -0.1f else newY,
                        rotation = newRotation
                    )
                }
            }
            durationCount++
        }
        onFinished()
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        particles.forEach { p ->
            val px = p.x * canvasWidth
            val py = p.y * canvasHeight

            if (py in -100f..(canvasHeight + 100f)) {
                withTransform({
                    rotate(p.rotation, pivot = Offset(px, py))
                }) {
                    when (p.shapeType) {
                        1 -> { // Rectangle / Ribbon
                            drawRect(
                                color = p.color,
                                topLeft = Offset(px - p.size, py - p.size / 2f),
                                size = Size(p.size * 2f, p.size)
                            )
                        }
                        2 -> { // Triangle
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(px, py - p.size)
                                lineTo(px - p.size, py + p.size)
                                lineTo(px + p.size, py + p.size)
                                close()
                            }
                            drawPath(path = path, color = p.color)
                        }
                        else -> { // Circle / Dot
                            drawCircle(
                                color = p.color,
                                radius = p.size / 1.5f,
                                center = Offset(px, py)
                            )
                        }
                    }
                }
            }
        }
    }
}

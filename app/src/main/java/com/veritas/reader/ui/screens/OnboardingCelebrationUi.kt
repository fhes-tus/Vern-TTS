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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.width
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * Onboarding screen displaying configured profile summary in a VIP Studio Pass card
 * and name personalization before library entrance (Sanctuary Climax).
 */
@Composable
fun OnboardingReadyCelebrationScreen(
    userName: String,
    onUserNameChange: (String) -> Unit = {},
    personaTitle: String = "Deep Study",
    interest: String = "Books & Novels",
    voiceTitle: String = "Aura",
    aiTitle: String = "Google Gemini"
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
            text = if (userName.isNotBlank()) "Your Sanctuary is Ready, ${userName.trim()}." else "Your Sanctuary Awaits.",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Your studio profile, neural narration, and reading rhythms have been calibrated for effortless immersion.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // VIP Studio Pass Card (Frosted Glass & Holographic Specular Rim)
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.70f)
            ),
            border = BorderStroke(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.35f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.40f),
                        Color.White.copy(alpha = 0.12f)
                    )
                )
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Pass Header: Title & Access Chip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "VERITAS STUDIO PASS",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.8.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                RoundedCornerShape(50)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "SANCTUARY · 001",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    thickness = 1.dp
                )

                // Personalized Reader Identity & Name Input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Monogram Avatar Circle
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.20f)
                                    )
                                )
                            )
                            .border(
                                1.5.dp,
                                Brush.sweepGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary,
                                        MaterialTheme.colorScheme.primary
                                    )
                                ),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val monogram = userName.trim().firstOrNull()?.uppercase() ?: "V"
                        Text(
                            text = monogram,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Reader Identity",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = userName,
                            onValueChange = onUserNameChange,
                            placeholder = { Text("What should we call you? (e.g. Alex)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Studio Calibration Matrix (2x2 Grid of frosted badges)
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        VipStudioPassBadge(
                            label = "Discipline",
                            value = personaTitle,
                            modifier = Modifier.weight(1f)
                        )
                        VipStudioPassBadge(
                            label = "Narrator",
                            value = "$voiceTitle · Calibrated",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        VipStudioPassBadge(
                            label = "Material",
                            value = interest,
                            modifier = Modifier.weight(1f)
                        )
                        VipStudioPassBadge(
                            label = "AI Studio",
                            value = aiTitle,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Security & Privacy Holographic Watermark Sheen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.4f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔒 ENCRYPTED ON-DEVICE · ZERO TELEMETRY · LOSSLESS SOUND",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        fontSize = 9.sp,
                        letterSpacing = 0.8.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

/**
 * Frosted badge for the VIP Studio Pass calibration row.
 */
@Composable
fun VipStudioPassBadge(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.65f))
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.15f),
                        Color.Transparent
                    )
                ),
                RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
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

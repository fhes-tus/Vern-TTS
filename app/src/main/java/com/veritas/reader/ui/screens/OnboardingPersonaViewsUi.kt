package com.veritas.reader.ui.screens

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.reader.ui.ReaderPersona
import com.veritas.reader.ui.ReaderPersonas
import com.veritas.reader.ui.rememberVeritasHaptics

/**
 * Onboarding persona selection screen allowing users to pick their reader profile type.
 * Embodies Chris Raroque's Micro-Interactions & Bespoke Glyphs philosophy.
 */
@Composable
fun OnboardingPersonaSelectionScreen(
    selectedPersona: String,
    onSelectPersona: (ReaderPersona) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Choose Your Rhythm",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            lineHeight = 32.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Vern tunes its typography, neural pacing, and synthesis tools to your reading discipline.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Select your reading discipline:",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 2x2 Grid of Persona Squircle Cards
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            ReaderPersonas.chunked(2).forEach { rowPersonas ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    rowPersonas.forEach { persona ->
                        val isSelected = persona.id == selectedPersona
                        PersonaSquircleCard(
                            persona = persona,
                            isSelected = isSelected,
                            onSelect = { onSelectPersona(persona) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

/**
 * Squircle card for a single Reader Persona.
 * Features tactile spring physics (press down to 0.96f, bounce release),
 * specular rim gradient strokes, and bespoke geometric line glyphs.
 */
@Composable
fun PersonaSquircleCard(
    persona: ReaderPersona,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberVeritasHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Tactile spring physics: down-press scales to 0.96f, release bounces back to 1.02f when selected
    val targetScale = when {
        isPressed -> 0.96f
        isSelected -> 1.02f
        else -> 1.0f
    }
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = if (isPressed) Spring.DampingRatioNoBouncy else Spring.DampingRatioMediumBouncy,
            stiffness = if (isPressed) 400f else 320f
        ),
        label = "personaCardScale"
    )

    // Specular rim gradient border: luminous gold/primary highlight when selected,
    // subtle top-down specular white sheen when unselected
    val rimBrush = if (isSelected) {
        Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.18f),
                Color.White.copy(alpha = 0.04f),
                Color.Transparent
            )
        )
    }

    Card(
        onClick = {
            haptic.select()
            onSelect()
        },
        interactionSource = interactionSource,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
            else
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f)
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            brush = rimBrush
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 8.dp else 2.dp
        ),
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(180.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Bespoke geometric line glyph
                Box(
                    modifier = Modifier.size(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (persona.id) {
                        "student" -> DeepStudyGlyph(isSelected = isSelected)
                        "professional" -> SpeedAndWorkGlyph(isSelected = isSelected)
                        "book_lover" -> LiteratureGlyph(isSelected = isSelected)
                        "educator" -> AudioFirstGlyph(isSelected = isSelected)
                        else -> DeepStudyGlyph(isSelected = isSelected)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = persona.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = persona.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    lineHeight = 13.sp
                )
            }

            // Selection indicator badge
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

// --------------------------------------------------------------------
// Bespoke Geometric Vector Glyphs (Replacing generic icons & emojis)
// --------------------------------------------------------------------

/**
 * Bespoke Glyph 1: Deep Study
 * Layered geometric open book with floating diamond focus reticle and retention lines.
 */
@Composable
fun DeepStudyGlyph(isSelected: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "deepStudyPulse")
    val diamondOffset by infiniteTransition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1400, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "diamondOffset"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(1000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "glowAlpha"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val tint = if (isSelected) primaryColor else onSurfaceColor

    Canvas(modifier = Modifier.size(48.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f + 4f
        val strokeW = 2.4.dp.toPx()

        // Geometric open pages (left & right wings)
        val leftPage = Path().apply {
            moveTo(cx, cy + 10f)
            cubicTo(cx - 7f, cy + 8f, cx - 16f, cy + 11f, cx - 21f, cy + 9f)
            lineTo(cx - 21f, cy - 7f)
            cubicTo(cx - 16f, cy - 5f, cx - 7f, cy - 8f, cx, cy - 6f)
            close()
        }
        val rightPage = Path().apply {
            moveTo(cx, cy + 10f)
            cubicTo(cx + 7f, cy + 8f, cx + 16f, cy + 11f, cx + 21f, cy + 9f)
            lineTo(cx + 21f, cy - 7f)
            cubicTo(cx + 16f, cy - 5f, cx + 7f, cy - 8f, cx, cy - 6f)
            close()
        }

        drawPath(
            path = leftPage,
            color = tint,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = rightPage,
            color = tint,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Center spine line
        drawLine(
            color = tint,
            start = Offset(cx, cy - 6f),
            end = Offset(cx, cy + 10f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )

        // Interior retention lines
        drawLine(
            color = tint.copy(alpha = 0.45f),
            start = Offset(cx - 16f, cy + 1f),
            end = Offset(cx - 5f, cy),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint.copy(alpha = 0.45f),
            start = Offset(cx + 5f, cy),
            end = Offset(cx + 16f, cy + 1f),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Floating diamond focus reticle above book
        val diamondY = cy - 16f + (if (isSelected) diamondOffset else 0f)
        val diamond = Path().apply {
            moveTo(cx, diamondY - 6f)
            lineTo(cx + 5f, diamondY)
            lineTo(cx, diamondY + 6f)
            lineTo(cx - 5f, diamondY)
            close()
        }

        if (isSelected) {
            drawCircle(
                color = primaryColor.copy(alpha = glowAlpha * 0.35f),
                radius = 10f,
                center = Offset(cx, diamondY)
            )
        }

        drawPath(
            path = diamond,
            color = if (isSelected) primaryColor else tint,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawCircle(
            color = if (isSelected) primaryColor else tint,
            radius = 2f,
            center = Offset(cx, diamondY)
        )
    }
}

/**
 * Bespoke Glyph 2: Speed & Work
 * Dynamic kinetic chevron lightning path with velocity energy trails.
 */
@Composable
fun SpeedAndWorkGlyph(isSelected: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "speedShimmer")
    val trailShift by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "trailShift"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val tint = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurface

    Canvas(modifier = Modifier.size(48.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val strokeW = 2.4.dp.toPx()

        // Kinetic lightning chevron path
        val lightning = Path().apply {
            moveTo(cx + 4f, cy - 18f)
            lineTo(cx - 7f, cy - 2f)
            lineTo(cx + 2f, cy - 2f)
            lineTo(cx - 4f, cy + 18f)
            lineTo(cx + 7f, cy + 2f)
            lineTo(cx - 2f, cy + 2f)
            close()
        }

        if (isSelected) {
            drawPath(
                path = lightning,
                brush = Brush.verticalGradient(
                    listOf(primaryColor.copy(alpha = 0.3f), primaryColor.copy(alpha = 0.05f))
                )
            )
        }

        drawPath(
            path = lightning,
            color = tint,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Left velocity energy dashes
        val shift = if (isSelected) trailShift else 0f
        drawLine(
            color = tint.copy(alpha = 0.5f),
            start = Offset(cx - 19f + shift, cy - 8f),
            end = Offset(cx - 11f + shift, cy - 8f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint.copy(alpha = 0.75f),
            start = Offset(cx - 21f - shift, cy),
            end = Offset(cx - 13f - shift, cy),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint.copy(alpha = 0.45f),
            start = Offset(cx - 17f + shift, cy + 8f),
            end = Offset(cx - 10f + shift, cy + 8f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Right speed spark node
        drawCircle(
            color = tint.copy(alpha = 0.7f),
            radius = 2.5f,
            center = Offset(cx + 16f, cy - 4f)
        )
    }
}

/**
 * Bespoke Glyph 3: Literature
 * Elegant humanist quill / calligraphy feather with flowing line art and ink droplet.
 */
@Composable
fun LiteratureGlyph(isSelected: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "literatureSway")
    val swayAngle by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(2200, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "sway"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val tint = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurface

    Canvas(modifier = Modifier.size(48.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val strokeW = 2.2.dp.toPx()

        withTransform({
            rotate(if (isSelected) swayAngle else 0f, pivot = Offset(cx, cy))
        }) {
            // Flowing quill stem
            val quillStem = Path().apply {
                moveTo(cx + 14f, cy - 18f)
                cubicTo(cx + 10f, cy - 6f, cx - 4f, cy + 8f, cx - 14f, cy + 18f)
            }
            drawPath(
                path = quillStem,
                color = tint,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )

            // Feather upper vane
            val featherVane = Path().apply {
                moveTo(cx + 14f, cy - 18f)
                cubicTo(cx + 4f, cy - 16f, cx - 2f, cy - 4f, cx + 4f, cy + 2f)
            }
            drawPath(
                path = featherVane,
                color = tint,
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )

            // Inner vane lines
            drawLine(
                color = tint.copy(alpha = 0.45f),
                start = Offset(cx + 8f, cy - 11f),
                end = Offset(cx + 2f, cy - 8f),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = tint.copy(alpha = 0.45f),
                start = Offset(cx + 4f, cy - 4f),
                end = Offset(cx - 2f, cy - 2f),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Ink droplet at nib
            drawCircle(
                color = tint,
                radius = 3.2f,
                center = Offset(cx - 14f, cy + 18f)
            )

            // Micro pedestal arc at bottom
            val baseArc = Path().apply {
                moveTo(cx - 18f, cy + 19f)
                cubicTo(cx - 10f, cy + 22f, cx + 2f, cy + 20f, cx + 10f, cy + 17f)
            }
            drawPath(
                path = baseArc,
                color = tint.copy(alpha = 0.35f),
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Bespoke Glyph 4: Audio First
 * Concentric acoustic resonance waves radiating outward from central sonic emitter.
 */
@Composable
fun AudioFirstGlyph(isSelected: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "audioWaveExpand")
    val wavePulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(1300, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "wavePulse"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val tint = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurface

    Canvas(modifier = Modifier.size(48.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val strokeW = 2.2.dp.toPx()
        val pulse = if (isSelected) wavePulse else 1f

        // Central core sonic emitter
        drawCircle(
            color = tint,
            radius = 4.5f,
            center = Offset(cx, cy)
        )

        // Inner sound waves (left and right arcs)
        val r1 = 11f * pulse
        drawArc(
            color = tint,
            startAngle = -50f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = Offset(cx - r1, cy - r1),
            size = Size(r1 * 2f, r1 * 2f),
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )
        drawArc(
            color = tint,
            startAngle = 130f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = Offset(cx - r1, cy - r1),
            size = Size(r1 * 2f, r1 * 2f),
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )

        // Outer sound waves (left and right arcs)
        val r2 = 19f * pulse
        drawArc(
            color = tint.copy(alpha = 0.65f),
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(cx - r2, cy - r2),
            size = Size(r2 * 2f, r2 * 2f),
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )
        drawArc(
            color = tint.copy(alpha = 0.65f),
            startAngle = 135f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(cx - r2, cy - r2),
            size = Size(r2 * 2f, r2 * 2f),
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )

        // Ambient halo ring when selected
        if (isSelected) {
            drawCircle(
                color = primaryColor.copy(alpha = 0.15f),
                radius = 23f * pulse,
                center = Offset(cx, cy)
            )
        }
    }
}

// --------------------------------------------------------------------
// Compatibility Aliases for Legacy Icon Calls
// --------------------------------------------------------------------

@Composable
fun StudentAnimatedIcon(isSelected: Boolean) = DeepStudyGlyph(isSelected)

@Composable
fun EducatorAnimatedIcon(isSelected: Boolean) = AudioFirstGlyph(isSelected)

@Composable
fun ProfessionalAnimatedIcon(isSelected: Boolean) = SpeedAndWorkGlyph(isSelected)

@Composable
fun BookLoverAnimatedIcon(isSelected: Boolean) = LiteratureGlyph(isSelected)

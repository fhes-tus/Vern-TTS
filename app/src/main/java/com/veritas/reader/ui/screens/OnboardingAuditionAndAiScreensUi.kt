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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.reader.aiAssistantIcon
import com.veritas.reader.ui.AiStudyFocusOptions
import com.veritas.reader.ui.VoiceAuditionPreset
import com.veritas.reader.ui.VoiceAuditionPresets
import com.veritas.reader.ui.rememberVeritasHaptics

/**
 * Screen for auditioning and choosing a narrator voice preset.
 * Embodies Chris Raroque's Layered Lighting, Physicality & Tactile Audio Waveform principles.
 */
@Composable
fun OnboardingVoiceAuditionScreen(
    selectedPreset: VoiceAuditionPreset,
    onSelectPreset: (VoiceAuditionPreset) -> Unit,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit
) {
    val haptic = rememberVeritasHaptics()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Select Your Narrator",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Experience neural voices tuned for effortless, long-form immersion.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Ambient fluid audio waveform visualizer (Centerpiece Living Canvas)
        FluidAudioWaveformVisualizer(
            isPlaying = isPlaying,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Preset List
        Text(
            text = "Voice Personalities",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(10.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            VoiceAuditionPresets.forEach { preset ->
                val isSelected = preset.id == selectedPreset.id
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()

                val cardScale by animateFloatAsState(
                    targetValue = when {
                        isPressed -> 0.97f
                        isSelected -> 1.015f
                        else -> 1.0f
                    },
                    animationSpec = spring(
                        dampingRatio = if (isPressed) Spring.DampingRatioNoBouncy else Spring.DampingRatioMediumBouncy,
                        stiffness = if (isPressed) 400f else 320f
                    ),
                    label = "voiceCardScale"
                )

                val rimBrush = if (isSelected) {
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
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
                        onSelectPreset(preset)
                    },
                    interactionSource = interactionSource,
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        else
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.60f)
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        brush = rimBrush
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = if (isSelected) 6.dp else 1.dp
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = cardScale
                            scaleY = cardScale
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Tactile play preview button
                        TactileVoicePlayButton(
                            isPlaying = isSelected && isPlaying,
                            isSelected = isSelected,
                            onTogglePlay = {
                                if (isSelected) {
                                    onTogglePlay()
                                } else {
                                    onSelectPreset(preset)
                                    onTogglePlay()
                                }
                            }
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = preset.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${preset.speed}x",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = preset.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Interactive Live Audition Player Card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f)
            ),
            border = BorderStroke(
                1.5.dp,
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)
                    )
                )
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Audio Sample",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Auditioning: ${selectedPreset.title}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (isPlaying) {
                        LiveWaveEqualizer()
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "\"${selectedPreset.sampleText}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                val buttonInteraction = remember { MutableInteractionSource() }
                val buttonPressed by buttonInteraction.collectIsPressedAsState()
                val buttonScale by animateFloatAsState(
                    targetValue = if (buttonPressed) 0.95f else 1.0f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                    label = "btnScale"
                )

                Button(
                    onClick = {
                        haptic.toggle(!isPlaying)
                        onTogglePlay()
                    },
                    interactionSource = buttonInteraction,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlaying) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
                        contentColor = if (isPlaying) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = buttonScale
                            scaleY = buttonScale
                        }
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Stop" else "Play Sample",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (isPlaying) "Stop Preview" else "Play Voice Sample",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

/**
 * Tactile play preview button with acoustic pulsing ring.
 */
@Composable
fun TactileVoicePlayButton(
    isPlaying: Boolean,
    isSelected: Boolean,
    onTogglePlay: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voicePlayPulse")
    val pulseRingScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(tween(1100, easing = EaseInOutSine), RepeatMode.Restart),
        label = "pulseRing"
    )
    val pulseRingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(tween(1100, easing = EaseInOutSine), RepeatMode.Restart),
        label = "pulseAlpha"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(46.dp)
    ) {
        // Glowing acoustic ring when playing
        if (isPlaying) {
            Box(
                modifier = Modifier
                    .size(44.dp * pulseRingScale)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = pulseRingAlpha)
                    )
            )
        }

        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isPlaying -> MaterialTheme.colorScheme.errorContainer
                        isSelected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Stop" else "Preview",
                tint = when {
                    isPlaying -> MaterialTheme.colorScheme.onErrorContainer
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Animated multi-harmonic oscillating Sine waveform ribbon.
 * Features dual-harmonic sine waves with quadratic envelope windowing (1 - x^2),
 * volumetric background lighting, and golden & violet ambient floating glow particles.
 */
@Composable
fun FluidAudioWaveformVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fluidWave")

    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase1"
    )
    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase2"
    )
    val ambientGlowPulse by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientGlow"
    )
    val waveAmplitude by animateFloatAsState(
        targetValue = if (isPlaying) 30f else 10f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessLow),
        label = "waveAmp"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0x18, 0x1E, 0x2A).copy(alpha = 0.75f),
                        Color(0x0E, 0x12, 0x1A).copy(alpha = 0.90f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val midY = height / 2f

            // 1. Ambient Volumetric Glow Pool (layered background lighting)
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = if (isPlaying) 0.35f * ambientGlowPulse else 0.14f * ambientGlowPulse),
                        tertiaryColor.copy(alpha = if (isPlaying) 0.22f * ambientGlowPulse else 0.08f * ambientGlowPulse),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.5f, midY),
                    radius = width * 0.42f
                ),
                topLeft = Offset(width * 0.1f, midY - 45f),
                size = Size(width * 0.8f, 90f)
            )

            val points = 72

            // 2. Primary harmonic sine wave ribbon (Golden / Amber)
            val path1 = Path()
            for (i in 0..points) {
                val ratio = i.toFloat() / points
                val x = ratio * width
                val normalizedX = ratio * 2f - 1f // [-1f..1f]
                val envelope = (1f - normalizedX * normalizedX).coerceAtLeast(0f)
                val primarySine = Math.sin((ratio * 3.2 * Math.PI + phase1)).toFloat()
                val subHarmonic = Math.sin((ratio * 6.4 * Math.PI + phase1 * 1.3)).toFloat() * 0.25f
                val y = midY + (primarySine + subHarmonic) * waveAmplitude * envelope

                if (i == 0) path1.moveTo(x, y) else path1.lineTo(x, y)
            }

            drawPath(
                path = path1,
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        primaryColor.copy(alpha = 0.25f),
                        primaryColor,
                        tertiaryColor.copy(alpha = 0.8f),
                        primaryColor.copy(alpha = 0.25f),
                        Color.Transparent
                    )
                ),
                style = Stroke(
                    width = if (isPlaying) 3.5.dp.toPx() else 2.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            )

            // 3. Secondary harmonic sine wave ribbon (Violet / Amethyst)
            val path2 = Path()
            for (i in 0..points) {
                val ratio = i.toFloat() / points
                val x = ratio * width
                val normalizedX = ratio * 2f - 1f
                val envelope = (1f - normalizedX * normalizedX).coerceAtLeast(0f)
                val secondarySine = Math.sin((ratio * 4.2 * Math.PI - phase2)).toFloat()
                val subHarmonic = Math.cos((ratio * 2.1 * Math.PI + phase2 * 0.8)).toFloat() * 0.3f
                val y = midY + (secondarySine + subHarmonic) * (waveAmplitude * 0.78f) * envelope

                if (i == 0) path2.moveTo(x, y) else path2.lineTo(x, y)
            }

            drawPath(
                path = path2,
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        tertiaryColor.copy(alpha = 0.2f),
                        tertiaryColor,
                        primaryColor.copy(alpha = 0.7f),
                        tertiaryColor.copy(alpha = 0.2f),
                        Color.Transparent
                    )
                ),
                style = Stroke(
                    width = if (isPlaying) 2.6.dp.toPx() else 1.6.dp.toPx(),
                    cap = StrokeCap.Round
                )
            )

            // 4. Ambient Golden and Violet Glow Particles
            val particleCount = 14
            for (p in 0 until particleCount) {
                val pNormX = (p.toFloat() / (particleCount - 1)) * 1.8f - 0.9f // [-0.9..0.9]
                val pEnv = (1f - pNormX * pNormX).coerceAtLeast(0f)
                val pColor = if (p % 2 == 0) primaryColor else tertiaryColor

                val drift = Math.sin((phase1 * (0.8 + p * 0.1) + p * 1.2)).toFloat() * (if (isPlaying) 10f else 5f)
                val pY = midY + Math.sin((pNormX * 3.2 * Math.PI + phase1)).toFloat() * waveAmplitude * pEnv + drift
                val pX = ((pNormX + 1f) / 2f) * width

                val radius = (if (isPlaying) 2.6.dp else 1.8.dp).toPx()

                // Glowing halo
                drawCircle(
                    color = pColor.copy(alpha = if (isPlaying) 0.35f * ambientGlowPulse else 0.15f * ambientGlowPulse),
                    radius = radius * 2.4f,
                    center = Offset(pX, pY)
                )
                // Bright core
                drawCircle(
                    color = pColor.copy(alpha = if (isPlaying) 0.95f else 0.60f),
                    radius = radius,
                    center = Offset(pX, pY)
                )
            }
        }
    }
}

/**
 * Animated 3-bar equalizer shown during live audio playback.
 */
@Composable
fun LiveWaveEqualizer() {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val h1 by infiniteTransition.animateFloat(
        initialValue = 4f, targetValue = 18f,
        animationSpec = infiniteRepeatable(tween(300, easing = LinearEasing), RepeatMode.Reverse), label = "h1"
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 16f, targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(450, easing = LinearEasing), RepeatMode.Reverse), label = "h2"
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 8f, targetValue = 20f,
        animationSpec = infiniteRepeatable(tween(380, easing = LinearEasing), RepeatMode.Reverse), label = "h3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(20.dp)
    ) {
        Box(modifier = Modifier.width(3.dp).height(h1.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary))
        Box(modifier = Modifier.width(3.dp).height(h2.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.tertiary))
        Box(modifier = Modifier.width(3.dp).height(h3.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary))
    }
}

/**
 * Screen for pairing a preferred AI study partner and setting study focus.
 */
@Composable
fun OnboardingAiSelectionScreen(
    selectedAi: String,
    onSelectAi: (String) -> Unit,
    selectedFocus: String,
    onSelectFocus: (String) -> Unit
) {
    val aiAssistants = listOf(
        "gemini" to "Google Gemini",
        "chatgpt" to "ChatGPT",
        "claude" to "Claude",
        "copilot" to "Copilot",
        "perplexity" to "Perplexity",
        "grok" to "xAI Grok"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Pair Your AI Study Partner",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Choose your default assistant for instant study handoffs, summaries, quizzes, and vocabulary.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Official AI Assistants",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 2-column Grid of 6 AI Assistants
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            aiAssistants.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { (id, name) ->
                        val isSelected = selectedAi.equals(id, ignoreCase = true)
                        Card(
                            onClick = { onSelectAi(id) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = aiAssistantIcon(id),
                                    contentDescription = name,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Primary Study Focus",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AiStudyFocusOptions.forEach { option ->
                val isSelected = selectedFocus == option.id
                Card(
                    onClick = { onSelectFocus(option.id) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(option.iconEmoji, fontSize = 20.sp)
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Active",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

/**
 * Showcase screen highlighting Veritas's core reading features.
 */
@Composable
fun OnboardingFeatureShowcaseScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Your Reading Superpowers",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Here is what makes reading in Vern uniquely powerful.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OnboardingFeatureCard(
                icon = "📑",
                badge = "Dual-Engine",
                title = "Original Canvas & Text Studio",
                desc = "Toggle between original visual PDF layout and responsive reflowed text with sentence-by-sentence karaoke highlighting."
            )
            OnboardingFeatureCard(
                icon = "🗣️",
                badge = "Multi-App Handoff",
                title = "Translation & Spoken Read-Out",
                desc = "Send structured translation requests to your priority translation tools and listen to spoken read-out of both original and translated text."
            )
            OnboardingFeatureCard(
                icon = "📊",
                badge = "Private & Local",
                title = "On-Device Reading Insights",
                desc = "Track your daily streaks, monthly time heatmaps, and listening history with zero cloud tracking."
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

/**
 * Feature card used in the OnboardingFeatureShowcaseScreen.
 */
@Composable
fun OnboardingFeatureCard(
    icon: String,
    badge: String,
    title: String,
    desc: String
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(icon, fontSize = 28.sp)
                SuggestionChip(
                    onClick = {},
                    label = { Text(badge, style = MaterialTheme.typography.labelSmall) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.primary
                    ),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

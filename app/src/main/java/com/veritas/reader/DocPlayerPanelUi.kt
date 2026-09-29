package com.veritas.reader

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veritas.reader.ui.VeritasSleekSlider
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)

internal enum class DocPanelDragValue { Collapsed, Expanded }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DocPlayerPanel(
    isPlaying: Boolean,
    statusMessage: String,
    rate: Float,
    pitch: Float,
    fontSizeSp: Int,
    sectionSpacingDp: Int = 10,
    queueCount: Int,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onRateChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onSectionSpacingChange: (Int) -> Unit = {},
    onOpenVoiceStudio: () -> Unit,
    voices: List<TtsVoiceOption>,
    voiceSettings: VoiceSettings,
    onVoiceSelected: (TtsVoiceOption) -> Unit,
    onToggleTextMode: () -> Unit = {}
) {
    val density = LocalDensity.current
    val maxOffsetPx = with(density) { 265.dp.toPx() }

    @Suppress("DEPRECATION")
    val draggableState = remember {
        AnchoredDraggableState(
            initialValue = DocPanelDragValue.Collapsed,
            positionalThreshold = { distance: Float -> distance * 0.5f },
            velocityThreshold = { with(density) { 100.dp.toPx() } },
            snapAnimationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            decayAnimationSpec = androidx.compose.animation.core.exponentialDecay()
        )
    }
    val anchors = remember(maxOffsetPx) {
        DraggableAnchors {
            DocPanelDragValue.Collapsed at maxOffsetPx
            DocPanelDragValue.Expanded at 0f
        }
    }
    SideEffect { draggableState.updateAnchors(anchors) }

    val currentOffset = if (draggableState.offset.isNaN()) maxOffsetPx else draggableState.requireOffset()
    val progress = (1f - (currentOffset / maxOffsetPx)).coerceIn(0f, 1f)
    val heightDp = 72.dp + (218.dp * progress)

    val coroutineScope = rememberCoroutineScope()

    val availableVoices = remember(voices, voiceSettings.localeTag) {
        if (voiceSettings.localeTag.isBlank()) voices.take(8)
        else voices.filter { it.localeTag.equals(voiceSettings.localeTag, ignoreCase = true) }
    }

    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.surface.luminance() < 0.5f

    val gradientBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                blendColors(scheme.surface, scheme.primary, 0.12f).copy(alpha = 0.94f),
                blendColors(scheme.surface, androidx.compose.ui.graphics.Color.Black, 0.20f).copy(alpha = 0.96f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                blendColors(scheme.surface, scheme.primaryContainer, 0.35f).copy(alpha = 0.95f),
                blendColors(scheme.surface, scheme.primary, 0.08f).copy(alpha = 0.97f)
            )
        )
    }

    val borderBrush = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.22f),
                scheme.primary.copy(alpha = 0.32f),
                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.65f),
                scheme.primary.copy(alpha = 0.25f),
                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.30f)
            )
        )
    }

    val cornerRadius = androidx.compose.ui.unit.lerp(34.dp, 24.dp, progress)
    val capsuleShape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 580.dp)
                .fillMaxWidth()
                .height(heightDp)
                .anchoredDraggable(
                    state = draggableState,
                    orientation = Orientation.Vertical
                ),
            shape = capsuleShape,
            color = androidx.compose.ui.graphics.Color.Transparent,
            shadowElevation = if (isDark) 10.dp else 8.dp,
            tonalElevation = 0.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(brush = gradientBrush, shape = capsuleShape)
                    .border(width = 1.dp, brush = borderBrush, shape = capsuleShape)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
            // Header Row — visible in both Collapsed and Expanded state
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Tap to toggle expand / collapse
                TextButton(
                    onClick = onToggleTextMode,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    )
                                ),
                                RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.veritas_reader_icon),
                            contentDescription = "Switch to Text Mode",
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                // Playback controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onPrevious,
                        enabled = canGoPrevious
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.NavigateBefore,
                            contentDescription = "Previous",
                            tint = if (canGoPrevious) MaterialTheme.colorScheme.onSurface
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                    FilledTonalIconButton(onClick = onPlayPause) {
                        androidx.compose.animation.AnimatedContent(
                            targetState = isPlaying,
                            transitionSpec = {
                                (androidx.compose.animation.scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) +
                                    androidx.compose.animation.fadeIn(tween(150)))
                                    .togetherWith(androidx.compose.animation.fadeOut(tween(100)))
                            },
                            label = "originalPlayMorph"
                        ) { playing ->
                            Icon(
                                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (playing) "Pause" else "Play"
                            )
                        }
                    }
                    IconButton(
                        onClick = onNext,
                        enabled = canGoNext
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.NavigateNext,
                            contentDescription = "Next",
                            tint = if (canGoNext) MaterialTheme.colorScheme.onSurface
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }

                // Voice Studio shortcut (visible in header)
                TextButton(
                    onClick = onOpenVoiceStudio,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Voice ›", style = MaterialTheme.typography.labelMedium)
                }
            }

            // Expanded area — Speed, Pitch, Font, Voice picker
            if (progress > 0.05f) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .graphicsLayer { alpha = progress }
                        .verticalScroll(rememberScrollState())
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        statusMessage.ifBlank { if (isPlaying) "Now reading" else "Original View" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                    // Speed row
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Speed ${"%.2f".format(rate)}x",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                    .clickable {
                                        val next = (((rate - 0.05f) * 20f).roundToInt().toFloat() / 20f).coerceIn(0.5f, 2.5f)
                                        onRateChange(next)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("-", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                    .clickable {
                                        val next = (((rate + 0.05f) * 20f).roundToInt().toFloat() / 20f).coerceIn(0.5f, 2.5f)
                                        onRateChange(next)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("+", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    VeritasSleekSlider(
                        value = rate,
                        onValueChange = onRateChange,
                        valueRange = 0.5f..2.5f,
                        stepIncrement = 0.05f,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(Modifier.height(10.dp))

                    // Pitch & Font Size row
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Pitch ${"%.2f".format(pitch)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                            .clickable {
                                                val next = (((pitch - 0.05f) * 20f).roundToInt().toFloat() / 20f).coerceIn(0.7f, 1.4f)
                                                onPitchChange(next)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("-", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                            .clickable {
                                                val next = (((pitch + 0.05f) * 20f).roundToInt().toFloat() / 20f).coerceIn(0.7f, 1.4f)
                                                onPitchChange(next)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("+", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            VeritasSleekSlider(
                                value = pitch,
                                onValueChange = onPitchChange,
                                valueRange = 0.7f..1.4f,
                                stepIncrement = 0.05f
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Text size ${fontSizeSp}sp",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                            .clickable { onFontSizeChange((fontSizeSp - 1).coerceIn(10, 28)) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("-", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                            .clickable { onFontSizeChange((fontSizeSp + 1).coerceIn(10, 28)) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("+", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            VeritasSleekSlider(
                                value = fontSizeSp.toFloat(),
                                onValueChange = { onFontSizeChange(it.toInt().coerceIn(10, 28)) },
                                valueRange = 10f..28f,
                                steps = 17
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Paragraph Spacing Slider
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "Line Spacing ${sectionSpacingDp}dp",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                        .clickable { onSectionSpacingChange((sectionSpacingDp - 1).coerceIn(6, 24)) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("-", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                        .clickable { onSectionSpacingChange((sectionSpacingDp + 1).coerceIn(6, 24)) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("+", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }

                        // 1-tap spacing preset chips
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(8, 10, 14, 18, 22).forEach { spacingPreset ->
                                val isSelected = sectionSpacingDp == spacingPreset
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                        .clickable { onSectionSpacingChange(spacingPreset) }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "${spacingPreset}dp",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(2.dp))

                    VeritasSleekSlider(
                        value = sectionSpacingDp.toFloat(),
                        onValueChange = { onSectionSpacingChange(it.toInt().coerceIn(6, 24)) },
                        valueRange = 6f..24f,
                        steps = 17,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(Modifier.height(6.dp))

                    // Quick Voice Picker
                    if (availableVoices.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                        Text(
                            "Voice",
                            modifier = Modifier.padding(horizontal = 8.dp),
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            availableVoices.forEach { voice ->
                                val isSelected = voice.name == voiceSettings.voiceName
                                if (isSelected) {
                                    Button(
                                        onClick = { onVoiceSelected(voice) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(50),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text(voice.name, style = MaterialTheme.typography.labelMedium)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { onVoiceSelected(voice) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(50),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text(voice.name, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }

                    // Full Voice Studio link
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = onOpenVoiceStudio,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Voice Studio ›", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}
}
}


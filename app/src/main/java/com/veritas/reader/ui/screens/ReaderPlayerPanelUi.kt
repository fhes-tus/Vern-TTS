package com.veritas.reader.ui.screens

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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.StayCurrentLandscape
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.reader.BrandMark
import com.veritas.reader.LocalVeritasBackdrop
import com.veritas.reader.TtsVoiceOption
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.VoiceSettings
import com.veritas.reader.isDeviceGlassCapable
import com.veritas.reader.ui.OnboardingController
import com.veritas.reader.ui.rememberStepHaptics
import com.veritas.reader.ui.VeritasSleekSlider
import com.veritas.reader.veritasGlassBackdrop
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

internal enum class DragValue { Collapsed, Expanded }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PlayerPanel(
    isPlaying: Boolean,
    isBackgroundActive: Boolean,
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
    onStop: () -> Unit,
    onNext: () -> Unit,
    onRateChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onSectionSpacingChange: (Int) -> Unit = {},
    onOpenVoiceStudio: () -> Unit,
    onPlayQueue: () -> Unit,
    onOpenAudioMode: () -> Unit,
    voices: List<TtsVoiceOption>,
    voiceSettings: VoiceSettings,
    onVoiceSelected: (TtsVoiceOption) -> Unit,
    documentId: String? = null,
    onToggleDocumentMode: () -> Unit = {},
    pageIndex: Int = 0,
    pageCount: Int = 1,
    onPageSelected: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val maxOffsetPx = with(density) { 265.dp.toPx() } // expanded height diff

    @Suppress("DEPRECATION")
    val draggableState = remember {
        AnchoredDraggableState(
            initialValue = DragValue.Collapsed,
            positionalThreshold = { distance: Float -> distance * 0.5f },
            velocityThreshold = { with(density) { 100.dp.toPx() } },
            snapAnimationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            decayAnimationSpec = androidx.compose.animation.core.exponentialDecay()
        )
    }

    val anchors = remember(maxOffsetPx) {
        DraggableAnchors {
            DragValue.Collapsed at maxOffsetPx
            DragValue.Expanded at 0f
        }
    }

    SideEffect {
        draggableState.updateAnchors(anchors)
    }

    val currentOffset = if (draggableState.offset.isNaN()) maxOffsetPx else draggableState.requireOffset()
    val progress = (1f - (currentOffset / maxOffsetPx)).coerceIn(0f, 1f)
    val hasPageSlider = pageCount > 1
    // Sleek player capsule height with centered playback controls
    val heightDp = 52.dp + (224.dp * progress)

    val currentLocaleTag = voiceSettings.localeTag
    val availableVoices = remember(voices, currentLocaleTag) {
        if (currentLocaleTag.isBlank()) {
            voices.take(8)
        } else {
            voices.filter { it.localeTag.equals(currentLocaleTag, ignoreCase = true) }
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val coverBitmap by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, documentId) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            documentId?.let { com.veritas.reader.DocumentRepository(context).findDocument(it) }
                ?.let { com.veritas.reader.BookCoverLoader.forDocument(context, it) }
        }
    }

    val scheme = MaterialTheme.colorScheme
    val isGlass = VeritasPackStyle.glassChromeEnabled() && isDeviceGlassCapable() && LocalVeritasBackdrop.current != null
    val opaqueGlassFallback = VeritasPackStyle.currentPackId() == "liquid_glass" && !isGlass
    val playerBrush = androidx.compose.ui.graphics.SolidColor(VeritasPackStyle.playerSurfaceColor(scheme))
    val borderBrush = VeritasPackStyle.navigationBorderBrush(scheme)

    val cornerRadius = androidx.compose.ui.unit.lerp(28.dp, 22.dp, progress)
    val capsuleShape = RoundedCornerShape(cornerRadius)
    val bottomSafeInsets = WindowInsets.navigationBars.union(
        WindowInsets.displayCutout.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(bottomSafeInsets)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            if (hasPageSlider) {
                val sliderShape = ArcConnectedSliderShape(14.dp)
                val sliderGlassShape = RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp, bottomStart = 2.dp, bottomEnd = 2.dp)
                Surface(
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .fillMaxWidth(0.88f)
                        .height(18.dp)
                        .veritasGlassBackdrop(sliderGlassShape, isGlass, surfaceOpacity = .36f),
                    shape = sliderShape,
                    color = if (opaqueGlassFallback) scheme.surface.copy(alpha = 0.85f) else if (isGlass) androidx.compose.ui.graphics.Color.Transparent else scheme.surface.copy(alpha = 0.75f),
                    border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.35f)),
                    shadowElevation = 0.dp
                ) {
                    SleekPlayerPageSlider(
                        pageIndex = pageIndex,
                        pageCount = pageCount,
                        onPageSelected = onPageSelected,
                        isPlaying = isPlaying,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp)
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heightDp)
                    .veritasGlassBackdrop(capsuleShape, isGlass, surfaceOpacity = .36f)
                    .anchoredDraggable(
                        state = draggableState,
                        orientation = Orientation.Vertical
                    )
                    .onGloballyPositioned { OnboardingController.updateBounds("player_panel_header", it) },
                shape = capsuleShape,
                color = if (opaqueGlassFallback) scheme.surface else androidx.compose.ui.graphics.Color.Transparent,
                shadowElevation = if (isGlass) 0.dp else VeritasPackStyle.chromeElevation(VeritasPackStyle.currentPackId()),
                tonalElevation = 0.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (isGlass || opaqueGlassFallback) {
                                Modifier
                            } else {
                                Modifier
                                    .background(brush = playerBrush, shape = capsuleShape)
                                    .border(width = 1.dp, brush = borderBrush, shape = capsuleShape)
                            }
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalArrangement = Arrangement.Top
                    ) {
                        // Header Row (Slimmer Capsule Main Controls)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                        // Left: Book Cover / Veritas Mode Toggle & 1-tap Speed Cycler Pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = onToggleDocumentMode,
                                modifier = Modifier.size(34.dp)
                            ) {
                                if (coverBitmap != null) {
                                    Image(
                                        bitmap = requireNotNull(coverBitmap).asImageBitmap(),
                                        contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_switch_to_original_document),
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(6.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    BrandMark(compact = true)
                                }
                            }

                            // Speed cycle chip
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        val nextRate = when {
                                            rate < 0.95f -> 1.0f
                                            rate < 1.20f -> 1.25f
                                            rate < 1.45f -> 1.5f
                                            rate < 1.95f -> 2.0f
                                            else -> 0.75f
                                        }
                                        onRateChange(nextRate)
                                    }
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${"%.2f".format(rate).trimEnd('0').trimEnd('.')}x",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Center: Previous, Morphing Play/Pause, Next
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IconButton(
                                onClick = onPrevious,
                                enabled = canGoPrevious,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FastRewind,
                                    contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_previous),
                                    tint = if (canGoPrevious) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Morphing high-contrast Play/Pause circle
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .clickable { onPlayPause() },
                                contentAlignment = Alignment.Center
                            ) {
                                com.veritas.reader.PlaybackControlIcon(
                                    isPlaying,
                                    com.veritas.reader.PlaybackStateStore.isPreparingFor(documentId),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }

                            IconButton(
                                onClick = onNext,
                                enabled = canGoNext,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FastForward,
                                    contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_next),
                                    tint = if (canGoNext) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Right: Orientation Toggle & Expand Chevron
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            val config = androidx.compose.ui.platform.LocalConfiguration.current
                            val isLandscape = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                            IconButton(
                                onClick = {
                                    val activity = context as? android.app.Activity
                                    activity?.requestedOrientation = if (isLandscape) {
                                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    } else {
                                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    }
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = if (isLandscape) Icons.Filled.StayCurrentPortrait else Icons.Filled.StayCurrentLandscape,
                                    contentDescription = if (isLandscape) {
                                        com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_switch_to_portrait)
                                    } else {
                                        com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_switch_to_landscape)
                                    },
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        draggableState.animateTo(
                                            if (draggableState.currentValue == DragValue.Collapsed) DragValue.Expanded else DragValue.Collapsed
                                        )
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (draggableState.currentValue == DragValue.Expanded) {
                                        Icons.Filled.KeyboardArrowDown
                                    } else {
                                        Icons.Filled.KeyboardArrowUp
                                    },
                                    contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_expand_player_panel),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Expanded Area (Speed Presets, Pitch, Font size, Quick Voice Picker)
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
                                statusMessage,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

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
                                        com.veritas.reader.ui.screens.menuString(
                                            com.veritas.reader.R.string.l10n_dynamic_153,
                                            com.veritas.reader.ui.screens.localizedUiValue("%.2f".format(rate))
                                        ),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                        color = MaterialTheme.colorScheme.onSurface
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
                                            Text("-", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
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
                                            Text("+", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speedPreset ->
                                        val isSelected = kotlin.math.abs(rate - speedPreset) < 0.04f
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                                .clickable { onRateChange(speedPreset) }
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "${speedPreset}x",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
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

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            com.veritas.reader.ui.screens.menuString(
                                                com.veritas.reader.R.string.l10n_dynamic_154,
                                                com.veritas.reader.ui.screens.localizedUiValue("%.2f".format(pitch))
                                            ),
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                            color = MaterialTheme.colorScheme.onSurface
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
                                                    val next = (((pitch - 0.05f) * 20f).roundToInt().toFloat() / 20f).coerceIn(0.7f, 1.4f)
                                                    onPitchChange(next)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("-", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                                .clickable {
                                                    val next = (((pitch + 0.05f) * 20f).roundToInt().toFloat() / 20f).coerceIn(0.7f, 1.4f)
                                                    onPitchChange(next)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("+", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
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
                                        com.veritas.reader.ui.screens.menuString(
                                            com.veritas.reader.R.string.l10n_dynamic_155,
                                            com.veritas.reader.ui.screens.localizedUiValue(fontSizeSp)
                                        ),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                        color = MaterialTheme.colorScheme.onSurface
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
                                                .clickable { onFontSizeChange((fontSizeSp - 1).coerceIn(10, 28)) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("-", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                                .clickable { onFontSizeChange((fontSizeSp + 1).coerceIn(10, 28)) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("+", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
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
                                    com.veritas.reader.ui.screens.menuString(
                                        com.veritas.reader.R.string.l10n_dynamic_156,
                                        com.veritas.reader.ui.screens.localizedUiValue(sectionSpacingDp)
                                    ),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                    color = MaterialTheme.colorScheme.onSurface
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
                                            .clickable { onSectionSpacingChange((sectionSpacingDp - 1).coerceIn(6, 24)) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("-", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                            .clickable { onSectionSpacingChange((sectionSpacingDp + 1).coerceIn(6, 24)) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("+", style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }

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
                                            com.veritas.reader.ui.screens.menuString(
                                                com.veritas.reader.R.string.l10n_dynamic_157,
                                                com.veritas.reader.ui.screens.localizedUiValue(spacingPreset)
                                            ),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
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

                        if (availableVoices.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                            Text(
                                com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_voice_3091c84),
                                modifier = Modifier.padding(horizontal = 8.dp),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)
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
                                            shape = VeritasPackStyle.chipShape(),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text(voice.name, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp))
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = { onVoiceSelected(voice) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            shape = VeritasPackStyle.chipShape(),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text(voice.name, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp))
                                        }
                                    }
                                }
                            }
                        }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    enabled = queueCount > 0,
                                    onClick = onPlayQueue,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        if (queueCount == 0) {
                                            com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_queue_empty)
                                        } else {
                                            com.veritas.reader.ui.screens.menuString(
                                                com.veritas.reader.R.string.l10n_dynamic_158,
                                                com.veritas.reader.ui.screens.localizedUiValue(queueCount)
                                            )
                                        },
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                TextButton(
                                    onClick = onOpenVoiceStudio,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_voice_studio_4758e58),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}

/**
 * Curved shape for the top page slider that arcs outward at its ends onto the player capsule below.
 */
private class ArcConnectedSliderShape(
    private val filletWidth: androidx.compose.ui.unit.Dp = 14.dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val r = with(density) { filletWidth.toPx() }.coerceAtMost(size.width * 0.25f)
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(0f, h)
            cubicTo(
                x1 = r * 0.45f, y1 = h,
                x2 = r * 0.55f, y2 = 0f,
                x3 = r, y3 = 0f
            )
            lineTo(w - r, 0f)
            cubicTo(
                x1 = w - r + r * 0.45f, y1 = 0f,
                x2 = w - r * 0.45f, y2 = h,
                x3 = w, y3 = h
            )
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * Ultra-sleek page scrubber seamlessly merged into the top of the floating player capsule.
 * Provides live tactile scrubbing across book pages with zero dividing line between slider and player.
 */
@Composable
internal fun SleekPlayerPageSlider(
    pageIndex: Int,
    pageCount: Int,
    onPageSelected: (Int) -> Unit,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (pageCount <= 1) return
    val density = LocalDensity.current
    val tickingPageSelect = rememberStepHaptics(pageIndex, onPageSelected)
    val scheme = MaterialTheme.colorScheme

    val infiniteTransition = rememberInfiniteTransition(label = "pageSliderWavy")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pageWavePhase"
    )
    val targetAmplitudeDp = if (isPlaying) 4.0.dp else 0.5.dp
    val animatedAmplitudePx by animateFloatAsState(
        targetValue = with(density) { targetAmplitudeDp.toPx() },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "pageWavyAmplitude"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(16.dp)
            .padding(horizontal = 4.dp)
            .semantics {
                contentDescription = "Page position: ${pageIndex + 1} of $pageCount"
                progressBarRangeInfo = ProgressBarRangeInfo(
                    (pageIndex + 1).toFloat(),
                    1f..pageCount.toFloat(),
                    (pageCount - 2).coerceAtLeast(0)
                )
                setProgress { requested ->
                    tickingPageSelect((requested.roundToInt() - 1).coerceIn(0, pageCount - 1))
                    true
                }
            }
            .pointerInput(pageCount) {
                detectTapGestures { offset ->
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    tickingPageSelect(((offset.x / width).coerceIn(0f, 1f) * (pageCount - 1)).roundToInt())
                }
            }
            .pointerInput(pageCount) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        tickingPageSelect(((offset.x / width).coerceIn(0f, 1f) * (pageCount - 1)).roundToInt())
                    },
                    onDrag = { change, _ ->
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        tickingPageSelect(((change.position.x / width).coerceIn(0f, 1f) * (pageCount - 1)).roundToInt())
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val progress = (pageIndex.toFloat() / (pageCount - 1).toFloat()).coerceIn(0f, 1f)
        val thumbSize = 10.dp
        val thumbPx = with(density) { thumbSize.toPx() }
        val trackWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(thumbPx)
        val usableWidthPx = (trackWidthPx - thumbPx).coerceAtLeast(1f)
        val thumbOffsetXPx = (usableWidthPx * progress).roundToInt()

        // Background track: subtle sleek rounded pill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.5.dp)
                .clip(CircleShape)
                .background(scheme.onSurface.copy(alpha = 0.20f))
        )

        // Active track reaching the center of thumb (with animated wavy seekbar effect)
        val activeWidthPx = (thumbPx / 2f + usableWidthPx * progress).coerceIn(0f, trackWidthPx)
        if (activeWidthPx > 0f) {
            val trackHeightPx = with(density) { 2.5.dp.toPx() }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
            ) {
                val yCenter = size.height / 2f
                // Solid straight active baseline
                drawLine(
                    color = scheme.primary,
                    start = Offset(0f, yCenter),
                    end = Offset(activeWidthPx, yCenter),
                    strokeWidth = trackHeightPx,
                    cap = StrokeCap.Round
                )

                // Three harmonic resonant strands floating and undulating ABOVE the baseline
                if (activeWidthPx > thumbPx * 0.5f) {
                    val wavelength1 = 26.dp.toPx()
                    val wavelength2 = 36.dp.toPx()
                    val wavelength3 = 18.dp.toPx()
                    val k1 = (2 * Math.PI / wavelength1).toFloat()
                    val k2 = (2 * Math.PI / wavelength2).toFloat()
                    val k3 = (2 * Math.PI / wavelength3).toFloat()

                    val taperDistance = 10.dp.toPx().coerceAtMost(activeWidthPx * 0.35f)

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
                        color = scheme.primary.copy(alpha = 0.40f),
                        style = Stroke(width = 1.0.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Strand 2: warm secondary harmonic
                    drawPath(
                        path = path2,
                        color = scheme.primary.copy(alpha = 0.65f),
                        style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Strand 1: crisp primary wave
                    drawPath(
                        path = path1,
                        color = scheme.primary,
                        style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }

        // Glowing outer halo
        val haloExtra = thumbPx * 0.35f
        val haloSizeDp = with(density) { (thumbPx + haloExtra * 2f).toDp() }
        val haloOffsetDp = with(density) { (thumbOffsetXPx - haloExtra).toDp() }
        Box(
            modifier = Modifier
                .offset(x = haloOffsetDp)
                .size(haloSizeDp)
                .background(
                    color = scheme.primary.copy(alpha = 0.22f),
                    shape = CircleShape
                )
        )

        // Sleek circular thumb with clear border
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffsetXPx, 0) }
                .size(thumbSize)
                .clip(CircleShape)
                .background(scheme.primary)
                .border(1.5.dp, scheme.surface, CircleShape)
        )
    }
}
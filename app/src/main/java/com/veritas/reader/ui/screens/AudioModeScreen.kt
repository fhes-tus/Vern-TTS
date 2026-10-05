package com.veritas.reader.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.reader.R
import com.veritas.reader.ReaderMode
import com.veritas.reader.ReaderModeToggle
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.ui.pressScale
import com.veritas.reader.ui.rememberVeritasHaptics
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

@Composable
fun AudioModeScreen(
    title: String,
    currentIndex: Int,
    totalChunks: Int,
    currentSentence: String,
    isPlaying: Boolean,
    coverFile: File? = null,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onDismiss: () -> Unit,
    isBookmarked: Boolean = false,
    onToggleBookmark: () -> Unit = {},
    rate: Float = 1.0f,
    onRateChange: (Float) -> Unit = {},
    readerMode: ReaderMode = ReaderMode.LISTEN,
    onReaderModeChange: (ReaderMode) -> Unit = {},
    hasCanvas: Boolean = false,
    documentChunks: List<String> = emptyList(),
    onSentenceClick: (Int) -> Unit = {},
    onOpenSleepTimer: () -> Unit = {},
    onOpenVoiceStudio: () -> Unit = {},
    onOpenNarrationStudio: () -> Unit = {},
    onExportAudio: () -> Unit = {}
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val contentColor = MaterialTheme.colorScheme.onBackground
    val primaryColor = MaterialTheme.colorScheme.primary
    val haptic = rememberVeritasHaptics()

    // ── Real-time elapsed tracker (Pre-Phase Item 1) ──
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    var baseElapsedAtLastChange by remember { mutableLongStateOf(0L) }
    var lastIndexChangeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showRead by remember { mutableStateOf(true) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            lastIndexChangeMillis =
                System.currentTimeMillis() - (elapsedSeconds - baseElapsedAtLastChange) * 1_000L
            while (true) {
                delay(1_000L)
                elapsedSeconds = baseElapsedAtLastChange +
                    (System.currentTimeMillis() - lastIndexChangeMillis) / 1_000L
            }
        }
    }

    LaunchedEffect(currentIndex) {
        baseElapsedAtLastChange = elapsedSeconds
        lastIndexChangeMillis = System.currentTimeMillis()
    }

    val avgSecondsPerSentence = if (currentIndex > 0) elapsedSeconds.toFloat() / currentIndex else 3.0f
    val remainingSentences = (totalChunks - currentIndex).coerceAtLeast(0)
    val estimatedRemainingSeconds = (remainingSentences * avgSecondsPerSentence).toLong()
    val totalEstimatedSeconds = elapsedSeconds + estimatedRemainingSeconds

    val progress = if (totalChunks > 0) currentIndex.toFloat() / totalChunks else 0f
    val animatedProgress by animateFloatAsState(targetValue = progress, animationSpec = com.veritas.reader.ui.VeritasMotion.spatialSlow())

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = backgroundColor,
        contentColor = contentColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .pointerInput(Unit) {
                    var dragAccumulator = 0f
                    detectDragGestures(
                        onDragEnd = { dragAccumulator = 0f },
                        onDragCancel = { dragAccumulator = 0f }
                    ) { change, dragAmount ->
                        change.consume()
                        dragAccumulator += dragAmount.x
                        if (dragAccumulator > 150) {
                            onPrevious()
                            dragAccumulator = 0f
                        } else if (dragAccumulator < -150) {
                            onNext()
                            dragAccumulator = 0f
                        }
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Header with mode toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = contentColor
                    )
                }
                ReaderModeToggle(
                    currentMode = readerMode,
                    onModeSelected = onReaderModeChange,
                    hasCanvas = hasCanvas,
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                )
                
                var toolsExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { toolsExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = contentColor
                        )
                    }
                    DropdownMenu(
                        expanded = toolsExpanded,
                        onDismissRequest = { toolsExpanded = false },
                        modifier = Modifier.width(220.dp)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Sleep timer") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Timer,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                toolsExpanded = false
                                onOpenSleepTimer()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Voice & language") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.RecordVoiceOver,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                toolsExpanded = false
                                onOpenVoiceStudio()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Narration mode") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.TheaterComedy,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                toolsExpanded = false
                                onOpenNarrationStudio()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export audio") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.FileDownload,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                toolsExpanded = false
                                onExportAudio()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Player / Read Sub-toggle
            Row(
                modifier = Modifier
                    .background(contentColor.copy(alpha = 0.06f), VeritasPackStyle.chipShape())
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val playerBg = if (!showRead) contentColor.copy(alpha = 0.15f) else Color.Transparent
                val playerTextColor = if (!showRead) contentColor else contentColor.copy(alpha = 0.5f)
                Box(
                    modifier = Modifier
                        .clip(VeritasPackStyle.chipShape())
                        .background(playerBg)
                        .clickable { showRead = false }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text("Player", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = playerTextColor)
                }

                val readBg = if (showRead) contentColor.copy(alpha = 0.15f) else Color.Transparent
                val readTextColor = if (showRead) contentColor else contentColor.copy(alpha = 0.5f)
                Box(
                    modifier = Modifier
                        .clip(VeritasPackStyle.chipShape())
                        .background(readBg)
                        .clickable { showRead = true }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text("Read", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = readTextColor)
                }
            }

            // 2. Center Content: Read List or Cover Art
            if (showRead) {
                val readListState = rememberLazyListState()

                LaunchedEffect(currentIndex) {
                    if (currentIndex in documentChunks.indices) {
                        // Wait for a valid measured viewport height
                        var viewportHeight = 0
                        var attempts = 0
                        while (viewportHeight <= 0 && attempts < 15) {
                            val layoutInfo = readListState.layoutInfo
                            viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                            if (viewportHeight <= 0) {
                                delay(30)
                            }
                            attempts++
                        }
                        if (viewportHeight > 0) {
                            // Anchor the active sentence's TOP in the upper fifth of the
                            // viewport (fixed top anchor, not centering) so the line being
                            // read sits high with one faded line of context above it and the
                            // rest of the upcoming text below — consistent for short and tall
                            // multi-line sentences.
                            val offset = -(viewportHeight * 0.10f).toInt()
                            readListState.animateScrollToItem(currentIndex, offset)
                        } else {
                            // Fallback to average offset if measurement still pending
                            readListState.animateScrollToItem(currentIndex, -90)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    LazyColumn(
                        state = readListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 8.dp),
                        contentPadding = PaddingValues(vertical = 140.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        itemsIndexed(documentChunks) { index, sentence ->
                            val isActive = index == currentIndex
                            // YouTube-Music-style lyric emphasis: the active line fades IN slowly
                            // with a decelerating ease and a subtle upward rise, while lines
                            // fading OUT dim faster so attention lands on the new sentence.
                            val alpha by animateFloatAsState(
                                targetValue = if (isActive) 1.0f else 0.35f,
                                animationSpec = if (isActive) com.veritas.reader.ui.VeritasMotion.effectsSlow()
                                    else com.veritas.reader.ui.VeritasMotion.effectsFast(),
                                label = "sentenceAlpha"
                            )
                            val fontSizeValue by animateFloatAsState(
                                targetValue = if (isActive) 19f else 15f,
                                animationSpec = com.veritas.reader.ui.VeritasMotion.spatial(),
                                label = "sentenceSize"
                            )
                            val lift by animateFloatAsState(
                                targetValue = if (isActive) 0f else 1f,
                                animationSpec = com.veritas.reader.ui.VeritasMotion.spatialSlow(),
                                label = "sentenceLift"
                            )
                            val fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                            val fontSize = fontSizeValue.sp
                            val textColor = contentColor.copy(alpha = alpha)

                            val sentenceInteraction = remember { MutableInteractionSource() }
                            Text(
                                text = sentence,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = fontSize,
                                    fontWeight = fontWeight,
                                    lineHeight = fontSize * 1.4f
                                ),
                                color = textColor,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                                    .graphicsLayer { translationY = lift * 6.dp.toPx() }
                                    .pressScale(sentenceInteraction, pressedScale = 0.98f)
                                    .clickable(
                                        interactionSource = sentenceInteraction,
                                        indication = null
                                    ) {
                                        onSentenceClick(index)
                                    }
                            )
                        }
                    }

                    // Top fading gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        backgroundColor,
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // Bottom fading gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        backgroundColor
                                    )
                                )
                            )
                    )
                }
            } else {
                // Cover image + Circular progress
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(280.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxSize(),
                            color = primaryColor,
                            strokeWidth = 6.dp,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        val coverBitmap = remember(coverFile?.absolutePath) {
                            coverFile?.takeIf { it.exists() }?.let { file ->
                                runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
                            }
                        }
                        if (coverBitmap != null) {
                            Image(
                                bitmap = coverBitmap.asImageBitmap(),
                                contentDescription = "Document cover",
                                modifier = Modifier
                                    .size(242.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.veritas_reader_icon),
                                contentDescription = "Cover",
                                modifier = Modifier
                                    .size(242.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }

            // 3. Sentence details + Ticker (Smooth per-second elapsed / dynamic total estimation)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (readerMode != ReaderMode.TEXT && !showRead) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = currentSentence,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = contentColor.copy(alpha = 0.8f),
                        modifier = Modifier.heightIn(min = 72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // Time progress row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = formatTime(elapsedSeconds),
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.6f)
                    )
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.weight(1f),
                        color = primaryColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        text = formatTime(totalEstimatedSeconds),
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.6f)
                    )
                }
            }

            // 4. Fine-tuning row (Speed and Bookmarks)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Speed cycle pill
                var speedDropdownExpanded by remember { mutableStateOf(false) }
                Box {
                    Surface(
                        shape = VeritasPackStyle.chipShape(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clickable {
                                speedDropdownExpanded = true
                            }
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.2fx", rate),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = speedDropdownExpanded,
                        onDismissRequest = { speedDropdownExpanded = false },
                        modifier = Modifier.width(200.dp)
                    ) {
                        Text(
                            text = "Playback Speed",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        HorizontalDivider()
                        
                        // Fine adjustments row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { onRateChange((rate - 0.05f).coerceIn(0.5f, 3.0f)) }) {
                                Text("-", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = String.format(Locale.US, "%.2fx", rate),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { onRateChange((rate + 0.05f).coerceIn(0.5f, 3.0f)) }) {
                                Text("+", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        HorizontalDivider()
                        
                        // Preset options
                        listOf(0.8f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                            DropdownMenuItem(
                                text = { Text("${speed}x") },
                                onClick = {
                                    onRateChange(speed)
                                    speedDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Right Bookmark button
                IconButton(
                    onClick = {
                        haptic.select()
                        onToggleBookmark()
                    }
                ) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkAdd,
                        contentDescription = if (isBookmarked) "Remove Bookmark" else "Bookmark Sentence",
                        tint = if (isBookmarked) MaterialTheme.colorScheme.primary else contentColor.copy(alpha = 0.8f),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // 5. Main Playback Controls (Vector Icons)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AudioModeButton(
                    icon = Icons.Default.SkipPrevious,
                    contentDescription = "Previous sentence",
                    size = 72.dp,
                    iconSize = 32.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = contentColor,
                    onClick = {
                        haptic.select()
                        onPrevious()
                    }
                )

                AudioModeButton(
                    icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    size = 88.dp,
                    iconSize = 44.dp,
                    color = primaryColor,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    onClick = {
                        haptic.toggle(!isPlaying)
                        onPlayPause()
                    }
                )

                AudioModeButton(
                    icon = Icons.Default.SkipNext,
                    contentDescription = "Next sentence",
                    size = 72.dp,
                    iconSize = 32.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = contentColor,
                    onClick = {
                        haptic.select()
                        onNext()
                    }
                )
            }
        }
    }
}

private fun formatTime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%02d:%02d", m, s)
    }
}

@Composable
fun AudioModeButton(
    icon: ImageVector,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    color: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val iconMotion = com.veritas.reader.ui.VeritasMotion.spatialFast<Float>()
    val iconFade = com.veritas.reader.ui.VeritasMotion.effectsFast<Float>()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !com.veritas.reader.ui.VeritasMotion.scheme.reduceMotion) 0.97f else 1f,
        animationSpec = com.veritas.reader.ui.VeritasMotion.spatialFast(),
        label = "audioModeButtonScale"
    )

    Surface(
        shape = CircleShape,
        color = color,
        contentColor = contentColor,
        modifier = Modifier
            .size(size)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Same springy scale-morph the home hero uses, so play↔pause feels
            // consistent across every player surface.
            AnimatedContent(
                targetState = icon,
                transitionSpec = {
                    (scaleIn(iconMotion, initialScale = .9f) + fadeIn(iconFade))
                        .togetherWith(fadeOut(iconFade)).using(null)
                },
                label = "audioModeIconMorph"
            ) { targetIcon ->
                Icon(
                    imageVector = targetIcon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(iconSize),
                    tint = contentColor
                )
            }
        }
    }
}

package com.veritas.reader.ui.screens

import com.veritas.reader.ui.NotesPaperTemplate

import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.provider.MediaStore
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.util.Locale

@Composable
internal fun NoteLiveRecordingBar(
    recordingDurationSec: Int,
    recordingAmplitudes: List<Int>,
    onCancel: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = com.veritas.reader.VeritasPackStyle.cardShape(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "recPulse")
                val pulse by infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(700, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "recPulseVal"
                )
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(20.dp)) {
                    Box(
                        modifier = Modifier
                            .size((16 * pulse).dp)
                            .background(Color.Red.copy(alpha = 0.35f * pulse), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .background(Color.Red, CircleShape)
                    )
                }
                val timerFormatted = String.format(
                    Locale.US,
                    "%02d:%02d",
                    recordingDurationSec / 60,
                    recordingDurationSec % 60
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Recording Voice Memo",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = timerFormatted,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Discard",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = com.veritas.reader.VeritasPackStyle.chipShape(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Insert", style = MaterialTheme.typography.labelMedium)
                }
            }

            // Live audio waveform amplitude visualization using smooth Canvas bars
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                val primaryColor = MaterialTheme.colorScheme.primary
                val amps = recordingAmplitudes
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasW = size.width
                    val canvasH = size.height
                    val barW = 2.5.dp.toPx()
                    val barGap = 2.dp.toPx()
                    val step = barW + barGap
                    val count = (canvasW / step).toInt().coerceAtLeast(10)
                    val recentAmps = if (amps.size > count) amps.takeLast(count) else amps
                    val padCount = (count - recentAmps.size).coerceAtLeast(0)
                    val fullAmps = List(padCount) { 0 } + recentAmps
                    val maxAmp = (fullAmps.maxOrNull() ?: 1).coerceAtLeast(1)
                    val centerY = canvasH / 2f
                    val startX = (canvasW - (count * step - barGap)) / 2f

                    for (i in 0 until count) {
                        val amp = fullAmps[i]
                        val norm = (amp.toFloat() / maxAmp.toFloat()).coerceIn(0.08f, 1f)
                        val barH = (norm * (canvasH - 4.dp.toPx())).coerceAtLeast(3.dp.toPx())
                        val halfH = barH / 2f
                        val x = startX + i * step + barW / 2f
                        drawLine(
                            color = primaryColor,
                            start = Offset(x, centerY - halfH),
                            end = Offset(x, centerY + halfH),
                            strokeWidth = barW,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun NoteImageBlockItem(
    block: NoteBlock.Image,
    index: Int,
    totalBlocks: Int,
    onCardColor: Color,
    onViewImage: (String) -> Unit,
    onCopyAttachment: (String) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap = remember(block.path) {
        loadNoteBitmap(context, block.path)
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clickable { onViewImage(block.path) },
        shape = com.veritas.reader.VeritasPackStyle.compactShape(),
        colors = CardDefaults.cardColors(containerColor = onCardColor.copy(alpha = 0.05f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Inline Note Image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error loading image", color = onCardColor)
                }
            }
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = com.veritas.reader.VeritasPackStyle.chipShape(),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Filled.Visibility,
                        contentDescription = "View image",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Text("Tap to view", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                NoteAttachmentMenu(
                    onCopy = { onCopyAttachment(block.path) },
                    canMoveUp = index > 0,
                    onMoveUp = { onMoveUp(index) },
                    canMoveDown = index < totalBlocks - 1,
                    onMoveDown = { onMoveDown(index) },
                    onDelete = { onRemove(index) }
                )
            }
        }
    }
}

@Composable
internal fun NoteAudioBlockItem(
    block: NoteBlock.Audio,
    index: Int,
    totalBlocks: Int,
    isPlaying: Boolean,
    activePlayingAudioPath: String?,
    playProgress: Float,
    currentPosition: String,
    onTogglePlay: (String) -> Unit,
    onSeek: (Float, String) -> Unit,
    onCopyAttachment: (String) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val audioPath = block.path
    val isThisPlaying = isPlaying && activePlayingAudioPath == audioPath
    val memoTotalDur = remember(audioPath) {
        try {
            val file = File(audioPath)
            if (file.exists()) {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(audioPath)
                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val durMs = durStr?.toLongOrNull() ?: 0L
                val totalSec = durMs / 1000
                retriever.release()
                String.format(Locale.US, "%d:%02d", totalSec / 60, totalSec % 60)
            } else "0:00"
        } catch (_: Exception) {
            "0:00"
        }
    }
    val dynamicDurationLabel = if (isThisPlaying) "$currentPosition / $memoTotalDur" else memoTotalDur

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AudioVoiceMemoWaveform(
            durationLabel = dynamicDurationLabel,
            isPlaying = isThisPlaying,
            progress = if (isThisPlaying) playProgress else 0f,
            onTogglePlay = { onTogglePlay(audioPath) },
            onSeek = { fraction -> onSeek(fraction, audioPath) },
            modifier = Modifier.weight(1f)
        )
        NoteAttachmentMenu(
            onCopy = { onCopyAttachment(audioPath) },
            canMoveUp = index > 0,
            onMoveUp = { onMoveUp(index) },
            canMoveDown = index < totalBlocks - 1,
            onMoveDown = { onMoveDown(index) },
            onDelete = { onRemove(index) }
        )
    }
}

@Composable
internal fun NoteVideoBlockItem(
    block: NoteBlock.Video,
    index: Int,
    totalBlocks: Int,
    onCardColor: Color,
    onViewVideo: (String) -> Unit,
    onCopyAttachment: (String) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val videoPath = block.path
    val videoThumbnail = remember(videoPath) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                ThumbnailUtils.createVideoThumbnail(
                    File(videoPath),
                    android.util.Size(320, 240),
                    null
                )
            } else {
                @Suppress("DEPRECATION")
                ThumbnailUtils.createVideoThumbnail(
                    videoPath,
                    MediaStore.Images.Thumbnails.MINI_KIND
                )
            }
        } catch (_: Exception) {
            null
        }
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(190.dp)
            .clickable { onViewVideo(videoPath) },
        shape = com.veritas.reader.VeritasPackStyle.compactShape(),
        colors = CardDefaults.cardColors(containerColor = onCardColor.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, onCardColor.copy(alpha = 0.2f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (videoThumbnail != null) {
                Image(
                    bitmap = videoThumbnail.asImageBitmap(),
                    contentDescription = "Video Thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Movie,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            // Play overlay button
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier
                    .size(52.dp)
                    .align(Alignment.Center)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Play Video",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = com.veritas.reader.VeritasPackStyle.chipShape(),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Filled.PlayCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Text("Tap to play", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                NoteAttachmentMenu(
                    onCopy = { onCopyAttachment(videoPath) },
                    canMoveUp = index > 0,
                    onMoveUp = { onMoveUp(index) },
                    canMoveDown = index < totalBlocks - 1,
                    onMoveDown = { onMoveDown(index) },
                    onDelete = { onRemove(index) }
                )
            }
        }
    }
}

@Composable
internal fun NoteFileBlockItem(
    block: NoteBlock.File,
    index: Int,
    totalBlocks: Int,
    onCardColor: Color,
    onOpenFile: (String) -> Unit,
    onShareFile: (String) -> Unit,
    onCopyAttachment: (String) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val fileName = block.fileName.ifBlank { File(block.path).name }
    val sizeText = remember(block.sizeBytes, block.path) {
        val size = if (block.sizeBytes > 0L) block.sizeBytes else runCatching { File(block.path).length() }.getOrDefault(0L)
        if (size <= 0L) "Document"
        else {
            val kb = size / 1024.0
            val mb = kb / 1024.0
            if (mb >= 1.0) String.format(Locale.US, "%.1f MB", mb)
            else String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
        }
    }
    val ext = remember(fileName) {
        val dot = fileName.lastIndexOf('.')
        if (dot >= 0 && dot < fileName.length - 1) fileName.substring(dot + 1).uppercase() else "FILE"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpenFile(block.path) },
        shape = com.veritas.reader.VeritasPackStyle.compactShape(),
        colors = CardDefaults.cardColors(containerColor = onCardColor.copy(alpha = 0.06f)),
        border = BorderStroke(1.dp, onCardColor.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = com.veritas.reader.VeritasPackStyle.compactShape(),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = ext.take(4),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = onCardColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = sizeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = onCardColor.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "• Tap to open",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(
                    onClick = { onShareFile(block.path) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Share file",
                        tint = onCardColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                NoteAttachmentMenu(
                    onCopy = { onCopyAttachment(block.path) },
                    canMoveUp = index > 0,
                    onMoveUp = { onMoveUp(index) },
                    canMoveDown = index < totalBlocks - 1,
                    onMoveDown = { onMoveDown(index) },
                    onDelete = { onRemove(index) }
                )
            }
        }
    }
}

@Composable
internal fun NotesBlocksList(
    blocks: List<NoteBlock>,
    isChecklist: Boolean,
    items: SnapshotStateList<Pair<Boolean, TextFieldValue>>,
    focusRequesters: MutableMap<Int, FocusRequester>,
    onCardColor: Color,
    richTextTransformation: VisualTransformation,
    editVersion: Int,
    isPlaying: Boolean,
    activePlayingAudioPath: String?,
    playProgress: Float,
    currentPosition: String,
    onTextBlockChange: (index: Int, block: NoteBlock.Text, processed: TextFieldValue) -> Unit,
    onTextBlockFocus: (index: Int, block: NoteBlock.Text) -> Unit,
    onTextBlockBackspaceAtStart: (index: Int) -> Boolean,
    onViewImage: (String) -> Unit,
    onViewVideo: (String) -> Unit,
    onOpenFile: (String) -> Unit = {},
    onShareFile: (String) -> Unit = {},
    onCopyAttachment: (String) -> Unit,
    onMoveBlockUp: (Int) -> Unit,
    onMoveBlockDown: (Int) -> Unit,
    onRemoveBlock: (Int) -> Unit,
    onTogglePlayAudio: (String) -> Unit,
    onSeekAudio: (Float, String) -> Unit,
    onChecklistChanged: () -> Unit,
    moveCheckedToBottom: Boolean = true,
    addNewItemsToTop: Boolean = false,
    paperTemplate: NotesPaperTemplate = NotesPaperTemplate.BLANK,
    hasLinkPreviews: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (!isChecklist) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            blocks.forEachIndexed { index, block ->
                when (block) {
                    is NoteBlock.Text -> {
                        NoteTextBlockItem(
                            block = block,
                            onValueChange = { processed -> onTextBlockChange(index, block, processed) },
                            onFocus = { onTextBlockFocus(index, block) },
                            onBackspaceAtStart = { onTextBlockBackspaceAtStart(index) },
                            visualTransformation = richTextTransformation,
                            onCardColor = onCardColor,
                            isOnlyBlock = blocks.size == 1 && !hasLinkPreviews,
                            paperTemplate = paperTemplate
                        )
                    }
                    is NoteBlock.Image -> {
                        NoteImageBlockItem(
                            block = block,
                            index = index,
                            totalBlocks = blocks.size,
                            onCardColor = onCardColor,
                            onViewImage = onViewImage,
                            onCopyAttachment = onCopyAttachment,
                            onMoveUp = onMoveBlockUp,
                            onMoveDown = onMoveBlockDown,
                            onRemove = onRemoveBlock
                        )
                    }
                    is NoteBlock.Audio -> {
                        NoteAudioBlockItem(
                            block = block,
                            index = index,
                            totalBlocks = blocks.size,
                            isPlaying = isPlaying,
                            activePlayingAudioPath = activePlayingAudioPath,
                            playProgress = playProgress,
                            currentPosition = currentPosition,
                            onTogglePlay = onTogglePlayAudio,
                            onSeek = onSeekAudio,
                            onCopyAttachment = onCopyAttachment,
                            onMoveUp = onMoveBlockUp,
                            onMoveDown = onMoveBlockDown,
                            onRemove = onRemoveBlock
                        )
                    }
                    is NoteBlock.Video -> {
                        NoteVideoBlockItem(
                            block = block,
                            index = index,
                            totalBlocks = blocks.size,
                            onCardColor = onCardColor,
                            onViewVideo = onViewVideo,
                            onCopyAttachment = onCopyAttachment,
                            onMoveUp = onMoveBlockUp,
                            onMoveDown = onMoveBlockDown,
                            onRemove = onRemoveBlock
                        )
                    }
                    is NoteBlock.File -> {
                        NoteFileBlockItem(
                            block = block,
                            index = index,
                            totalBlocks = blocks.size,
                            onCardColor = onCardColor,
                            onOpenFile = onOpenFile,
                            onShareFile = onShareFile,
                            onCopyAttachment = onCopyAttachment,
                            onMoveUp = onMoveBlockUp,
                            onMoveDown = onMoveBlockDown,
                            onRemove = onRemoveBlock
                        )
                    }
                }
            }
        }
    } else {
        val mediaBlocks = remember(blocks, editVersion) { blocks.filter { it !is NoteBlock.Text } }
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (mediaBlocks.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    mediaBlocks.forEach { mediaBlock ->
                        val index = blocks.indexOf(mediaBlock)
                        when (mediaBlock) {
                            is NoteBlock.Image -> {
                                NoteImageBlockItem(
                                    block = mediaBlock,
                                    index = index,
                                    totalBlocks = blocks.size,
                                    onCardColor = onCardColor,
                                    onViewImage = onViewImage,
                                    onCopyAttachment = onCopyAttachment,
                                    onMoveUp = onMoveBlockUp,
                                    onMoveDown = onMoveBlockDown,
                                    onRemove = onRemoveBlock
                                )
                            }
                            is NoteBlock.Audio -> {
                                NoteAudioBlockItem(
                                    block = mediaBlock,
                                    index = index,
                                    totalBlocks = blocks.size,
                                    isPlaying = isPlaying,
                                    activePlayingAudioPath = activePlayingAudioPath,
                                    playProgress = playProgress,
                                    currentPosition = currentPosition,
                                    onTogglePlay = onTogglePlayAudio,
                                    onSeek = onSeekAudio,
                                    onCopyAttachment = onCopyAttachment,
                                    onMoveUp = onMoveBlockUp,
                                    onMoveDown = onMoveBlockDown,
                                    onRemove = onRemoveBlock
                                )
                            }
                            is NoteBlock.Video -> {
                                NoteVideoBlockItem(
                                    block = mediaBlock,
                                    index = index,
                                    totalBlocks = blocks.size,
                                    onCardColor = onCardColor,
                                    onViewVideo = onViewVideo,
                                    onCopyAttachment = onCopyAttachment,
                                    onMoveUp = onMoveBlockUp,
                                    onMoveDown = onMoveBlockDown,
                                    onRemove = onRemoveBlock
                                )
                            }
                            is NoteBlock.File -> {
                                NoteFileBlockItem(
                                    block = mediaBlock,
                                    index = index,
                                    totalBlocks = blocks.size,
                                    onCardColor = onCardColor,
                                    onOpenFile = onOpenFile,
                                    onShareFile = onShareFile,
                                    onCopyAttachment = onCopyAttachment,
                                    onMoveUp = onMoveBlockUp,
                                    onMoveDown = onMoveBlockDown,
                                    onRemove = onRemoveBlock
                                )
                            }
                            else -> {}
                        }
                    }
                }
            }
            NoteChecklistSection(
                items = items,
                focusRequesters = focusRequesters,
                onCardColor = onCardColor,
                onChecklistChanged = onChecklistChanged,
                moveCheckedToBottom = moveCheckedToBottom,
                addNewItemsToTop = addNewItemsToTop,
                paperTemplate = paperTemplate
            )
        }
    }
}


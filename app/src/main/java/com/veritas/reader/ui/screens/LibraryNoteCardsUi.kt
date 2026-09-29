package com.veritas.reader.ui.screens


import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veritas.reader.ReaderAnnotation
import com.veritas.reader.SavedDocument
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.copyTextToClipboard
import com.veritas.reader.getBookAndAuthor
import com.veritas.reader.shareBookmarkAsImage
import com.veritas.reader.shareNoteAsWords
import java.util.Locale


data class NoteGroup(
    val id: String,
    val document: SavedDocument,
    val annotations: List<ReaderAnnotation>,
    val noteText: String,
    val highlightColor: String?,
    val startSentence: Int,
    val endSentence: Int,
    val audioPath: String? = null,
    val audioDurationSeconds: Int = 0
)

fun groupNotes(
    document: SavedDocument,
    annotations: List<ReaderAnnotation>
): List<NoteGroup> {
    val sorted = annotations.sortedBy { it.chunkIndex }
    val groups = mutableListOf<NoteGroup>()
    
    val withGroup = sorted.filter { !it.selectionGroupId.isNullOrBlank() }
    val withoutGroup = sorted.filter { it.selectionGroupId.isNullOrBlank() }
    
    val groupedById = withGroup.groupBy { it.selectionGroupId }
    groupedById.forEach { (groupId, groupAnnots) ->
        val sortedAnnots = groupAnnots.sortedBy { it.chunkIndex }
        val start = sortedAnnots.first().chunkIndex
        val end = sortedAnnots.last().chunkIndex
        val text = sortedAnnots.first().note
        val color = sortedAnnots.first().highlightColor
        val audio = sortedAnnots.firstOrNull { !it.audioPath.isNullOrBlank() }?.audioPath
        val duration = sortedAnnots.firstOrNull { it.audioDurationSeconds > 0 }?.audioDurationSeconds ?: 0
        groups.add(
            NoteGroup(
                id = groupId ?: java.util.UUID.randomUUID().toString(),
                document = document,
                annotations = sortedAnnots,
                noteText = text,
                highlightColor = color,
                startSentence = start,
                endSentence = end,
                audioPath = audio,
                audioDurationSeconds = duration
            )
        )
    }
    
    withoutGroup.forEach { ann ->
        groups.add(
            NoteGroup(
                id = "single-${document.id}-${ann.chunkIndex}",
                document = document,
                annotations = listOf(ann),
                noteText = ann.note,
                highlightColor = ann.highlightColor,
                startSentence = ann.chunkIndex,
                endSentence = ann.chunkIndex,
                audioPath = ann.audioPath,
                audioDurationSeconds = ann.audioDurationSeconds
            )
        )
    }
    
    return groups.sortedBy { it.startSentence }
}



@Composable
internal fun NoteGroupCard(
    group: NoteGroup,
    sentenceTextLookup: (Int) -> String?,
    onOpenAt: (Int) -> Unit,
    onDeleteGroup: () -> Unit
) {
    val context = LocalContext.current
    var expanded by rememberSaveable(group.id) { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    
    val bookTitle = group.document.title
    val (cleanTitle, authorName) = remember(bookTitle) { getBookAndAuthor(bookTitle) }
    
    val collapsedText = remember(group, sentenceTextLookup) {
        val firstAnn = group.annotations.firstOrNull()
        val firstText = firstAnn?.let { sentenceTextLookup(it.chunkIndex) }
        if (firstText.isNullOrBlank()) {
            if (group.startSentence == group.endSentence) "Sentence ${group.startSentence + 1}"
            else "Sentences ${group.startSentence + 1}–${group.endSentence + 1}"
        } else {
            if (firstText.length > 60) firstText.take(57) + "..." else firstText
        }
    }

    val memoDuration = remember(group.audioPath, group.audioDurationSeconds) {
        if (group.audioDurationSeconds > 0) {
            String.format(java.util.Locale.US, "%02d:%02d", group.audioDurationSeconds / 60, group.audioDurationSeconds % 60)
        } else if (!group.audioPath.isNullOrBlank()) {
            try {
                val file = java.io.File(group.audioPath)
                if (file.exists()) {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(group.audioPath)
                    val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    val durMs = durStr?.toLongOrNull() ?: 0L
                    val totalSec = durMs / 1000
                    retriever.release()
                    String.format(java.util.Locale.US, "%02d:%02d", totalSec / 60, totalSec % 60)
                } else "0:00"
            } catch (_: Exception) {
                "0:00"
            }
        } else ""
    }

    val recordingState by com.veritas.reader.VoiceNoteRecorder.recordingState.collectAsState()
    val activeAudioPath by com.veritas.reader.VoiceNoteRecorder.activeAudioPath.collectAsState()
    val isPlayingThis = recordingState == com.veritas.reader.VoiceRecordingState.PLAYING && activeAudioPath == group.audioPath
    val playbackProgress by com.veritas.reader.VoiceNoteRecorder.playbackProgress.collectAsState()
    val playbackPositionMs by com.veritas.reader.VoiceNoteRecorder.playbackPositionMs.collectAsState()
    
    val highlightColor = remember(group.highlightColor) {
        runCatching { Color(android.graphics.Color.parseColor(group.highlightColor)) }
            .getOrDefault(Color(0xFFFFE082))
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(color = highlightColor, shape = CircleShape)
                )
                
                Spacer(modifier = Modifier.width(10.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = collapsedText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (!group.audioPath.isNullOrBlank()) {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = "Voice Memo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        if (group.noteText.isNotBlank()) {
                            Text(
                                text = group.noteText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else if (!group.audioPath.isNullOrBlank()) {
                            Text(
                                text = "Voice Memo ($memoDuration)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            if (expanded) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(10.dp))
                
                val sentencesText = remember(group.annotations, sentenceTextLookup) {
                    group.annotations.map { ann ->
                        sentenceTextLookup(ann.chunkIndex) ?: ""
                    }
                }
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    group.annotations.forEachIndexed { idx, ann ->
                        val text = sentencesText.getOrNull(idx)?.ifBlank { null } ?: "Loading sentence text..."
                        
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(36.dp)
                                    .background(color = highlightColor, shape = RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    
                    if (group.noteText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.EditNote,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Note",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = group.noteText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    if (!group.audioPath.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val currentPositionLabel = if (isPlayingThis && playbackPositionMs > 0) {
                            val curSec = playbackPositionMs / 1000
                            String.format(Locale.US, "%d:%02d", curSec / 60, curSec % 60)
                        } else "0:00"
                        val dynamicDurationLabel = if (isPlayingThis) {
                            "$currentPositionLabel / $memoDuration"
                        } else {
                            if (group.noteText.isNotBlank()) "Voice Memo • $memoDuration" else "Voice Memo ($memoDuration)"
                        }
                        AudioVoiceMemoWaveform(
                            durationLabel = dynamicDurationLabel,
                            isPlaying = isPlayingThis,
                            progress = if (isPlayingThis) playbackProgress else 0f,
                            onTogglePlay = {
                                if (isPlayingThis) {
                                    com.veritas.reader.VoiceNoteRecorder.stopPlayback()
                                } else {
                                    com.veritas.reader.VoiceNoteRecorder.playAudio(group.audioPath)
                                }
                            },
                            onSeek = { fraction ->
                                if (isPlayingThis) {
                                    com.veritas.reader.VoiceNoteRecorder.seekTo(fraction)
                                } else {
                                    com.veritas.reader.VoiceNoteRecorder.playAudio(group.audioPath)
                                    com.veritas.reader.VoiceNoteRecorder.seekTo(fraction)
                                }
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .clickable { onOpenAt(group.startSentence) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Open in document ↗",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "Actions",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Share as text") },
                                    onClick = {
                                        showMenu = false
                                        val contextText = sentencesText.joinToString(" ")
                                        shareNoteAsWords(
                                            context = context,
                                            bookTitle = cleanTitle,
                                            sentenceText = contextText,
                                            noteText = group.noteText
                                        )
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Share as image") },
                                    onClick = {
                                        showMenu = false
                                        val fullText = sentencesText.joinToString(" ") + "\n\nNote: " + group.noteText
                                        shareBookmarkAsImage(
                                            context = context,
                                            bookTitle = cleanTitle,
                                            authorName = authorName,
                                            highlightedText = fullText,
                                            highlightColorHex = group.highlightColor ?: "#FFE082"
                                        )
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy") },
                                    onClick = {
                                        showMenu = false
                                        val fullText = sentencesText.joinToString(" ") + "\n\nNote: " + group.noteText
                                        copyTextToClipboard(context, "Note & Context Text", fullText)
                                    },
                                    leadingIcon = { Icon(Icons.Filled.ContentPaste, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        showMenu = false
                                        onDeleteGroup()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                )
                            }
                         }
                     }
                 }
             }
         }
     }
 }




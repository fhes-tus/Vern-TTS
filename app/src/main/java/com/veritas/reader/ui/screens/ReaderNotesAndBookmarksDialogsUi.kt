package com.veritas.reader.ui.screens


import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veritas.reader.AnnotationType
import com.veritas.reader.DocumentRepository
import com.veritas.reader.ReaderAnnotation
import com.veritas.reader.ReaderDocument
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.VoiceNoteRecorder
import com.veritas.reader.VoiceRecordingState
import com.veritas.reader.capWords
import com.veritas.reader.loadDocumentNoteAudio
import com.veritas.reader.saveDocumentNoteAudio


@Composable
fun BooknotesDialog(
    document: ReaderDocument,
    annotations: List<ReaderAnnotation>,
    documentNote: String,
    currentIndex: Int,
    onDocumentNoteChange: (String) -> Unit,
    onSaveDocumentNote: () -> Unit,
    onAddCurrentNote: () -> Unit,
    onJumpToSection: (Int) -> Unit,
    onExportNotes: () -> Unit,
    onExportPdf: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val docRepo = remember(context) { DocumentRepository(context.applicationContext) }
    var generalMemos by remember(document.id) {
        mutableStateOf(docRepo.loadDocumentNoteAudio(document.id.orEmpty()))
    }
    val recordingState by VoiceNoteRecorder.recordingState.collectAsState()
    val recordingDuration by VoiceNoteRecorder.recordingDurationSeconds.collectAsState()
    var isRecordingGeneral by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isRecordingGeneral = true
            VoiceNoteRecorder.startRecording(context, document.id.orEmpty(), -1)
        }
    }

    val notes = annotations
        .filter { it.type == AnnotationType.NOTE }
        .sortedBy { it.chunkIndex }
    AlertDialog(
        onDismissRequest = {
            VoiceNoteRecorder.stopAll()
            onDismiss()
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                Button(
                    onClick = {
                        VoiceNoteRecorder.stopAll()
                        docRepo.saveDocumentNoteAudio(document.id.orEmpty(), generalMemos)
                        onSaveDocumentNote()
                    },
                    shape = VeritasPackStyle.chipShape(),
                    modifier = Modifier.wrapContentWidth()
                ) {
                    Text("Save")
                }
                OutlinedButton(
                    onClick = onExportPdf,
                    enabled = documentNote.isNotBlank() || annotations.isNotEmpty(),
                    shape = VeritasPackStyle.chipShape(),
                    modifier = Modifier.wrapContentWidth()
                ) {
                    Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Study Guide PDF")
                }
                OutlinedButton(
                    onClick = onExportNotes,
                    enabled = documentNote.isNotBlank() || notes.any { it.note.isNotBlank() },
                    shape = VeritasPackStyle.chipShape(),
                    modifier = Modifier.wrapContentWidth()
                ) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export")
                }
                TextButton(
                    onClick = {
                        VoiceNoteRecorder.stopAll()
                        onDismiss()
                    },
                    shape = VeritasPackStyle.chipShape(),
                    modifier = Modifier.wrapContentWidth()
                ) {
                    Text("Close")
                }
            }
        },
        dismissButton = null,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Booknotes")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    document.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                OutlinedTextField(
                    value = documentNote,
                    onValueChange = { onDocumentNoteChange(capWords(it, 500)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    label = { Text("General note") },
                    placeholder = { Text("Write notes about this document") },
                    minLines = 6,
                    maxLines = 12,
                    shape = VeritasPackStyle.cardShape()
                )
                Text(
                    "${
                        documentNote.trim().split(Regex("\\s+")).count { it.isNotBlank() }
                    } / 500 words",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Voice memos attached to General Note
                if (generalMemos.isNotEmpty()) {
                    Text(
                        "Attached Voice Memos (${generalMemos.size})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        generalMemos.forEachIndexed { aIdx, memo ->
                            AssistChip(
                                onClick = { VoiceNoteRecorder.playAudio(memo.first) },
                                label = { Text("🎙️ Memo ${aIdx + 1} (${memo.second}s)") },
                                leadingIcon = {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = "Delete voice memo",
                                        modifier = Modifier.size(14.dp).clickable {
                                            val updated = generalMemos.filterIndexed { i, _ -> i != aIdx }
                                            generalMemos = updated
                                            VoiceNoteRecorder.deleteAudioFile(memo.first)
                                            docRepo.saveDocumentNoteAudio(document.id.orEmpty(), updated)
                                        }
                                    )
                                }
                            )
                        }
                    }
                }

                // General Note Voice Recording Bar
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isRecordingGeneral && recordingState == VoiceRecordingState.RECORDING) {
                            Text(
                                "🔴 Recording memo... (${recordingDuration}s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    val result = VoiceNoteRecorder.stopRecording()
                                    isRecordingGeneral = false
                                    if (result != null) {
                                        val updated = generalMemos + Pair(result.first, result.second)
                                        generalMemos = updated
                                        docRepo.saveDocumentNoteAudio(document.id.orEmpty(), updated)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Stop")
                            }
                        } else {
                            Text(
                                if (generalMemos.isEmpty()) "Attach voice memo" else "Add another memo",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                                        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED
                                    ) {
                                        permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        isRecordingGeneral = true
                                        VoiceNoteRecorder.startRecording(context, document.id.orEmpty(), -1)
                                    }
                                },
                                shape = RoundedCornerShape(50)
                            ) {
                                Icon(Icons.Outlined.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (generalMemos.isEmpty()) "Record" else "Add memo")
                            }
                        }
                    }
                }

                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sentence notes", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "Notes attached to individual sentences.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = onAddCurrentNote,
                        shape = VeritasPackStyle.chipShape()
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add current")
                    }
                }
                Text(
                    "Current sentence: ${currentIndex + 1}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (notes.isEmpty()) {
                    Text(
                        "No sentence notes yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    notes.forEach { note ->
                        val excerpt = document.chunks.getOrNull(note.chunkIndex)
                            .orEmpty()
                            .replace(Regex("\\s+"), " ")
                            .trim()
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onJumpToSection(note.chunkIndex) },
                            shape = VeritasPackStyle.compactShape(),
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = VeritasPackStyle.surfaceAlpha()))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Sentence ${note.chunkIndex + 1}",
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Icon(
                                        Icons.AutoMirrored.Filled.NavigateNext,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    note.note.ifBlank { "Empty note" },
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (note.audioPath != null) {
                                    val memoPaths = note.audioPath.split("||").map { it.trim() }.filter { it.isNotEmpty() }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.horizontalScroll(rememberScrollState())
                                    ) {
                                        memoPaths.forEachIndexed { aIdx, aPath ->
                                            AssistChip(
                                                onClick = { com.veritas.reader.VoiceNoteRecorder.playAudio(aPath) },
                                                label = { Text(if (memoPaths.size > 1) "🎙️ Memo ${aIdx + 1}" else "🎙️ Voice Memo (${note.audioDurationSeconds}s)") },
                                                leadingIcon = {
                                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                                }
                                            )
                                        }
                                    }
                                }
                                if (excerpt.isNotBlank()) {
                                    Text(
                                        excerpt,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun DocumentNotesDialog(
    document: ReaderDocument,
    annotations: List<ReaderAnnotation>,
    documentNote: String,
    currentIndex: Int,
    onDocumentNoteChange: (String) -> Unit,
    onSaveDocumentNote: () -> Unit,
    onAddCurrentNote: () -> Unit,
    onJumpToSection: (Int) -> Unit,
    onExportNotes: () -> Unit,
    onExportPdf: () -> Unit = {},
    onDismiss: () -> Unit
) {
    BooknotesDialog(
        document = document,
        annotations = annotations,
        documentNote = documentNote,
        currentIndex = currentIndex,
        onDocumentNoteChange = onDocumentNoteChange,
        onSaveDocumentNote = onSaveDocumentNote,
        onAddCurrentNote = onAddCurrentNote,
        onJumpToSection = onJumpToSection,
        onExportNotes = onExportNotes,
        onExportPdf = onExportPdf,
        onDismiss = onDismiss
    )
}

@Composable
internal fun BookmarksOverviewDialog(
    document: ReaderDocument,
    annotations: List<ReaderAnnotation>,
    onJumpToSection: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    // Group contiguous bookmarked sentences into a single file-level bookmark entry so a
    // multi-sentence selection shows as ONE bookmark spanning a range, not many rows.
    val bookmarkRanges = remember(annotations) {
        val indexes = annotations
            .filter { it.type == AnnotationType.BOOKMARK }
            .map { it.chunkIndex }
            .distinct()
            .sorted()
        val ranges = mutableListOf<IntRange>()
        var runStart = -1
        var prev = -2
        indexes.forEach { idx ->
            if (idx == prev + 1) {
                prev = idx
            } else {
                if (runStart >= 0) ranges.add(runStart..prev)
                runStart = idx
                prev = idx
            }
        }
        if (runStart >= 0) ranges.add(runStart..prev)
        ranges
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss, shape = VeritasPackStyle.chipShape()) { Text("Close") }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Bookmarks")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    document.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                HorizontalDivider()

                if (bookmarkRanges.isNotEmpty()) {
                    Text(
                        "Bookmarks",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    bookmarkRanges.forEach { range ->
                        val excerpt = (range.first..range.last)
                            .joinToString(" ") { document.chunks.getOrNull(it).orEmpty() }
                            .replace(Regex("\\s+"), " ")
                            .trim()
                        val label = if (range.first == range.last) {
                            "Sentence ${range.first + 1}"
                        } else {
                            "Sentences ${range.first + 1}–${range.last + 1} (${range.last - range.first + 1})"
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onJumpToSection(range.first) },
                            shape = VeritasPackStyle.compactShape(),
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = VeritasPackStyle.surfaceAlpha()))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Bookmark,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        label,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Icon(
                                        Icons.Filled.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (excerpt.isNotBlank()) {
                                    Text(
                                        excerpt,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        "No bookmarks yet in this document. Select one or more sentences and tap the bookmark icon to save them here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    )
}




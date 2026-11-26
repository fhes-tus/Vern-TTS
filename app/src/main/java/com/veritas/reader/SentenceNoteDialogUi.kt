package com.veritas.reader

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Mic
import com.veritas.reader.VernAlertDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.veritas.reader.ui.screens.FullScreenSettingsScaffold


@Composable
internal fun SentenceNoteDialog(
    document: ReaderDocument,
    sentenceIndexes: List<Int>,
    noteDraft: String,
    audioPath: String? = null,
    audioDuration: Int = 0,
    onNoteChange: (String) -> Unit,
    onAudioChange: (String?, Int) -> Unit = { _, _ -> },
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val indexes = sentenceIndexes
        .filter { it in document.chunks.indices }
        .distinct()
        .sorted()
    val wordCount = noteDraft.trim().split(Regex("\\s+")).count { it.isNotBlank() }
    var audioMemos by remember(document.id, indexes) {
        val paths = audioPath.orEmpty().split("||").map { it.trim() }.filter { it.isNotEmpty() }
        mutableStateOf(paths.map { it to (if (paths.size == 1) audioDuration else 0) })
    }
    val durationReader = remember { NoteAudioDurationReader() }
    LaunchedEffect(audioMemos.map { it.first }) {
        audioMemos = audioMemos.map { (path, previous) -> path to
            (durationReader.read(path)?.div(1000)?.toInt() ?: previous) }
    }
    val isExistingNote = remember(document.id, indexes) { noteDraft.isNotBlank() || !audioPath.isNullOrBlank() }
    var confirmDelete by remember { mutableStateOf(false) }
    val recordingState by VoiceNoteRecorder.recordingState.collectAsState()
    val recordingDuration by VoiceNoteRecorder.recordingDurationSeconds.collectAsState()
    val activeAudioPath by VoiceNoteRecorder.activeAudioPath.collectAsState()

    var permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val docId = document.id ?: "doc"
            val targetIdx = indexes.firstOrNull() ?: 0
            VoiceNoteRecorder.startRecording(context, docId, targetIdx)
        }
    }

    FullScreenSettingsScaffold(
        title = com.veritas.reader.ui.screens.menuString(R.string.ui_sentence_note),
        onBack = {
            VoiceNoteRecorder.stopAll()
            onDismiss()
        },
        scrollable = false,
        modifier = Modifier.testTag("sentence-note-screen")
    ) {
        Text(document.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (indexes.isNotEmpty()) Card(Modifier.fillMaxWidth(), shape = VeritasPackStyle.compactShape()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        indexes.take(5).forEach { index ->
                            val excerpt = ReadingTextCleanup.forDisplay(document.chunks[index]).replace(Regex("\\s+"), " ")
                            Text(com.veritas.reader.ui.screens.menuString(R.string.l10n_dynamic_052,
                                com.veritas.reader.ui.screens.localizedUiValue(index + 1), com.veritas.reader.ui.screens.localizedUiValue(excerpt)),
                                style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        if (indexes.size > 5) Text(com.veritas.reader.ui.screens.menuString(R.string.l10n_dynamic_053,
                            com.veritas.reader.ui.screens.localizedUiValue(indexes.size - 5)),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedTextField(
                    value = noteDraft,
                    onValueChange = onNoteChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sentence-note-input")
                        .heightIn(min = 200.dp),
                    label = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_sentence_note)) },
                    placeholder = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_write_the_note_to_attach_to_this_sentence)) },
                    minLines = 4,
                    maxLines = 8,
                    shape = RoundedCornerShape(16.dp)
                )
                Text(
                    com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_048, com.veritas.reader.ui.screens.localizedUiValue(wordCount)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Multiple Voice Memos Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    audioMemos.forEachIndexed { idx, memo ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val isThisPlaying = recordingState == VoiceRecordingState.PLAYING && activeAudioPath == memo.first
                                Text(
                                    if (isThisPlaying) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_049, com.veritas.reader.ui.screens.localizedUiValue(idx + 1), com.veritas.reader.ui.screens.localizedUiValue(memo.second))
                                    else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_050, com.veritas.reader.ui.screens.localizedUiValue(idx + 1), com.veritas.reader.ui.screens.localizedUiValue(memo.second)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isThisPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        if (isThisPlaying) {
                                            VoiceNoteRecorder.stopPlayback()
                                        } else {
                                            VoiceNoteRecorder.playAudio(memo.first)
                                        }
                                    }
                                ) {
                                    Icon(
                                        if (isThisPlaying) Icons.Filled.Close else Icons.Filled.PlayArrow,
                                        contentDescription = if (isThisPlaying) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_stop) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_play),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (isThisPlaying) {
                                            VoiceNoteRecorder.stopPlayback()
                                        }
                                        val updated = audioMemos.filterIndexed { i, _ -> i != idx }
                                        audioMemos = updated
                                        val combined = if (updated.isEmpty()) null else updated.joinToString("||") { it.first }
                                        val totDur = updated.sumOf { it.second }
                                        onAudioChange(combined, totDur)
                                    }
                                ) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_delete_voice_memo),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Recording or Add Memo Row
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
                            if (recordingState == VoiceRecordingState.RECORDING) {
                                Text(
                                    com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_051, com.veritas.reader.ui.screens.localizedUiValue(recordingDuration)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        val result = VoiceNoteRecorder.stopRecording()
                                        if (result != null) {
                                            val updated = audioMemos + Pair(result.first, result.second)
                                            audioMemos = updated
                                            val combined = updated.joinToString("||") { it.first }
                                            val totDur = updated.sumOf { it.second }
                                            onAudioChange(combined, totDur)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_stop))
                                }
                            } else {
                                Text(
                                    if (audioMemos.isEmpty()) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_attach_voice_memo) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_add_another_voice_memo),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(
                                    onClick = {
                                        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                            permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                        } else {
                                            val docId = document.id ?: "doc"
                                            val targetIdx = indexes.firstOrNull() ?: 0
                                            VoiceNoteRecorder.startRecording(context, docId, targetIdx)
                                        }
                                    },
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Icon(Icons.Outlined.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (audioMemos.isEmpty()) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_record) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_add_memo))
                                }
                            }
                        }
                    }
                }

            }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (isExistingNote) TextButton(onClick = { confirmDelete = true }, enabled = recordingState != VoiceRecordingState.RECORDING) {
                Text(com.veritas.reader.ui.screens.menuString(R.string.ui_delete_note), color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = {
                    VoiceNoteRecorder.stopAll()
                    val combined = if (audioMemos.isEmpty()) null else audioMemos.joinToString("||") { it.first }
                    val totDur = audioMemos.sumOf { it.second }
                    onAudioChange(combined, totDur)
                    onSave()
                },
                enabled = recordingState != VoiceRecordingState.RECORDING && (noteDraft.isNotBlank() || audioMemos.isNotEmpty()),
                shape = VeritasPackStyle.chipShape(),
                modifier = Modifier.weight(1f)
            ) {
                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_save))
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text(com.veritas.reader.ui.screens.menuString(R.string.ui_delete_note)) },
        confirmButton = {
            TextButton(onClick = {
                confirmDelete = false
                VoiceNoteRecorder.stopAll()
                onDelete()
            }) { Text(com.veritas.reader.ui.screens.menuString(R.string.ui_delete)) }
        }, dismissButton = {
            TextButton(onClick = { confirmDelete = false }) {
                Text(com.veritas.reader.ui.screens.menuString(R.string.ui_cancel))
            }
        })
}

@Preview(showBackground = true)
@Composable
internal fun SentenceNoteDialogPreview() {
    val sampleDoc = remember {
        ReaderDocument(
            id = "doc_1",
            title = "Meditations",
            sourceLabel = "EPUB",
            rawText = "Waste no more time arguing what a good man should be. Be one.",
            sentences = listOf("Waste no more time arguing what a good man should be.", "Be one.")
        )
    }
    MaterialTheme {
        SentenceNoteDialog(
            document = sampleDoc,
            sentenceIndexes = listOf(0),
            noteDraft = "Reflect on this daily.",
            audioPath = null,
            audioDuration = 0,
            onNoteChange = {},
            onAudioChange = { _, _ -> },
            onSave = {},
            onDelete = {},
            onDismiss = {}
        )
    }
}

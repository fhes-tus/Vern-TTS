package com.veritas.reader.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.veritas.reader.AnnotationType
import com.veritas.reader.DocumentRepository
import com.veritas.reader.GeneralNote
import com.veritas.reader.MathText
import com.veritas.reader.R
import com.veritas.reader.SavedDocument
import com.veritas.reader.TextChunker
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.ui.ReaderUiState
import com.veritas.reader.ui.rememberVeritasHaptics
import java.text.SimpleDateFormat
import java.util.Date


@Composable
internal fun LibraryNotesTab(
    documents: List<SavedDocument>,
    uiState: ReaderUiState,
    notesListState: LazyListState,
    annotationFilter: String,
    selectedGeneralNoteTag: String,
    selectedAnnotationKeys: Set<String>,
    onSelectedAnnotationKeysChange: (Set<String>) -> Unit,
    onConfirmAnnotationDelete: () -> Unit,
    onOpenDocument: (SavedDocument) -> Unit,
    onOpenDocumentAt: (SavedDocument, Int) -> Unit,
    onEditGeneralNote: (GeneralNote) -> Unit,
    onDeleteGeneralNote: (String) -> Unit,
    onToggleGeneralNotePin: (String) -> Unit,
    onChangeGeneralNoteColor: (String, String?) -> Unit,
    onDeleteAnnotations: (Set<String>) -> Unit,
    onWriteGeneralNote: () -> Unit,
    onNavigateToTab: (VeritasHomeTab) -> Unit,
    onImportFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val libraryPrefs = remember { context.getSharedPreferences("veritas_library_settings", Context.MODE_PRIVATE) }
    val repository = remember(context) { DocumentRepository(context) }
    var loadedDocSentences by remember { mutableStateOf(emptyMap<String, List<String>>()) }
    val docIdsWithAnnotations = remember(uiState.allAnnotations) {
        uiState.allAnnotations.map { it.documentId }.toSet()
    }
    LaunchedEffect(docIdsWithAnnotations, uiState.documents) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val neededIds = docIdsWithAnnotations.filter { it !in loadedDocSentences }
            if (neededIds.isNotEmpty()) {
                val newMap = neededIds.associateWith { docId ->
                    val docMetadata = uiState.documents.firstOrNull { it.id == docId }
                    if (docMetadata != null) {
                        val text = repository.readText(docMetadata)
                        TextChunker.chunk(text)
                    } else {
                        emptyList()
                    }
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    loadedDocSentences = loadedDocSentences + newMap
                }
            }
        }
    }

    val annotatedDocuments = remember(documents, uiState.allAnnotations, uiState.documentNotes, uiState.documentTitles) {
        val annotationsByDocument = uiState.allAnnotations
            .filter { it.type == AnnotationType.BOOKMARK || it.type == AnnotationType.NOTE }
            .groupBy { it.documentId }
        val markedDocumentIds = annotationsByDocument.keys + uiState.documentNotes.keys
        markedDocumentIds.mapNotNull { documentId ->
            val document = documents.firstOrNull { it.id == documentId } ?: SavedDocument(
                id = documentId,
                title = uiState.documentTitles[documentId] ?: "Deleted Book",
                fileName = "",
                sourceLabel = "Deleted",
                createdAt = 0,
                updatedAt = 0,
                currentIndex = 0,
                chunkCount = 0,
                charCount = 0,
                preview = ""
            )
            val annotations = annotationsByDocument[documentId].orEmpty().sortedBy { annotation -> annotation.chunkIndex }
            val documentNote = uiState.documentNotes[documentId].orEmpty()
            if (annotations.isEmpty() && documentNote.isBlank()) {
                null
            } else {
                MarkedDocument(
                    document = document,
                    annotations = annotations,
                    documentNote = documentNote,
                    updatedAt = maxOf(
                        annotations.maxOfOrNull { it.updatedAt } ?: 0L,
                        if (documentNote.isBlank()) 0L else document.updatedAt
                    )
                )
            }
        }.sortedByDescending { it.updatedAt }
    }
    val filteredAnnotatedDocuments = remember(annotatedDocuments, annotationFilter) {
        annotatedDocuments.map { markedDoc ->
            val bookmarks = markedDoc.annotations.filter { it.type == AnnotationType.BOOKMARK }
            val notes = markedDoc.annotations.filter { it.type == AnnotationType.NOTE }
            markedDoc.copy(
                annotations = when (annotationFilter) {
                    "Bookmarks" -> bookmarks
                    "Booknotes" -> notes
                    else -> markedDoc.annotations
                },
                documentNote = if (annotationFilter == "Bookmarks") "" else markedDoc.documentNote
            )
        }.filter {
            (annotationFilter == "All") || it.annotations.isNotEmpty() || it.documentNote.isNotBlank()
        }
    }
    val annotationSelectionMode = selectedAnnotationKeys.isNotEmpty()

    val bookmarksOnly = remember(filteredAnnotatedDocuments) {
        filteredAnnotatedDocuments.filter { markedDoc -> markedDoc.annotations.any { it.type == AnnotationType.BOOKMARK } }
    }
    val allBookmarkGroups = remember(bookmarksOnly, uiState.allAnnotations) {
        bookmarksOnly.flatMap { markedDocument ->
            val doc = markedDocument.document
            val docBookmarks = markedDocument.annotations.filter { it.type == AnnotationType.BOOKMARK }
            groupBookmarks(doc, docBookmarks)
        }
    }
    val notesOnly = remember(filteredAnnotatedDocuments) {
        filteredAnnotatedDocuments.filter { markedDoc -> markedDoc.annotations.any { it.type == AnnotationType.NOTE } || markedDoc.documentNote.isNotBlank() }
    }


    val trueGeneralNotes = remember(uiState.generalNotes) {
        uiState.generalNotes.filterNot { it.title.startsWith("__vocab__") }
    }
    var noteSearchQuery by remember { mutableStateOf("") }
    var isGridView by remember { mutableStateOf(libraryPrefs.getBoolean("is_notes_grid_view", true)) }
    var noteSortOrder by remember { mutableStateOf("date") }
    val processedGeneralNotes = remember(trueGeneralNotes, noteSearchQuery, noteSortOrder) {
        var list = trueGeneralNotes.filter { note ->
            noteSearchQuery.isBlank() || 
            note.title.contains(noteSearchQuery, ignoreCase = true) ||
            note.content.contains(noteSearchQuery, ignoreCase = true)
        }
        list = if (noteSortOrder == "title") {
            list.sortedBy { it.title.lowercase() }
        } else {
            list.sortedByDescending { it.updatedAt }
        }
        list
    }


                                Column(modifier = Modifier.fillMaxSize()) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .zIndex(2f),
                                        color = Color.Transparent,
                                        shadowElevation = 0.dp
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            BasicTextField(
                                                value = noteSearchQuery,
                                                onValueChange = { noteSearchQuery = it },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(38.dp)
                                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50)),
                                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                                singleLine = true,
                                                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                                                decorationBox = { innerTextField ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .padding(horizontal = 14.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Filled.Search,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Box(
                                                            modifier = Modifier.weight(1f),
                                                            contentAlignment = Alignment.CenterStart
                                                        ) {
                                                            if (noteSearchQuery.isEmpty()) {
                                                                Text(
                                                                    text = "Search notes...",
                                                                    style = MaterialTheme.typography.bodyMedium,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                                )
                                                            }
                                                            innerTextField()
                                                        }
                                                        if (noteSearchQuery.isNotEmpty()) {
                                                            IconButton(
                                                                onClick = { noteSearchQuery = "" },
                                                                modifier = Modifier.size(20.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Close,
                                                                    contentDescription = "Clear",
                                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            )

                                            IconButton(
                                                onClick = {
                                                    isGridView = !isGridView
                                                    libraryPrefs.edit().putBoolean("is_notes_grid_view", isGridView).apply()
                                                },
                                                modifier = Modifier.size(38.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                            ) {
                                                Icon(
                                                    imageVector = if (isGridView) Icons.Filled.GridView else Icons.AutoMirrored.Filled.List,
                                                    contentDescription = "Toggle layout",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = { noteSortOrder = if (noteSortOrder == "date") "title" else "date" },
                                                modifier = Modifier.size(38.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                            ) {
                                                Icon(
                                                    imageVector = if (noteSortOrder == "date") Icons.Filled.SortByAlpha else Icons.Filled.Schedule,
                                                    contentDescription = "Sort notes",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }

                                    var playingAudioNoteId by remember { mutableStateOf<String?>(null) }
                                    var activeNotePlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
                                    var activeNoteProgress by remember { mutableFloatStateOf(0f) }

                                    LaunchedEffect(playingAudioNoteId, activeNotePlayer) {
                                        if (playingAudioNoteId != null && activeNotePlayer != null) {
                                            while (playingAudioNoteId != null && activeNotePlayer != null) {
                                                try {
                                                    val cur = activeNotePlayer?.currentPosition ?: 0
                                                    val dur = activeNotePlayer?.duration ?: 1
                                                    if (dur > 0) {
                                                        activeNoteProgress = (cur.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                                                    }
                                                } catch (_: Exception) {}
                                                kotlinx.coroutines.delay(100)
                                            }
                                        } else {
                                            activeNoteProgress = 0f
                                        }
                                    }

                                    DisposableEffect(Unit) {
                                        onDispose {
                                            activeNotePlayer?.release()
                                            activeNotePlayer = null
                                        }
                                    }

                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 18.dp),
                                        state = notesListState,
                                        contentPadding = PaddingValues(top = 8.dp, bottom = 22.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val tagFilteredNotes = when (selectedGeneralNoteTag) {
                                            "General" -> uiState.generalNotes.filter { !it.isChecklist && it.reminderAt == null && it.allAudioUrls.isEmpty() }
                                            "Reminder", "Reminders" -> uiState.generalNotes.filter { it.reminderAt != null }
                                            "Pinned" -> uiState.generalNotes.filter { it.pinned }
                                            "Audio", "Voice Memos" -> uiState.generalNotes.filter { it.allAudioUrls.isNotEmpty() }
                                            "Checklists" -> uiState.generalNotes.filter { it.isChecklist }
                                            else -> uiState.generalNotes
                                        }
                                        val filteredNotes = if (noteSearchQuery.isBlank()) tagFilteredNotes else tagFilteredNotes.filter {
                                            it.title.contains(noteSearchQuery, ignoreCase = true) || it.content.contains(noteSearchQuery, ignoreCase = true)
                                        }
                                        val finalNotes = if (noteSortOrder == "title") filteredNotes.sortedBy { it.title } else filteredNotes.sortedByDescending { it.updatedAt }

                                        if (finalNotes.isEmpty()) {
                                            item {
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 32.dp, horizontal = 8.dp),
                                                    shape = VeritasPackStyle.cardShape(),
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = VeritasPackStyle.surfaceAlpha())),
                                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                                ) {
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(24.dp),
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Outlined.EditNote,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(48.dp)
                                                        )
                                                        Text(
                                                            text = if (noteSearchQuery.isNotBlank()) "No matching notes" else "No notes found",
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Text(
                                                            text = if (noteSearchQuery.isNotBlank()) "Try searching for a different keyword." else "Tap + Note below to capture ideas, write personal notes, or set reminders.",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                        )
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Button(
                                                            onClick = { onWriteGeneralNote() },
                                                            shape = VeritasPackStyle.chipShape(),
                                                            colors = ButtonDefaults.buttonColors(
                                                                containerColor = MaterialTheme.colorScheme.primary,
                                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                                            )
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Outlined.EditNote,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text("Write Note")
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            val pinnedNotes = finalNotes.filter { it.pinned }
                                            val otherNotes = finalNotes.filter { !it.pinned }

                                            @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
                                            @Composable
                                            fun NoteCardItem(generalNote: GeneralNote) {
                                                val cardBgColor = generalNote.color?.let { Color(android.graphics.Color.parseColor(it)) }
                                                    ?: MaterialTheme.colorScheme.surface.copy(alpha = VeritasPackStyle.surfaceAlpha())
                                                val onCardColor = if (generalNote.color != null) Color(0xFF1E293B) else MaterialTheme.colorScheme.onSurface
                                                var showNoteMenu by remember { mutableStateOf(false) }
                                                var confirmNoteDelete by remember { mutableStateOf(false) }
                                                val noteShareContext = LocalContext.current

                                                if (confirmNoteDelete) {
                                                    AlertDialog(
                                                        onDismissRequest = { confirmNoteDelete = false },
                                                        title = { Text(stringResource(R.string.delete_note_title)) },
                                                        text = { Text(stringResource(R.string.delete_note_message)) },
                                                        confirmButton = {
                                                            TextButton(onClick = {
                                                                confirmNoteDelete = false
                                                                onDeleteGeneralNote(generalNote.id)
                                                            }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                                        },
                                                        dismissButton = {
                                                            TextButton(onClick = { confirmNoteDelete = false }) {
                                                                Text(stringResource(R.string.action_cancel))
                                                            }
                                                        }
                                                    )
                                                }

                                                Box {
                                                    val noteHaptic = rememberVeritasHaptics()
                                                    Card(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .combinedClickable(
                                                                onClick = { onEditGeneralNote(generalNote) },
                                                                onLongClick = {
                                                                    noteHaptic.longPress()
                                                                    showNoteMenu = true
                                                                }
                                                            )
                                                            .padding(vertical = 4.dp),
                                                        shape = RoundedCornerShape(16.dp),
                                                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                                                        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
                                                    ) {
                                                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                modifier = Modifier.fillMaxWidth()
                                                            ) {
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                    modifier = Modifier.weight(1f, fill = false)
                                                                ) {
                                                                    if (generalNote.pinned) {
                                                                        Surface(
                                                                            shape = RoundedCornerShape(50),
                                                                            color = MaterialTheme.colorScheme.primaryContainer,
                                                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                                        ) {
                                                                            Row(
                                                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                                                verticalAlignment = Alignment.CenterVertically,
                                                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                                            ) {
                                                                                Icon(
                                                                                    imageVector = Icons.Filled.PushPin,
                                                                                    contentDescription = null,
                                                                                    tint = MaterialTheme.colorScheme.primary,
                                                                                    modifier = Modifier.size(11.dp)
                                                                                )
                                                                                Text(
                                                                                    text = "Pinned",
                                                                                    style = MaterialTheme.typography.labelSmall,
                                                                                    fontWeight = FontWeight.Bold,
                                                                                    color = MaterialTheme.colorScheme.primary
                                                                                )
                                                                            }
                                                                        }
                                                                    }
                                                                    if (generalNote.title.isNotBlank()) {
                                                                        Text(
                                                                            generalNote.title,
                                                                            fontWeight = FontWeight.Bold,
                                                                            style = MaterialTheme.typography.titleSmall,
                                                                            color = onCardColor,
                                                                            maxLines = 1,
                                                                            overflow = TextOverflow.Ellipsis
                                                                        )
                                                                    }
                                                                }

                                                                IconButton(
                                                                    onClick = { onToggleGeneralNotePin(generalNote.id) },
                                                                    modifier = Modifier.size(28.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = if (generalNote.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                                                        contentDescription = if (generalNote.pinned) "Unpin note" else "Pin note",
                                                                        tint = if (generalNote.pinned) MaterialTheme.colorScheme.primary else onCardColor.copy(alpha = 0.5f),
                                                                        modifier = Modifier.size(16.dp)
                                                                    )
                                                                }
                                                            }

                                                            val noteAudios = generalNote.allAudioUrls
                                                            if (noteAudios.isNotEmpty()) {
                                                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                    noteAudios.forEachIndexed { audioIdx, audioPath ->
                                                                        val memoDuration = remember(audioPath) {
                                                                            if (audioPath.isBlank()) "0:00"
                                                                            else {
                                                                                try {
                                                                                    val file = java.io.File(audioPath)
                                                                                    if (file.exists()) {
                                                                                        val retriever = android.media.MediaMetadataRetriever()
                                                                                        retriever.setDataSource(audioPath)
                                                                                        val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                                                                                        val durMs = durStr?.toLongOrNull() ?: 0L
                                                                                        val totalSec = durMs / 1000
                                                                                        retriever.release()
                                                                                        String.format(java.util.Locale.US, "%02d:%02d", totalSec / 60, totalSec % 60)
                                                                                    } else "0:00"
                                                                                } catch (e: Exception) {
                                                                                    "0:00"
                                                                                }
                                                                            }
                                                                        }
                                                                        val audioKey = "${generalNote.id}_$audioIdx"
                                                                        val isPlayingThisAudio = playingAudioNoteId == audioKey
                                                                        AudioVoiceMemoWaveform(
                                                                            durationLabel = if (noteAudios.size > 1) "Memo ${audioIdx + 1} • $memoDuration" else memoDuration,
                                                                            isPlaying = isPlayingThisAudio,
                                                                            progress = if (isPlayingThisAudio) activeNoteProgress else 0f,
                                                                            onTogglePlay = {
                                                                                try {
                                                                                    if (playingAudioNoteId == audioKey) {
                                                                                        activeNotePlayer?.stop()
                                                                                        activeNotePlayer?.release()
                                                                                        activeNotePlayer = null
                                                                                        playingAudioNoteId = null
                                                                                        activeNoteProgress = 0f
                                                                                    } else {
                                                                                        activeNotePlayer?.stop()
                                                                                        activeNotePlayer?.release()
                                                                                        val player = android.media.MediaPlayer().apply {
                                                                                            setDataSource(audioPath)
                                                                                            prepare()
                                                                                            setOnCompletionListener {
                                                                                                playingAudioNoteId = null
                                                                                                activeNotePlayer?.release()
                                                                                                activeNotePlayer = null
                                                                                                activeNoteProgress = 0f
                                                                                            }
                                                                                            start()
                                                                                        }
                                                                                        activeNotePlayer = player
                                                                                        playingAudioNoteId = audioKey
                                                                                    }
                                                                                } catch (e: Exception) {
                                                                                    e.printStackTrace()
                                                                                    playingAudioNoteId = null
                                                                                    activeNoteProgress = 0f
                                                                                }
                                                                            },
                                                                            onSeek = { fraction ->
                                                                                try {
                                                                                    val clamped = fraction.coerceIn(0f, 1f)
                                                                                    if (playingAudioNoteId == audioKey && activeNotePlayer != null) {
                                                                                        val dur = activeNotePlayer?.duration ?: 0
                                                                                        if (dur > 0) {
                                                                                            activeNotePlayer?.seekTo((clamped * dur).toInt())
                                                                                            activeNoteProgress = clamped
                                                                                        }
                                                                                    } else {
                                                                                        activeNotePlayer?.stop()
                                                                                        activeNotePlayer?.release()
                                                                                        val player = android.media.MediaPlayer().apply {
                                                                                            setDataSource(audioPath)
                                                                                            prepare()
                                                                                            val dur = duration.coerceAtLeast(1)
                                                                                            seekTo((clamped * dur).toInt())
                                                                                            setOnCompletionListener {
                                                                                                playingAudioNoteId = null
                                                                                                activeNotePlayer?.release()
                                                                                                activeNotePlayer = null
                                                                                                activeNoteProgress = 0f
                                                                                            }
                                                                                            start()
                                                                                        }
                                                                                        activeNotePlayer = player
                                                                                        playingAudioNoteId = audioKey
                                                                                        activeNoteProgress = clamped
                                                                                    }
                                                                                } catch (e: Exception) {
                                                                                    e.printStackTrace()
                                                                                }
                                                                            }
                                                                        )
                                                                    }
                                                                }
                                                            } else if (generalNote.isChecklist) {
                                                                val items = generalNote.content.split("\n").filter { it.isNotBlank() }.take(4).map { line ->
                                                                    val checked = line.startsWith("[x]")
                                                                    val text = line.removePrefix("[ ] ").removePrefix("[x] ")
                                                                    checked to text
                                                                }
                                                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                    items.forEach { (checked, text) ->
                                                                        Row(
                                                                            verticalAlignment = Alignment.CenterVertically,
                                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                                        ) {
                                                                            Icon(
                                                                                imageVector = if (checked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                                                                                contentDescription = null,
                                                                                tint = onCardColor.copy(alpha = 0.6f),
                                                                                modifier = Modifier.size(14.dp)
                                                                            )
                                                                            Text(
                                                                                text = text,
                                                                                style = MaterialTheme.typography.bodySmall.copy(
                                                                                    color = if (checked) onCardColor.copy(alpha = 0.5f) else onCardColor,
                                                                                    textDecoration = if (checked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                                                                ),
                                                                                maxLines = 1,
                                                                                overflow = TextOverflow.Ellipsis
                                                                            )
                                                                        }
                                                                    }
                                                                    if (generalNote.content.split("\n").filter { it.isNotBlank() }.size > 4) {
                                                                        Text(
                                                                            text = "+ more items",
                                                                            style = MaterialTheme.typography.labelSmall,
                                                                            color = onCardColor.copy(alpha = 0.5f),
                                                                            modifier = Modifier.padding(start = 20.dp)
                                                                        )
                                                                    }
                                                                }
                                                            } else {
                                                                Text(
                                                                    text = renderMarkdown(MathText.beautify(generalNote.content)),
                                                                    maxLines = 5,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = onCardColor
                                                                )
                                                            }

                                                            val locale = LocalConfiguration.current.locales[0]
                                                            val updatedTime = remember(generalNote.updatedAt, locale) {
                                                                val diffMillis = System.currentTimeMillis() - generalNote.updatedAt
                                                                val diffHours = diffMillis / (1000 * 60 * 60)
                                                                val diffDays = diffHours / 24
                                                                when {
                                                                    diffHours < 1 -> "Just now"
                                                                    diffHours < 24 -> "$diffHours hours ago"
                                                                    diffDays < 7 -> "$diffDays days ago"
                                                                    else -> SimpleDateFormat("dd/MM/yyyy", locale).format(Date(generalNote.updatedAt))
                                                                }
                                                            }

                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Text(
                                                                    text = updatedTime,
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = onCardColor.copy(alpha = 0.5f)
                                                                )

                                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                                    if (!generalNote.imageUrl.isNullOrBlank()) {
                                                                        Icon(
                                                                            imageVector = Icons.Default.Image,
                                                                            contentDescription = "Image attachment",
                                                                            tint = onCardColor.copy(alpha = 0.5f),
                                                                            modifier = Modifier.size(13.dp)
                                                                        )
                                                                    }
                                                                    if (generalNote.allAudioUrls.isNotEmpty()) {
                                                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                                            Icon(
                                                                                imageVector = Icons.Default.Mic,
                                                                                contentDescription = "Audio attachment",
                                                                                tint = onCardColor.copy(alpha = 0.5f),
                                                                                modifier = Modifier.size(13.dp)
                                                                            )
                                                                            if (generalNote.allAudioUrls.size > 1) {
                                                                                Text(
                                                                                    text = "${generalNote.allAudioUrls.size}",
                                                                                    style = MaterialTheme.typography.labelSmall,
                                                                                    color = onCardColor.copy(alpha = 0.5f)
                                                                                )
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                    DropdownMenu(expanded = showNoteMenu, onDismissRequest = { showNoteMenu = false }) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            val notePresets = listOf(null, "#FFF59D", "#A5D6A7", "#90CAF9", "#F48FB1", "#FFCC80")
                                                            notePresets.forEach { hex ->
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(26.dp)
                                                                        .clip(CircleShape)
                                                                        .background(
                                                                            hex?.let { Color(android.graphics.Color.parseColor(it)) }
                                                                                ?: MaterialTheme.colorScheme.surfaceVariant
                                                                        )
                                                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                                                        .clickable {
                                                                            onChangeGeneralNoteColor(generalNote.id, hex)
                                                                            showNoteMenu = false
                                                                        }
                                                                )
                                                            }
                                                        }
                                                        DropdownMenuItem(
                                                            text = { Text(if (generalNote.pinned) "Unpin" else "Pin") },
                                                            leadingIcon = { Icon(if (generalNote.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, contentDescription = null) },
                                                            onClick = { showNoteMenu = false; onToggleGeneralNotePin(generalNote.id) }
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text("Share") },
                                                            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                                                            onClick = { showNoteMenu = false; shareGeneralNote(noteShareContext, generalNote) }
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text("Delete") },
                                                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                                            onClick = { showNoteMenu = false; confirmNoteDelete = true }
                                                        )
                                                    }
                                                }
                                            }

                                            if (pinnedNotes.isNotEmpty()) {
                                                item {
                                                    Text(
                                                        text = "PINNED",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                                                    )
                                                }
                                                if (isGridView) {
                                                    item(key = "general-notes-grid-pinned") {
                                                        Row(
                                                            modifier = Modifier.animateItem().fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            val leftNotes = pinnedNotes.filterIndexed { index, _ -> index % 2 == 0 }
                                                            val rightNotes = pinnedNotes.filterIndexed { index, _ -> index % 2 == 1 }

                                                            Column(
                                                                modifier = Modifier.weight(1f),
                                                                verticalArrangement = Arrangement.spacedBy(0.dp)
                                                            ) {
                                                                leftNotes.forEach { note ->
                                                                    NoteCardItem(note)
                                                                }
                                                            }

                                                            Column(
                                                                modifier = Modifier.weight(1f),
                                                                verticalArrangement = Arrangement.spacedBy(0.dp)
                                                            ) {
                                                                rightNotes.forEach { note ->
                                                                    NoteCardItem(note)
                                                                }
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    itemsIndexed(pinnedNotes, key = { _, note -> "pinned-${note.id}" }) { _, note ->
                                                        NoteCardItem(note)
                                                    }
                                                }
                                            }

                                            if (otherNotes.isNotEmpty()) {
                                                if (pinnedNotes.isNotEmpty()) {
                                                    item {
                                                        Text(
                                                            text = "OTHERS",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                                                        )
                                                    }
                                                }
                                                if (isGridView) {
                                                    item(key = "general-notes-grid-others") {
                                                        Row(
                                                            modifier = Modifier.animateItem().fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            val leftNotes = otherNotes.filterIndexed { index, _ -> index % 2 == 0 }
                                                            val rightNotes = otherNotes.filterIndexed { index, _ -> index % 2 == 1 }

                                                            Column(
                                                                modifier = Modifier.weight(1f),
                                                                verticalArrangement = Arrangement.spacedBy(0.dp)
                                                            ) {
                                                                leftNotes.forEach { note ->
                                                                    NoteCardItem(note)
                                                                }
                                                            }

                                                            Column(
                                                                modifier = Modifier.weight(1f),
                                                                verticalArrangement = Arrangement.spacedBy(0.dp)
                                                            ) {
                                                                rightNotes.forEach { note ->
                                                                    NoteCardItem(note)
                                                                }
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    itemsIndexed(otherNotes, key = { _, note -> "other-${note.id}" }) { _, note ->
                                                        NoteCardItem(note)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

}

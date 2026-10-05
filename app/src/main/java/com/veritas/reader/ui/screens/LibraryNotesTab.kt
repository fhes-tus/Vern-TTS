package com.veritas.reader.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.key
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.List
import com.veritas.reader.ui.NotesPaperTemplate
import com.veritas.reader.ui.NotesSettings
import com.veritas.reader.ui.notesPaperBackground
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.veritas.reader.AnnotationType
import com.veritas.reader.DocumentRepository
import com.veritas.reader.GeneralNote
import com.veritas.reader.NoteRevision
import com.veritas.reader.MathText
import com.veritas.reader.R
import com.veritas.reader.SavedDocument
import com.veritas.reader.TextChunker
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.ui.ReaderUiState
import com.veritas.reader.ui.rememberVeritasHaptics
import java.text.SimpleDateFormat
import java.util.Date

private val NOTE_ATTACHMENT_TAG_REGEX = Regex(
    """(!\[[^\]]*\]\([^)]+\)|\[(?:video|image|photo|audio|file)(?::[^\]]*)?\]\([^)]+\)|\[(?:video|image|photo|audio|file):[^\]]+\])""",
    RegexOption.IGNORE_CASE
)
private val NOTE_VIDEO_TAG_REGEX = Regex("""\[video(?::[^]]*)?\]\([^)]+\)|\[video:[^]]+\]""", RegexOption.IGNORE_CASE)
private val NOTE_IMAGE_TAG_REGEX = Regex("""!\[[^\]]*\]\([^)]+\)|\[image(?::[^]]*)?\]\([^)]+\)|\[image:[^]]+\]""", RegexOption.IGNORE_CASE)
private val NOTE_AUDIO_TAG_REGEX = Regex("""\[audio(?::[^]]*)?\]\([^)]+\)|\[audio:[^]]+\]""", RegexOption.IGNORE_CASE)
private val NOTE_FILE_TAG_REGEX = Regex("""\[file(?::[^]]*)?\]\([^)]+\)|\[file:[^]]+\]""", RegexOption.IGNORE_CASE)

data class NoteCollectionActions(
    val createNotebook: (String) -> Unit,
    val moveNote: (String, String?) -> Unit,
    val setLabels: (String, List<String>) -> Unit = { _, _ -> },
    val createLabel: suspend (String) -> com.veritas.reader.NoteNotebook = { error("Label creation is unavailable") },
    val setDraftLabels: (List<String>) -> Unit = {},
    val renameNotebook: (String, String) -> Unit,
    val deleteNotebook: (String) -> Unit,
    val restoreNote: (String) -> Unit,
    val permanentlyDeleteNote: (String) -> Unit,
    val revisionsFor: suspend (String) -> List<NoteRevision>,
    val restoreRevision: (NoteRevision) -> Unit
)

private fun extractPathFromTag(tag: String): String {
    val parenStart = tag.indexOf('(')
    val parenEnd = tag.lastIndexOf(')')
    if (parenStart != -1 && parenEnd > parenStart) {
        return tag.substring(parenStart + 1, parenEnd).trim()
    }
    val colonIndex = tag.indexOf(':')
    if (colonIndex != -1) {
        return tag.substring(colonIndex + 1).removeSuffix("]").trim()
    }
    return tag
}

private fun formatMediaDuration(path: String): String {
    if (path.isBlank()) return ""
    return try {
        val file = java.io.File(path)
        if (file.exists()) {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(path)
            val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durMs = durStr?.toLongOrNull() ?: 0L
            val totalSec = durMs / 1000
            retriever.release()
            if (totalSec > 0) String.format(java.util.Locale.US, "%02d:%02d", totalSec / 60, totalSec % 60) else ""
        } else ""
    } catch (_: Exception) {
        ""
    }
}

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
    notesSettings: NotesSettings = uiState.notesSettings,
    noteSearchQuery: String = "",
    collectionActions: NoteCollectionActions? = null,
    showTrash: Boolean,
    selectedNotebookId: String?,
    onSelectedNotebookChange: (String?) -> Unit,
    createNotebookDialog: Boolean,
    onCreateNotebookDialogChange: (Boolean) -> Unit,
    manageNotebooksDialog: Boolean,
    onManageNotebooksDialogChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val floatingBottomPadding = LocalHomeBottomPadding.current

    val trueGeneralNotes = remember(uiState.generalNotes) {
        uiState.generalNotes.filterNot { it.title.startsWith("__vocab__") }
    }
    val isGridView = notesSettings.isGridView
    val noteSortOrder = notesSettings.defaultSortOrder
    var notebookToDelete by remember { mutableStateOf<com.veritas.reader.NoteNotebook?>(null) }
    var notebookToRename by remember { mutableStateOf<com.veritas.reader.NoteNotebook?>(null) }
    var renamedNotebook by remember { mutableStateOf("") }
    var notebookName by remember { mutableStateOf("") }
    LaunchedEffect(uiState.noteNotebooks, selectedNotebookId) {
        if (selectedNotebookId != null && uiState.noteNotebooks.none { it.id == selectedNotebookId }) onSelectedNotebookChange(null)
    }
                                Column(modifier = modifier.fillMaxSize()) {
                                    Spacer(Modifier.height(8.dp))
                                    if (createNotebookDialog && collectionActions != null) {
                                        NotebookNameDialog("New notebook", notebookName, { notebookName = it }, uiState.noteNotebooks,
                                            onDismiss = { onCreateNotebookDialogChange(false) },
                                            onSave = { collectionActions.createNotebook(notebookName.trim()); notebookName = ""; onCreateNotebookDialogChange(false) })
                                    }
                                    if (manageNotebooksDialog && collectionActions != null) {
                                        AlertDialog(
                                            onDismissRequest = { onManageNotebooksDialogChange(false) }, title = { Text("Manage notebooks") },
                                            text = { Column(modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                                                uiState.noteNotebooks.forEach { notebook ->
                                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                                        Text(notebook.name, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                        IconButton(onClick = { onManageNotebooksDialogChange(false); notebookToRename = notebook; renamedNotebook = notebook.name }) { Icon(Icons.Default.Edit, "Rename ${notebook.name}") }
                                                        IconButton(onClick = { onManageNotebooksDialogChange(false); notebookToDelete = notebook }) { Icon(Icons.Default.Delete, "Delete ${notebook.name}") }
                                                    }
                                                }
                                            } },
                                            confirmButton = { TextButton(onClick = { onManageNotebooksDialogChange(false) }) { Text("Done") } }
                                        )
                                    }
                                    notebookToDelete?.let { notebook ->
                                        AlertDialog(
                                            onDismissRequest = { notebookToDelete = null }, title = { Text("Delete label?") },
                                            text = { Text("Notes in ${notebook.name} will keep their content and other notebooks. Trashed notes stay in Trash.") },
                                            confirmButton = { TextButton(onClick = { collectionActions?.deleteNotebook?.invoke(notebook.id); notebookToDelete = null }) { Text("Delete") } },
                                            dismissButton = { TextButton(onClick = { notebookToDelete = null }) { Text("Cancel") } }
                                        )
                                    }
                                    notebookToRename?.let { notebook ->
                                        NotebookNameDialog("Rename label", renamedNotebook, { renamedNotebook = it }, uiState.noteNotebooks,
                                            editingId = notebook.id, onDismiss = { notebookToRename = null },
                                            onSave = { collectionActions?.renameNotebook?.invoke(notebook.id, renamedNotebook.trim()); notebookToRename = null })
                                    }
                                    if (!showTrash && selectedGeneralNoteTag == "Highlights") {
                                        NotesHighlights(documents, uiState.allAnnotations, noteSearchQuery, onOpenDocumentAt, onDeleteAnnotations)
                                    } else LazyColumn(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 18.dp),
                                        state = notesListState,
                                        contentPadding = PaddingValues(top = 8.dp, bottom = floatingBottomPadding + 92.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val sourceNotes = if (showTrash) uiState.trashedGeneralNotes else trueGeneralNotes
                                        val notebookNotes = if (showTrash || selectedNotebookId == null) sourceNotes else sourceNotes.filter { selectedNotebookId in it.allLabelIds }
                                        val tagFilteredNotes = if (showTrash) notebookNotes else when (selectedGeneralNoteTag) {
                                            "General" -> notebookNotes.filter { !it.isChecklist && it.reminderAt == null && it.allAudioUrls.isEmpty() }
                                            "Reminder", "Reminders" -> notebookNotes.filter { it.reminderAt != null }
                                            "Pinned" -> notebookNotes.filter { it.pinned }
                                            "Audio", "Voice Memos" -> notebookNotes.filter { it.allAudioUrls.isNotEmpty() }
                                            "Checklists" -> notebookNotes.filter { it.isChecklist }
                                            else -> notebookNotes
                                        }
                                        val filteredNotes = if (noteSearchQuery.isBlank()) tagFilteredNotes else tagFilteredNotes.filter {
                                            it.title.contains(noteSearchQuery, ignoreCase = true) || it.content.contains(noteSearchQuery, ignoreCase = true)
                                        }
                                        val finalNotes = if (showTrash) filteredNotes.sortedByDescending { it.deletedAt } else when (noteSortOrder) {
                                            "title" -> filteredNotes.sortedWith(compareByDescending<GeneralNote> { it.pinned }.thenBy { it.title.lowercase() })
                                            "created" -> filteredNotes.sortedWith(compareByDescending<GeneralNote> { it.pinned }.thenByDescending { it.createdAt })
                                            else -> filteredNotes.sortedWith(compareByDescending<GeneralNote> { it.pinned }.thenByDescending { it.updatedAt })
                                        }

                                        if (finalNotes.isEmpty()) {
                                            item {
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 32.dp, horizontal = 8.dp),
                                                    shape = VeritasPackStyle.cardShape(),
                                                    colors = CardDefaults.cardColors(containerColor = VeritasPackStyle.panelColor(MaterialTheme.colorScheme)),
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
                                                            text = if (noteSearchQuery.isNotBlank()) "No matching notes" else if (showTrash) "Trash is empty" else "No notes found",
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Text(
                                                            text = if (noteSearchQuery.isNotBlank()) "Try searching for a different keyword." else if (showTrash) "Deleted notes appear here until you restore or permanently delete them." else "Try another filter or create a note.",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                        )
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        if (!showTrash) Button(
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
                                            val pinnedNotes = if (showTrash) emptyList() else finalNotes.filter { it.pinned }
                                            val otherNotes = if (showTrash) finalNotes else finalNotes.filter { !it.pinned }

                                            @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
                                            @Composable
                                            fun NoteCardItem(generalNote: GeneralNote) {
                                                val cardBgColor = generalNote.color?.let { Color(android.graphics.Color.parseColor(it)) }
                                                    ?: VeritasPackStyle.panelColor(MaterialTheme.colorScheme)
                                                val onCardColor = if (generalNote.color != null) Color(0xFF1E293B) else MaterialTheme.colorScheme.onSurface
                                                val noteInset = VeritasPackStyle.cardInset(VeritasPackStyle.currentPackId())
                                                var showNoteMenu by remember { mutableStateOf(false) }
                                                var confirmNoteDelete by remember { mutableStateOf(false) }
                                                var showNotebookPicker by remember { mutableStateOf(false) }
                                                var showRevisionHistory by remember { mutableStateOf(false) }
                                                val noteShareContext = LocalContext.current

                                                if (confirmNoteDelete) {
                                                    AlertDialog(
                                                        onDismissRequest = { confirmNoteDelete = false },
                                                        title = { Text(if (showTrash) "Delete permanently?" else "Move to trash?") },
                                                        text = { Text(if (showTrash) "This note will be permanently deleted. Its attachment files are kept to avoid removing media shared by another note." else "You can restore this note from Trash.") },
                                                        confirmButton = {
                                                            TextButton(onClick = {
                                                                confirmNoteDelete = false
                                                                if (showTrash) collectionActions?.permanentlyDeleteNote?.invoke(generalNote.id)
                                                                else onDeleteGeneralNote(generalNote.id)
                                                            }) { Text(if (showTrash) "Delete permanently" else "Move to trash", color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
                                                            .testTag("note_card_${generalNote.id}")
                                                            .combinedClickable(
                                                                onClick = { if (showTrash) showNoteMenu = true else onEditGeneralNote(generalNote) },
                                                                onLongClick = {
                                                                    noteHaptic.longPress()
                                                                    showNoteMenu = true
                                                                }
                                                            )
                                                            .padding(vertical = 4.dp)
                                                            .notesPaperBackground(notesSettings.paperTemplate, onCardColor.copy(alpha = 0.05f)),
                                                        shape = VeritasPackStyle.compactShape(),
                                                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                                                        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme),
                                                        elevation = CardDefaults.cardElevation(defaultElevation = VeritasPackStyle.chromeElevation(VeritasPackStyle.currentPackId()))
                                                    ) {
                                                        Column(modifier = Modifier.padding(start = noteInset, end = noteInset, top = noteInset,
                                                            bottom = if (notesSettings.showRichLinkPreviews && NoteLinks.read(generalNote.content).isNotEmpty()) 0.dp else noteInset),
                                                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            if (notesSettings.richAttachmentPreviews) NoteCardThumbnail(generalNote)
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
                                                                    if (notesSettings.showBookSourceBadges) {
                                                                        val linkedDoc = remember(generalNote.content, generalNote.title, documents) {
                                                                            if (documents.isEmpty()) null
                                                                            else {
                                                                                documents.firstOrNull { doc ->
                                                                                    doc.title.isNotBlank() && (
                                                                                        generalNote.content.contains(doc.title, ignoreCase = true) ||
                                                                                        generalNote.title.contains(doc.title, ignoreCase = true)
                                                                                    )
                                                                                }
                                                                            }
                                                                        }
                                                                        if (linkedDoc != null) {
                                                                            Surface(
                                                                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                                                                shape = RoundedCornerShape(6.dp)
                                                                            ) {
                                                                                Row(
                                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                                                ) {
                                                                                    Icon(
                                                                                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                                                                        contentDescription = null,
                                                                                        modifier = Modifier.size(10.dp),
                                                                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                                                                    )
                                                                                    Text(
                                                                                        text = linkedDoc.title,
                                                                                        style = MaterialTheme.typography.labelSmall,
                                                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                                                        maxLines = 1,
                                                                                        overflow = TextOverflow.Ellipsis
                                                                                    )
                                                                                }
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

                                                            val hasVideo = remember(generalNote.content) {
                                                                generalNote.allVideoUrls.isNotEmpty() || NOTE_VIDEO_TAG_REGEX.containsMatchIn(generalNote.content)
                                                            }
                                                            val hasImage = remember(generalNote.content, generalNote.imageUrl) {
                                                                !generalNote.imageUrl.isNullOrBlank() || generalNote.allImageUrls.isNotEmpty() || NOTE_IMAGE_TAG_REGEX.containsMatchIn(generalNote.content)
                                                            }
                                                            val hasFile = remember(generalNote.content) {
                                                                VeritasNoteEditing.parseNoteBlocks(generalNote.content).any { it is NoteBlock.File }
                                                            }
                                                            val hasAudio = remember(generalNote.content, generalNote.audioUrls, generalNote.audioUrl) {
                                                                generalNote.allAudioUrls.isNotEmpty() || NOTE_AUDIO_TAG_REGEX.containsMatchIn(generalNote.content)
                                                            }
                                                            val firstAudioPath = remember(generalNote.allAudioUrls, generalNote.content) {
                                                                generalNote.allAudioUrls.firstOrNull()
                                                                    ?: NOTE_AUDIO_TAG_REGEX.find(generalNote.content)?.value?.let { extractPathFromTag(it) }
                                                                    ?: ""
                                                            }
                                                            val audioDur = remember(firstAudioPath) {
                                                                formatMediaDuration(firstAudioPath)
                                                            }
                                                            val noteBlocks = remember(generalNote.content) { VeritasNoteEditing.parseNoteBlocks(NoteLinks.strip(generalNote.content)) }
                                                            val fileAttachments = noteBlocks.filterIsInstance<NoteBlock.File>()
                                                            val fileBadgeLabel = fileAttachments.firstOrNull()?.fileName?.substringAfterLast('.', "")?.uppercase()?.take(4)?.ifBlank { "File" } ?: "File"
                                                            val sanitizedContent = remember(generalNote.content) {
                                                                noteBlocks.filterIsInstance<NoteBlock.Text>().joinToString("\n") { it.value.text }.trim()
                                                            }
                                                            if (generalNote.isChecklist && notesSettings.previewLines > 0) {
                                                                val maxItems = notesSettings.previewLines.coerceAtLeast(1)
                                                                val allLines = remember(generalNote.content) {
                                                                    noteBlocks.filterIsInstance<NoteBlock.Text>().joinToString("\n") { it.value.text }.split("\n")
                                                                        .map { it.trim() }
                                                                        .filter { it.removePrefix("[ ] ").removePrefix("[x] ").isNotBlank() }
                                                                }
                                                                val items = allLines.take(maxItems).map { line ->
                                                                    val checked = line.startsWith("[x]")
                                                                    val text = line.removePrefix("[ ] ").removePrefix("[x] ")
                                                                    checked to text
                                                                }
                                                                if (items.isNotEmpty()) {
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
                                                                                    text = RichTextFormatter.transform(text).text,
                                                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                                                        color = if (checked) onCardColor.copy(alpha = 0.5f) else onCardColor,
                                                                                        textDecoration = if (checked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                                                                    ),
                                                                                    maxLines = 1,
                                                                                    overflow = TextOverflow.Ellipsis
                                                                                )
                                                                            }
                                                                        }
                                                                        if (allLines.size > maxItems) {
                                                                            Text(
                                                                                text = "+ ${allLines.size - maxItems} more items",
                                                                                style = MaterialTheme.typography.labelSmall,
                                                                                color = onCardColor.copy(alpha = 0.5f),
                                                                                modifier = Modifier.padding(start = 20.dp)
                                                                            )
                                                                        }
                                                                    }
                                                                } else if (hasVideo || hasImage || hasFile || hasAudio) {
                                                                    Surface(
                                                                        shape = RoundedCornerShape(8.dp),
                                                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                                        modifier = Modifier.fillMaxWidth()
                                                                    ) {
                                                                        Row(
                                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                                            verticalAlignment = Alignment.CenterVertically,
                                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                                        ) {
                                                                            val (mIcon, mLabel) = when {
                                                                                hasVideo -> Icons.Default.Videocam to "Video Note"
                                                                                hasFile -> Icons.Default.AttachFile to (if (fileBadgeLabel != "File") "File • $fileBadgeLabel" else "Attached File")
                                                                                hasImage -> Icons.Default.Image to "Image Note"
                                                                                hasAudio -> Icons.Default.Mic to (if (audioDur.isNotBlank()) "Voice Memo • $audioDur" else "Voice Memo")
                                                                                else -> Icons.Default.AttachFile to "Attachment"
                                                                            }
                                                                            Icon(
                                                                                imageVector = mIcon,
                                                                                contentDescription = mLabel,
                                                                                tint = MaterialTheme.colorScheme.primary,
                                                                                modifier = Modifier.size(16.dp)
                                                                            )
                                                                            Text(
                                                                                text = mLabel,
                                                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                                                                color = onCardColor,
                                                                                maxLines = 1,
                                                                                overflow = TextOverflow.Ellipsis
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            } else {
                                                                if (notesSettings.previewLines > 0 && sanitizedContent.isNotBlank()) {
                                                                    Text(
                                                                        text = RichTextFormatter.transform(MathText.beautify(sanitizedContent)).text,
                                                                        maxLines = notesSettings.previewLines.coerceAtLeast(1),
                                                                        overflow = TextOverflow.Ellipsis,
                                                                        style = MaterialTheme.typography.bodySmall,
                                                                        color = onCardColor
                                                                    )
                                                                } else if (hasVideo || hasImage || hasFile || hasAudio) {
                                                                    Surface(
                                                                        shape = RoundedCornerShape(8.dp),
                                                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                                        modifier = Modifier.fillMaxWidth()
                                                                    ) {
                                                                        Row(
                                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                                            verticalAlignment = Alignment.CenterVertically,
                                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                                        ) {
                                                                            val (mIcon, mLabel) = when {
                                                                                hasVideo -> Icons.Default.Videocam to "Video Note"
                                                                                hasFile -> Icons.Default.AttachFile to (if (fileBadgeLabel != "File") "File • $fileBadgeLabel" else "Attached File")
                                                                                hasImage -> Icons.Default.Image to "Image Note"
                                                                                hasAudio -> Icons.Default.Mic to (if (audioDur.isNotBlank()) "Voice Memo • $audioDur" else "Voice Memo")
                                                                                else -> Icons.Default.AttachFile to "Attachment"
                                                                            }
                                                                            Icon(
                                                                                imageVector = mIcon,
                                                                                contentDescription = mLabel,
                                                                                tint = MaterialTheme.colorScheme.primary,
                                                                                modifier = Modifier.size(16.dp)
                                                                            )
                                                                            Text(
                                                                                text = mLabel,
                                                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                                                                color = onCardColor,
                                                                                maxLines = 1,
                                                                                overflow = TextOverflow.Ellipsis
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            }

                                                            val locale = LocalConfiguration.current.locales[0]
                                                            NoteLabelsRow(uiState.noteNotebooks, generalNote.allLabelIds, compact = true,
                                                                onLabel = { if (!showTrash) onSelectedNotebookChange(it) })
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

                                                                if (notesSettings.showAttachmentBadges) {
                                                                    Row(
                                                                        modifier = Modifier.weight(1f, fill = false),
                                                                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        if (hasVideo) {
                                                                            Surface(
                                                                                shape = RoundedCornerShape(6.dp),
                                                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                                                            ) {
                                                                                Row(
                                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                                                ) {
                                                                                    Icon(
                                                                                        imageVector = Icons.Default.Videocam,
                                                                                        contentDescription = "Video",
                                                                                        tint = MaterialTheme.colorScheme.primary,
                                                                                        modifier = Modifier.size(12.dp)
                                                                                    )
                                                                                    Text(
                                                                                        text = "Video",
                                                                                        style = MaterialTheme.typography.labelSmall,
                                                                                        color = onCardColor.copy(alpha = 0.75f)
                                                                                    )
                                                                                }
                                                                            }
                                                                        }
                                                                        if (hasImage) {
                                                                            Surface(
                                                                                shape = RoundedCornerShape(6.dp),
                                                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                                                            ) {
                                                                                Row(
                                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                                                ) {
                                                                                    Icon(
                                                                                        imageVector = Icons.Default.Image,
                                                                                        contentDescription = "Image",
                                                                                        tint = MaterialTheme.colorScheme.primary,
                                                                                        modifier = Modifier.size(12.dp)
                                                                                    )
                                                                                    Text(
                                                                                        text = "Image",
                                                                                        style = MaterialTheme.typography.labelSmall,
                                                                                        color = onCardColor.copy(alpha = 0.75f)
                                                                                    )
                                                                                }
                                                                            }
                                                                        }
                                                                        if (hasFile) {
                                                                            Surface(
                                                                                shape = RoundedCornerShape(6.dp),
                                                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                                                            ) {
                                                                                Row(
                                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                                                ) {
                                                                                    Icon(
                                                                                        imageVector = Icons.Default.AttachFile,
                                                                                        contentDescription = "File",
                                                                                        tint = MaterialTheme.colorScheme.primary,
                                                                                        modifier = Modifier.size(12.dp)
                                                                                    )
                                                                                    Text(
                                                                                        text = fileBadgeLabel,
                                                                                        style = MaterialTheme.typography.labelSmall,
                                                                                        color = onCardColor.copy(alpha = 0.75f),
                                                                                        maxLines = 1,
                                                                                        overflow = TextOverflow.Ellipsis
                                                                                    )
                                                                                }
                                                                            }
                                                                        }
                                                                        if (hasAudio) {
                                                                            Surface(
                                                                                shape = RoundedCornerShape(6.dp),
                                                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                                                            ) {
                                                                                Row(
                                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                                                ) {
                                                                                    Icon(
                                                                                        imageVector = Icons.Default.Mic,
                                                                                        contentDescription = "Audio",
                                                                                        tint = MaterialTheme.colorScheme.primary,
                                                                                        modifier = Modifier.size(12.dp)
                                                                                    )
                                                                                    Text(
                                                                                        text = if (audioDur.isNotBlank()) audioDur else "Audio",
                                                                                        style = MaterialTheme.typography.labelSmall,
                                                                                        color = onCardColor.copy(alpha = 0.75f)
                                                                                    )
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                } else {
                                                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                                        if (hasImage) {
                                                                            Icon(
                                                                                imageVector = Icons.Default.Image,
                                                                                contentDescription = "Image attachment",
                                                                                tint = onCardColor.copy(alpha = 0.5f),
                                                                                modifier = Modifier.size(13.dp)
                                                                            )
                                                                        }
                                                                        if (hasAudio) {
                                                                            Icon(
                                                                                imageVector = Icons.Default.Mic,
                                                                                contentDescription = "Audio attachment",
                                                                                tint = onCardColor.copy(alpha = 0.5f),
                                                                                modifier = Modifier.size(13.dp)
                                                                            )
                                                                        }
                                                                        if (hasVideo) {
                                                                            Icon(
                                                                                imageVector = Icons.Default.Videocam,
                                                                                contentDescription = "Video attachment",
                                                                                tint = onCardColor.copy(alpha = 0.5f),
                                                                                modifier = Modifier.size(13.dp)
                                                                            )
                                                                        }
                                                                        if (hasFile) {
                                                                            Icon(
                                                                                imageVector = Icons.Default.AttachFile,
                                                                                contentDescription = "File attachment",
                                                                                tint = onCardColor.copy(alpha = 0.5f),
                                                                                modifier = Modifier.size(13.dp)
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        if (notesSettings.showRichLinkPreviews) {
                                                            NoteLinks.read(generalNote.content).take(1).forEach { NoteLinkCard(it, footer = true) }
                                                        }
                                                    }
                                                    DropdownMenu(expanded = showNoteMenu, onDismissRequest = { showNoteMenu = false }) {
                                                        if (!showTrash) Row(
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
                                                        if (showTrash) {
                                                            DropdownMenuItem(text = { Text("Restore") }, onClick = { showNoteMenu = false; collectionActions?.restoreNote?.invoke(generalNote.id) })
                                                        } else {
                                                            DropdownMenuItem(
                                                                text = { Text(if (generalNote.pinned) "Unpin" else "Pin") },
                                                                leadingIcon = { Icon(if (generalNote.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, contentDescription = null) },
                                                                onClick = { showNoteMenu = false; onToggleGeneralNotePin(generalNote.id) }
                                                            )
                                                            if (collectionActions != null) {
                                                                DropdownMenuItem(text = { Text("Notebooks") }, onClick = { showNoteMenu = false; showNotebookPicker = true })
                                                                DropdownMenuItem(text = { Text("Revision history") }, onClick = { showNoteMenu = false; showRevisionHistory = true })
                                                            }
                                                            DropdownMenuItem(
                                                                text = { Text("Share") },
                                                                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                                                                onClick = { showNoteMenu = false; shareGeneralNote(noteShareContext, generalNote) }
                                                            )
                                                        }
                                                        DropdownMenuItem(
                                                            text = { Text(if (showTrash) "Delete permanently" else "Move to trash") },
                                                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                                            onClick = { showNoteMenu = false; confirmNoteDelete = true }
                                                        )
                                                    }
                                                    if (showNotebookPicker && collectionActions != null) {
                                                        NoteLabelsDialog(uiState.noteNotebooks, generalNote.allLabelIds,
                                                            onSave = { collectionActions.setLabels(generalNote.id, it); showNotebookPicker = false },
                                                            onDismiss = { showNotebookPicker = false },
                                                            onCreate = collectionActions.createLabel)
                                                    }
                                                    if (showRevisionHistory && collectionActions != null) {
                                                        NoteRevisionHistoryDialog(generalNote.id, collectionActions,
                                                            onDismiss = { showRevisionHistory = false })
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
                                                                    key(note.id) { NoteCardItem(note) }
                                                                }
                                                            }

                                                            Column(
                                                                modifier = Modifier.weight(1f),
                                                                verticalArrangement = Arrangement.spacedBy(0.dp)
                                                            ) {
                                                                rightNotes.forEach { note ->
                                                                    key(note.id) { NoteCardItem(note) }
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
                                                                    key(note.id) { NoteCardItem(note) }
                                                                }
                                                            }

                                                            Column(
                                                                modifier = Modifier.weight(1f),
                                                                verticalArrangement = Arrangement.spacedBy(0.dp)
                                                            ) {
                                                                rightNotes.forEach { note ->
                                                                    key(note.id) { NoteCardItem(note) }
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

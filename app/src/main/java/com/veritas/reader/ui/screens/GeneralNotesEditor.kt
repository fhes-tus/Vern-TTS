package com.veritas.reader.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.veritas.reader.GeneralNote
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralNotesEditor(
    note: GeneralNote?,
    onSave: (title: String, content: String, color: String?, isPinned: Boolean, isChecklist: Boolean, imageUrl: String?, audioUrl: String?, reminderAt: Long?, shouldDismiss: Boolean, allAudioUrls: List<String>) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
    onCopy: () -> Unit = {}
) {
    val context = LocalContext.current
    val initialRawContent = note?.content ?: ""
    val initialContentWithAttachments = remember(note) {
        val sb = StringBuilder()
        val legacyImg = note?.imageUrl?.takeIf { it.isNotBlank() }
        if (legacyImg != null && !initialRawContent.contains(legacyImg)) {
            sb.append("![image](").append(legacyImg).append(")\n")
        }
        sb.append(initialRawContent)
        note?.allAudioUrls?.forEach { aUrl ->
            if (aUrl.isNotBlank() && !sb.contains(aUrl)) {
                if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append("\n")
                sb.append("[audio](").append(aUrl).append(")\n")
            }
        }
        sb.toString()
    }
    var title by remember { mutableStateOf(note?.title ?: "") }
    val blocks = remember(note?.id) {
        mutableStateListOf<NoteBlock>().apply {
            addAll(VeritasNoteEditing.parseNoteBlocks(initialContentWithAttachments))
        }
    }
    var focusedBlockIndex by remember { mutableIntStateOf(0) }
    var contentText by remember { mutableStateOf(VeritasNoteEditing.serializeNoteBlocks(blocks)) }
    var contentValue by remember {
        val firstText = blocks.filterIsInstance<NoteBlock.Text>().firstOrNull()?.value ?: TextFieldValue(contentText)
        mutableStateOf(firstText)
    }
    var editVersion by remember { mutableIntStateOf(0) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var noteColor by remember { mutableStateOf(note?.color) }
    var isPinned by remember { mutableStateOf(note?.pinned ?: false) }
    var isChecklist by remember { mutableStateOf(note?.isChecklist ?: false) }
    var imageUrl by remember { mutableStateOf(note?.imageUrl) }
    var audioUrls by remember { mutableStateOf(note?.allAudioUrls ?: emptyList()) }
    var reminderAt by remember { mutableStateOf(note?.reminderAt) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showReminderMenu by remember { mutableStateOf(false) }
    var confirmDeleteNote by remember { mutableStateOf(false) }
    var showExactAlarmPrompt by remember { mutableStateOf(false) }

    fun syncBlocksToContent() {
        contentText = VeritasNoteEditing.serializeNoteBlocks(blocks)
        val firstImg = blocks.filterIsInstance<NoteBlock.Image>().firstOrNull()?.path
        if (firstImg != null) imageUrl = firstImg
        val noteAudios = blocks.filterIsInstance<NoteBlock.Audio>().map { it.path }
        if (noteAudios.isNotEmpty()) {
            audioUrls = (noteAudios + audioUrls).distinct()
        }
        hasUnsavedChanges = true
        editVersion++
    }
    var viewingImagePath by remember { mutableStateOf<String?>(null) }
    var viewingVideoPath by remember { mutableStateOf<String?>(null) }

    fun insertAttachmentAtCursor(attachment: NoteBlock) {
        if (blocks.isEmpty()) {
            blocks.add(attachment)
            val newText = NoteBlock.Text(TextFieldValue(""))
            blocks.add(newText)
            focusedBlockIndex = 1
            contentValue = newText.value
            syncBlocksToContent()
            return
        }

        val targetIndex = focusedBlockIndex.coerceIn(0, blocks.lastIndex)
        val currentBlock = blocks.getOrNull(targetIndex)
        if (currentBlock is NoteBlock.Text) {
            val tfv = currentBlock.value
            val cursorPos = tfv.selection.start.coerceIn(0, tfv.text.length)
            val textBefore = tfv.text.substring(0, cursorPos).trimEnd('\n')
            val textAfter = tfv.text.substring(cursorPos).trimStart('\n')

            if (textBefore.isNotEmpty()) {
                currentBlock.value = TextFieldValue(textBefore, TextRange(textBefore.length))
                blocks[targetIndex] = NoteBlock.Text(TextFieldValue(textBefore, TextRange(textBefore.length)))
                blocks.add(targetIndex + 1, attachment)
                val remainingBlock = NoteBlock.Text(TextFieldValue(textAfter, TextRange(0)))
                blocks.add(targetIndex + 2, remainingBlock)
                focusedBlockIndex = targetIndex + 2
                contentValue = remainingBlock.value
            } else {
                blocks.add(targetIndex, attachment)
                val remainingBlock = NoteBlock.Text(TextFieldValue(textAfter, TextRange(0)))
                blocks[targetIndex + 1] = remainingBlock
                focusedBlockIndex = targetIndex + 1
                contentValue = remainingBlock.value
            }
        } else {
            val insertAt = (targetIndex + 1).coerceIn(0, blocks.size)
            blocks.add(insertAt, attachment)
            if (insertAt + 1 >= blocks.size || blocks[insertAt + 1] !is NoteBlock.Text) {
                blocks.add(insertAt + 1, NoteBlock.Text(TextFieldValue("")))
            }
            focusedBlockIndex = (insertAt + 1).coerceAtMost(blocks.lastIndex)
            val nextText = blocks.getOrNull(focusedBlockIndex) as? NoteBlock.Text
            if (nextText != null) contentValue = nextText.value
        }
        syncBlocksToContent()
    }

    fun removeBlockAt(idx: Int) {
        if (idx !in blocks.indices) return
        val removed = blocks.removeAt(idx)
        if (removed is NoteBlock.Image && imageUrl == removed.path) {
            imageUrl = blocks.filterIsInstance<NoteBlock.Image>().firstOrNull()?.path
        } else if (removed is NoteBlock.Audio) {
            audioUrls = audioUrls.filter { it != removed.path }
        }
        val beforeIdx = idx - 1
        if (beforeIdx >= 0 && beforeIdx < blocks.size && blocks[beforeIdx] is NoteBlock.Text &&
            idx < blocks.size && blocks[idx] is NoteBlock.Text
        ) {
            val prevText = (blocks[beforeIdx] as NoteBlock.Text).value
            val nextText = (blocks[idx] as NoteBlock.Text).value
            val merged = TextFieldValue(
                text = prevText.text + nextText.text,
                selection = TextRange(prevText.text.length)
            )
            (blocks[beforeIdx] as NoteBlock.Text).value = merged
            blocks[beforeIdx] = NoteBlock.Text(merged)
            blocks.removeAt(idx)
            focusedBlockIndex = beforeIdx
            contentValue = merged
        } else {
            focusedBlockIndex = idx.coerceAtMost(blocks.lastIndex)
            val cur = blocks.getOrNull(focusedBlockIndex) as? NoteBlock.Text
            if (cur != null) contentValue = cur.value
        }
        if (blocks.isEmpty()) {
            val emptyText = NoteBlock.Text(TextFieldValue(""))
            blocks.add(emptyText)
            focusedBlockIndex = 0
            contentValue = emptyText.value
        }
        syncBlocksToContent()
    }

    fun moveBlockUp(idx: Int) {
        if (idx > 0 && idx < blocks.size) {
            val item = blocks.removeAt(idx)
            blocks.add(idx - 1, item)
            syncBlocksToContent()
            editVersion++
            hasUnsavedChanges = true
        }
    }

    fun moveBlockDown(idx: Int) {
        if (idx >= 0 && idx < blocks.size - 1) {
            val item = blocks.removeAt(idx)
            blocks.add(idx + 1, item)
            syncBlocksToContent()
            editVersion++
            hasUnsavedChanges = true
        }
    }

    fun copyAttachment(path: String) {
        com.veritas.reader.copyTextToClipboard(context, "Attachment", path)
    }

    // Note reminders use standard setAndAllowWhileIdle without requiring special system settings permission
    if (confirmDeleteNote && note != null) {
        DeleteNoteConfirmDialog(
            onConfirmDelete = {
                confirmDeleteNote = false
                onDelete(note.id)
            },
            onDismiss = { confirmDeleteNote = false }
        )
    }

    var expandedMenu by remember { mutableStateOf(NotesToolbarMenu.NONE) }
    var triggerImagePickerOnStart by remember { mutableStateOf(false) }

    val focusRequesters = remember { mutableMapOf<Int, FocusRequester>() }
    val items = remember {
        val list = mutableStateListOf<Pair<Boolean, TextFieldValue>>()
        val lines = contentText.split("\n").filter { it.isNotBlank() }
        lines.forEach { line ->
            val checked = line.startsWith("[x]")
            val text = line.removePrefix("[ ] ").removePrefix("[x] ")
            list.add(checked to TextFieldValue(text, TextRange(text.length)))
        }
        if (list.isEmpty()) {
            list.add(false to TextFieldValue(""))
        }
        list
    }

    fun updateChecklistString() {
        val result = items.joinToString("\n") { (checked, tfv) ->
            if (checked) "[x] ${tfv.text}" else "[ ] ${tfv.text}"
        }
        contentText = result
        contentValue = TextFieldValue(result)
        hasUnsavedChanges = true
        editVersion++
    }

    val undoStack = remember { mutableStateListOf<TextFieldValue>() }
    val redoStack = remember { mutableStateListOf<TextFieldValue>() }

    fun pushHistory() {
        val activeTfv = (blocks.getOrNull(focusedBlockIndex) as? NoteBlock.Text)?.value ?: contentValue
        undoStack.add(activeTfv)
        if (undoStack.size > 200) undoStack.removeAt(0)
        redoStack.clear()
        hasUnsavedChanges = true
        editVersion++
    }

    val mediaManager = rememberGeneralNotesMediaManager(
        onInsertAttachment = { insertAttachmentAtCursor(it) },
        audioUrls = audioUrls,
        onAudioUrlsChanged = {
            audioUrls = it
            hasUnsavedChanges = true
            editVersion++
        },
        onImageUrlChanged = {
            imageUrl = it
            hasUnsavedChanges = true
            editVersion++
        },
        focusedBlockIndex = focusedBlockIndex,
        blocksCount = blocks.size
    )

    // Continuous auto-save: debounced 800ms
    LaunchedEffect(editVersion, title, noteColor, isPinned, isChecklist, imageUrl, audioUrls, reminderAt) {
        if (editVersion == 0 && !hasUnsavedChanges) return@LaunchedEffect
        delay(800)
        val contentToSave = if (isChecklist) {
            items.joinToString("\n") { (checked, tfv) ->
                if (checked) "[x] ${tfv.text}" else "[ ] ${tfv.text}"
            }
        } else {
            VeritasNoteEditing.serializeNoteBlocks(blocks)
        }
        if (title.isNotBlank() || contentToSave.isNotBlank() || imageUrl != null || audioUrls.isNotEmpty()) {
            onSave(title, contentToSave, noteColor, isPinned, isChecklist, imageUrl, audioUrls.firstOrNull(), reminderAt, false, audioUrls)
            hasUnsavedChanges = false
        }
    }

    // Heartbeat auto-save: 2000ms
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
            if (hasUnsavedChanges) {
                val currentContent = if (isChecklist) {
                    items.joinToString("\n") { (checked, tfv) ->
                        if (checked) "[x] ${tfv.text}" else "[ ] ${tfv.text}"
                    }
                } else {
                    VeritasNoteEditing.serializeNoteBlocks(blocks)
                }
                if (title.isNotBlank() || currentContent.isNotBlank() || imageUrl != null || audioUrls.isNotEmpty()) {
                    onSave(title, currentContent, noteColor, isPinned, isChecklist, imageUrl, audioUrls.firstOrNull(), reminderAt, false, audioUrls)
                    hasUnsavedChanges = false
                }
            }
        }
    }

    fun mutateActiveText(transform: (TextFieldValue) -> TextFieldValue) {
        if (isChecklist) return
        pushHistory()
        val textBlock = blocks.getOrNull(focusedBlockIndex) as? NoteBlock.Text
        if (textBlock != null) {
            val newValue = transform(textBlock.value)
            blocks[focusedBlockIndex] = NoteBlock.Text(newValue)
            contentValue = newValue
            syncBlocksToContent()
        } else {
            val newValue = transform(contentValue)
            contentValue = newValue
            contentText = newValue.text
        }
    }

    fun shareNote() {
        val body = if (isChecklist) {
            contentText.lineSequence().joinToString("\n") { line ->
                when {
                    line.startsWith("[x]") -> "☑ " + line.removePrefix("[x]").trim()
                    line.startsWith("[ ]") -> "☐ " + line.removePrefix("[ ]").trim()
                    else -> line
                }
            }
        } else {
            RichTextFormatter.stripMarkup(contentValue.text)
        }
        val plain = buildString {
            if (title.isNotBlank()) {
                append(title)
                append("\n\n")
            }
            append(body)
        }.trim()
        if (plain.isBlank()) {
            Toast.makeText(context, "Nothing to share yet", Toast.LENGTH_SHORT).show()
            return
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title.ifBlank { "Vern note" })
            putExtra(Intent.EXTRA_TEXT, plain)
        }
        runCatching { context.startActivity(Intent.createChooser(send, "Share note")) }
    }

    fun pickReminderDateTime() {
        val now = Calendar.getInstance()
        val base = reminderAt?.let { Calendar.getInstance().apply { timeInMillis = it } } ?: now
        DatePickerDialog(
            context,
            { _, year, month, day ->
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        val cal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, year)
                            set(Calendar.MONTH, month)
                            set(Calendar.DAY_OF_MONTH, day)
                            set(Calendar.HOUR_OF_DAY, hour)
                            set(Calendar.MINUTE, minute)
                            set(Calendar.SECOND, 0)
                        }
                        if (cal.timeInMillis <= System.currentTimeMillis()) {
                            Toast.makeText(context, "Pick a time in the future", Toast.LENGTH_SHORT).show()
                        } else {
                            reminderAt = cal.timeInMillis
                        }
                    },
                    base.get(Calendar.HOUR_OF_DAY),
                    base.get(Calendar.MINUTE),
                    false
                ).show()
            },
            base.get(Calendar.YEAR),
            base.get(Calendar.MONTH),
            base.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = now.timeInMillis
        }.show()
    }

    val cardBgColor = noteColor?.let { Color(android.graphics.Color.parseColor(it)) } ?: MaterialTheme.colorScheme.surface
    val onCardColor = if (noteColor != null) Color.Black else MaterialTheme.colorScheme.onSurface
    val richTextTransformation = remember(onCardColor) { RichTextVisualTransformation(onCardColor) }

    fun performSave() {
        val finalContent = if (isChecklist) {
            items.joinToString("\n") { (checked, tfv) ->
                if (checked) "[x] ${tfv.text}" else "[ ] ${tfv.text}"
            }
        } else {
            VeritasNoteEditing.serializeNoteBlocks(blocks)
        }
        onSave(title, finalContent, noteColor, isPinned, isChecklist, imageUrl, audioUrls.firstOrNull(), reminderAt, true, audioUrls)
        hasUnsavedChanges = false
    }

    BackHandler {
        val hasAnyText = if (isChecklist) items.any { it.second.text.isNotBlank() } else blocks.any { it is NoteBlock.Text && it.value.text.isNotBlank() }
        if (title.isNotBlank() || hasAnyText || imageUrl != null || audioUrls.isNotEmpty()) {
            performSave()
        } else {
            onDismiss()
        }
    }

    Scaffold(
        topBar = {
            NotesTopAppBar(
                isPinned = isPinned,
                reminderAt = reminderAt,
                showReminderMenu = showReminderMenu,
                cardBgColor = cardBgColor,
                onCardColor = onCardColor,
                onBack = {
                    val hasAnyText = if (isChecklist) items.any { it.second.text.isNotBlank() } else blocks.any { it is NoteBlock.Text && it.value.text.isNotBlank() }
                    if (title.isNotBlank() || hasAnyText || imageUrl != null || audioUrls.isNotEmpty()) {
                        performSave()
                    } else {
                        onDismiss()
                    }
                },
                onTogglePin = { isPinned = !isPinned },
                onShowReminderMenu = { showReminderMenu = true },
                onDismissReminderMenu = { showReminderMenu = false },
                onSetReminderLaterToday = {
                    reminderAt = System.currentTimeMillis() + 3 * 60 * 60 * 1000L
                    showReminderMenu = false
                },
                onSetReminderTomorrow = {
                    val cal = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, 1)
                        set(Calendar.HOUR_OF_DAY, 9)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                    }
                    reminderAt = cal.timeInMillis
                    showReminderMenu = false
                },
                onPickReminderDateTime = {
                    showReminderMenu = false
                    pickReminderDateTime()
                },
                onRemoveReminder = {
                    reminderAt = null
                    showReminderMenu = false
                },
                onSave = { performSave() }
            )
        },
        bottomBar = {
            NotesBottomToolbar(
                onCardColor = onCardColor,
                cardBgColor = cardBgColor,
                expandedMenu = expandedMenu,
                showColorPicker = showColorPicker,
                noteColor = noteColor,
                isChecklist = isChecklist,
                isRecording = mediaManager.isRecording,
                hasAudio = audioUrls.isNotEmpty(),
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                hasExistingNote = note != null,
                onCloseMenu = { expandedMenu = NotesToolbarMenu.NONE },
                onOpenAttachments = { expandedMenu = NotesToolbarMenu.ATTACHMENTS },
                onOpenFormatting = { expandedMenu = NotesToolbarMenu.FORMATTING },
                onToggleColorPicker = { showColorPicker = !showColorPicker },
                onToggleRecording = mediaManager.onToggleRecording,
                onUndo = {
                    if (undoStack.isNotEmpty()) {
                        val prev = undoStack.removeLast()
                        redoStack.add(contentValue)
                        contentValue = prev
                        if (isChecklist) {
                            contentText = prev.text
                        }
                    }
                },
                onRedo = {
                    if (redoStack.isNotEmpty()) {
                        val next = redoStack.removeLast()
                        undoStack.add(contentValue)
                        contentValue = next
                        if (isChecklist) {
                            contentText = next.text
                        }
                    }
                },
                onShare = { shareNote() },
                onDelete = { confirmDeleteNote = true },
                onCopy = { onCopy() },
                onColorSelected = { hex ->
                    noteColor = hex
                    showColorPicker = false
                },
                onCycleHeading = { mutateActiveText { VeritasNoteEditing.cycleHeading(it) } },
                onApplyMarker = { marker -> mutateActiveText { VeritasNoteEditing.toggleInlineMarker(it, marker) } },
                onToggleQuote = { mutateActiveText { VeritasNoteEditing.toggleQuotePrefix(it) } },
                onApplyLinePrefix = { prefix -> mutateActiveText { VeritasNoteEditing.toggleLinePrefix(it, prefix) } },
                onToggleTask = { mutateActiveText { VeritasNoteEditing.toggleTaskCheckbox(it) } },
                onApplyOutdent = { mutateActiveText { VeritasNoteEditing.applyOutdent(it) } },
                onApplyIndent = { mutateActiveText { VeritasNoteEditing.applyIndent(it) } },
                onToggleChecklist = {
                    val activeTfv = (blocks.getOrNull(focusedBlockIndex) as? NoteBlock.Text)?.value ?: contentValue
                    if (activeTfv.selection.min != activeTfv.selection.max) {
                        mutateActiveText { VeritasNoteEditing.toggleTaskCheckbox(it) }
                    } else {
                        isChecklist = !isChecklist
                        if (isChecklist && contentText.isBlank()) {
                            contentText = "[ ] "
                        }
                    }
                    expandedMenu = NotesToolbarMenu.NONE
                },
                onPickImage = {
                    mediaManager.onPickImage()
                    expandedMenu = NotesToolbarMenu.NONE
                },
                onPickVideo = {
                    mediaManager.onPickVideo()
                    expandedMenu = NotesToolbarMenu.NONE
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(cardBgColor)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (reminderAt != null) {
                val label = remember(reminderAt) {
                    SimpleDateFormat("EEE, d MMM • h:mm a", Locale.getDefault()).format(Date(reminderAt!!))
                }
                AssistChip(
                    onClick = { showReminderMenu = true },
                    label = { Text("Reminder: $label") },
                    leadingIcon = { Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Remove reminder",
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { reminderAt = null }
                        )
                    }
                )
            }

            if (mediaManager.isRecording) {
                NoteLiveRecordingBar(
                    recordingDurationSec = mediaManager.recordingDurationSec,
                    recordingAmplitudes = mediaManager.recordingAmplitudes,
                    onCancel = { mediaManager.onCancelRecording() },
                    onStop = { mediaManager.onToggleRecording() }
                )
            }

            TextField(
                value = title,
                onValueChange = {
                    title = it
                    hasUnsavedChanges = true
                    editVersion++
                },
                placeholder = {
                    Text(
                        "Title",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = onCardColor.copy(alpha = 0.4f)
                    )
                },
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = onCardColor
                ),
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = onCardColor
                )
            )

            NotesBlocksList(
                blocks = blocks,
                isChecklist = isChecklist,
                items = items,
                focusRequesters = focusRequesters,
                onCardColor = onCardColor,
                richTextTransformation = richTextTransformation,
                editVersion = editVersion,
                isPlaying = mediaManager.isPlaying,
                activePlayingAudioPath = mediaManager.activePlayingAudioPath,
                playProgress = mediaManager.playProgress,
                currentPosition = mediaManager.currentPosition,
                onTextBlockChange = { index, block, processed ->
                    val prev = block.value
                    val isBoundary = processed.text.length < prev.text.length ||
                        (processed.text.length - prev.text.length) > 1 ||
                        processed.text.lastOrNull()?.isWhitespace() == true
                    if (isBoundary && prev.text != processed.text) {
                        undoStack.add(prev)
                        if (undoStack.size > 200) undoStack.removeAt(0)
                        redoStack.clear()
                    }
                    block.value = processed
                    contentValue = processed
                    focusedBlockIndex = index
                    hasUnsavedChanges = true
                    editVersion++
                },
                onTextBlockFocus = { index, block ->
                    focusedBlockIndex = index
                    contentValue = block.value
                },
                onTextBlockBackspaceAtStart = { index ->
                    if (index > 0) {
                        val prevBlock = blocks.getOrNull(index - 1)
                        if (prevBlock !is NoteBlock.Text) {
                            removeBlockAt(index - 1)
                            true
                        } else false
                    } else false
                },
                onViewImage = { viewingImagePath = it },
                onViewVideo = { viewingVideoPath = it },
                onCopyAttachment = { copyAttachment(it) },
                onMoveBlockUp = { moveBlockUp(it) },
                onMoveBlockDown = { moveBlockDown(it) },
                onRemoveBlock = { removeBlockAt(it) },
                onTogglePlayAudio = { mediaManager.onTogglePlayAudio(it) },
                onSeekAudio = { frac, path -> mediaManager.onSeekAudio(frac, path) },
                onChecklistChanged = {
                    updateChecklistString()
                    hasUnsavedChanges = true
                    editVersion++
                }
            )
        }
    }

    viewingImagePath?.let { imgPath ->
        NoteImageViewerDialog(
            path = imgPath,
            onShare = { path -> shareMediaFile(context, path, "image/*") },
            onDismiss = { viewingImagePath = null }
        )
    }

    viewingVideoPath?.let { vidPath ->
        NoteVideoViewerDialog(
            path = vidPath,
            onOpenExternal = { path -> openVideoFile(context, path) },
            onShare = { path -> shareMediaFile(context, path, "video/*") },
            onDismiss = { viewingVideoPath = null }
        )
    }
}

package com.veritas.reader.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import com.veritas.reader.ui.NotesSettings
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralNotesEditor(
    note: GeneralNote?,
    saveStatus: String = "",
    notesSettings: NotesSettings = NotesSettings(),
    onSaveNotesSettings: (NotesSettings) -> Unit = {},
    notebooks: List<com.veritas.reader.NoteNotebook> = emptyList(),
    onCreateNotebook: suspend (String) -> com.veritas.reader.NoteNotebook = { error("Notebook creation unavailable") },
    onSave: (title: String, content: String, color: String?, isPinned: Boolean, isChecklist: Boolean, imageUrl: String?, audioUrl: String?, reminderAt: Long?, shouldDismiss: Boolean, allAudioUrls: List<String>, notebookIds: List<String>) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
    onCopyDraft: (String, String, String?, Boolean, Boolean, String?, List<String>, Long?, List<String>) -> Unit = { _, _, _, _, _, _, _, _, _ -> }
) {
    val context = LocalContext.current
    var showNotesSettings by remember { mutableStateOf(false) }
    var showNotebooks by remember { mutableStateOf(false) }
    var notebookIds by rememberSaveable { mutableStateOf(note?.allLabelIds.orEmpty()) }
    val initialRawContent = NoteLinks.strip(note?.content ?: "")
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
    var title by rememberSaveable { mutableStateOf(note?.title ?: "") }
    val blockSaver = remember { Saver<androidx.compose.runtime.snapshots.SnapshotStateList<NoteBlock>, String>(
        save = { VeritasNoteEditing.serializeNoteBlocks(it) },
        restore = { raw -> mutableStateListOf<NoteBlock>().apply { addAll(VeritasNoteEditing.parseNoteBlocks(raw)) } }
    ) }
    val blocks = rememberSaveable(saver = blockSaver) {
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
    var hasUnsavedChanges by rememberSaveable { mutableStateOf(false) }
    if (showNotebooks) NoteLabelsDialog(notebooks, notebookIds,
        onSave = { ids ->
            if (ids != notebookIds) { notebookIds = ids; hasUnsavedChanges = true; editVersion++ }
            showNotebooks = false
        }, onDismiss = { showNotebooks = false }, onCreate = onCreateNotebook)
    var noteColor by rememberSaveable { mutableStateOf(note?.color) }
    var isPinned by rememberSaveable { mutableStateOf(note?.pinned ?: false) }
    var isChecklist by rememberSaveable { mutableStateOf(note?.isChecklist ?: notesSettings.newNoteIsChecklist) }
    var imageUrl by remember { mutableStateOf(blocks.filterIsInstance<NoteBlock.Image>().firstOrNull()?.path) }
    var audioUrls by remember { mutableStateOf(blocks.filterIsInstance<NoteBlock.Audio>().map { it.path }) }
    var reminderAt by rememberSaveable { mutableStateOf(note?.reminderAt) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showReminderMenu by remember { mutableStateOf(false) }
    var confirmDeleteNote by remember { mutableStateOf(false) }

    val linkSaver = Saver<androidx.compose.runtime.snapshots.SnapshotStateList<NoteLink>, String>(
        save = { NoteLinks.write("", it) }, restore = { mutableStateListOf<NoteLink>().apply { addAll(NoteLinks.read(it)) } })
    var dismissedLinks by rememberSaveable { mutableStateOf(NoteLinks.dismissed(note?.content.orEmpty())) }
    val fetchedLinkUrls = remember { mutableSetOf<String>() }
    val links = rememberSaveable(saver = linkSaver) {
        mutableStateListOf<NoteLink>().apply {
            addAll(NoteLinks.read(note?.content.orEmpty()))
            NoteLinks.urls(initialRawContent).filter { url -> url !in dismissedLinks && none { it.url == url } }.forEach { add(NoteLink(it, NoteLinks.host(it))) }
        }
    }
    val seenLinkUrls = remember { mutableSetOf<String>().apply { addAll(links.map { it.url }); addAll(dismissedLinks) } }
    val initialHistory = remember {
        NoteUndoHistory.read(note?.id, NoteEditorSnapshot(NoteLinks.write(VeritasNoteEditing.serializeNoteBlocks(blocks), links, dismissedLinks), isChecklist, 0, TextRange.Zero, title))
    }
    val undoStack = remember { mutableStateListOf<NoteEditorSnapshot>().apply { addAll(initialHistory?.undo.orEmpty()) } }
    val redoStack = remember { mutableStateListOf<NoteEditorSnapshot>().apply { addAll(initialHistory?.redo.orEmpty()) } }
    var lastTextEditAt by remember { mutableStateOf(0L) }
    var lastTextEditBlock by remember { mutableIntStateOf(-1) }

    fun captureSnapshot() = NoteEditorSnapshot(
        NoteLinks.write(if (isChecklist) contentText else VeritasNoteEditing.serializeNoteBlocks(blocks), links, dismissedLinks), isChecklist, focusedBlockIndex,
        (blocks.getOrNull(focusedBlockIndex) as? NoteBlock.Text)?.value?.selection ?: TextRange.Zero, title
    )
    fun pushHistory() {
        lastTextEditAt = 0L
        val snapshot = captureSnapshot()
        undoStack.removeAll { snapshot.at - it.at >= NoteUndoHistory.LIFETIME_MS }
        if (undoStack.lastOrNull()?.let { it.copy(at = snapshot.at) } != snapshot) undoStack.add(snapshot)
        while (undoStack.size > 60 || (undoStack.size > 1 && undoStack.sumOf { it.content.length } > 2_000_000)) undoStack.removeAt(0)
        redoStack.clear()
    }

    fun syncBlocksToContent() {
        contentText = VeritasNoteEditing.serializeNoteBlocks(blocks)
        val firstImg = blocks.filterIsInstance<NoteBlock.Image>().firstOrNull()?.path
        imageUrl = firstImg
        val noteAudios = blocks.filterIsInstance<NoteBlock.Audio>().map { it.path }
        audioUrls = noteAudios.distinct()
        hasUnsavedChanges = true
        editVersion++
    }
    var viewingImagePath by remember { mutableStateOf<String?>(null) }
    var viewingVideoPath by remember { mutableStateOf<String?>(null) }

    fun insertAttachmentAtCursor(attachment: NoteBlock) {
        pushHistory()
        isChecklist = false
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
        pushHistory()
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
                text = prevText.text + "\n" + nextText.text,
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
        pushHistory()
        if (idx > 0 && idx < blocks.size) {
            val item = blocks.removeAt(idx)
            blocks.add(idx - 1, item)
            syncBlocksToContent()
            editVersion++
            hasUnsavedChanges = true
        }
    }

    fun moveBlockDown(idx: Int) {
        pushHistory()
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

    val focusRequesters = remember { mutableMapOf<Int, FocusRequester>() }
    val items = remember {
        val list = mutableStateListOf<Pair<Boolean, TextFieldValue>>()
        val lines = blocks.filterIsInstance<NoteBlock.Text>().joinToString("\n") { it.value.text }.split("\n").filter { it.isNotBlank() }
        lines.forEach { line ->
            val checked = Regex("^(?:- )?\\[[xX]\\]").containsMatchIn(line)
            val text = line.replace(Regex("^(?:- )?\\[[ xX]\\] ?"), "")
            list.add(checked to TextFieldValue(text, TextRange(text.length)))
        }
        if (list.isEmpty()) {
            list.add(false to TextFieldValue(""))
        }
        list
    }

    fun bodyForSave(): String {
        if (!isChecklist) return VeritasNoteEditing.serializeNoteBlocks(blocks)
        val text = items.joinToString("\n") { (checked, value) -> "[${if (checked) "x" else " "}] ${value.text}" }
        val media = VeritasNoteEditing.serializeNoteBlocks(blocks.filter { it !is NoteBlock.Text })
        return if (media.isBlank()) text else "$media\n$text"
    }

    fun updateChecklistString() {
        val result = bodyForSave()
        contentText = result
        contentValue = TextFieldValue(result)
        blocks.clear()
        blocks.addAll(VeritasNoteEditing.parseNoteBlocks(result))
        hasUnsavedChanges = true
        editVersion++
    }


    fun restoreSnapshot(snapshot: NoteEditorSnapshot) {
        lastTextEditAt = 0L
        blocks.clear()
        blocks.addAll(VeritasNoteEditing.parseNoteBlocks(NoteLinks.strip(snapshot.content)))
        title = snapshot.title
        links.clear(); links.addAll(NoteLinks.read(snapshot.content))
        dismissedLinks = NoteLinks.dismissed(snapshot.content)
        isChecklist = snapshot.checklist
        items.clear()
        blocks.filterIsInstance<NoteBlock.Text>().joinToString("\n") { it.value.text }.lineSequence().filter { it.isNotBlank() }.forEach { line ->
            val checked = Regex("^(?:- )?\\[[xX]\\]").containsMatchIn(line)
            items.add(checked to TextFieldValue(line.replace(Regex("^(?:- )?\\[[ xX]\\] ?"), "")))
        }
        if (items.isEmpty()) items.add(false to TextFieldValue(""))
        focusedBlockIndex = snapshot.blockIndex.coerceIn(0, blocks.lastIndex)
        (blocks.getOrNull(focusedBlockIndex) as? NoteBlock.Text)?.let { block ->
            block.value = block.value.copy(selection = TextRange(snapshot.selection.start.coerceIn(0, block.value.text.length), snapshot.selection.end.coerceIn(0, block.value.text.length)))
            contentValue = block.value
        }
        syncBlocksToContent()
    }

    val mediaManager = rememberGeneralNotesMediaManager(
        onInsertAttachment = { insertAttachmentAtCursor(it) },
        audioUrls = audioUrls,
        focusedBlockIndex = focusedBlockIndex,
        blocksCount = blocks.size
    )

    val historyToKeep by rememberUpdatedState(Triple(note?.id, captureSnapshot(), undoStack.toList() to redoStack.toList()))
    DisposableEffect(Unit) {
        onDispose {
            val (id, current, stacks) = historyToKeep
            NoteUndoHistory.write(id, current, stacks.first, stacks.second)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            val cutoff = System.currentTimeMillis() - NoteUndoHistory.LIFETIME_MS
            undoStack.removeAll { it.at <= cutoff }; redoStack.removeAll { it.at <= cutoff }
        }
    }
    LaunchedEffect(editVersion, notesSettings.showRichLinkPreviews) {
        if (!notesSettings.showRichLinkPreviews) return@LaunchedEffect
        val body = if (isChecklist) contentText else VeritasNoteEditing.serializeNoteBlocks(blocks)
        for (url in NoteLinks.urls(body).filter { it !in seenLinkUrls }) {
            seenLinkUrls.add(url)
            links.add(NoteLink(url, NoteLinks.host(url)))
        }
        for (link in links.toList().filter { it.url !in fetchedLinkUrls && it.title == NoteLinks.host(it.url) && it.image.isEmpty() }) {
            fetchedLinkUrls.add(link.url)
            val fetched = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { NoteLinks.fetch(link.url) }
            val index = links.indexOfFirst { it.url == link.url }
            if (index >= 0 && fetched != links[index]) {
                links[index] = fetched
                if (editVersion > 0) hasUnsavedChanges = true
            }
        }
    }

    // Continuous auto-save: debounced 800ms
    LaunchedEffect(editVersion, title, noteColor, isPinned, isChecklist, imageUrl, audioUrls, reminderAt, links.toList(), notebookIds) {
        if (!saveStatus.startsWith("Could not") && !hasUnsavedChanges && title == (note?.title ?: "") && noteColor == note?.color &&
            isPinned == (note?.pinned ?: false) && isChecklist == (note?.isChecklist ?: notesSettings.newNoteIsChecklist) && reminderAt == note?.reminderAt) return@LaunchedEffect
        delay(800)
        val contentToSave = bodyForSave()
        if (note != null || title.isNotBlank() || contentToSave.isNotBlank() || links.isNotEmpty() || imageUrl != null || audioUrls.isNotEmpty()) {
            onSave(title, NoteLinks.write(contentToSave, links, dismissedLinks), noteColor, isPinned, isChecklist, imageUrl, audioUrls.firstOrNull(), reminderAt, false, audioUrls, notebookIds)
            hasUnsavedChanges = false
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
            RichTextFormatter.stripMarkup(VeritasNoteEditing.serializeNoteBlocks(blocks))
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
        if (!saveStatus.startsWith("Could not") && !hasUnsavedChanges && title == (note?.title ?: "") && noteColor == note?.color &&
            isPinned == (note?.pinned ?: false) && isChecklist == (note?.isChecklist ?: notesSettings.newNoteIsChecklist) && reminderAt == note?.reminderAt) {
            onDismiss()
            return
        }
        if (mediaManager.isImporting) {
            Toast.makeText(context, "Wait for the attachment to finish, then save.", Toast.LENGTH_SHORT).show()
            return
        }
        if (mediaManager.isRecording) mediaManager.onToggleRecording()
        val finalContent = bodyForSave()
        onSave(title, NoteLinks.write(finalContent, links, dismissedLinks), noteColor, isPinned, isChecklist, imageUrl, audioUrls.firstOrNull(), reminderAt, true, audioUrls, notebookIds)
        hasUnsavedChanges = false
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val flushDraft by rememberUpdatedState(newValue = {
        if (mediaManager.isRecording) mediaManager.onToggleRecording()
        if (hasUnsavedChanges || noteColor != note?.color || isPinned != (note?.pinned ?: false) || reminderAt != note?.reminderAt) {
            val body = bodyForSave()
            onSave(title, NoteLinks.write(body, links, dismissedLinks), noteColor, isPinned, isChecklist, imageUrl, audioUrls.firstOrNull(), reminderAt, false, audioUrls, notebookIds)
        }
    })
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) flushDraft() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    BackHandler {
        if (mediaManager.isRecording) mediaManager.onToggleRecording()
        val hasAnyText = if (isChecklist) items.any { it.second.text.isNotBlank() } else blocks.any { it is NoteBlock.Text && it.value.text.isNotBlank() }
        if (note != null || title.isNotBlank() || hasAnyText || links.isNotEmpty() || blocks.any { it !is NoteBlock.Text } || imageUrl != null || audioUrls.isNotEmpty()) {
            performSave()
        } else {
            onDismiss()
        }
    }

    Scaffold(
        topBar = {
            NotesTopAppBar(
                onOpenSettings = { showNotesSettings = true },
                isPinned = isPinned,
                reminderAt = reminderAt,
                showReminderMenu = showReminderMenu,
                cardBgColor = cardBgColor,
                onCardColor = onCardColor,
                onBack = {
                    if (mediaManager.isRecording) mediaManager.onToggleRecording()
                    val hasAnyText = if (isChecklist) items.any { it.second.text.isNotBlank() } else blocks.any { it is NoteBlock.Text && it.value.text.isNotBlank() }
                    if (note != null || title.isNotBlank() || hasAnyText || links.isNotEmpty() || blocks.any { it !is NoteBlock.Text } || imageUrl != null || audioUrls.isNotEmpty()) {
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
                onNotebooks = { showNotebooks = true },
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
                    undoStack.removeAll { System.currentTimeMillis() - it.at >= NoteUndoHistory.LIFETIME_MS }
                    if (undoStack.isNotEmpty()) {
                        val previous = undoStack.removeAt(undoStack.lastIndex)
                        redoStack.add(captureSnapshot())
                        restoreSnapshot(previous)
                    }
                },
                onRedo = {
                    redoStack.removeAll { System.currentTimeMillis() - it.at >= NoteUndoHistory.LIFETIME_MS }
                    if (redoStack.isNotEmpty()) {
                        val next = redoStack.removeAt(redoStack.lastIndex)
                        undoStack.add(captureSnapshot())
                        restoreSnapshot(next)
                    }
                },
                onShare = { shareNote() },
                onDelete = { confirmDeleteNote = true },
                onCopy = {
                    val body = bodyForSave()
                    onCopyDraft(title, NoteLinks.write(body, links, dismissedLinks), noteColor, isPinned, isChecklist, imageUrl, audioUrls, reminderAt, notebookIds)
                },
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
                        if (!isChecklist && blocks.any { it !is NoteBlock.Text }) {
                            Toast.makeText(context, "Use task checkboxes in this note to keep its attachments.", Toast.LENGTH_SHORT).show()
                        } else {
                            pushHistory()
                            if (!isChecklist) {
                                items.clear()
                                VeritasNoteEditing.serializeNoteBlocks(blocks).lineSequence().filter { it.isNotBlank() }.forEach { line ->
                                    val checked = Regex("^(?:- )?\\[[xX]\\]").containsMatchIn(line)
                                    items.add(checked to TextFieldValue(line.replace(Regex("^(?:- )?\\[[ xX]\\] ?"), "")))
                                }
                                if (items.isEmpty()) items.add(false to TextFieldValue(""))
                            } else {
                                updateChecklistString()
                            }
                            isChecklist = !isChecklist
                            hasUnsavedChanges = true; editVersion++
                        }
                    }
                    expandedMenu = NotesToolbarMenu.NONE
                },
                onPickImage = {
                    mediaManager.onPickImage()
                    expandedMenu = NotesToolbarMenu.NONE
                },
                onTakePhoto = {
                    mediaManager.onTakePhoto()
                    expandedMenu = NotesToolbarMenu.NONE
                },
                onPickVideo = {
                    mediaManager.onPickVideo()
                    expandedMenu = NotesToolbarMenu.NONE
                },
                onPickFile = {
                    mediaManager.onPickFile()
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
                .padding(horizontal = 8.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NoteLabelsRow(notebooks, notebookIds, onLabel = { showNotebooks = true })
            Text(
                if (mediaManager.isImporting) "Adding attachment…" else if (hasUnsavedChanges) "Unsaved changes" else saveStatus.ifBlank { "Saved" },
                style = MaterialTheme.typography.labelSmall,
                color = if (saveStatus.startsWith("Could not")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                    if (title != it) pushHistory()
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

            val typography = MaterialTheme.typography
            fun scaled(style: androidx.compose.ui.text.TextStyle) = style.copy(
                fontSize = style.fontSize * notesSettings.editorFontScale,
                lineHeight = style.lineHeight * notesSettings.editorFontScale * notesSettings.editorLineSpacing)
            MaterialTheme(typography = typography.copy(bodyLarge = scaled(typography.bodyLarge), bodyMedium = scaled(typography.bodyMedium))) {
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
                    val now = System.currentTimeMillis()
                    if (prev.text != processed.text) {
                        // IMEs can send replacement as clear + insert; keep that one undo action.
                        if (undoStack.isEmpty() || now - lastTextEditAt > 500 || lastTextEditBlock != index) pushHistory()
                        lastTextEditAt = now
                        lastTextEditBlock = index
                    }
                    block.value = processed
                    contentValue = processed
                    focusedBlockIndex = index
                    if (prev.text != processed.text) {
                        hasUnsavedChanges = true
                        editVersion++
                    }
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
                onOpenFile = { path -> openDocumentFile(context, path, blocks.filterIsInstance<NoteBlock.File>().firstOrNull { it.path == path }?.fileName) },
                onShareFile = { path -> shareMediaFile(context, path, "*/*") },
                onCopyAttachment = { copyAttachment(it) },
                onMoveBlockUp = { moveBlockUp(it) },
                onMoveBlockDown = { moveBlockDown(it) },
                onRemoveBlock = { removeBlockAt(it) },
                onTogglePlayAudio = { mediaManager.onTogglePlayAudio(it) },
                onSeekAudio = { frac, path -> mediaManager.onSeekAudio(frac, path) },
                onChecklistChanged = {
                    pushHistory()
                    updateChecklistString()
                    hasUnsavedChanges = true
                    editVersion++
                },
                moveCheckedToBottom = notesSettings.moveCheckedToBottom,
                addNewItemsToTop = notesSettings.addNewItemsToTop,
                paperTemplate = notesSettings.paperTemplate,
                hasLinkPreviews = notesSettings.showRichLinkPreviews && links.isNotEmpty()

            )
            }
            if (notesSettings.showRichLinkPreviews && links.isNotEmpty()) {
                Spacer(Modifier.height(80.dp * notesSettings.editorFontScale * notesSettings.editorLineSpacing))
            }
            if (notesSettings.showRichLinkPreviews) links.toList().forEach { link ->
                NoteLinkCard(link, onRemove = {
                    pushHistory()
                    links.remove(link)
                    dismissedLinks = (dismissedLinks + link.url).distinct()
                    hasUnsavedChanges = true
                    editVersion++
                })
            }
        }
    }

    if (showNotesSettings) NotesSettingsSheet(notesSettings, onSaveNotesSettings) { showNotesSettings = false }

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

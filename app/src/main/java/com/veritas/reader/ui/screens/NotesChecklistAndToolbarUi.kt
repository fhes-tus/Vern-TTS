package com.veritas.reader.ui.screens

import androidx.compose.material.icons.automirrored.outlined.MenuBook

import androidx.compose.ui.text.TextLayoutResult
import com.veritas.reader.ui.NotesPaperTemplate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.StrikethroughS
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NotificationAdd
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class NotesToolbarMenu {
    NONE,
    FORMATTING,
    ATTACHMENTS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotesTopAppBar(
    isPinned: Boolean,
    reminderAt: Long?,
    showReminderMenu: Boolean,
    cardBgColor: Color,
    onCardColor: Color,
    onBack: () -> Unit,
    onTogglePin: () -> Unit,
    onShowReminderMenu: () -> Unit,
    onDismissReminderMenu: () -> Unit,
    onSetReminderLaterToday: () -> Unit,
    onSetReminderTomorrow: () -> Unit,
    onPickReminderDateTime: () -> Unit,
    onRemoveReminder: () -> Unit,
    onSave: () -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    TopAppBar(
        title = { },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_back))
            }
        },
        actions = {
            IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_notes_settings_cc5a665)) }
            IconButton(onClick = onTogglePin) {
                Icon(
                    imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_pin_note_ae126ef),
                    tint = if (isPinned) MaterialTheme.colorScheme.primary else onCardColor.copy(alpha = 0.8f)
                )
            }

            Box {
                IconButton(onClick = onShowReminderMenu) {
                    Icon(
                        imageVector = if (reminderAt != null) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationAdd,
                        contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_reminder),
                        tint = if (reminderAt != null) MaterialTheme.colorScheme.primary else onCardColor.copy(alpha = 0.8f)
                    )
                }
                DropdownMenu(expanded = showReminderMenu, onDismissRequest = onDismissReminderMenu) {
                    DropdownMenuItem(
                        text = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_later_today_3_hrs)) },
                        onClick = onSetReminderLaterToday,
                        leadingIcon = { Icon(Icons.Filled.Schedule, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_tomorrow_morning_9_am)) },
                        onClick = onSetReminderTomorrow,
                        leadingIcon = { Icon(Icons.Filled.WbSunny, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_pick_date_time)) },
                        onClick = onPickReminderDateTime,
                        leadingIcon = { Icon(Icons.Filled.EditCalendar, contentDescription = null) }
                    )
                    if (reminderAt != null) {
                        DropdownMenuItem(
                            text = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_remove_reminder)) },
                            onClick = onRemoveReminder,
                            leadingIcon = { Icon(Icons.Filled.NotificationsOff, contentDescription = null) }
                        )
                    }
                }
            }

            IconButton(onClick = onSave) {
                Icon(
                    imageVector = Icons.Filled.Archive,
                    contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_save_note),
                    tint = onCardColor.copy(alpha = 0.8f)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = cardBgColor,
            titleContentColor = onCardColor,
            actionIconContentColor = onCardColor.copy(alpha = 0.8f),
            navigationIconContentColor = onCardColor
        )
    )
}

val noteColorPalette: List<Pair<String, String?>> = listOf(
    "Default" to null,
    "Red" to "#FFCDD2",
    "Orange" to "#FFE0B2",
    "Yellow" to "#FFF9C4",
    "Green" to "#C8E6C9",
    "Teal" to "#B2DFDB",
    "Blue" to "#B3E5FC",
    "DarkBlue" to "#C5CAE9",
    "Purple" to "#D1C4E9",
    "Pink" to "#F8BBD0",
    "Brown" to "#D7CCC8"
)

@Composable
internal fun NotesColorPaletteRow(
    selectedColorHex: String?,
    onColorSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        noteColorPalette.forEach { (_, hex) ->
            val color = hex?.let { Color(android.graphics.Color.parseColor(it)) } ?: MaterialTheme.colorScheme.surface
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color, shape = com.veritas.reader.VeritasPackStyle.compactShape())
                    .clickable { onColorSelected(hex) }
                    .border(
                        width = if (selectedColorHex == hex) 2.dp else 1.dp,
                        color = if (selectedColorHex == hex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = com.veritas.reader.VeritasPackStyle.compactShape()
                    )
            )
        }
    }
}

@Composable
internal fun NoteChecklistSection(
    items: SnapshotStateList<Pair<Boolean, TextFieldValue>>,
    focusRequesters: MutableMap<Int, FocusRequester>,
    onCardColor: Color,
    onChecklistChanged: () -> Unit,
    moveCheckedToBottom: Boolean = true,
    addNewItemsToTop: Boolean = false,
    paperTemplate: NotesPaperTemplate = NotesPaperTemplate.BLANK,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEachIndexed { idx, (checked, tfv) ->
            val focusRequester = remember(idx) { focusRequesters.getOrPut(idx) { FocusRequester() } }
            var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { isChecked ->
                        VeritasNoteEditing.toggleChecklistItem(items, idx, isChecked, moveCheckedToBottom = moveCheckedToBottom)
                        onChecklistChanged()
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        checkmarkColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                BasicTextField(
                    value = tfv,
                    onTextLayout = { layout = it },
                    onValueChange = { newTfv ->
                        items[idx] = checked to newTfv
                        onChecklistChanged()
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = onCardColor,
                        textDecoration = if (checked) TextDecoration.LineThrough else null
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .notesTextPaper(paperTemplate, onCardColor.copy(alpha = .10f), layout)
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyDown) {
                                val cursor = tfv.selection.start
                                val textBefore = tfv.text.substring(0, cursor)
                                val textAfter = tfv.text.substring(cursor)

                                items[idx] = checked to tfv.copy(text = textBefore, selection = TextRange(textBefore.length))
                                val newItem = false to TextFieldValue(textAfter, TextRange(0))
                                val insertAt = if (addNewItemsToTop) 0 else idx + 1
                                items.add(insertAt, newItem)
                                onChecklistChanged()

                                coroutineScope.launch {
                                    delay(50)
                                    focusRequesters[insertAt]?.requestFocus()
                                }
                                true
                            } else {
                                false
                            }
                        }
                )
                IconButton(
                    onClick = {
                        if (items.size > 1) {
                            items.removeAt(idx)
                            onChecklistChanged()
                        } else {
                            items[0] = false to TextFieldValue("")
                            onChecklistChanged()
                        }
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_remove_item),
                        tint = onCardColor.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        TextButton(
            onClick = {
                val nextIdx = VeritasNoteEditing.addNewChecklistItem(
                    items = items,
                    addNewItemsToTop = addNewItemsToTop,
                    moveCheckedToBottom = moveCheckedToBottom
                )
                onChecklistChanged()
                coroutineScope.launch {
                    delay(50)
                    focusRequesters[nextIdx]?.requestFocus()
                }
            },
            colors = ButtonDefaults.textButtonColors(contentColor = onCardColor)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_add_item))
                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_add_list_item))
            }
        }
    }
}

@Composable
internal fun NotesBottomToolbar(
    onNotebooks: () -> Unit = {},
    onCardColor: Color,
    cardBgColor: Color,
    expandedMenu: NotesToolbarMenu,
    showColorPicker: Boolean,
    noteColor: String?,
    isChecklist: Boolean,
    isRecording: Boolean,
    hasAudio: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    hasExistingNote: Boolean,
    onCloseMenu: () -> Unit,
    onOpenAttachments: () -> Unit,
    onOpenFormatting: () -> Unit,
    onToggleColorPicker: () -> Unit,
    onToggleRecording: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onColorSelected: (String?) -> Unit,
    // Formatting callbacks
    onCycleHeading: () -> Unit,
    onApplyMarker: (String) -> Unit,
    onToggleQuote: () -> Unit,
    onApplyLinePrefix: (String) -> Unit,
    onToggleTask: () -> Unit,
    onApplyOutdent: () -> Unit,
    onApplyIndent: () -> Unit,
    // Attachment callbacks
    onToggleChecklist: () -> Unit,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onPickFile: () -> Unit = {},
    onTakePhoto: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(cardBgColor)
            .navigationBarsPadding()
            .imePadding()
    ) {
        if (showColorPicker) {
            NotesColorPaletteRow(
                selectedColorHex = noteColor,
                onColorSelected = onColorSelected
            )
        }

        Surface(
            tonalElevation = 3.dp,
            color = cardBgColor,
            modifier = Modifier.fillMaxWidth()
        ) {
            when (expandedMenu) {
                NotesToolbarMenu.FORMATTING -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onCloseMenu) {
                            Icon(Icons.Filled.Close, contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_close_menu), tint = onCardColor)
                        }
                        VerticalDivider(
                            modifier = Modifier
                                .height(24.dp)
                                .padding(horizontal = 4.dp),
                            color = onCardColor.copy(alpha = 0.2f)
                        )

                        FormatToolbarButton(Icons.Filled.Title, com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_heading), onCardColor) { onCycleHeading() }
                        FormatToolbarButton(Icons.Filled.FormatBold, com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_bold), onCardColor) { onApplyMarker("**") }
                        FormatToolbarButton(Icons.Filled.FormatItalic, com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_italic), onCardColor) { onApplyMarker("*") }
                        FormatToolbarButton(Icons.Filled.FormatUnderlined, com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_underline), onCardColor) { onApplyMarker("__") }
                        FormatToolbarButton(Icons.Filled.StrikethroughS, com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_strikethrough), onCardColor) { onApplyMarker("~~") }
                        FormatToolbarButton(Icons.Filled.FormatQuote, com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_quote), onCardColor) { onToggleQuote() }
                        FormatToolbarButton(Icons.Filled.Code, com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_monospace), onCardColor) { onApplyMarker("`") }
                        FormatToolbarButton(Icons.AutoMirrored.Filled.FormatListBulleted, com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_bullet_list), onCardColor) { onApplyLinePrefix("- ") }
                        FormatToolbarButton(Icons.Filled.FormatListNumbered, com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_numbered_list), onCardColor) { onApplyLinePrefix("1. ") }
                        FormatToolbarButton(Icons.Filled.CheckBoxOutlineBlank, com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_task), onCardColor) { onToggleTask() }
                        FormatToolbarButton(Icons.AutoMirrored.Filled.FormatIndentDecrease, com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_outdent), onCardColor) { onApplyOutdent() }
                        FormatToolbarButton(Icons.AutoMirrored.Filled.FormatIndentIncrease, com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_indent), onCardColor) { onApplyIndent() }
                    }
                }
                NotesToolbarMenu.ATTACHMENTS -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onCloseMenu) {
                            Icon(Icons.Filled.Close, contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_close_menu), tint = onCardColor)
                        }
                        VerticalDivider(
                            modifier = Modifier
                                .height(24.dp)
                                .padding(horizontal = 4.dp),
                            color = onCardColor.copy(alpha = 0.2f)
                        )

                        // Checklist Toggle
                        IconButton(onClick = onToggleChecklist) {
                            Icon(
                                imageVector = if (isChecklist) Icons.Filled.Checklist else Icons.Outlined.Checklist,
                                contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_checklist_toggle),
                                tint = if (isChecklist) MaterialTheme.colorScheme.primary else onCardColor.copy(alpha = 0.8f)
                            )
                        }

                        IconButton(onClick = onTakePhoto) {
                            Icon(Icons.Outlined.PhotoCamera, com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_take_photo), tint = onCardColor.copy(alpha = 0.8f))
                        }
                        // Attach Image
                        IconButton(onClick = onPickImage) {
                            Icon(
                                imageVector = Icons.Outlined.Image,
                                contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_attach_image),
                                tint = onCardColor.copy(alpha = 0.8f)
                            )
                        }

                        // Attach Video
                        IconButton(onClick = onPickVideo) {
                            Icon(
                                imageVector = Icons.Outlined.VideoLibrary,
                                contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_attach_video),
                                tint = onCardColor.copy(alpha = 0.8f)
                            )
                        }

                        // Attach File
                        IconButton(onClick = onPickFile) {
                            Icon(
                                imageVector = Icons.Outlined.AttachFile,
                                contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_attach_file),
                                tint = onCardColor.copy(alpha = 0.8f)
                            )
                        }

                        // Record Audio
                        IconButton(onClick = onToggleRecording) {
                            Icon(
                                imageVector = if (isRecording) Icons.Filled.Stop else Icons.Outlined.Mic,
                                contentDescription = if (isRecording) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_stop_recording) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_record_audio_9ede70b),
                                tint = if (isRecording) MaterialTheme.colorScheme.error else onCardColor.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
                NotesToolbarMenu.NONE -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onOpenAttachments) {
                                Icon(Icons.Filled.AddBox, contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_add_attachments), tint = onCardColor.copy(alpha = 0.8f))
                            }
                            IconButton(onClick = onToggleRecording) {
                                Icon(
                                    imageVector = if (isRecording) Icons.Filled.Stop else Icons.Outlined.Mic,
                                    contentDescription = if (isRecording) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_stop_recording) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_record_audio_9ede70b),
                                    tint = if (isRecording) MaterialTheme.colorScheme.error else (if (hasAudio) MaterialTheme.colorScheme.primary else onCardColor.copy(alpha = 0.8f))
                                )
                            }
                            IconButton(onClick = onToggleColorPicker) {
                                Icon(Icons.Outlined.Palette, contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_color_picker), tint = onCardColor.copy(alpha = 0.8f))
                            }
                            if (!isChecklist) {
                                IconButton(onClick = onOpenFormatting) {
                                    Icon(Icons.Filled.TextFields, contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_formatting_tools), tint = onCardColor.copy(alpha = 0.8f))
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onUndo,
                                enabled = canUndo
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_undo),
                                    tint = onCardColor.copy(alpha = if (canUndo) 0.85f else 0.3f)
                                )
                            }
                            IconButton(
                                onClick = onRedo,
                                enabled = canRedo
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Redo,
                                    contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_redo),
                                    tint = onCardColor.copy(alpha = if (canRedo) 0.85f else 0.3f)
                                )
                            }

                            var showOverflow by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { showOverflow = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_more_options), tint = onCardColor.copy(alpha = 0.8f))
                                }
                                DropdownMenu(
                                    expanded = showOverflow,
                                    onDismissRequest = { showOverflow = false }
                                ) {
                                    DropdownMenuItem(text = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_notebooks)) },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.MenuBook, null) },
                                        onClick = { showOverflow = false; onNotebooks() })
                                    DropdownMenuItem(
                                        text = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_share)) },
                                        onClick = {
                                            showOverflow = false
                                            onShare()
                                        },
                                        leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) }
                                    )
                                    if (hasExistingNote) {
                                        DropdownMenuItem(
                                            text = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_delete), color = MaterialTheme.colorScheme.error) },
                                            onClick = {
                                                showOverflow = false
                                                onDelete()
                                            },
                                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_make_a_copy)) },
                                            onClick = {
                                                showOverflow = false
                                                onCopy()
                                            },
                                            leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) }
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

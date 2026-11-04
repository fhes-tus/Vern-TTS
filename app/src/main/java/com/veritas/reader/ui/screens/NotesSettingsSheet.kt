package com.veritas.reader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.ui.NotesPaperTemplate
import com.veritas.reader.ui.NotesSettings
import com.veritas.reader.ui.VeritasSwitch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NotesSettingsSheet(notesSettings: NotesSettings, onSaveNotesSettings: (NotesSettings) -> Unit, onDismiss: () -> Unit) {
    var confirmReset by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    ModalBottomSheet(
        shape = com.veritas.reader.VeritasPackStyle.sheetShape(),onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = scheme.background.copy(alpha = 1f), contentColor = scheme.onSurface,
        scrimColor = scheme.scrim.copy(alpha = 0.5f)) {
        Column(Modifier.fillMaxWidth().background(VeritasPackStyle.backgroundBrush(scheme)).verticalScroll(rememberScrollState())
            .navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_notes_settings), style = MaterialTheme.typography.headlineSmall,
                    fontWeight = MaterialTheme.typography.titleLarge.fontWeight, color = scheme.onBackground, modifier = Modifier.weight(1f))
                IconButton(onDismiss) { Icon(Icons.Default.Close, androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_close_notes_settings)) }
            }
            NotesSettingsGroup(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_notes_page)) {
                NotesToggle(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_display_rich_link_previews), androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_show_website_cards_in_notes_and_on_the_notes_page), notesSettings.showRichLinkPreviews, icon = Icons.Outlined.Link) {
                    onSaveNotesSettings(notesSettings.copy(showRichLinkPreviews = it))
                }
                NotesChoices(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_layout), listOf(true to androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_grid), false to androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_list)), notesSettings.isGridView) {
                    onSaveNotesSettings(notesSettings.copy(isGridView = it))
                }
                NotesChoices(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_sort_by), listOf("date" to androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_last_edited), "created" to androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_newest), "title" to androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_title)), notesSettings.defaultSortOrder) {
                    onSaveNotesSettings(notesSettings.copy(defaultSortOrder = it))
                }
                NotesChoices(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_text_previews), listOf(0 to androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_hidden), 2 to androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_short), 5 to androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_standard), 8 to androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_long)), notesSettings.previewLines) {
                    onSaveNotesSettings(notesSettings.copy(previewLines = it))
                }
                NotesToggle(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_attachment_thumbnails), androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_show_images_and_video_previews_on_note_cards), notesSettings.richAttachmentPreviews, icon = Icons.Outlined.Image) {
                    onSaveNotesSettings(notesSettings.copy(richAttachmentPreviews = it))
                }
                NotesToggle(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_attachment_badges), androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_show_the_kinds_of_media_in_each_note), notesSettings.showAttachmentBadges, icon = Icons.Outlined.AttachFile) {
                    onSaveNotesSettings(notesSettings.copy(showAttachmentBadges = it))
                }
                NotesToggle(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_book_sources), androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_show_the_book_linked_to_a_reading_note), notesSettings.showBookSourceBadges, icon = Icons.Outlined.MenuBook) {
                    onSaveNotesSettings(notesSettings.copy(showBookSourceBadges = it))
                }
                NotesToggle(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_voice_waveforms), androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_show_the_waveform_beside_a_voice_memo), notesSettings.voiceMemoWaveformPreview, icon = Icons.Outlined.GraphicEq) {
                    onSaveNotesSettings(notesSettings.copy(voiceMemoWaveformPreview = it))
                }
            }
            NotesSettingsGroup(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_writing)) {
                NotesChoices(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_new_notes), listOf(false to androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_text), true to androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_checklist)), notesSettings.newNoteIsChecklist) {
                    onSaveNotesSettings(notesSettings.copy(newNoteIsChecklist = it))
                }
                NotesPaperChoices(notesSettings.paperTemplate) {
                    onSaveNotesSettings(notesSettings.copy(paperTemplate = it))
                }
                NotesChoices(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_text_size), listOf(0.85f to androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_small), 1f to androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_default), 1.15f to androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_large), 1.25f to androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_larger)), notesSettings.editorFontScale) {
                    onSaveNotesSettings(notesSettings.copy(editorFontScale = it))
                }
                NotesChoices(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_line_spacing), listOf(1f to androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_default), 1.25f to androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_relaxed), 1.5f to androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_wide)), notesSettings.editorLineSpacing) {
                    onSaveNotesSettings(notesSettings.copy(editorLineSpacing = it))
                }
                NotesWritingPreview(notesSettings)
                Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_text_size_and_spacing_apply_only_while_writing_notes), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            NotesSettingsGroup(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_checklists)) {
                NotesToggle(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_completed_items_at_the_bottom), androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_move_an_item_down_when_you_check_it), notesSettings.moveCheckedToBottom, icon = Icons.Outlined.VerticalAlignBottom) {
                    onSaveNotesSettings(notesSettings.copy(moveCheckedToBottom = it))
                }
                NotesToggle(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_add_new_items_at_the_top), androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_start_a_new_item_above_the_existing_list), notesSettings.addNewItemsToTop, icon = Icons.Outlined.VerticalAlignTop) {
                    onSaveNotesSettings(notesSettings.copy(addNewItemsToTop = it))
                }
            }
            NotesSettingsGroup(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_saving_backup)) {
                Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_notes_save_automatically_as_you_write_the_editor_shows_when_your), style = MaterialTheme.typography.bodyMedium)
                Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_app_backups_include_notes_and_these_preferences_zip_backups_also), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                TextButton({ confirmReset = true }) { Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_reset_notes_preferences)) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false },
        title = { Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_reset_notes_preferences_c9adb9e)) },
        text = { Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_restore_the_default_layout_and_writing_preferences_your_notes_and)) },
        confirmButton = { TextButton({ onSaveNotesSettings(NotesSettings()); confirmReset = false }) { Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_reset_preferences)) } },
        dismissButton = { TextButton({ confirmReset = false }) { Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_cancel)) } })
}

@Composable
private fun NotesSettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsHubSectionTitle(title)
        Card(Modifier.fillMaxWidth(), shape = VeritasPackStyle.cardShape(),
            colors = CardDefaults.cardColors(containerColor = scheme.surface.copy(alpha = 1f)),
            border = VeritasPackStyle.cardBorder(scheme)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
        }
    }
}

@Composable
private fun NotesAccentIcon(icon: ImageVector, accent: Color, size: Int = 36) {
    Surface(shape = CircleShape, color = accent.copy(alpha = .10f)) {
        Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(if (size > 40) 26.dp else 20.dp), tint = accent)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotesPaperChoices(selectedTemplate: NotesPaperTemplate, onSelect: (NotesPaperTemplate) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_paper), style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NotesPaperTemplate.entries.forEach { template ->
                val active = template == selectedTemplate
                Surface(shape = com.veritas.reader.VeritasPackStyle.compactShape(),
                    color = if (active) lerp(scheme.surface.copy(alpha = 1f), scheme.primary.copy(alpha = 1f), .08f) else scheme.surface.copy(alpha = 1f),
                    border = BorderStroke(if (active) 2.dp else 1.dp, if (active) scheme.primary else scheme.outlineVariant),
                    modifier = Modifier.weight(1f).semantics { selected = active }
                        .clickable(role = Role.RadioButton) { onSelect(template) }) {
                    Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.fillMaxWidth().height(46.dp)
                            .notesPaperSwatch(template, scheme.primary.copy(alpha = .28f))) {
                            if (active) Icon(Icons.Default.Check, null, Modifier.size(16.dp).align(Alignment.TopEnd), tint = scheme.primary)
                        }
                        Text(localizedSettingsOption(template.label), style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center, minLines = 2, maxLines = 2,
                            color = if (active) scheme.primary else scheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesWritingPreview(settings: NotesSettings) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = com.veritas.reader.VeritasPackStyle.compactShape(), color = lerp(scheme.surface.copy(alpha = 1f), scheme.tertiary.copy(alpha = 1f), .06f),
        border = BorderStroke(1.dp, scheme.tertiary.copy(alpha = .15f))) {
        Column(Modifier.fillMaxWidth().testTag("notes_writing_preview")
            .heightIn(min = 280.dp).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Palette, null, Modifier.size(16.dp), tint = scheme.tertiary)
                Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_live_preview), style = MaterialTheme.typography.labelSmall, color = scheme.tertiary)
            }
            Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_a_thought_worth_keeping), style = MaterialTheme.typography.titleSmall)
            val bodyStyle = MaterialTheme.typography.bodyLarge.copy(
                fontSize = MaterialTheme.typography.bodyLarge.fontSize * settings.editorFontScale,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * settings.editorFontScale * settings.editorLineSpacing)
            if (settings.newNoteIsChecklist) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.CheckBoxOutlineBlank, null, Modifier.size(18.dp), tint = scheme.tertiary)
                    NotesPreviewText(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.l10n_capture_one_good_idea), bodyStyle, settings.paperTemplate, scheme.tertiary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = scheme.tertiary)
                    NotesPreviewText(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.l10n_make_a_little_space_to_begin), bodyStyle.copy(textDecoration = TextDecoration.LineThrough, color = scheme.onSurfaceVariant), settings.paperTemplate, scheme.tertiary)
                }
            } else NotesPreviewText(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.l10n_ideas_become_clearer_when_you_give_them_a_little_room_keep_the_wo),
                bodyStyle, settings.paperTemplate, scheme.tertiary, Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> NotesChoices(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, text) ->
                FilterChip(selected == value, { onSelect(value) }, label = { Text(text) }, shape = VeritasPackStyle.chipShape(),
                    leadingIcon = if (selected == value) { { Icon(Icons.Default.Check, null, Modifier.size(14.dp)) } } else null,
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer))
            }
        }
    }
}

@Composable
private fun NotesToggle(title: String, description: String, checked: Boolean, icon: ImageVector, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NotesAccentIcon(icon, MaterialTheme.colorScheme.primary, 32)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        VeritasSwitch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun NotesPreviewText(text: String, style: androidx.compose.ui.text.TextStyle,
    template: NotesPaperTemplate, accent: Color, modifier: Modifier = Modifier) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Box(modifier.fillMaxWidth().notesTextPaper(template, accent.copy(alpha = .14f), layout)) {
        Text(text, style = style, onTextLayout = { layout = it })
    }
}

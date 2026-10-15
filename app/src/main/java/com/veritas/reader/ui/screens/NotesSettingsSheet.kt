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
                Text("Notes settings", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = MaterialTheme.typography.titleLarge.fontWeight, color = scheme.onBackground, modifier = Modifier.weight(1f))
                IconButton(onDismiss) { Icon(Icons.Default.Close, "Close Notes settings") }
            }
            NotesSettingsGroup("Notes page") {
                NotesToggle("Display rich link previews", "Show website cards in notes and on the Notes page", notesSettings.showRichLinkPreviews, icon = Icons.Outlined.Link) {
                    onSaveNotesSettings(notesSettings.copy(showRichLinkPreviews = it))
                }
                NotesChoices("Layout", listOf(true to "Grid", false to "List"), notesSettings.isGridView) {
                    onSaveNotesSettings(notesSettings.copy(isGridView = it))
                }
                NotesChoices("Sort by", listOf("date" to "Last edited", "created" to "Newest", "title" to "Title"), notesSettings.defaultSortOrder) {
                    onSaveNotesSettings(notesSettings.copy(defaultSortOrder = it))
                }
                NotesChoices("Text previews", listOf(0 to "Hidden", 2 to "Short", 5 to "Standard", 8 to "Long"), notesSettings.previewLines) {
                    onSaveNotesSettings(notesSettings.copy(previewLines = it))
                }
                NotesToggle("Attachment thumbnails", "Show images and video previews on note cards", notesSettings.richAttachmentPreviews, icon = Icons.Outlined.Image) {
                    onSaveNotesSettings(notesSettings.copy(richAttachmentPreviews = it))
                }
                NotesToggle("Attachment badges", "Show the kinds of media in each note", notesSettings.showAttachmentBadges, icon = Icons.Outlined.AttachFile) {
                    onSaveNotesSettings(notesSettings.copy(showAttachmentBadges = it))
                }
                NotesToggle("Book sources", "Show the book linked to a reading note", notesSettings.showBookSourceBadges, icon = Icons.Outlined.MenuBook) {
                    onSaveNotesSettings(notesSettings.copy(showBookSourceBadges = it))
                }
                NotesToggle("Voice waveforms", "Show the waveform beside a voice memo", notesSettings.voiceMemoWaveformPreview, icon = Icons.Outlined.GraphicEq) {
                    onSaveNotesSettings(notesSettings.copy(voiceMemoWaveformPreview = it))
                }
            }
            NotesSettingsGroup("Writing") {
                NotesChoices("New notes", listOf(false to "Text", true to "Checklist"), notesSettings.newNoteIsChecklist) {
                    onSaveNotesSettings(notesSettings.copy(newNoteIsChecklist = it))
                }
                NotesPaperChoices(notesSettings.paperTemplate) {
                    onSaveNotesSettings(notesSettings.copy(paperTemplate = it))
                }
                NotesChoices("Text size", listOf(0.85f to "Small", 1f to "Default", 1.15f to "Large", 1.25f to "Larger"), notesSettings.editorFontScale) {
                    onSaveNotesSettings(notesSettings.copy(editorFontScale = it))
                }
                NotesChoices("Line spacing", listOf(1f to "Default", 1.25f to "Relaxed", 1.5f to "Wide"), notesSettings.editorLineSpacing) {
                    onSaveNotesSettings(notesSettings.copy(editorLineSpacing = it))
                }
                NotesWritingPreview(notesSettings)
                Text("Text size and spacing apply only while writing notes.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            NotesSettingsGroup("Checklists") {
                NotesToggle("Completed items at the bottom", "Move an item down when you check it", notesSettings.moveCheckedToBottom, icon = Icons.Outlined.VerticalAlignBottom) {
                    onSaveNotesSettings(notesSettings.copy(moveCheckedToBottom = it))
                }
                NotesToggle("Add new items at the top", "Start a new item above the existing list", notesSettings.addNewItemsToTop, icon = Icons.Outlined.VerticalAlignTop) {
                    onSaveNotesSettings(notesSettings.copy(addNewItemsToTop = it))
                }
            }
            NotesSettingsGroup("Saving & backup") {
                Text("Notes save automatically as you write. The editor shows when your changes are saved.", style = MaterialTheme.typography.bodyMedium)
                Text("App backups include notes and these preferences. ZIP backups also carry supported local note attachments. Use Backup & restore in app settings.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                TextButton({ confirmReset = true }) { Text("Reset Notes preferences") }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false },
        title = { Text("Reset Notes preferences?") },
        text = { Text("Restore the default layout and writing preferences. Your notes and attachments will stay as they are.") },
        confirmButton = { TextButton({ onSaveNotesSettings(NotesSettings()); confirmReset = false }) { Text("Reset preferences") } },
        dismissButton = { TextButton({ confirmReset = false }) { Text("Cancel") } })
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
        Text("Paper", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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
                        Text(template.label, style = MaterialTheme.typography.labelSmall,
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
                Text("Live preview", style = MaterialTheme.typography.labelSmall, color = scheme.tertiary)
            }
            Text("A thought worth keeping", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            val bodyStyle = MaterialTheme.typography.bodyLarge.copy(
                fontSize = MaterialTheme.typography.bodyLarge.fontSize * settings.editorFontScale,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * settings.editorFontScale * settings.editorLineSpacing)
            if (settings.newNoteIsChecklist) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.CheckBoxOutlineBlank, null, Modifier.size(18.dp), tint = scheme.tertiary)
                    NotesPreviewText("Capture one good idea", bodyStyle, settings.paperTemplate, scheme.tertiary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = scheme.tertiary)
                    NotesPreviewText("Make a little space to begin", bodyStyle.copy(textDecoration = TextDecoration.LineThrough, color = scheme.onSurfaceVariant), settings.paperTemplate, scheme.tertiary)
                }
            } else NotesPreviewText("Ideas become clearer when you give them a little room.\nKeep the words that matter to you.\n\nLeave space for the next thought.",
                bodyStyle, settings.paperTemplate, scheme.tertiary, Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> NotesChoices(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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

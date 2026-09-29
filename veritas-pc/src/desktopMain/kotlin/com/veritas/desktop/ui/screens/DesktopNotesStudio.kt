package com.veritas.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.desktop.models.ChecklistItem
import com.veritas.desktop.models.RichNote
import com.veritas.desktop.system.ClipboardHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val NOTE_COLOR_TAGS = listOf(
    "Yellow" to Color(0xFFF59E0B),
    "Slate" to Color(0xFF64748B),
    "Green" to Color(0xFF10B981),
    "Ocean" to Color(0xFF0284C7),
    "Purple" to Color(0xFF8B5CF6),
    "Rose" to Color(0xFFF43F5E)
)

@Composable
fun DesktopNotesStudio(
    notes: List<RichNote>,
    onSaveNote: (RichNote) -> Unit,
    onDeleteNote: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var showEditorDialog by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<RichNote?>(null) }

    val filteredNotes = notes.filter {
        it.title.contains(searchQuery, ignoreCase = true) ||
                it.content.contains(searchQuery, ignoreCase = true) ||
                (it.documentTitle?.contains(searchQuery, ignoreCase = true) ?: false)
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Notes & Highlights Studio", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Capture study insights, checklists, and document highlights.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Export All to Clipboard
                OutlinedButton(
                    onClick = {
                        val markdown = buildString {
                            append("# Veritas Notes Export\n\n")
                            for (n in notes) {
                                append("## ${n.title.ifBlank { "Untitled Note" }}\n")
                                if (!n.documentTitle.isNullOrBlank()) append("*From: ${n.documentTitle}*\n\n")
                                append("${n.content}\n\n")
                                if (n.checklistItems.isNotEmpty()) {
                                    for (item in n.checklistItems) {
                                        append("- [${if (item.isChecked) "x" else " "}] ${item.text}\n")
                                    }
                                    append("\n")
                                }
                            }
                        }
                        ClipboardHelper.setClipboardText(markdown)
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Markdown", fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        editingNote = null
                        showEditorDialog = true
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Note", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search your notes, books, and thoughts...") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Notes List
        if (filteredNotes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.NoteAlt, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No notes found", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredNotes, key = { it.id }) { note ->
                    val colorEntry = NOTE_COLOR_TAGS.firstOrNull { it.first == note.colorTag } ?: NOTE_COLOR_TAGS.first()

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                editingNote = note
                                showEditorDialog = true
                            },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colorEntry.second.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(colorEntry.second)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = note.title.ifBlank { "Untitled Note" },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (note.isPinned) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(Icons.Default.PushPin, "Pinned", tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (!note.documentTitle.isNullOrBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                text = note.documentTitle,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }

                                    IconButton(
                                        onClick = { onDeleteNote(note.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            if (note.content.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(note.content, style = MaterialTheme.typography.bodyMedium, lineHeight = 22.sp)
                            }

                            // Checklist Items
                            if (note.isChecklist && note.checklistItems.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    for ((cIdx, item) in note.checklistItems.withIndex()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable {
                                                val updatedItems = note.checklistItems.toMutableList()
                                                updatedItems[cIdx] = item.copy(isChecked = !item.isChecked)
                                                onSaveNote(note.copy(checklistItems = updatedItems))
                                            }
                                        ) {
                                            Checkbox(
                                                checked = item.isChecked,
                                                onCheckedChange = { isChecked ->
                                                    val updatedItems = note.checklistItems.toMutableList()
                                                    updatedItems[cIdx] = item.copy(isChecked = isChecked)
                                                    onSaveNote(note.copy(checklistItems = updatedItems))
                                                },
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = item.text,
                                                style = MaterialTheme.typography.bodySmall,
                                                textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                                                color = if (item.isChecked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(note.updatedAt)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }

    // Edit / Create Dialog
    if (showEditorDialog) {
        NoteEditorDialog(
            note = editingNote,
            onDismiss = { showEditorDialog = false },
            onSave = { savedNote ->
                onSaveNote(savedNote)
                showEditorDialog = false
            }
        )
    }
}

@Composable
private fun NoteEditorDialog(
    note: RichNote?,
    onDismiss: () -> Unit,
    onSave: (RichNote) -> Unit
) {
    var title by remember { mutableStateOf(note?.title ?: "") }
    var content by remember { mutableStateOf(note?.content ?: "") }
    var selectedColor by remember { mutableStateOf(note?.colorTag ?: "Yellow") }
    var isPinned by remember { mutableStateOf(note?.isPinned ?: false) }
    var isChecklist by remember { mutableStateOf(note?.isChecklist ?: false) }
    var checklistText by remember {
        mutableStateOf(note?.checklistItems?.joinToString("\n") { it.text } ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (note == null) "Create Note" else "Edit Note", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Note Content") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )

                // Checklist toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Checklist Mode", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = isChecklist, onCheckedChange = { isChecklist = it })
                }

                if (isChecklist) {
                    OutlinedTextField(
                        value = checklistText,
                        onValueChange = { checklistText = it },
                        label = { Text("Checklist Items (one per line)") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Pin toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Pin Note to Top", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = isPinned, onCheckedChange = { isPinned = it })
                }

                // Color Tags
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for ((name, color) in NOTE_COLOR_TAGS) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    if (selectedColor == name) 2.dp else 0.dp,
                                    if (selectedColor == name) Color.White else Color.Transparent,
                                    CircleShape
                                )
                                .clickable { selectedColor = name }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val items = if (isChecklist) {
                        checklistText.split("\n").filter { it.isNotBlank() }.map { ChecklistItem(it.trim(), false) }
                    } else emptyList()

                    val newNote = (note ?: RichNote()).copy(
                        title = title,
                        content = content,
                        colorTag = selectedColor,
                        isPinned = isPinned,
                        isChecklist = isChecklist,
                        checklistItems = items
                    )
                    onSave(newNote)
                }
            ) {
                Text("Save Note")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

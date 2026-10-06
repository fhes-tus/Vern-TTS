package com.veritas.reader.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veritas.reader.LibraryViewMode
import com.veritas.reader.ui.NotesSettings

internal val LocalHomeBottomPadding = compositionLocalOf { 0.dp }

@Stable
class ClassicsHeaderState(query: String = "", listMode: Boolean = false) {
    var query by mutableStateOf(query)
    var listMode by mutableStateOf(listMode)
}

@Composable
internal fun rememberClassicsHeaderState(): ClassicsHeaderState = rememberSaveable(saver = listSaver(
    save = { listOf(it.query, it.listMode) },
    restore = { ClassicsHeaderState(it[0] as String, it[1] as Boolean) }
)) { ClassicsHeaderState() }

@Composable
internal fun LibraryHeaderActions(
    section: LibrarySection, viewMode: LibraryViewMode, onViewMode: (LibraryViewMode) -> Unit,
    classics: ClassicsHeaderState, searchExpanded: Boolean, onSearch: () -> Unit,
    onReadingLists: () -> Unit, onReadingHistory: () -> Unit
) {
    var viewMenu by remember { mutableStateOf(false) }
    LaunchedEffect(section) { viewMenu = false }
    Box {
        IconButton({ if (section == LibrarySection.CLASSICS) classics.listMode = !classics.listMode else viewMenu = true },
            Modifier.testTag(if (section == LibrarySection.CLASSICS) "classics_view_mode" else "library_view_mode")) {
            val grid = if (section == LibrarySection.CLASSICS) !classics.listMode else viewMode == LibraryViewMode.TILES
            Icon(if (grid) Icons.Default.GridView else Icons.AutoMirrored.Filled.List, "View mode")
        }
        DropdownMenu(viewMenu, { viewMenu = false }) {
            LibraryViewMode.entries.forEach { mode ->
                DropdownMenuItem({ Text(mode.label) }, { onViewMode(mode); viewMenu = false })
            }
            HorizontalDivider()
            DropdownMenuItem({ Text("Reading lists") }, { viewMenu = false; onReadingLists() })
            DropdownMenuItem({ Text("Reading history") }, { viewMenu = false; onReadingHistory() })
        }
    }
    IconButton(onSearch, Modifier.testTag("library_search_toggle")) {
        Icon(if (searchExpanded) Icons.Default.SearchOff else Icons.Default.Search,
            if (searchExpanded) "Close library search" else "Search library")
    }
}

@Composable
internal fun NotesHeaderActions(
    settings: NotesSettings,
    onSave: (NotesSettings) -> Unit,
    onSettings: () -> Unit,
    searchExpanded: Boolean,
    onSearch: () -> Unit,
    showTrash: Boolean,
    onShowTrash: (Boolean) -> Unit,
    notebooks: List<com.veritas.reader.NoteNotebook>,
    selectedNotebookId: String?,
    onSelectNotebook: (String?) -> Unit,
    canManageNotebooks: Boolean,
    onCreateNotebook: () -> Unit,
    onManageNotebooks: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    var notebookPicker by remember { mutableStateOf(false) }
    var sortPicker by remember { mutableStateOf(false) }
    IconButton({ onSave(settings.copy(isGridView = !settings.isGridView)) }) {
        Icon(if (settings.isGridView) Icons.Default.GridView else Icons.AutoMirrored.Filled.List, "Toggle layout")
    }
    IconButton(onSearch, Modifier.testTag("notes_search_toggle")) {
        Icon(if (searchExpanded) Icons.Default.SearchOff else Icons.Default.Search,
            if (searchExpanded) "Close notes search" else "Search notes")
    }
    Box {
        IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Notes options") }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(
                text = { Text(if (showTrash) "Show notes" else "Show trash") },
                onClick = { onShowTrash(!showTrash); onSelectNotebook(null); menu = false }
            )
            if (!showTrash) {
                DropdownMenuItem(
                    text = { Text("Notebooks") },
                    onClick = { notebookPicker = true; menu = false },
                    leadingIcon = { Icon(Icons.Default.Label, null) }
                )
            }
            HorizontalDivider()
            if (!showTrash) DropdownMenuItem({ Text("Sort notes") }, { menu = false; sortPicker = true }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null) })
            DropdownMenuItem({ Text("Notes settings") }, { menu = false; onSettings() }, leadingIcon = { Icon(Icons.Default.Settings, null) })
        }
    }
    if (notebookPicker) NotebookPickerDialog(notebooks, selectedNotebookId,
        onSelect = { onSelectNotebook(it); notebookPicker = false },
        onDismiss = { notebookPicker = false },
        onCreate = if (canManageNotebooks) ({ notebookPicker = false; onCreateNotebook() }) else null,
        onManage = if (canManageNotebooks && notebooks.isNotEmpty()) ({ notebookPicker = false; onManageNotebooks() }) else null)
    if (sortPicker) AlertDialog(onDismissRequest = { sortPicker = false }, title = { Text("Sort notes") },
        text = { Column {
            listOf("date" to "Last edited", "created" to "Newest", "title" to "Title").forEach { (value, label) ->
                TextButton(onClick = { onSave(settings.copy(defaultSortOrder = value)); sortPicker = false }, modifier = Modifier.fillMaxWidth()) {
                    Text(label, Modifier.weight(1f))
                    if (settings.defaultSortOrder == value) Icon(Icons.Default.Check, null)
                }
            }
        } }, confirmButton = { TextButton({ sortPicker = false }) { Text("Close") } })
}

@Composable
internal fun LibrarySortControl(sort: String, onSort: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box {
            TextButton({ expanded = true }) { Text("Sort: $sort") }
            DropdownMenu(expanded, { expanded = false }) {
                listOf("Updated", "Newest", "Title", "Progress", "Type").forEach { value ->
                    DropdownMenuItem({ Text(value) }, { onSort(value); expanded = false })
                }
            }
        }
    }
}

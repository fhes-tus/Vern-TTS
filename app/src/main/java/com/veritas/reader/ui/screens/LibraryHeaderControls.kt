package com.veritas.reader.ui.screens

import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote

import androidx.compose.material.icons.automirrored.outlined.MenuBook

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
            Icon(if (grid) Icons.Default.GridView else Icons.AutoMirrored.Filled.List, com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_view_mode))
        }
        DropdownMenu(viewMenu, { viewMenu = false }) {
            LibraryViewMode.entries.forEach { mode ->
                DropdownMenuItem({ Text(com.veritas.reader.ui.screens.localizedCatalogLabel(mode.label)) }, { onViewMode(mode); viewMenu = false })
            }
            HorizontalDivider()
            DropdownMenuItem({ Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_reading_lists)) }, { viewMenu = false; onReadingLists() })
            DropdownMenuItem({ Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_reading_history)) }, { viewMenu = false; onReadingHistory() })
        }
    }
    IconButton(onSearch, Modifier.testTag("library_search_toggle")) {
        Icon(if (searchExpanded) Icons.Default.SearchOff else Icons.Default.Search,
            if (searchExpanded) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_close_library_search) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_search_library))
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
    onManageNotebooks: () -> Unit,
    onAppSettings: () -> Unit = {}
) {
    var menu by remember { mutableStateOf(false) }
    var notebookPicker by remember { mutableStateOf(false) }
    var sortPicker by remember { mutableStateOf(false) }
    IconButton({ onSave(settings.copy(isGridView = !settings.isGridView)) }) {
        Icon(if (settings.isGridView) Icons.Default.GridView else Icons.AutoMirrored.Filled.List, com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_toggle_layout))
    }
    IconButton(onSearch, Modifier.testTag("notes_search_toggle")) {
        Icon(if (searchExpanded) Icons.Default.SearchOff else Icons.Default.Search,
            if (searchExpanded) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_close_notes_search) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_search_notes))
    }
    Box {
        IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_notes_options)) }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(
                text = { Text(if (showTrash) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_show_notes) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_show_trash)) },
                leadingIcon = { Icon(if (showTrash) Icons.Outlined.EditNote else Icons.Outlined.DeleteOutline, null) },
                onClick = { onShowTrash(!showTrash); onSelectNotebook(null); menu = false }
            )
            if (!showTrash) {
                DropdownMenuItem(
                    text = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_notebooks)) },
                    onClick = { notebookPicker = true; menu = false },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.MenuBook, null) }
                )
            }
            HorizontalDivider()
            if (!showTrash) DropdownMenuItem({ Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_sort_notes)) }, { menu = false; sortPicker = true }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null) })
            DropdownMenuItem({ Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_notes_settings)) }, { menu = false; onSettings() }, leadingIcon = { Icon(Icons.Default.Settings, null) })
            DropdownMenuItem({ Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_app_settings)) }, { menu = false; onAppSettings() }, leadingIcon = { Icon(Icons.Default.Settings, null) })
        }
    }
    if (notebookPicker) NotebookPickerDialog(notebooks, selectedNotebookId,
        onSelect = { onSelectNotebook(it); notebookPicker = false },
        onDismiss = { notebookPicker = false },
        onCreate = if (canManageNotebooks) ({ notebookPicker = false; onCreateNotebook() }) else null,
        onManage = if (canManageNotebooks && notebooks.isNotEmpty()) ({ notebookPicker = false; onManageNotebooks() }) else null)
    if (sortPicker) AlertDialog(onDismissRequest = { sortPicker = false }, title = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_sort_notes)) },
        text = { Column {
            listOf("date" to "Last edited", "created" to "Newest", "title" to "Title").forEach { (value, label) ->
                TextButton(onClick = { onSave(settings.copy(defaultSortOrder = value)); sortPicker = false }, modifier = Modifier.fillMaxWidth()) {
                    Text(localizedCatalogLabel(label), Modifier.weight(1f))
                    if (settings.defaultSortOrder == value) Icon(Icons.Default.Check, null)
                }
            }
        } }, confirmButton = { TextButton({ sortPicker = false }) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_close)) } })
}

@Composable
internal fun LibrarySortControl(sort: String, onSort: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.End) {
        Box {
            TextButton({ expanded = true }) { Text(menuString(com.veritas.reader.R.string.l10n_dynamic_090, localizedCatalogLabel(sort))) }
            DropdownMenu(expanded, { expanded = false }) {
                listOf("Updated", "Newest", "Title", "Progress", "Type").forEach { value ->
                    DropdownMenuItem({ Text(localizedCatalogLabel(value)) }, { onSort(value); expanded = false })
                }
            }
        }
    }
}

package com.veritas.reader.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.veritas.reader.VernAlertDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veritas.reader.SavedDocument
import com.veritas.reader.VeritasReadingList
import com.veritas.reader.VeritasReadingListCatalog
import com.veritas.reader.VeritasReadingListSortMode

@Composable
fun ReadingListsDialog(
    catalog: VeritasReadingListCatalog,
    documents: List<SavedDocument>,
    activeDocumentId: String?,
    onDismiss: () -> Unit,
    onCreateList: (String) -> Unit,
    onAddDocument: (String, String) -> Unit,
    onRemoveDocument: (String, String) -> Unit,
    onOpenDocument: (SavedDocument) -> Unit,
    onMoveDocument: (String, String, Int) -> Unit,
    onSetSortMode: (String, VeritasReadingListSortMode) -> Unit,
    onArchiveList: (String) -> Unit,
    onDeleteList: (String) -> Unit
) {
    var newListTitle by rememberSaveable { mutableStateOf("") }
    val activeLists = catalog.activeLists
    val activeDocument = documents.firstOrNull { it.id == activeDocumentId }
    val documentTitles = remember(documents) { documents.associate { it.id to it.title } }
    val documentsById = remember(documents) { documents.associateBy { it.id } }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onDismiss, shape = RoundedCornerShape(50)) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_close)) } },
        title = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_reading_lists)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_create_list), style = MaterialTheme.typography.labelLarge)
                        OutlinedTextField(
                            value = newListTitle,
                            onValueChange = { newListTitle = it },
                            label = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_list_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(50)
                        )
                        Button(
                            onClick = {
                                onCreateList(newListTitle)
                                newListTitle = ""
                            },
                            enabled = newListTitle.trim().isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_create))
                        }
                    }
                }

                if (activeLists.isEmpty()) {
                    Text(
                        com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_no_reading_lists_yet),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    activeLists.forEach { list ->
                        ReadingListCard(
                            list = list,
                            documentsById = documentsById,
                            documentTitles = documentTitles,
                            activeDocument = activeDocument,
                            onAddDocument = onAddDocument,
                            onRemoveDocument = onRemoveDocument,
                            onOpenDocument = onOpenDocument,
                            onMoveDocument = onMoveDocument,
                            onSetSortMode = onSetSortMode,
                            onArchiveList = onArchiveList,
                            onDeleteList = onDeleteList
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun ReadingListCard(
    list: VeritasReadingList,
    documentsById: Map<String, SavedDocument>,
    documentTitles: Map<String, String>,
    activeDocument: SavedDocument?,
    onAddDocument: (String, String) -> Unit,
    onRemoveDocument: (String, String) -> Unit,
    onOpenDocument: (SavedDocument) -> Unit,
    onMoveDocument: (String, String, Int) -> Unit,
    onSetSortMode: (String, VeritasReadingListSortMode) -> Unit,
    onArchiveList: (String) -> Unit,
    onDeleteList: (String) -> Unit
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val orderedDocuments = remember(list, documentsById, documentTitles) {
        list.orderedItems(documentTitles).mapNotNull { documentsById[it.documentId] }
    }
    val containsActive = activeDocument?.let { list.contains(it.id) } == true
    val manualOrder = list.sortMode == VeritasReadingListSortMode.MANUAL

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(list.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_217, com.veritas.reader.ui.screens.localizedUiValue(orderedDocuments.size)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    OutlinedButton(onClick = { showSortMenu = true }, shape = RoundedCornerShape(50)) {
                        Text(sortModeLabel(list.sortMode))
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        VeritasReadingListSortMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(sortModeLabel(mode)) },
                                onClick = {
                                    showSortMenu = false
                                    onSetSortMode(list.id, mode)
                                }
                            )
                        }
                    }
                }
            }

            if (activeDocument != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (containsActive) onRemoveDocument(list.id, activeDocument.id) else onAddDocument(list.id, activeDocument.id)
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = containsActive,
                        onCheckedChange = { checked ->
                            if (checked) onAddDocument(list.id, activeDocument.id) else onRemoveDocument(list.id, activeDocument.id)
                        }
                    )
                    Text(
                        if (containsActive) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_current_reading_is_in_this_list) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_add_current_reading),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (orderedDocuments.isEmpty()) {
                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_this_list_is_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                orderedDocuments.forEachIndexed { index, document ->
                    ReadingListDocumentRow(
                        document = document,
                        canMoveUp = manualOrder && index > 0,
                        canMoveDown = manualOrder && index < orderedDocuments.lastIndex,
                        onOpen = { onOpenDocument(document) },
                        onMoveUp = { onMoveDocument(list.id, document.id, -1) },
                        onMoveDown = { onMoveDocument(list.id, document.id, 1) },
                        onRemove = { onRemoveDocument(list.id, document.id) }
                    )
                }
            }

            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onArchiveList(list.id) }, shape = RoundedCornerShape(50)) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_archive)) }
                Button(onClick = { confirmDelete = true }, shape = RoundedCornerShape(50)) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_delete)) }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_delete_list)) },
            text = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_this_removes_the_list_only_saved_readings_stay_in_your_library)) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        onDeleteList(list.id)
                    },
                    shape = RoundedCornerShape(50)
                ) {
                    Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_delete))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmDelete = false }, shape = RoundedCornerShape(50)) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_cancel)) }
            }
        )
    }
}

@Composable
private fun ReadingListDocumentRow(
    document: SavedDocument,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f).clickable { onOpen() }) {
            Text(document.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
            Text(
                document.sourceLabel.ifBlank { com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_text) },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onMoveUp, enabled = canMoveUp) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_up)) }
        TextButton(onClick = onMoveDown, enabled = canMoveDown) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_down)) }
        TextButton(onClick = onRemove) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_remove)) }
    }
}

private fun sortModeLabel(mode: VeritasReadingListSortMode): String =
    when (mode) {
        VeritasReadingListSortMode.MANUAL -> "Manual"
        VeritasReadingListSortMode.TITLE_ASCENDING -> "Title A-Z"
        VeritasReadingListSortMode.TITLE_DESCENDING -> "Title Z-A"
        VeritasReadingListSortMode.NEWEST_ADDED -> "Newest"
        VeritasReadingListSortMode.OLDEST_ADDED -> "Oldest"
    }

@Composable
fun ManageDocumentListsDialog(
    document: SavedDocument,
    catalog: VeritasReadingListCatalog,
    onDismiss: () -> Unit,
    onCreateReadingList: (String) -> Unit,
    onAddDocumentToReadingList: (String, String) -> Unit,
    onRemoveDocumentFromReadingList: (String, String) -> Unit
) {
    var newListTitle by rememberSaveable { mutableStateOf("") }
    val activeLists = catalog.activeLists

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(50)) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_close)) }
        },
        title = {
            Text(
                text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_save_to_collection),
                style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_choose_collections_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (activeLists.isEmpty()) {
                    Text(
                        com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_no_collections_yet),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    activeLists.forEach { list ->
                        val containsDoc = list.contains(document.id)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (containsDoc) {
                                        onRemoveDocumentFromReadingList(list.id, document.id)
                                    } else {
                                        onAddDocumentToReadingList(list.id, document.id)
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (containsDoc) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = containsDoc,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            onAddDocumentToReadingList(list.id, document.id)
                                        } else {
                                            onRemoveDocumentFromReadingList(list.id, document.id)
                                        }
                                    }
                                )
                                Text(
                                    text = list.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_new_collection_name), style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(
                            value = newListTitle,
                            onValueChange = { newListTitle = it },
                            label = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_new_collection_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(50)
                        )
                        Button(
                            onClick = {
                                onCreateReadingList(newListTitle)
                                newListTitle = ""
                            },
                            enabled = newListTitle.trim().isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_create_collection))
                        }
                    }
                }
            }
        }
    )
}

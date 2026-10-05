package com.veritas.reader

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.veritas.reader.ui.screens.FeatureDropdownMenuItem
import java.util.Locale


@Composable
internal fun FileBrowserDialog(
    roots: List<VeritasBrowserRoot>,
    entries: List<VeritasBrowserFile>,
    location: VeritasBrowserLocation?,
    canGoUp: Boolean,
    scanning: Boolean,
    message: String?,
    allFilesAccessGranted: Boolean,
    importing: Boolean,
    importingName: String,
    onDismiss: () -> Unit,
    onPickFolder: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onOpenFilePicker: () -> Unit,
    onRefresh: () -> Unit,
    onGoUp: () -> Unit,
    onEnterDirectory: (VeritasBrowserFile) -> Unit,
    onRemoveAllAccess: () -> Unit,
    onImportFile: (VeritasBrowserFile) -> Unit,
    onImportMultipleFiles: (List<VeritasBrowserFile>, Boolean) -> Unit,
    onDeleteFiles: (List<VeritasBrowserFile>) -> Unit = {}
) {
    val selectedFiles = remember { mutableStateListOf<VeritasBrowserFile>() }
    // Deleting reaches the user's own storage and cannot be undone, so nothing is removed
    // until this is confirmed with the files named.
    var pendingDelete by remember { mutableStateOf<List<VeritasBrowserFile>>(emptyList()) }
    
    FileBrowserDeleteConfirmationDialog(
        doomedFiles = pendingDelete,
        onConfirmDelete = {
            onDeleteFiles(pendingDelete)
            selectedFiles.clear()
            pendingDelete = emptyList()
        },
        onDismiss = { pendingDelete = emptyList() }
    )

    LaunchedEffect(location) {
        selectedFiles.clear()
    }

    var query by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(VeritasBrowserTab.ALL) }
    var sortMode by remember { mutableStateOf(VeritasBrowserSort.NAME) }
    var sortAscending by remember { mutableStateOf(true) }
    var showMoreMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val browserPrefs = remember { context.getSharedPreferences("veritas_library_settings", Context.MODE_PRIVATE) }
    var viewMode by remember {
        mutableStateOf(
            runCatching {
                LibraryViewMode.valueOf(
                    browserPrefs.getString("file_view_mode", LibraryViewMode.TILES.name) ?: LibraryViewMode.TILES.name
                )
            }.getOrDefault(LibraryViewMode.TILES)
        )
    }
    var showSortDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showViewMenu by remember { mutableStateOf(false) }
    val windowWidthDp = with(androidx.compose.ui.platform.LocalDensity.current) {
        androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width.toDp()
    }
    val columnCount = when {
        windowWidthDp >= 840.dp -> 4
        windowWidthDp >= 600.dp -> 3
        else -> 2
    }
    val browserFeatures = remember(roots, allFilesAccessGranted) {
        VeritasFeatureRegistry.resolve(
            VeritasFeatureSurface.FILE_BROWSER_OVERFLOW,
            VeritasFeatureContext(hasFileBrowserSession = roots.isNotEmpty() || allFilesAccessGranted)
        ).associateBy { it.definition.id }
    }
    // The scanner deduplicates on its IO thread; canonical path reads here
    // would stall rendering each new batch.
    val distinctEntries = entries
    val visibleEntries = remember(distinctEntries, query, selectedTab, sortMode, sortAscending) {
        val needle = query.trim()
        val filtered = distinctEntries
            .filter { selectedTab == VeritasBrowserTab.ALL || it.isDirectory || it.type == selectedTab }
            .filter { file ->
                needle.isBlank() ||
                        file.name.contains(needle, ignoreCase = true) ||
                        file.rootLabel.contains(needle, ignoreCase = true) ||
                        file.relativePath.contains(needle, ignoreCase = true)
            }
        val comparator = when (sortMode) {
            VeritasBrowserSort.NAME -> compareBy<VeritasBrowserFile> { it.name.lowercase(Locale.getDefault()) }
            VeritasBrowserSort.DATE -> compareBy { it.modifiedAt }
            VeritasBrowserSort.SIZE -> compareBy { it.sizeBytes }
            VeritasBrowserSort.PATH -> compareBy { it.relativePath.lowercase(Locale.getDefault()) }
        }
        val sorted =
            if (sortAscending) filtered.sortedWith(comparator) else filtered.sortedWith(comparator.reversed())
        sorted.sortedBy { it.isDirectory }
    }

    val folders = remember(visibleEntries, canGoUp, query) {
        val list = visibleEntries.filter { it.isDirectory }
        if (canGoUp && query.isBlank()) {
            listOf(
                VeritasBrowserFile(
                    uri = Uri.parse("vern://parent_directory"),
                    name = ".. (Go up)",
                    mimeType = "",
                    sizeBytes = 0L,
                    modifiedAt = 0L,
                    rootLabel = "",
                    relativePath = "",
                    isDirectory = true,
                    isSupported = true,
                    targetLocation = VeritasBrowserLocation(rootLabel = "Parent")
                )
            ) + list
        } else {
            list
        }
    }
    val files = remember(visibleEntries) {
        val nonDirs = visibleEntries.filter { !it.isDirectory }
        // De-prioritize OCR images: documents appear first, images appear last.
        // Stable sort preserves user's chosen sort order (name, date, size, path) within each group.
        nonDirs.sortedBy { it.type == VeritasBrowserTab.OCR }
    }

    val allVisibleSelected = files.isNotEmpty() && files.all { vf ->
        selectedFiles.any { it.uri == vf.uri }
    }

    val toggleSelectAllForActiveFilter: () -> Unit = {
        if (allVisibleSelected) {
            val visibleUris = files.map { it.uri }.toSet()
            selectedFiles.removeAll { it.uri in visibleUris }
        } else {
            val currentUris = selectedFiles.map { it.uri }.toSet()
            val toAdd = files.filter { it.uri !in currentUris }
            selectedFiles.addAll(toAdd)
        }
    }

    val floatingGlass = VeritasPackStyle.glassChromeEnabled() && isDeviceGlassCapable()
    val fileBackdrop = if (floatingGlass) rememberVeritasLayerBackdrop() else null
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        VeritasBackdropProvider(fileBackdrop) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .background(VeritasPackStyle.backgroundBrush(MaterialTheme.colorScheme))
        ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .recordVeritasBackdrop(fileBackdrop, floatingGlass)
                        .background(VeritasPackStyle.backgroundBrush(MaterialTheme.colorScheme))
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    if (selectedFiles.isNotEmpty()) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { selectedFiles.clear() }) {
                                    Icon(Icons.Filled.Close, "Clear selection", tint = MaterialTheme.colorScheme.primary)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("${selectedFiles.size} selected", style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    Text(if (allVisibleSelected) "All ${selectedTab.label} selected" else
                                        "${files.count { vf -> selectedFiles.any { it.uri == vf.uri } }} of ${files.size} in ${selectedTab.label}",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = { pendingDelete = selectedFiles.toList() }, enabled = !importing) {
                                    Icon(Icons.Outlined.Delete, "Delete ${selectedFiles.size} files", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = toggleSelectAllForActiveFilter, modifier = Modifier.weight(1f), enabled = !importing) {
                                    Text(if (allVisibleSelected) "Deselect tab" else "Select tab (${files.size})",
                                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                                Button(onClick = {
                                    onImportMultipleFiles(VeritasFileBrowserScanner.deduplicateBrowserFiles(selectedFiles.toList()), false)
                                    selectedFiles.clear()
                                }, enabled = !importing, shape = VeritasPackStyle.chipShape()) { Text("Import") }
                                Button(onClick = {
                                    onImportMultipleFiles(VeritasFileBrowserScanner.deduplicateBrowserFiles(selectedFiles.toList()), true)
                                    selectedFiles.clear()
                                }, enabled = !importing, shape = VeritasPackStyle.chipShape(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary,
                                        contentColor = MaterialTheme.colorScheme.onSecondary)) { Text("Queue") }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (canGoUp) {
                                            onGoUp()
                                        } else {
                                            onDismiss()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Column {
                                    Text(
                                        "File browser",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        when {
                                            allFilesAccessGranted -> "All Files access • ${location?.label ?: "Phone storage"}"
                                            roots.isEmpty() -> "No folders approved"
                                            else -> location?.label
                                                ?: "${roots.size} approved folder${if (roots.size == 1) "" else "s"}"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Box {
                                IconButton(
                                    onClick = { showMoreMenu = true },
                                    enabled = !importing
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = "More options",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Import with file picker") },
                                        enabled = !importing,
                                        onClick = {
                                            showMoreMenu = false
                                            onOpenFilePicker()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Go up") },
                                        enabled = canGoUp,
                                        onClick = {
                                            showMoreMenu = false
                                            onGoUp()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Refresh files") },
                                        enabled = (roots.isNotEmpty() || allFilesAccessGranted) && !scanning,
                                        onClick = {
                                            showMoreMenu = false
                                            onRefresh()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (allFilesAccessGranted) "All files access granted" else "Grant all files access") },
                                        enabled = !allFilesAccessGranted,
                                        onClick = {
                                            showMoreMenu = false
                                            onRequestAllFilesAccess()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Folders to scan") },
                                        onClick = {
                                            showMoreMenu = false
                                            onPickFolder()
                                        }
                                    )
                                    FeatureDropdownMenuItem(
                                        feature = browserFeatures.requireResolvedFeature(
                                            VeritasFeatureId.FILE_BROWSER_SORTING
                                        ),
                                        label = "Sort files",
                                        onClick = {
                                            showMoreMenu = false
                                            showSortDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Remove all files access") },
                                        enabled = roots.isNotEmpty(),
                                        onClick = {
                                            showMoreMenu = false
                                            onRemoveAllAccess()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .padding(horizontal = 12.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50)),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                        decorationBox = { innerTextField ->
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (query.isEmpty()) {
                                        Text(
                                            text = "Search files...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VeritasBrowserTab.entries.forEach { tab ->
                            val count =
                                if (tab == VeritasBrowserTab.ALL) distinctEntries.count { !it.isDirectory } else distinctEntries.count { !it.isDirectory && it.type == tab }
                            if (selectedTab == tab) {
                                Button(
                                    onClick = { selectedTab = tab },
                                    shape = VeritasPackStyle.chipShape(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("${tab.label} $count")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { selectedTab = tab },
                                    shape = VeritasPackStyle.chipShape(),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("${tab.label} $count")
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${visibleEntries.count { !it.isDirectory }} files, ${visibleEntries.count { it.isDirectory }} folders",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )

                        // Sort Dropdown Chip
                        Box {
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50))
                                    .clickable { showSortMenu = true }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "${sortMode.label} ${if (sortAscending) "▲" else "▼"}",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                VeritasBrowserSort.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text("Sort by ${mode.label}") },
                                        onClick = {
                                            sortMode = mode
                                            showSortMenu = false
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(if (sortAscending) "Descending order" else "Ascending order") },
                                    onClick = {
                                        sortAscending = !sortAscending
                                        showSortMenu = false
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // View Menu (View mode / Refresh)
                        Box {
                            IconButton(onClick = { showViewMenu = true }) {
                                Text(
                                    text = viewMode.icon,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            DropdownMenu(expanded = showViewMenu, onDismissRequest = { showViewMenu = false }) {
                                LibraryViewMode.values().forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text("${mode.icon} ${mode.label}", color = MaterialTheme.colorScheme.onSurface) },
                                        onClick = {
                                            viewMode = mode
                                            browserPrefs.edit().putString("file_view_mode", mode.name).apply()
                                            showViewMenu = false
                                        }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Refresh files") },
                                    onClick = {
                                        onRefresh()
                                        showViewMenu = false
                                    }
                                )
                            }
                        }
                    }

                    message?.let {
                        Text(
                            it,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (importing) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                "Importing ${importingName.ifBlank { "selected file" }}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    when {
                        roots.isEmpty() && !allFilesAccessGranted -> FileBrowserEmptyState(
                            onPickFolder = onPickFolder,
                            onRequestAllFilesAccess = onRequestAllFilesAccess
                        )

                        scanning && entries.isEmpty() -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    "Opening folder.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        visibleEntries.isEmpty() -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No files or folders match this view.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        else -> LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {

                            if (scanning) {
                                item("scan-progress") { Text("Finding more files…", style = MaterialTheme.typography.labelSmall) }
                            }

                            if (files.isNotEmpty()) {
                                item("files-header") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = when (selectedTab) {
                                                    VeritasBrowserTab.ALL -> "Files & Documents"
                                                    VeritasBrowserTab.OCR -> "Photos & Images"
                                                    else -> "${selectedTab.label} Documents"
                                                },
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Black
                                            )
                                            Text(
                                                text = when (selectedTab) {
                                                    VeritasBrowserTab.ALL -> "Documents prioritized • Images at end"
                                                    VeritasBrowserTab.OCR -> "Select images for text extraction (OCR)"
                                                    else -> "${files.size} ${selectedTab.label} file${if (files.size == 1) "" else "s"}"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        OutlinedButton(
                                            onClick = toggleSelectAllForActiveFilter,
                                            shape = com.veritas.reader.VeritasPackStyle.chipShape(),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            border = BorderStroke(
                                                1.dp,
                                                if (allVisibleSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                            ),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (allVisibleSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent,
                                                contentColor = if (allVisibleSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        ) {
                                            Icon(
                                                imageVector = if (allVisibleSelected) Icons.Filled.Close else Icons.Filled.SelectAll,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (allVisibleSelected) "Deselect ${selectedTab.label}" else "Select all ${selectedTab.label} (${files.size})",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                            if (viewMode == LibraryViewMode.TILES) {
                                val chunkedFiles = files.chunked(columnCount)
                                items(chunkedFiles.size) { rowIndex ->
                                    val rowFiles = chunkedFiles[rowIndex]
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        rowFiles.forEach { file ->
                                            val isSelected = selectedFiles.any { it.uri == file.uri }
                                            FileBrowserFileTileCard(
                                                file = file,
                                                importing = importing,
                                                onOpenDirectory = { onEnterDirectory(file) },
                                                onImport = { onImportFile(file) },
                                                isSelected = isSelected,
                                                onSelectedChange = { checked ->
                                                    if (checked) {
                                                        if (selectedFiles.none { it.uri == file.uri }) selectedFiles.add(file)
                                                    } else {
                                                        selectedFiles.removeAll { it.uri == file.uri }
                                                    }
                                                },
                                                selectionMode = selectedFiles.isNotEmpty(),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        val remainder = columnCount - rowFiles.size
                                        repeat(remainder) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            } else {
                                items(files, key = { it.uri.toString() }) { file ->
                                    val isSelected = selectedFiles.any { it.uri == file.uri }
                                    FileBrowserFileRow(
                                        file = file,
                                        viewMode = viewMode,
                                        importing = importing,
                                        onOpenDirectory = { onEnterDirectory(file) },
                                        onImport = { onImportFile(file) },
                                        isSelected = isSelected,
                                        onSelectedChange = { checked ->
                                            if (checked) {
                                                if (selectedFiles.none { it.uri == file.uri }) selectedFiles.add(file)
                                            } else {
                                                selectedFiles.removeAll { it.uri == file.uri }
                                            }
                                        },
                                        selectionMode = selectedFiles.isNotEmpty()
                                    )
                                }
                            }
                            if (folders.isNotEmpty()) {
                                item("folders-header") {
                                    Text(
                                        "Folders",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(top = 12.dp)
                                    )
                                }
                            }
                            if (viewMode == LibraryViewMode.TILES) {
                                val chunkedFolders = folders.chunked(columnCount)
                                items(chunkedFolders.size) { rowIndex ->
                                    val rowFolders = chunkedFolders[rowIndex]
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        rowFolders.forEach { folder ->
                                            FileBrowserFileTileCard(
                                                file = folder,
                                                importing = importing,
                                                onOpenDirectory = {
                                                    if (folder.name == ".. (Go up)") {
                                                        onGoUp()
                                                    } else {
                                                        onEnterDirectory(folder)
                                                    }
                                                },
                                                onImport = { onImportFile(folder) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        val remainder = columnCount - rowFolders.size
                                        repeat(remainder) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            } else {
                                items(folders, key = { it.uri.toString() }) { folder ->
                                    FileBrowserFileRow(
                                        file = folder,
                                        viewMode = viewMode,
                                        importing = importing,
                                        onOpenDirectory = {
                                            if (folder.name == ".. (Go up)") {
                                                onGoUp()
                                            } else {
                                                onEnterDirectory(folder)
                                            }
                                        },
                                        onImport = { onImportFile(folder) }
                                    )
                                }
                            }
                        }
                    }
                }
                if (selectedFiles.isEmpty()) Button(
                    onClick = onOpenFilePicker,
                    enabled = !importing,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(22.dp)
                        .veritasGlassBackdrop(VeritasPackStyle.chipShape(), floatingGlass, surfaceOpacity = .72f),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = if (floatingGlass) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.primary,
                        contentColor = if (floatingGlass) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary),
                    shape = VeritasPackStyle.chipShape(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Browse storage")
                }
                if (showSortDialog) {
                    FileBrowserSortDialog(
                        sortMode = sortMode,
                        sortAscending = sortAscending,
                        onSortModeChange = { sortMode = it },
                        onSortAscendingChange = { sortAscending = it },
                        onDismiss = { showSortDialog = false }
                    )
                }
            }
        }
        }
        }
    }


package com.veritas.reader.ui.screens

import android.content.Context
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.edit
import com.veritas.reader.LibraryViewMode
import com.veritas.reader.ResolvedVeritasFeature
import com.veritas.reader.SavedDocument
import com.veritas.reader.SoftChip
import com.veritas.reader.VeritasFeatureContext
import com.veritas.reader.VeritasFeatureId
import com.veritas.reader.VeritasFeatureRegistry
import com.veritas.reader.VeritasFeatureSurface
import com.veritas.reader.progressFraction
import com.veritas.reader.ui.OnboardingController
import com.veritas.reader.ui.ReaderUiState
import java.util.Locale


@Composable
internal fun LibraryBooksTab(
    documents: List<SavedDocument>,
    queuedDocuments: List<SavedDocument>,
    uiState: ReaderUiState,
    libraryListState: LazyListState,
    libraryQuery: String,
    onLibraryQueryChange: (String) -> Unit,
    statusFilter: String,
    onStatusFilterChange: (String) -> Unit,
    sourceFilter: String,
    onSourceFilterChange: (String) -> Unit,
    collectionFilter: String,
    onCollectionFilterChange: (String) -> Unit,
    readingListFilter: String,
    onReadingListFilterChange: (String) -> Unit,
    sortMode: String,
    columnCount: Int,
    libraryViewMode: LibraryViewMode,
    onLibraryViewModeChange: (LibraryViewMode) -> Unit,
    selectedDocumentIds: Set<String>,
    onSelectedDocumentIdsChange: (Set<String>) -> Unit,
    onBatchFavoriteDocuments: (Set<String>) -> Unit,
    onBatchQueueDocuments: (Set<String>) -> Unit,
    onShowBatchCollectionDialog: () -> Unit,
    onConfirmBatchDelete: () -> Unit,
    onOpenDocument: (SavedDocument) -> Unit,
    onDeleteDocument: (SavedDocument) -> Unit,
    onToggleQueue: (SavedDocument) -> Unit,
    onMoveQueueUp: (SavedDocument) -> Unit,
    onMoveQueueDown: (SavedDocument) -> Unit,
    onToggleFavorite: (SavedDocument) -> Unit,
    onRenameDocument: (SavedDocument) -> Unit,
    onSetCollection: (SavedDocument) -> Unit,
    onShowDetails: (SavedDocument) -> Unit,
    onManageLists: (SavedDocument) -> Unit,
    onImportFile: () -> Unit,
    onOpenReadingLists: () -> Unit,
    onOpenReadingHistory: () -> Unit,
    onSearchLibraryContent: (String) -> Unit,
    onRefreshMainPage: () -> Unit,
    isQueued: (SavedDocument) -> Boolean,
    onReorderDocuments: (List<SavedDocument>) -> Unit = {},
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope? = null,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope? = null,
    filters: @Composable () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val floatingBottomPadding = LocalHomeBottomPadding.current

    var lastMainPageRefreshAt by remember { mutableLongStateOf(0L) }
    var showLibraryViewMenu by remember { mutableStateOf(false) }
    var showBatchMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val libraryPrefs = remember { context.getSharedPreferences("veritas_library_settings", Context.MODE_PRIVATE) }
    val selectionMode = selectedDocumentIds.isNotEmpty()
    val selectedHomeTab = VeritasHomeTab.LIBRARY

    val libraryFeatures = remember(documents.size, queuedDocuments.size) {
        VeritasFeatureRegistry.resolve(
            VeritasFeatureSurface.LIBRARY_OVERFLOW,
            VeritasFeatureContext(
                hasSavedDocument = documents.isNotEmpty(),
                queueCount = queuedDocuments.size
            )
        ).associateBy { it.definition.id }
    }

    fun libraryFeature(id: VeritasFeatureId): ResolvedVeritasFeature =
        libraryFeatures.requireResolvedFeature(id)

    val visibleDocuments by remember(documents, queuedDocuments, libraryQuery, statusFilter, sourceFilter, collectionFilter, readingListFilter, sortMode, uiState.readingListCatalog) {
        derivedStateOf {
            documents.asSequence()
                .filter { doc ->
                    val q = libraryQuery.trim()
                    q.isBlank() || doc.title.contains(q, ignoreCase = true) || doc.preview.contains(q, ignoreCase = true) || doc.sourceLabel.contains(q, ignoreCase = true) || doc.collection.contains(q, ignoreCase = true)
                }
                .filter { doc ->
                    when (statusFilter) {
                        "Favorites" -> doc.favorite
                        "Queued" -> isQueued(doc)
                        "Unread" -> doc.currentIndex <= 0
                        "In progress" -> doc.chunkCount > 1 && doc.currentIndex in 1 until doc.chunkCount - 1
                        "Completed" -> doc.chunkCount > 0 && doc.currentIndex >= doc.chunkCount - 1
                        else -> true
                    }
                }
                .filter { doc -> sourceFilter == "All" || doc.sourceLabel == sourceFilter }
                .filter { doc ->
                    when (collectionFilter) {
                        "All" -> true
                        "Unfiled" -> doc.collection.isBlank()
                        else -> doc.collection == collectionFilter
                    }
                }
                .filter { doc ->
                    if (readingListFilter == "All") {
                        true
                    } else {
                        val list = uiState.readingListCatalog.list(readingListFilter)
                        list?.contains(doc.id) == true
                    }
                }
                .toList()
                .let { list ->
                    if (statusFilter == "Queued") {
                        // Play order, not the library's sort: sequence is the point of a queue.
                        val position = queuedDocuments.withIndex().associate { (i, doc) -> doc.id to i }
                        return@let list.sortedBy { position[it.id] ?: Int.MAX_VALUE }
                    }
                    when (sortMode) {
                        "Title" -> list.sortedBy { it.title.lowercase(Locale.getDefault()) }
                        "Progress" -> list.sortedByDescending { progressFraction(it) }
                        "Type" -> list.sortedWith(compareBy<SavedDocument> { it.sourceLabel }.thenBy { it.title.lowercase(Locale.getDefault()) })
                        "Newest" -> list.sortedByDescending { it.createdAt }
                        "Custom" -> list
                        else -> list
                    }
                }
        }
    }

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    var draggingDocId by remember { mutableStateOf<String?>(null) }
    var dragAccumulatedOffset by remember { mutableStateOf(Offset.Zero) }
    var localDocuments by remember(visibleDocuments) { mutableStateOf(visibleDocuments) }

    LaunchedEffect(visibleDocuments) {
        if (draggingDocId == null) {
            localDocuments = visibleDocuments
        }
    }


                                Column(modifier = Modifier.fillMaxSize()) {
                                    if (libraryQuery.trim().length >= 3) {
                                        TextButton(
                                            onClick = {
                                                onSearchLibraryContent(libraryQuery)
                                                
                                            },
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)
                                        ) {
                                            Text("Search inside documents for “${libraryQuery.trim()}”")
                                        }
                                    }

                                    LazyColumn(
                                        modifier = Modifier
                                            .staggeredEntrance(1)
                                            .fillMaxSize()
                                            .pointerInput(libraryListState) {
                                                var pullDistance = 0f
                                                detectVerticalDragGestures(
                                                    onDragStart = { pullDistance = 0f },
                                                    onVerticalDrag = { _, dragAmount ->
                                                        val atTop = libraryListState.firstVisibleItemIndex == 0 && libraryListState.firstVisibleItemScrollOffset == 0
                                                        if (atTop && dragAmount > 0f) pullDistance += dragAmount
                                                    },
                                                    onDragEnd = {
                                                        val now = System.currentTimeMillis()
                                                        if (pullDistance > 120f && now - lastMainPageRefreshAt > 1500L) {
                                                            lastMainPageRefreshAt = now
                                                            onRefreshMainPage()
                                                        }
                                                        pullDistance = 0f
                                                    },
                                                    onDragCancel = { pullDistance = 0f }
                                                )
                                            }
                                            .padding(horizontal = 18.dp),
                                        state = libraryListState,
                                        contentPadding = PaddingValues(top = 0.dp, bottom = floatingBottomPadding + 92.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        item("library_filters") { filters() }
        if (selectedHomeTab == VeritasHomeTab.LIBRARY && selectionMode) item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (selectionMode) {
                            "${selectedDocumentIds.size} selected • ${visibleDocuments.size} showing"
                        } else {
                            "${visibleDocuments.size} showing"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (selectionMode) {
                    Box {
                        TextButton(onClick = { showBatchMenu = true }) {
                            Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "Batch actions")
                        }
                        DropdownMenu(expanded = showBatchMenu, onDismissRequest = { showBatchMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("All") },
                                onClick = {
                                    onSelectedDocumentIdsChange(visibleDocuments.map { it.id }.toSet())
                                    showBatchMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = {
                                    showBatchMenu = false
                                    onConfirmBatchDelete()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Add to favorites") },
                                onClick = {
                                    onBatchFavoriteDocuments(selectedDocumentIds)
                                    onSelectedDocumentIdsChange(emptySet())
                                    showBatchMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Add to Queue") },
                                onClick = {
                                    onBatchQueueDocuments(selectedDocumentIds)
                                    onSelectedDocumentIdsChange(emptySet())
                                    showBatchMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Move to collection") },
                                onClick = {
                                    
                                    showBatchMenu = false
                                    onShowBatchCollectionDialog()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Cancel") },
                                onClick = {
                                    onSelectedDocumentIdsChange(emptySet())
                                    showBatchMenu = false
                                }
                            )
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SoftChip(sortMode)
                    }
                }
            }
        }

        if (documents.isEmpty()) {
            item { EmptyLibraryCard(onImportFile = onImportFile) }
        } else if (visibleDocuments.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No matching readings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        Text("Clear the search or change filters to see more saved readings.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(
                            onClick = {
                                onLibraryQueryChange("")
                                onStatusFilterChange("All")
                                onSourceFilterChange("All")
                                onCollectionFilterChange("All")
                                onReadingListFilterChange("All")
                            }
                        ) {
                            Text("Clear filters")
                        }
                    }
                }
            }
        } else {
            if (libraryViewMode == LibraryViewMode.TILES) {
                itemsIndexed(localDocuments.chunked(columnCount), key = { index, _ -> "grid-row-$index" }) { _, rowDocs ->
                    Row(
                        modifier = Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .zIndex(if (rowDocs.any { it.id == draggingDocId }) 100f else 1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowDocs.forEach { doc ->
                            val isDragging = doc.id == draggingDocId
                            val dragScale by animateFloatAsState(
                                targetValue = if (isDragging) 1.06f else 1f,
                                animationSpec = com.veritas.reader.ui.VeritasMotion.spatialFast(),
                                label = "gridDragScale"
                            )
                            val gridColThresholdPx = with(density) { 130.dp.toPx() }
                            val gridRowThresholdPx = with(density) { 200.dp.toPx() }

                            DocumentTileCard(
                                document = doc,
                                isQueued = isQueued(doc),
                                selectionMode = selectionMode,
                                selected = doc.id in selectedDocumentIds,
                                onOpen = { onOpenDocument(doc) },
                                onLongPress = { onSelectedDocumentIdsChange(selectedDocumentIds + doc.id) },
                                onToggleSelected = {
                                    onSelectedDocumentIdsChange(if (doc.id in selectedDocumentIds) selectedDocumentIds - doc.id else selectedDocumentIds + doc.id)
                                },
                                onDelete = { onDeleteDocument(doc) },
                                onToggleQueue = { onToggleQueue(doc) },
                                onMoveQueueUp = { onMoveQueueUp(doc) },
                                onMoveQueueDown = { onMoveQueueDown(doc) },
                                onToggleFavorite = { onToggleFavorite(doc) },
                                onRename = { onRenameDocument(doc) },
                                onSetCollection = { onSetCollection(doc) },
                                onShowDetails = { onShowDetails(doc) },
                                modifier = Modifier
                                    .weight(1f)
                                    .animateItem()
                                    .zIndex(if (isDragging) 100f else 1f)
                                    .graphicsLayer {
                                        scaleX = dragScale
                                        scaleY = dragScale
                                        if (isDragging) {
                                            translationX = dragAccumulatedOffset.x
                                            translationY = dragAccumulatedOffset.y
                                            shadowElevation = 36f
                                        }
                                    }
                                    .pointerInput(doc.id, selectionMode) {
                                        if (!selectionMode) {
                                            awaitEachGesture {
                                                val down = awaitFirstDown(requireUnconsumed = false)
                                                var dragStarted = false
                                                var totalPan = Offset.Zero
                                                val longPress = awaitLongPressOrCancellation(down.id)
                                                if (longPress != null) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    var released = false
                                                    while (!released) {
                                                        val event = awaitPointerEvent()
                                                        val change = event.changes.firstOrNull { it.id == down.id }
                                                        if (change == null || !change.pressed) {
                                                            released = true
                                                            if (!dragStarted) {
                                                                onSelectedDocumentIdsChange(selectedDocumentIds + doc.id)
                                                            } else {
                                                                if (draggingDocId != null) {
                                                                    onReorderDocuments(localDocuments)
                                                                    draggingDocId = null
                                                                    dragAccumulatedOffset = Offset.Zero
                                                                }
                                                            }
                                                        } else {
                                                            val dragAmount = change.positionChange()
                                                            totalPan += dragAmount
                                                            if (!dragStarted && totalPan.getDistance() > viewConfiguration.touchSlop) {
                                                                dragStarted = true
                                                                draggingDocId = doc.id
                                                                dragAccumulatedOffset = Offset.Zero
                                                            }
                                                            if (dragStarted) {
                                                                change.consume()
                                                                dragAccumulatedOffset += dragAmount
                                                                val currentIdx = localDocuments.indexOfFirst { it.id == draggingDocId }
                                                                if (currentIdx != -1) {
                                                                    val col = currentIdx % columnCount
                                                                    if (col < columnCount - 1 && dragAccumulatedOffset.x > gridColThresholdPx && currentIdx + 1 < localDocuments.size) {
                                                                        val updated = localDocuments.toMutableList()
                                                                        val targetIdx = currentIdx + 1
                                                                        val item = updated.removeAt(currentIdx)
                                                                        updated.add(targetIdx, item)
                                                                        localDocuments = updated
                                                                        dragAccumulatedOffset = Offset(dragAccumulatedOffset.x - gridColThresholdPx, dragAccumulatedOffset.y)
                                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                    } else if (col > 0 && dragAccumulatedOffset.x < -gridColThresholdPx && currentIdx - 1 >= 0) {
                                                                        val updated = localDocuments.toMutableList()
                                                                        val targetIdx = currentIdx - 1
                                                                        val item = updated.removeAt(currentIdx)
                                                                        updated.add(targetIdx, item)
                                                                        localDocuments = updated
                                                                        dragAccumulatedOffset = Offset(dragAccumulatedOffset.x + gridColThresholdPx, dragAccumulatedOffset.y)
                                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                    } else if (dragAccumulatedOffset.y > gridRowThresholdPx && currentIdx + columnCount < localDocuments.size) {
                                                                        val updated = localDocuments.toMutableList()
                                                                        val targetIdx = currentIdx + columnCount
                                                                        val item = updated.removeAt(currentIdx)
                                                                        updated.add(targetIdx, item)
                                                                        localDocuments = updated
                                                                        dragAccumulatedOffset = Offset(dragAccumulatedOffset.x, dragAccumulatedOffset.y - gridRowThresholdPx)
                                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                    } else if (dragAccumulatedOffset.y < -gridRowThresholdPx && currentIdx - columnCount >= 0) {
                                                                        val updated = localDocuments.toMutableList()
                                                                        val targetIdx = currentIdx - columnCount
                                                                        val item = updated.removeAt(currentIdx)
                                                                        updated.add(targetIdx, item)
                                                                        localDocuments = updated
                                                                        dragAccumulatedOffset = Offset(dragAccumulatedOffset.x, dragAccumulatedOffset.y + gridRowThresholdPx)
                                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    .then(
                                        if (doc == localDocuments.firstOrNull()) {
                                            Modifier.onGloballyPositioned { OnboardingController.updateBounds("document_card_0", it) }
                                        } else {
                                            Modifier
                                        }
                                    ),
                                onManageLists = { onManageLists(doc) },
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                        }
                        val remainder = columnCount - rowDocs.size
                        repeat(remainder) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                itemsIndexed(localDocuments, key = { _, doc -> doc.id }) { _, doc ->
                    val isDragging = doc.id == draggingDocId
                    val dragScale by animateFloatAsState(
                        targetValue = if (isDragging) 1.04f else 1f,
                        animationSpec = com.veritas.reader.ui.VeritasMotion.spatialFast(),
                        label = "listDragScale"
                    )
                    val listThresholdPx = with(density) { 90.dp.toPx() }

                    DocumentCard(
                        document = doc,
                        isQueued = isQueued(doc),
                        viewMode = libraryViewMode,
                        selectionMode = selectionMode,
                        selected = doc.id in selectedDocumentIds,
                        onOpen = { onOpenDocument(doc) },
                        onLongPress = { onSelectedDocumentIdsChange(selectedDocumentIds + doc.id) },
                        onToggleSelected = {
                            onSelectedDocumentIdsChange(if (doc.id in selectedDocumentIds) selectedDocumentIds - doc.id else selectedDocumentIds + doc.id)
                        },
                        onDelete = { onDeleteDocument(doc) },
                        onToggleQueue = { onToggleQueue(doc) },
                        onMoveQueueUp = { onMoveQueueUp(doc) },
                        onMoveQueueDown = { onMoveQueueDown(doc) },
                        onToggleFavorite = { onToggleFavorite(doc) },
                        onRename = { onRenameDocument(doc) },
                        onSetCollection = { onSetCollection(doc) },
                        onShowDetails = { onShowDetails(doc) },
                        onManageLists = { onManageLists(doc) },
                        modifier = Modifier
                            .animateItem()
                            .zIndex(if (isDragging) 100f else 1f)
                            .graphicsLayer {
                                scaleX = dragScale
                                scaleY = dragScale
                                if (isDragging) {
                                    translationY = dragAccumulatedOffset.y
                                    shadowElevation = 32f
                                }
                            }
                            .pointerInput(doc.id, selectionMode) {
                                if (!selectionMode) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        var dragStarted = false
                                        var totalPan = Offset.Zero
                                        val longPress = awaitLongPressOrCancellation(down.id)
                                        if (longPress != null) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            var released = false
                                            while (!released) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull { it.id == down.id }
                                                if (change == null || !change.pressed) {
                                                    released = true
                                                    if (!dragStarted) {
                                                        onSelectedDocumentIdsChange(selectedDocumentIds + doc.id)
                                                    } else {
                                                        if (draggingDocId != null) {
                                                            onReorderDocuments(localDocuments)
                                                            draggingDocId = null
                                                            dragAccumulatedOffset = Offset.Zero
                                                        }
                                                    }
                                                } else {
                                                    val dragAmount = change.positionChange()
                                                    totalPan += dragAmount
                                                    if (!dragStarted && totalPan.getDistance() > viewConfiguration.touchSlop) {
                                                        dragStarted = true
                                                        draggingDocId = doc.id
                                                        dragAccumulatedOffset = Offset.Zero
                                                    }
                                                    if (dragStarted) {
                                                        change.consume()
                                                        dragAccumulatedOffset += dragAmount
                                                        val currentIdx = localDocuments.indexOfFirst { it.id == draggingDocId }
                                                        if (currentIdx != -1) {
                                                            if (dragAccumulatedOffset.y > listThresholdPx && currentIdx < localDocuments.lastIndex) {
                                                                val updated = localDocuments.toMutableList()
                                                                val targetIdx = currentIdx + 1
                                                                val item = updated.removeAt(currentIdx)
                                                                updated.add(targetIdx, item)
                                                                localDocuments = updated
                                                                dragAccumulatedOffset = Offset(dragAccumulatedOffset.x, dragAccumulatedOffset.y - listThresholdPx)
                                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            } else if (dragAccumulatedOffset.y < -listThresholdPx && currentIdx > 0) {
                                                                val updated = localDocuments.toMutableList()
                                                                val targetIdx = currentIdx - 1
                                                                val item = updated.removeAt(currentIdx)
                                                                updated.add(targetIdx, item)
                                                                localDocuments = updated
                                                                dragAccumulatedOffset = Offset(dragAccumulatedOffset.x, dragAccumulatedOffset.y + listThresholdPx)
                                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            .then(
                                if (doc == localDocuments.firstOrNull()) {
                                    Modifier.onGloballyPositioned { OnboardingController.updateBounds("document_card_0", it) }
                                } else {
                                    Modifier
                                }
                            ),
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(22.dp)) }
                                    }
                                }

}

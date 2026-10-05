package com.veritas.reader.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.reader.BrandMark
import com.veritas.reader.CoverExtractor
import com.veritas.reader.LibraryViewMode
import com.veritas.reader.SavedDocument
import com.veritas.reader.SoftChip
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.formatEstimatedReadTime
import com.veritas.reader.formatUpdated
import com.veritas.reader.progressFraction
import com.veritas.reader.progressPercent
import com.veritas.reader.ui.pressScale


@Composable
internal fun rememberDocumentCover(documentId: String, title: String, originalFileName: String, catalogId: String = ""): android.graphics.Bitmap? {
    val context = LocalContext.current
    val cover by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, documentId, catalogId) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.veritas.reader.BookCoverLoader.document(context, documentId, catalogId)
        }
    }
    return cover
}
@Composable
internal fun RecentImportItem(
    document: SavedDocument,
    isQueued: Boolean,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleQueue: () -> Unit,
    onMoveQueueUp: () -> Unit = {},
    onMoveQueueDown: () -> Unit = {},
    onSetCollection: () -> Unit,
    onManageLists: () -> Unit,
    onRename: () -> Unit,
    onShowDetails: () -> Unit,
    onDelete: () -> Unit
) {
    var showActions by remember { mutableStateOf(false) }
    val coverBitmap = rememberDocumentCover(document.id, document.title, document.originalFileName, document.catalogId)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (coverBitmap != null) {
                Image(
                    bitmap = coverBitmap.asImageBitmap(),
                    contentDescription = "Cover",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                VeritasCoverPlaceholder(
                    documentId = document.id,
                    title = document.title,
                    sourceLabel = document.sourceLabel,
                    modifier = Modifier.fillMaxSize(),
                    compact = true
                )
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = document.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitleText = buildString {
                append(document.sourceLabel)
                append(" • ")
                append(formatEstimatedReadTime(document))
                val progress = progressPercent(document)
                if (progress > 0) {
                    append(" • $progress% read")
                }
            }
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), CircleShape)
                        .clickable { showActions = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "More options",
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = showActions,
                    onDismissRequest = { showActions = false },
                    modifier = Modifier.width(240.dp)
                ) {
                    DropdownMenuItem(text = { Text("Open reading") }, onClick = { showActions = false; onOpen() })
                    DropdownMenuItem(text = { Text(if (document.favorite) "Remove from favorites" else "Add to favorites") }, onClick = { showActions = false; onToggleFavorite() })
                    DropdownMenuItem(text = { Text(if (isQueued) "Remove from Queue" else "Add to Queue") }, onClick = { showActions = false; onToggleQueue() })
                    if (isQueued) {
                        // Reordering by menu rather than by drag: the queue lives in a
                        // scrolling grid, where a small drag handle is easy to miss.
                        DropdownMenuItem(
                            text = { Text("Move up in queue") },
                            onClick = { showActions = false; onMoveQueueUp() }
                        )
                        DropdownMenuItem(
                            text = { Text("Move down in queue") },
                            onClick = { showActions = false; onMoveQueueDown() }
                        )
                    }
                    DropdownMenuItem(text = { Text("Move to collection") }, onClick = { showActions = false; onSetCollection() })
                    DropdownMenuItem(text = { Text("Save to lists") }, onClick = { showActions = false; onManageLists() })
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { showActions = false; onRename() })
                    DropdownMenuItem(text = { Text("Details") }, onClick = { showActions = false; onShowDetails() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { showActions = false; onDelete() })
                }
            }
        }
    }
}

@Composable
internal fun HomeRecentBookGridItem(
    document: SavedDocument,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coverBitmap = rememberDocumentCover(document.id, document.title, document.originalFileName, document.catalogId)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        shape = VeritasPackStyle.cardShape(),
        colors = CardDefaults.cardColors(
            containerColor = com.veritas.reader.VeritasPackStyle.panelColor(MaterialTheme.colorScheme)
        ),
        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(166.dp)
                    .clip(VeritasPackStyle.coverStageShape(inset = 10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                if (coverBitmap != null) {
                    Image(
                        bitmap = coverBitmap.asImageBitmap(),
                        contentDescription = "Cover",
                        modifier = Modifier.width(96.dp).height(144.dp).clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    VeritasCoverPlaceholder(
                        documentId = document.id,
                        title = document.title,
                        sourceLabel = document.sourceLabel,
                        modifier = Modifier.width(96.dp).height(144.dp).clip(RoundedCornerShape(6.dp)),
                        compact = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = document.title,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            val subtitleText = buildString {
                append(document.sourceLabel.uppercase())
                append(" • ")
                append(formatEstimatedReadTime(document))
                val progress = progressPercent(document)
                if (progress > 0) {
                    append(" • $progress% read")
                }
            }
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}



@Composable
fun EmptyLibraryCard(onImportFile: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = VeritasPackStyle.surfaceAlpha())),
        shape = VeritasPackStyle.cardShape(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
    ) {
        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BrandMark(compact = true)
            Text("No saved readings yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(
                "Import a file or paste text to create your first local reading item. Progress, annotations, and queue state will be saved automatically.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onImportFile) { Text("Import first file") }
        }
    }
}

@Composable
fun DocumentCard(
    document: SavedDocument,
    isQueued: Boolean,
    viewMode: LibraryViewMode,
    selectionMode: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onToggleSelected: () -> Unit,
    onDelete: () -> Unit,
    onToggleQueue: () -> Unit,
    onMoveQueueUp: () -> Unit = {},
    onMoveQueueDown: () -> Unit = {},
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onSetCollection: () -> Unit,
    onShowDetails: () -> Unit,
    onManageLists: () -> Unit = {},
    modifier: Modifier = Modifier,
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope? = null,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope? = null
) {
    val progress = progressFraction(document)
    var showActions by remember { mutableStateOf(false) }
    val selectionScale by animateFloatAsState(
        targetValue = if (selected) 0.965f else 1f,
        animationSpec = com.veritas.reader.ui.VeritasMotion.spatialFast(),
        label = "documentCardSelectionScale"
    )
    val coverSize = when (viewMode) {
        LibraryViewMode.SMALL, LibraryViewMode.LIST -> 48.dp
        LibraryViewMode.DETAILS -> 72.dp
        else -> 64.dp
    }
    val showChips = viewMode == LibraryViewMode.MEDIUM || viewMode == LibraryViewMode.DETAILS
    val showPreview = viewMode == LibraryViewMode.DETAILS

    val coverBitmap = rememberDocumentCover(document.id, document.title, document.originalFileName, document.catalogId)

    // Dips the card while held. DocumentCard drives taps through
    // detectTapGestures, so the press flag is tracked here rather than
    // observed from an InteractionSource.
    var pressed by remember { mutableStateOf(false) }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = selectionScale, scaleY = selectionScale)
            .pressScale(pressed)
            .pointerInput(selectionMode, selected, document.id) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onLongPress = { onLongPress() },
                    onTap = {
                        if (selectionMode) onToggleSelected() else onOpen()
                    }
                )
            },
        shape = VeritasPackStyle.compactShape(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = VeritasPackStyle.surfaceAlpha())
            } else {
                com.veritas.reader.VeritasPackStyle.panelColor(MaterialTheme.colorScheme)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(if (viewMode == LibraryViewMode.LIST) 10.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(coverSize)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                when {
                                    selected -> MaterialTheme.colorScheme.primary
                                    document.favorite -> MaterialTheme.colorScheme.tertiaryContainer
                                    else -> MaterialTheme.colorScheme.primaryContainer
                                },
                                if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    )
                    .then(
                        if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                            with(sharedTransitionScope) {
                                Modifier.sharedElement(
                                    sharedContentState = rememberSharedContentState(key = "cover_${document.id}"),
                                    animatedVisibilityScope = animatedVisibilityScope
                                )
                            }
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (coverBitmap != null && !selected) {
                    Image(
                        bitmap = coverBitmap.asImageBitmap(),
                        contentDescription = "Cover",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (selected || document.favorite) {
                    Text(
                        if (selected) "✓" else "★",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                    )
                } else {
                    VeritasCoverPlaceholder(
                        documentId = document.id,
                        title = document.title,
                        sourceLabel = document.sourceLabel,
                        modifier = Modifier.fillMaxSize(),
                        compact = true
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    document.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${document.sourceLabel} • ${progressPercent(document)}% • ${formatEstimatedReadTime(document)} • ${document.chunkCount} sent.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (showPreview) {
                    Text(document.preview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (viewMode != LibraryViewMode.LIST) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
                if (showChips) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (document.collection.isNotBlank()) SoftChip(document.collection, emphasis = true)
                        if (isQueued) SoftChip("Queued")
                        if (document.favorite) SoftChip("Favorite")
                        SoftChip(formatUpdated(document.updatedAt))
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (selectionMode) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                                CircleShape
                            )
                            .clickable { onToggleSelected() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (selected) "✓" else "+", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f))
                            .clickable { onShowDetails() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Book Summary & Details",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Box {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), CircleShape)
                                .clickable { showActions = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "Document actions",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showActions,
                            onDismissRequest = { showActions = false },
                            modifier = Modifier.width(260.dp)
                        ) {
                            DropdownMenuItem(text = { Text("Open reading") }, onClick = { showActions = false; onOpen() })
                            DropdownMenuItem(text = { Text(if (document.favorite) "Remove from favorites" else "Add to favorites") }, onClick = { showActions = false; onToggleFavorite() })
                            DropdownMenuItem(text = { Text(if (isQueued) "Remove from Queue" else "Add to Queue") }, onClick = { showActions = false; onToggleQueue() })
                            DropdownMenuItem(text = { Text("Move to collection") }, onClick = { showActions = false; onSetCollection() })
                            DropdownMenuItem(text = { Text("Save to lists") }, onClick = { showActions = false; onManageLists() })
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { showActions = false; onRename() })
                            DropdownMenuItem(text = { Text("Details") }, onClick = { showActions = false; onShowDetails() })
                            DropdownMenuItem(text = { Text("Delete") }, onClick = { showActions = false; onDelete() })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DocumentTileCard(
    document: SavedDocument,
    isQueued: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onToggleSelected: () -> Unit,
    onDelete: () -> Unit,
    onToggleQueue: () -> Unit,
    onMoveQueueUp: () -> Unit = {},
    onMoveQueueDown: () -> Unit = {},
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onSetCollection: () -> Unit,
    onShowDetails: () -> Unit,
    modifier: Modifier = Modifier,
    onManageLists: () -> Unit = {},
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope? = null,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope? = null
) {
    var showActions by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val selectionScale by animateFloatAsState(
        targetValue = if (selected) 0.965f else 1f,
        animationSpec = com.veritas.reader.ui.VeritasMotion.spatialFast(),
        label = "documentTileSelectionScale"
    )
    val coverBitmap = rememberDocumentCover(document.id, document.title, document.originalFileName, document.catalogId)
    val isUnread = document.currentIndex == 0

    var pressed by remember { mutableStateOf(false) }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = selectionScale, scaleY = selectionScale)
            .pressScale(pressed),
        shape = VeritasPackStyle.cardShape(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = VeritasPackStyle.surfaceAlpha())
            } else {
                com.veritas.reader.VeritasPackStyle.panelColor(MaterialTheme.colorScheme)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (selected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(selectionMode, selected, document.id) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            tryAwaitRelease()
                            pressed = false
                        },
                        onLongPress = { onLongPress() },
                        onTap = { if (selectionMode) onToggleSelected() else onOpen() }
                    )
                }
                .padding(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(166.dp)
                    .clip(VeritasPackStyle.coverStageShape(inset = 10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .then(
                        if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                            with(sharedTransitionScope) {
                                Modifier.sharedElement(
                                    sharedContentState = rememberSharedContentState(key = "cover_${document.id}"),
                                    animatedVisibilityScope = animatedVisibilityScope
                                )
                            }
                        } else Modifier
                    )
            ) {
                if (coverBitmap != null) {
                    Image(
                        bitmap = coverBitmap.asImageBitmap(),
                        contentDescription = "Cover",
                        modifier = Modifier.width(96.dp).height(144.dp).align(Alignment.Center).clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    VeritasCoverPlaceholder(
                        documentId = document.id,
                        title = document.title,
                        sourceLabel = document.sourceLabel,
                        modifier = Modifier.width(96.dp).height(144.dp).align(Alignment.Center).clip(RoundedCornerShape(6.dp)),
                        compact = false
                    )
                }

                if (selectionMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (isUnread) {
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "NEW",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (document.favorite) {
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                    .size(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = "Favorite",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }

                    Box {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
                                .clickable { showActions = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "Document actions",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                            DropdownMenuItem(text = { Text("Open reading") }, onClick = { showActions = false; onOpen() })
                            DropdownMenuItem(text = { Text(if (document.favorite) "Remove favorite" else "Add favorite") }, onClick = { showActions = false; onToggleFavorite() })
                            DropdownMenuItem(text = { Text(if (isQueued) "Remove from Queue" else "Add to Queue") }, onClick = { showActions = false; onToggleQueue() })
                            DropdownMenuItem(text = { Text("Move to collection") }, onClick = { showActions = false; onSetCollection() })
                            DropdownMenuItem(text = { Text("Save to lists") }, onClick = { showActions = false; onManageLists() })
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { showActions = false; onRename() })
                            DropdownMenuItem(text = { Text("Details") }, onClick = { showActions = false; onShowDetails() })
                            DropdownMenuItem(text = { Text("Delete") }, onClick = { showActions = false; onDelete() })
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = document.title,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            val subtitleText = buildString {
                append(document.sourceLabel.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() })
                append(" • ")
                append(formatEstimatedReadTime(document))
                val progress = progressPercent(document)
                if (progress > 0) {
                    append(" • $progress% read")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f))
                        .clickable { onShowDetails() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Book Summary & Details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            if (progressFraction(document) > 0f) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progressFraction(document) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun LibraryDocumentCardsPreview() {
    val sampleDoc = remember {
        SavedDocument(
            id = "doc_preview_1",
            title = "The Art of War",
            fileName = "art_of_war.txt",
            sourceLabel = "TXT",
            createdAt = 1700000000000L,
            updatedAt = 1700000000000L,
            currentIndex = 25,
            chunkCount = 100,
            charCount = 50000,
            preview = "Sun Tzu said: The art of war is of vital importance to the State.",
            favorite = true,
            collection = "Philosophy"
        )
    }
    MaterialTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            DocumentCard(
                document = sampleDoc,
                isQueued = false,
                viewMode = LibraryViewMode.MEDIUM,
                selectionMode = false,
                selected = false,
                onOpen = {},
                onLongPress = {},
                onToggleSelected = {},
                onDelete = {},
                onToggleQueue = {},
                onToggleFavorite = {},
                onRename = {},
                onSetCollection = {},
                onShowDetails = {}
            )
        }
    }
}





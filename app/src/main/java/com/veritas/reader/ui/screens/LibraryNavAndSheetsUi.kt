package com.veritas.reader.ui.screens


import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.veritas.reader.VeritasPackStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.reader.SavedDocument
import com.veritas.reader.SourceBadge
import com.veritas.reader.VeritasThemeState
import com.veritas.reader.WebArticleExtractor
import com.veritas.reader.progressFraction
import com.veritas.reader.progressPercent
import kotlinx.coroutines.delay


@Composable
fun QueueSection(
    queuedDocuments: List<SavedDocument>,
    onPlayQueue: () -> Unit,
    onOpenDocument: (SavedDocument) -> Unit,
    onMoveUp: (SavedDocument) -> Unit,
    onMoveDown: (SavedDocument) -> Unit,
    onRemove: (SavedDocument) -> Unit,
    onClearQueue: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Queue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Text(
                        if (queuedDocuments.isEmpty()) "Build a playlist from your library." else "${queuedDocuments.size} queued item${if (queuedDocuments.size == 1) "" else "s"} ready for continuous playback.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = onClearQueue, enabled = queuedDocuments.isNotEmpty()) { Text("Clear") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onPlayQueue, enabled = queuedDocuments.isNotEmpty()) { Text("Play") }
            }

            if (queuedDocuments.isEmpty()) {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        "Use Queue on any document card to add it here. Queue order controls what plays next in the background service.",
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                queuedDocuments.forEachIndexed { index, document ->
                    QueueItemRow(
                        position = index + 1,
                        document = document,
                        canMoveUp = index > 0,
                        canMoveDown = index < queuedDocuments.lastIndex,
                        onOpen = { onOpenDocument(document) },
                        onMoveUp = { onMoveUp(document) },
                        onMoveDown = { onMoveDown(document) },
                        onRemove = { onRemove(document) }
                    )
                }
            }
        }
    }
}

@Composable
fun QueueItemRow(
    position: Int,
    document: SavedDocument,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    val progress = progressFraction(document)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$position", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f).clickable { onOpen() }) {
                    Text(document.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text("${document.sourceLabel} • ${progressPercent(document)}% • sentence ${document.currentIndex + 1}/${document.chunkCount.coerceAtLeast(1)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SourceBadge(document.sourceLabel)
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onMoveUp, enabled = canMoveUp) { Text("Up") }
                OutlinedButton(onClick = onMoveDown, enabled = canMoveDown) { Text("Down") }
                TextButton(onClick = onRemove) { Text("Remove") }
            }
        }
    }
}



@Composable
internal fun EmbeddedOnboardingBlock(
    onOpenFileBrowser: () -> Unit,
    onPasteText: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center
            ) {
                Text("🎧", fontSize = 40.sp)
            }
            Text(
                "Listen to anything, eyes-free.",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Import PDFs, EPUBs, documents, or paste web articles to get started.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onOpenFileBrowser,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Browse Files")
                }
                FilledTonalButton(
                    onClick = onPasteText,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Paste Text")
                }
            }
        }
    }
}


// One-time staggered entrance for top-level home cards: fade in + slide up ~16dp
// with a per-card delay, on the tab's FIRST show only. `played` is saveable, so
// scrolling away and back (or revisiting the tab) never replays it — the animation
// can therefore never run during scroll frames.
@Composable
internal fun Modifier.staggeredEntrance(position: Int): Modifier {
    if (VeritasThemeState.reduceMotion) return this
    var played by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (played) 1f else 0f) }
    val entranceMotion = com.veritas.reader.ui.VeritasMotion.spatial<Float>()
    if (!played) {
        LaunchedEffect(Unit) {
            delay(position * 40L)
            progress.animateTo(1f, entranceMotion)
            played = true
        }
    }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 16.dp.toPx()
    }
}

@Composable
internal fun RowScope.BottomNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Color, androidx.compose.ui.unit.Dp) -> Unit,
    label: String,
    showLabel: Boolean = true,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.surface.luminance() < 0.5f

    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            scheme.primary
        } else {
            scheme.onSurfaceVariant.copy(alpha = 0.72f)
        },
        animationSpec = com.veritas.reader.ui.VeritasMotion.effectsFast(),
        label = "navColor"
    )
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = com.veritas.reader.ui.VeritasMotion.scheme.reduceMotion
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) 0.97f else 1f,
        animationSpec = com.veritas.reader.ui.VeritasMotion.spatialFast(),
        label = "navPress"
    )
    // The selected state is shared across tabs; the pack only adapts its shape/material.
    val pillProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = if (reduceMotion) androidx.compose.animation.core.snap()
            else androidx.compose.animation.core.spring(dampingRatio = .72f, stiffness = 500f),
        label = "navPill"
    )
    val activePillColor = if (isDark) {
        scheme.primary.copy(alpha = 0.28f)
    } else {
        scheme.primary.copy(alpha = 0.18f)
    }
    val activePillBorder = if (isDark) {
        scheme.primary.copy(alpha = 0.50f)
    } else {
        scheme.primary.copy(alpha = 0.35f)
    }

    val selectionSize = pillProgress.coerceIn(0f, 1.12f)
    val pillWidth = if (showLabel) (60f + 4f * selectionSize).dp else (50f + 4f * selectionSize).dp
    val pillHeight = if (showLabel) (46f + 2f * selectionSize).dp else (40f + 2f * selectionSize).dp
    val pillShape = VeritasPackStyle.chipShape()

    Box(
        modifier = modifier
            .weight(1f)
            .fillMaxHeight()
            .semantics(mergeDescendants = true) {
                this.selected = selected
                this.role = Role.Tab
                this.contentDescription = label
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            },
        contentAlignment = Alignment.Center
    ) {
        if (pillProgress > 0.01f) {
            Box(
                modifier = Modifier
                    .width(pillWidth)
                    .height(pillHeight)
                    .graphicsLayer {
                        scaleX = 0.65f + 0.35f * pillProgress
                        scaleY = 0.65f + 0.35f * pillProgress
                        alpha = pillProgress.coerceIn(0f, 1f)
                    }
                    .background(
                        color = activePillColor,
                        shape = pillShape
                    )
                    .border(
                        width = 1.dp,
                        color = activePillBorder,
                        shape = pillShape
                    )
            )
        }

        if (showLabel) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                icon(contentColor, (21f + 3.5f * selectionSize).dp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = (10.5f + .5f * selectionSize).sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = contentColor,
                    maxLines = 1
                )
            }
        } else {
            Box(
                contentAlignment = Alignment.Center
            ) {
                icon(contentColor, (23f + 3.5f * selectionSize).dp)
            }
        }
    }
}

@Composable
internal fun ImportSheetMenu(
    onSelectOption: (ImportOption) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Add something", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            IconButton(onClick = onDismiss) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = "Close")
            }
        }
        
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ImportSheetOptionCard(
                icon = Icons.AutoMirrored.Filled.LibraryBooks,
                title = "Classic Books Catalog",
                subtitle = "Browse free public domain masterpieces (Meditations, Art of War…)",
                onClick = { onSelectOption(ImportOption.CLASSICS) }
            )
            ImportSheetOptionCard(
                icon = Icons.Filled.FolderOpen,
                title = "File browser",
                subtitle = "Browse and batch import local files",
                onClick = { onSelectOption(ImportOption.BROWSE) }
            )
            ImportSheetOptionCard(
                icon = Icons.Filled.ContentPaste,
                title = "Paste text",
                subtitle = "Copy and paste any content",
                onClick = { onSelectOption(ImportOption.PASTE) }
            )
            ImportSheetOptionCard(
                icon = Icons.Filled.Language,
                title = "From web",
                subtitle = "Paste a link to an article",
                onClick = { onSelectOption(ImportOption.WEB) }
            )
            ImportSheetOptionCard(
                icon = Icons.Filled.PhotoCamera,
                title = "Scan / OCR",
                subtitle = "Take a photo of printed text",
                onClick = { onSelectOption(ImportOption.SCAN) }
            )
            ImportSheetOptionCard(
                icon = Icons.Filled.Description,
                title = "Browse phone folders",
                subtitle = "Open system file chooser",
                onClick = { onSelectOption(ImportOption.FILE) }
            )
            ImportSheetOptionCard(
                icon = Icons.Filled.EditNote,
                title = "Write note",
                subtitle = "Create a freeform reading note",
                onClick = { onSelectOption(ImportOption.WRITE_NOTE) }
            )
        }
    }
}

@Composable
internal fun ImportSheetOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressedColor by animateColorAsState(
        if (pressed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        label = "addActionPress"
    )
    Card(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = if (pressed) 0.98f else 1f; scaleY = scaleX },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = pressedColor)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
            Column {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun ImportSheetWeb(
    urlText: String,
    onUrlChange: (String) -> Unit,
    onImport: (String) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack) {
                Text("←", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Text("Import link", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        
        Text("Paste a link to any web article, report, or blog post below:", color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        OutlinedTextField(
            value = urlText,
            onValueChange = onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Article Link") },
            placeholder = { Text("https://example.com/article...") },
            singleLine = true
        )
        
        Button(
            onClick = { onImport(urlText) },
            modifier = Modifier.fillMaxWidth(),
            enabled = urlText.isNotBlank() && WebArticleExtractor.looksLikeUrl(urlText)
        ) {
            Text("Import web article")
        }
    }
}

@Composable
internal fun ImportSheetPaste(
    pastedText: String,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack) {
                Text("←", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Text("Paste text", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        
        Text("Paste loose text, a document snippet, or email contents below:", color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        OutlinedTextField(
            value = pastedText,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth().height(180.dp),
            label = { Text("Content") },
            placeholder = { Text("Paste text here...") }
        )
        
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = pastedText.isNotBlank()
        ) {
            Text("Save reading")
        }
    }
}




package com.veritas.reader.ui.screens


import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veritas.reader.ReaderAnnotation
import com.veritas.reader.SavedDocument
import com.veritas.reader.StudyGuidePdfExporter
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.copyTextToClipboard
import com.veritas.reader.getBookAndAuthor
import com.veritas.reader.shareBookmarkAsImage
import com.veritas.reader.shareBookmarkAsWords
import java.util.UUID


data class BookmarkGroup(
    val id: String,
    val document: SavedDocument,
    val annotations: List<ReaderAnnotation>,
    val highlightColor: String?,
    val startSentence: Int,
    val endSentence: Int
)

fun groupBookmarks(
    document: SavedDocument,
    annotations: List<ReaderAnnotation>
): List<BookmarkGroup> {
    val sorted = annotations.sortedBy { it.chunkIndex }
    val groups = mutableListOf<BookmarkGroup>()
    
    val withGroup = sorted.filter { !it.selectionGroupId.isNullOrBlank() }
    val withoutGroup = sorted.filter { it.selectionGroupId.isNullOrBlank() }
    
    val groupedById = withGroup.groupBy { it.selectionGroupId }
    groupedById.forEach { (groupId, groupAnnots) ->
        val sortedAnnots = groupAnnots.sortedBy { it.chunkIndex }
        val start = sortedAnnots.first().chunkIndex
        val end = sortedAnnots.last().chunkIndex
        val color = sortedAnnots.first().highlightColor ?: "#FFE082"
        groups.add(
            BookmarkGroup(
                id = groupId ?: UUID.randomUUID().toString(),
                document = document,
                annotations = sortedAnnots,
                highlightColor = color,
                startSentence = start,
                endSentence = end
            )
        )
    }
    
    if (withoutGroup.isNotEmpty()) {
        var currentRun = mutableListOf<ReaderAnnotation>()
        for (ann in withoutGroup) {
            if (currentRun.isEmpty()) {
                currentRun.add(ann)
            } else {
                val lastAnn = currentRun.last()
                if (ann.chunkIndex == lastAnn.chunkIndex + 1 && ann.highlightColor == lastAnn.highlightColor) {
                    currentRun.add(ann)
                } else {
                    val start = currentRun.first().chunkIndex
                    val end = currentRun.last().chunkIndex
                    val color = currentRun.first().highlightColor ?: "#FFE082"
                    groups.add(
                        BookmarkGroup(
                            id = "legacy-${document.id}-$start-$end",
                            document = document,
                            annotations = currentRun,
                            highlightColor = color,
                            startSentence = start,
                            endSentence = end
                        )
                    )
                    currentRun = mutableListOf(ann)
                }
            }
        }
        if (currentRun.isNotEmpty()) {
            val start = currentRun.first().chunkIndex
            val end = currentRun.last().chunkIndex
            val color = currentRun.first().highlightColor ?: "#FFE082"
            groups.add(
                BookmarkGroup(
                    id = "legacy-${document.id}-$start-$end",
                    document = document,
                    annotations = currentRun,
                    highlightColor = color,
                    startSentence = start,
                    endSentence = end
                )
            )
        }
    }
    
    return groups.sortedBy { it.startSentence }
}



@Composable
internal fun BookmarkDocumentCard(
    document: SavedDocument,
    groups: List<BookmarkGroup>,
    sentenceTextLookup: (Int) -> String?,
    onOpenAt: (Int) -> Unit,
    onDeleteAnnotations: (Set<String>) -> Unit
) {
    var expanded by rememberSaveable(document.id) { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete all bookmarks?") },
            text = { Text("This will permanently remove all ${groups.size} bookmarks for \"${document.title}\".") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        onDeleteAnnotations(groups.flatMap { it.annotations.map { a -> a.stableKey } }.toSet())
                        showDeleteConfirmDialog = false
                    }
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = VeritasPackStyle.cardShape(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = VeritasPackStyle.surfaceAlpha())),
        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Bookmark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = document.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${groups.size} bookmark${if (groups.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                var showBatchMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showBatchMenu = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Batch Actions",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showBatchMenu,
                        onDismissRequest = { showBatchMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Share as PDF") },
                            onClick = {
                                showBatchMenu = false
                                val allBookmarks = groups.flatMap { it.annotations }
                                val pdf = StudyGuidePdfExporter.generateBookmarksBatchPdf(
                                    context = context,
                                    documentTitle = document.title,
                                    bookmarks = allBookmarks,
                                    sentenceTextLookup = sentenceTextLookup
                                )
                                if (pdf != null) {
                                    StudyGuidePdfExporter.sharePdfFile(context, pdf, "Share Bookmarks PDF")
                                }
                            },
                            leadingIcon = { Icon(Icons.Outlined.Bookmark, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy all") },
                            onClick = {
                                showBatchMenu = false
                                val allText = groups.joinToString("\n\n") { g ->
                                    val text = (g.startSentence..g.endSentence).mapNotNull { sentenceTextLookup(it) }.joinToString(" ")
                                    "• $text [Sentence ${g.startSentence + 1}]"
                                }
                                copyTextToClipboard(context, "Bookmarks Batch", allText)
                            },
                            leadingIcon = { Icon(Icons.Filled.ContentPaste, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete all") },
                            onClick = {
                                showBatchMenu = false
                                showDeleteConfirmDialog = true
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        )
                    }
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 14.dp))
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    groups.forEach { group ->
                        BookmarkGroupCard(
                            group = group,
                            sentenceTextLookup = sentenceTextLookup,
                            onOpenAt = onOpenAt,
                            onDeleteGroup = {
                                onDeleteAnnotations(group.annotations.map { it.stableKey }.toSet())
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun BookmarkGroupCard(
    group: BookmarkGroup,
    sentenceTextLookup: (Int) -> String?,
    onOpenAt: (Int) -> Unit,
    onDeleteGroup: () -> Unit
) {
    val context = LocalContext.current
    var expanded by rememberSaveable(group.id) { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    
    val bookTitle = group.document.title
    val (cleanTitle, authorName) = remember(bookTitle) { getBookAndAuthor(bookTitle) }
    
    val collapsedText = remember(group, sentenceTextLookup) {
        val firstAnn = group.annotations.firstOrNull()
        val firstText = firstAnn?.let { sentenceTextLookup(it.chunkIndex) }
        if (firstText.isNullOrBlank()) {
            if (group.startSentence == group.endSentence) "Sentence ${group.startSentence + 1}"
            else "Sentences ${group.startSentence + 1}–${group.endSentence + 1}"
        } else {
            if (firstText.length > 60) firstText.take(57) + "..." else firstText
        }
    }
    
    val highlightColor = remember(group.highlightColor) {
        runCatching { Color(android.graphics.Color.parseColor(group.highlightColor)) }
            .getOrDefault(Color(0xFFFFE082))
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(color = highlightColor, shape = CircleShape)
                )
                
                Spacer(modifier = Modifier.width(10.dp))
                
                Text(
                    text = collapsedText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            if (expanded) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(10.dp))
                
                val sentencesText = remember(group.annotations, sentenceTextLookup) {
                    group.annotations.map { ann ->
                        sentenceTextLookup(ann.chunkIndex) ?: ""
                    }
                }
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    group.annotations.forEachIndexed { idx, ann ->
                        val text = sentencesText.getOrNull(idx)?.ifBlank { null } ?: "Loading sentence text..."
                        
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(36.dp)
                                    .background(color = highlightColor, shape = RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .clickable { onOpenAt(group.startSentence) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Open in document ↗",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "Actions",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Share as text") },
                                    onClick = {
                                        showMenu = false
                                        val fullText = sentencesText.joinToString(" ")
                                        shareBookmarkAsWords(
                                            context = context,
                                            bookTitle = cleanTitle,
                                            text = fullText
                                        )
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Share as image") },
                                    onClick = {
                                        showMenu = false
                                        val fullText = sentencesText.joinToString(" ")
                                        shareBookmarkAsImage(
                                            context = context,
                                            bookTitle = cleanTitle,
                                            authorName = authorName,
                                            highlightedText = fullText,
                                            highlightColorHex = group.highlightColor ?: "#FFE082"
                                        )
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy") },
                                    onClick = {
                                        showMenu = false
                                        val fullText = sentencesText.joinToString(" ")
                                        copyTextToClipboard(context, "Bookmark Text", fullText)
                                    },
                                    leadingIcon = { Icon(Icons.Filled.ContentPaste, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        showMenu = false
                                        onDeleteGroup()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}




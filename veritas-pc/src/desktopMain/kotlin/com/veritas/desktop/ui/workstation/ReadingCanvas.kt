package com.veritas.desktop.ui.workstation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.desktop.models.Bookmark
import com.veritas.desktop.models.DesktopDocument
import com.veritas.desktop.models.ReaderSettings
import com.veritas.desktop.models.TextAnnotation
import com.veritas.desktop.ui.theme.getComposeFontFamily

@Composable
fun ReadingCanvas(
    document: DesktopDocument?,
    currentIndex: Int,
    isPlaying: Boolean,
    settings: ReaderSettings,
    searchQuery: String,
    bookmarks: List<Bookmark>,
    annotations: List<TextAnnotation>,
    onSentenceClick: (Int) -> Unit,
    onSentenceDoubleTap: (Int) -> Unit,
    onToggleBookmark: (Int, String) -> Unit,
    onAddAnnotation: (Int, String) -> Unit,
    onLaunchRsvp: (Int) -> Unit,
    onBackToLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (document == null || document.chunks.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Document Loaded",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Select a book from the library or drop a file to start reading.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onBackToLibrary) {
                    Text("Return to Library")
                }
            }
        }
        return
    }

    val listState = rememberLazyListState()

    // Auto-scroll to active sentence when reading
    LaunchedEffect(currentIndex) {
        if (settings.autoScrollToSentence && currentIndex in document.chunks.indices) {
            listState.animateScrollToItem((currentIndex - 1).coerceAtLeast(0))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = if (settings.isTwoColumnSpread) 1150.dp else settings.maxReadingWidthDp.dp)
                .fillMaxHeight()
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = 28.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Header item: Title + Source + RSVP quick launch
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = document.title,
                                    fontFamily = getComposeFontFamily(settings.fontFamily),
                                    fontSize = (settings.fontSize * 1.55f).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${document.sourceLabel} • ${document.chunks.size} sentences • Progress: ${document.progressPercent}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            // RSVP Speed Reader Button
                            FilledTonalButton(
                                onClick = { onLaunchRsvp(currentIndex) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Bolt, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("RSVP Speed Reader", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // Smart Catch-Up banner if opened in middle of document
                        if (currentIndex > 5) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Smart Resume: You previously left off at sentence ${currentIndex + 1}. Press Spacebar to resume listening.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    }
                }

                // Sentence Rows
                itemsIndexed(document.chunks, key = { idx, _ -> idx }) { idx, chunk ->
                    val isActive = idx == currentIndex
                    val isBookmarked = bookmarks.any { it.chunkIndex == idx }
                    val noteForChunk = annotations.firstOrNull { it.chunkIndex == idx }

                    SentenceRow(
                        index = idx,
                        text = chunk,
                        isActive = isActive,
                        isBookmarked = isBookmarked,
                        annotation = noteForChunk,
                        isPlaying = isPlaying && isActive,
                        settings = settings,
                        searchQuery = searchQuery,
                        onClick = { onSentenceClick(idx) },
                        onPlayHere = { onSentenceDoubleTap(idx) },
                        onToggleBookmark = { onToggleBookmark(idx, chunk) },
                        onAddAnnotation = { onAddAnnotation(idx, chunk) },
                        onLaunchRsvp = { onLaunchRsvp(idx) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SentenceRow(
    index: Int,
    text: String,
    isActive: Boolean,
    isBookmarked: Boolean,
    annotation: TextAnnotation?,
    isPlaying: Boolean,
    settings: ReaderSettings,
    searchQuery: String,
    onClick: () -> Unit,
    onPlayHere: () -> Unit,
    onToggleBookmark: () -> Unit,
    onAddAnnotation: () -> Unit,
    onLaunchRsvp: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = when {
            isActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            annotation != null -> Color(0xFFF59E0B).copy(alpha = 0.12f)
            else -> Color.Transparent
        }
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        color = bgColor,
        border = if (isActive) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Sentence number
                if (settings.showSentenceIndices) {
                    Text(
                        text = "${index + 1}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.width(36.dp).padding(top = 2.dp)
                    )
                }

                // Sentence text with search highlights
                val annotatedText = buildAnnotatedString {
                    if (searchQuery.isNotBlank() && text.contains(searchQuery, ignoreCase = true)) {
                        var startIdx = 0
                        val lowerText = text.lowercase()
                        val lowerQuery = searchQuery.lowercase()

                        while (startIdx < text.length) {
                            val matchIdx = lowerText.indexOf(lowerQuery, startIdx)
                            if (matchIdx == -1) {
                                append(text.substring(startIdx))
                                break
                            }
                            append(text.substring(startIdx, matchIdx))
                            withStyle(
                                SpanStyle(
                                    background = Color(0xFFF59E0B),
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append(text.substring(matchIdx, matchIdx + searchQuery.length))
                            }
                            startIdx = matchIdx + searchQuery.length
                        }
                    } else {
                        append(text)
                    }
                }

                Text(
                    text = annotatedText,
                    fontFamily = getComposeFontFamily(settings.fontFamily),
                    fontSize = settings.fontSize.sp,
                    lineHeight = (settings.fontSize * settings.lineHeightMultiplier).sp,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )

                // Inline hover/action buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    IconButton(
                        onClick = onPlayHere,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Read from here",
                            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onToggleBookmark,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = if (isBookmarked) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onAddAnnotation,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = "Add note",
                            tint = if (annotation != null) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Attached Note preview line
            if (annotation != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = if (settings.showSentenceIndices) 36.dp else 0.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Note, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = annotation.noteContent,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

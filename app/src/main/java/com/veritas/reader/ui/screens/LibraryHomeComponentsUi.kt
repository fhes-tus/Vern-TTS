package com.veritas.reader.ui.screens


import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.reader.CoverExtractor
import com.veritas.reader.PlaybackStateStore
import com.veritas.reader.SavedDocument
import com.veritas.reader.StatTile
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.progressFraction
import com.veritas.reader.progressPercent


@Composable
internal fun HomeQuickActions(
    continueDocument: SavedDocument?,
    documentCount: Int,
    longestStreak: Int,
    onOpenContinue: (SavedDocument) -> Unit,
    onPlayPauseContinue: (SavedDocument) -> Unit,
    onClearContinue: (SavedDocument) -> Unit,
    onImportClick: () -> Unit,
    importMenuExpanded: Boolean,
    onDismissImportMenu: () -> Unit,
    onOpenFile: () -> Unit,
    onOpenFileBrowser: () -> Unit,
    onOpenImportSettings: () -> Unit,
    onPasteText: () -> Unit,
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope? = null,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val disabled = continueDocument == null
        val context = LocalContext.current
        val coverFile = remember(continueDocument?.id) { continueDocument?.id?.let { CoverExtractor.coverFile(context, it) } }
        val coverBitmap = remember(coverFile) {
            coverFile?.let { file ->
                runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
            }
        }

        val isFirstDay = longestStreak <= 1
        val headerTitle = if (continueDocument != null && isFirstDay) "First day of reading" else "Continue reading"

        Text(
            text = headerTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                // Always clickable so an empty card still gives feedback instead of feeling
                // broken: when there's nothing to resume, guide the user with a toast.
                .clickable {
                    if (continueDocument != null) {
                        onOpenContinue(continueDocument)
                    } else {
                        val msg = if (documentCount == 0) {
                            "No documents yet — tap Add to import a file or paste text."
                        } else {
                            "No recent reading yet — open a document from your Library to resume here."
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                },
            shape = VeritasPackStyle.cardShape(),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (disabled) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f * VeritasPackStyle.surfaceAlpha())
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = VeritasPackStyle.surfaceAlpha())
                }
            ),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Larger Premium Cover Thumbnail
                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .height(96.dp)
                        .clip(VeritasPackStyle.compactShape())
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .then(
                            if (sharedTransitionScope != null && animatedVisibilityScope != null && continueDocument?.id != null) {
                                with(sharedTransitionScope) {
                                    Modifier.sharedElement(
                                        sharedContentState = rememberSharedContentState(key = "recent_cover_${continueDocument.id}"),
                                        animatedVisibilityScope = animatedVisibilityScope
                                    )
                                }
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (coverBitmap != null) {
                        Image(
                            bitmap = coverBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text("🎧", fontSize = 28.sp)
                    }
                }
                
                // Detailed Information
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (continueDocument == null) {
                        Text(
                            text = "Resume reading",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (documentCount == 0) "Open a file to start reading" else "Pick a document from your library to begin.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            text = continueDocument.title,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        val progressPercentVal = progressPercent(continueDocument)
                        
                        Text(
                            text = "Sentence ${continueDocument.currentIndex + 1} of ${continueDocument.chunkCount} • $progressPercentVal% read",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        // Inline Progress Bar
                        LinearProgressIndicator(
                            progress = { progressFraction(continueDocument) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        )
                    }
                }
                
                // Audio controls inline
                if (continueDocument != null) {
                    val isActiveAndPlaying = PlaybackStateStore.activeDocumentId == continueDocument.id && PlaybackStateStore.isPlaying
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .clickable { onPlayPauseContinue(continueDocument) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isActiveAndPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isActiveAndPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        IconButton(
                            onClick = { onClearContinue(continueDocument) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = "Add content",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = VeritasPackStyle.cardShape(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = VeritasPackStyle.surfaceAlpha())),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
        ) {
            Box {
                HomeActionRow(
                    icon = Icons.Filled.Add,
                    title = "Import file",
                    body = "Upload PDF, EPUB, DOCX, PPTX, TXT, or HTML",
                    iconBackground = Color(0xFFF0F3FF),
                    iconForeground = Color(0xFF7C6FFF),
                    onClick = onImportClick
                )
                DropdownMenu(expanded = importMenuExpanded, onDismissRequest = onDismissImportMenu) {
                    DropdownMenuItem(text = { Text("Open file") }, onClick = onOpenFile)
                    DropdownMenuItem(text = { Text("File browser") }, onClick = onOpenFileBrowser)
                    DropdownMenuItem(text = { Text("Import settings") }, onClick = onOpenImportSettings)
                    DropdownMenuItem(text = { Text("Paste text or URL") }, onClick = onPasteText)
                }
            }
        }
    }
}

@Composable
internal fun HomeActionRow(
    icon: ImageVector,
    title: String,
    body: String,
    iconBackground: Color,
    iconForeground: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(iconBackground, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconForeground,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}






@Composable
internal fun OverviewPill(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(50),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}



@Composable
fun HomePanel(
    totalCount: Int,
    unreadCount: Int,
    inProgressCount: Int,
    completedCount: Int,
    favoriteCount: Int,
    collectionCount: Int,
    sourceCount: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Reading Overview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatTile(value = "$totalCount", label = "Saved", modifier = Modifier.width(84.dp))
            StatTile(value = "$unreadCount", label = "Unread", modifier = Modifier.width(84.dp))
            StatTile(value = "$inProgressCount", label = "Reading", modifier = Modifier.width(84.dp))
            StatTile(value = "$completedCount", label = "Done", modifier = Modifier.width(72.dp))
            StatTile(value = "$favoriteCount", label = "Starred", modifier = Modifier.width(72.dp))
            StatTile(value = "$collectionCount", label = "Collections", modifier = Modifier.width(80.dp))
            StatTile(value = "$sourceCount", label = "Formats", modifier = Modifier.width(72.dp))
        }
    }
}




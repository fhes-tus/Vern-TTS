package com.veritas.desktop.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.desktop.models.DesktopDocument
import com.veritas.desktop.models.HabitTracker
import com.veritas.desktop.ui.components.DesktopCoverView

@Composable
fun DesktopHomeDashboard(
    continueDocument: DesktopDocument?,
    recentDocuments: List<DesktopDocument>,
    isPlaying: Boolean,
    activeDocumentId: String?,
    currentIndex: Int,
    habitTracker: HabitTracker,
    onOpenDocument: (DesktopDocument) -> Unit,
    onTogglePlay: (DesktopDocument) -> Unit,
    onClearContinue: () -> Unit,
    onImportFile: () -> Unit,
    onPasteText: () -> Unit,
    onOpenBookshelf: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 1. Hero Continue Reading Station
        item {
            DesktopHomeHeroCard(
                continueDocument = continueDocument,
                isPlaying = isPlaying,
                activeDocumentId = activeDocumentId,
                currentIndex = currentIndex,
                habitTracker = habitTracker,
                onOpenDocument = onOpenDocument,
                onTogglePlay = onTogglePlay,
                onClearContinue = onClearContinue,
                onImportNew = onImportFile
            )
        }

        // 2. Quick Import Hub (3-Action Pills)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                QuickActionCard(
                    title = "Import Document",
                    subtitle = "PDF, EPUB, DOCX, TXT",
                    icon = Icons.Default.UploadFile,
                    iconColor = Color(0xFF38BDF8),
                    onClick = onImportFile,
                    modifier = Modifier.weight(1f)
                )

                QuickActionCard(
                    title = "Paste Clipboard Text",
                    subtitle = "Instant text-to-speech",
                    icon = Icons.Default.ContentPaste,
                    iconColor = Color(0xFF10B981),
                    onClick = onPasteText,
                    modifier = Modifier.weight(1f)
                )

                QuickActionCard(
                    title = "Personal Bookshelf",
                    subtitle = "${recentDocuments.size} books in library",
                    icon = Icons.Default.AutoStories,
                    iconColor = Color(0xFFF59E0B),
                    onClick = onOpenBookshelf,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 3. Jump Back In / Recent Books
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Jump Back In",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                TextButton(onClick = onOpenBookshelf) {
                    Text("View all books (${recentDocuments.size})", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(recentDocuments.take(8), key = { it.id }) { doc ->
                    RecentBookCard(
                        document = doc,
                        onClick = { onOpenDocument(doc) }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(84.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(10.dp),
                color = iconColor.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = iconColor, modifier = Modifier.size(22.dp))
                }
            }

            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RecentBookCard(
    document: DesktopDocument,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(135.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .width(135.dp)
                .height(180.dp)
        ) {
            DesktopCoverView(
                documentId = document.id,
                title = document.title,
                sourceLabel = document.sourceLabel,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = document.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = "${document.progressPercent}% read",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

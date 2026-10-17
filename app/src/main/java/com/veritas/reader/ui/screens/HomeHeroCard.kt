package com.veritas.reader.ui.screens

import com.veritas.reader.vernScatteredCard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veritas.reader.PlaybackStateStore
import com.veritas.reader.SavedDocument
import com.veritas.reader.VeritasPackStyle

/** Reading is Home's primary action; audio controls remain in the shared player. */
@Composable
internal fun VeritasHomeHeroCard(
    continueDocument: SavedDocument?,
    weeklyBook: ClassicBookEntry?,
    gradient: Brush,
    onCardColor: Color,
    onOpen: (SavedDocument) -> Unit,
    onClear: (SavedDocument) -> Unit,
    onAddContent: () -> Unit,
    onPreviewClassic: (ClassicBookEntry) -> Unit
) {
    val doc = continueDocument
    val graphicPhase = rememberInsightsPhase("homeHeroGraphic")
    Card(
        modifier = Modifier.fillMaxWidth().vernScatteredCard("home-hero", amount = .5f),
        shape = VeritasPackStyle.cardShape(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
    ) {
        Column(
            Modifier.fillMaxWidth().background(gradient)
                .insightsCardGraphic(onCardColor, MaterialTheme.colorScheme.tertiary, graphicPhase)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (doc == null && weeklyBook != null) {
                WeeklyPickContent(weeklyBook, onCardColor) { onPreviewClassic(weeklyBook) }
            } else if (doc == null) {
                Icon(Icons.Filled.AutoStories, null, tint = onCardColor, modifier = Modifier.size(32.dp))
                Text("Your next read starts here", style = MaterialTheme.typography.headlineSmall, color = onCardColor)
                Text("Add a book, document or article to read and listen at your own pace.",
                    style = MaterialTheme.typography.bodyMedium, color = onCardColor.copy(alpha = 0.85f))
                Button(onClick = onAddContent, shape = VeritasPackStyle.chipShape(),
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.Add, null, Modifier.size(20.dp))
                    Text("Add your first read", Modifier.padding(start = 8.dp))
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Continue reading", style = MaterialTheme.typography.titleSmall, color = onCardColor)
                        Text("Pick up where you left off", style = MaterialTheme.typography.bodySmall,
                            color = onCardColor.copy(alpha = 0.8f))
                    }
                    IconButton(onClick = { onClear(doc) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, "Dismiss continue reading", tint = onCardColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp))
                    }
                }
                val cover = rememberDocumentCover(doc.id, doc.title, doc.originalFileName, doc.catalogId)
                val index = if (PlaybackStateStore.activeDocumentId == doc.id) PlaybackStateStore.currentIndex else doc.currentIndex
                val fraction = com.veritas.reader.progressFraction(doc.copy(currentIndex = index))
                val progress by animateFloatAsState(fraction,
                    animationSpec = com.veritas.reader.ui.VeritasMotion.spatial(), label = "homeReadingProgress")
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val fullWidthAction = maxWidth < 320.dp || LocalDensity.current.fontScale >= 1.3f
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(96.dp).height(144.dp).bookCoverMotion("hero-${doc.id}").clip(RoundedCornerShape(8.dp))
                                .background(onCardColor.copy(alpha = 0.08f)).clickable { onOpen(doc) },
                                contentAlignment = Alignment.Center) {
                                if (cover != null) Image(cover.asImageBitmap(), "Cover of ${doc.title}",
                                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                                else VeritasCoverPlaceholder(doc.id, doc.title, doc.sourceLabel,
                                    modifier = Modifier.fillMaxSize())
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(doc.title, style = MaterialTheme.typography.titleLarge,
                                    color = onCardColor, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                if (doc.sourceLabel.isNotBlank()) Text(doc.sourceLabel,
                                    style = MaterialTheme.typography.bodySmall, color = onCardColor.copy(alpha = 0.8f),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(if (index <= 0) "Ready to begin" else "${(fraction * 100).toInt()}% through",
                                        style = MaterialTheme.typography.labelMedium, color = onCardColor.copy(alpha = 0.9f))
                                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(5.dp),
                                        color = onCardColor, trackColor = onCardColor.copy(alpha = 0.18f))
                                }
                                if (!fullWidthAction) ContinueReadingAction(doc, onOpen)
                            }
                        }
                        if (fullWidthAction) ContinueReadingAction(doc, onOpen)
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueReadingAction(doc: SavedDocument, onOpen: (SavedDocument) -> Unit) {
    Button(onClick = { onOpen(doc) }, shape = VeritasPackStyle.chipShape(),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Icon(Icons.Filled.AutoStories, null, Modifier.size(20.dp))
        Text("Continue", Modifier.padding(start = 8.dp), maxLines = 1)
    }
}

@Composable
private fun WeeklyPickContent(book: ClassicBookEntry, onCardColor: Color, onPreview: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Book of the Week", style = MaterialTheme.typography.titleSmall, color = onCardColor)
        Text("A new classic every Monday", style = MaterialTheme.typography.bodySmall,
            color = onCardColor.copy(alpha = 0.8f))
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        ClassicBookCover(book, modifier = Modifier.clickable(onClick = onPreview), width = 96.dp, height = 144.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(book.title, style = MaterialTheme.typography.titleLarge,
                color = onCardColor, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(book.author, style = MaterialTheme.typography.bodyMedium,
                color = onCardColor.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(book.genre, style = MaterialTheme.typography.labelMedium, color = onCardColor.copy(alpha = 0.8f))
        }
    }
    Text(book.description, style = MaterialTheme.typography.bodyMedium, color = onCardColor.copy(alpha = 0.9f),
        maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    Button(onClick = onPreview, shape = VeritasPackStyle.chipShape(),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Icon(Icons.Filled.AutoStories, null, Modifier.size(20.dp))
        Text("Preview book", Modifier.padding(start = 8.dp))
    }
}

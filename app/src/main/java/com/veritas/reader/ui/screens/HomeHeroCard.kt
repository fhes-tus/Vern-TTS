package com.veritas.reader.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.reader.CoverExtractor
import com.veritas.reader.PlaybackStateStore
import com.veritas.reader.ReaderTrackerSnapshot
import com.veritas.reader.SavedDocument
import com.veritas.reader.VeritasPackStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Home hero card, per the user's sketch:
 *  - full-height cover on the left, shown WHOLE (Fit, never cropped)
 *  - title + live insight line vertically centered beside it, with the clear (x) at
 *    the row's right edge
 *  - progress bar under the title block
 *  - compact stat pills (streak — hidden under 2 days — / this week / done)
 *  - play button anchored bottom-right
 * When TTS is playing the active document the card becomes a live mini-player
 * (pulsing cover, live sentence line). Fixed geometry, cover decoded off-thread.
 */
@Composable
fun VeritasHomeHeroCard(
    tracker: ReaderTrackerSnapshot,
    continueDocument: SavedDocument?,
    gradient: Brush,
    onCardColor: Color,
    weeklyMinutes: Long,
    todayMinutes: Long,
    dailyGoalMinutes: Int = 20,
    onOpen: (SavedDocument) -> Unit,
    onPlayPause: (SavedDocument) -> Unit,
    onClear: (SavedDocument) -> Unit,
    onAddContent: () -> Unit,
    onPreviewClassic: ((ClassicBookEntry) -> Unit)? = null,
    onDownloadAndOpenClassic: ((ClassicBookEntry) -> Unit)? = null,
    onShowDetails: ((SavedDocument) -> Unit)? = null
) {
    val context = LocalContext.current
    val doc = continueDocument

    // ── Async, downsampled cover decode (never on the main thread) ──
    var cover by remember(doc?.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(doc?.id, doc?.title) {
        cover = null
        val id = doc?.id ?: return@LaunchedEffect
        cover = withContext(Dispatchers.IO) {
            com.veritas.reader.BookCoverLoader.document(context, id, doc.catalogId)
        }
    }
    val coverAlpha by animateFloatAsState(
        targetValue = if (cover != null) 1f else 0f,
        animationSpec = com.veritas.reader.ui.VeritasMotion.effects(),
        label = "coverFade"
    )

    // ── Live playback state for THIS document ──
    val isPlayingThis = doc != null &&
        PlaybackStateStore.isPlaying && PlaybackStateStore.activeDocumentId == doc.id
    val liveIndex = if (doc != null && PlaybackStateStore.activeDocumentId == doc.id) {
        PlaybackStateStore.currentIndex
    } else {
        doc?.currentIndex ?: 0
    }
    val chunkCount = (doc?.chunkCount ?: 0).coerceAtLeast(1)
    val progressTarget = ((liveIndex + 1).toFloat() / chunkCount.toFloat()).coerceIn(0f, 1f)
    val progress by animateFloatAsState(
        targetValue = if (doc != null) progressTarget else 0f,
        animationSpec = com.veritas.reader.ui.VeritasMotion.spatial(),
        label = "heroProgress"
    )

    // Subtle breathing pulse on the cover while listening.
    val coverScale = if (isPlayingThis && !com.veritas.reader.ui.VeritasMotion.scheme.reduceMotion) {
        val pulse = rememberInfiniteTransition(label = "coverPulse")
        val value by pulse.animateFloat(1f, 1.02f,
            infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "coverScale")
        value
    } else 1f

    // ── Data-earned insight lines (rotating; live sentence line while listening) ──
    val percent = (progressTarget * 100).toInt()
    val insights = remember(doc?.id, percent, todayMinutes, tracker.currentStreak, tracker.documentsCompletedThisMonth, weeklyMinutes, isPlayingThis) {
        buildList {
            if (doc != null && percent in 5..99) add("You're $percent% through — keep going")
            if (todayMinutes > 0) add("${todayMinutes}m read today")
            if (tracker.currentStreak >= 2) add("${tracker.currentStreak}-day streak — keep it alive")
            if (tracker.documentsCompletedThisMonth >= 1) {
                add("${tracker.documentsCompletedThisMonth} book${if (tracker.documentsCompletedThisMonth == 1) "" else "s"} finished this month")
            }
            if (weeklyMinutes > 0) add("${weeklyMinutes}m this week")
            if (isEmpty()) add(if (doc != null) "Pick up where you left off" else "Import or open a document to begin")
        }
    }
    var insightIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(insights.size, isPlayingThis) {
        insightIndex = 0
        if (insights.size > 1 && !isPlayingThis) {
            while (true) {
                delay(6000)
                insightIndex = (insightIndex + 1) % insights.size
            }
        }
    }
    val insightLine = if (isPlayingThis) {
        "Listening • sentence ${liveIndex + 1} of $chunkCount"
    } else {
        insights[insightIndex % insights.size]
    }

    val bookOfTheDay = remember { getBookOfTheDay() }
    var classicCoverBitmap by remember(bookOfTheDay.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(bookOfTheDay.id) {
        classicCoverBitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("covers/${bookOfTheDay.id}.jpg").use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = VeritasPackStyle.cardShape(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .clickable {
                    if (doc != null) onOpen(doc)
                    else onPreviewClassic?.invoke(bookOfTheDay) ?: onAddContent()
                }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Cover: prominent natural book trim, perfectly top-aligned with right column
                Box(
                    modifier = Modifier
                        .width(112.dp)
                        .height(164.dp)
                        .graphicsLayer {
                            scaleX = coverScale
                            scaleY = coverScale
                        }
                        .clip(RoundedCornerShape(10.dp))
                        .then(
                            if (doc != null) Modifier.background(onCardColor.copy(alpha = 0.08f))
                            else Modifier.background(Brush.linearGradient(bookOfTheDay.coverGradient))
                        )
                        .border(1.dp, onCardColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .clickable {
                            if (doc != null) {
                                if (onShowDetails != null) onShowDetails(doc) else onOpen(doc)
                            } else {
                                onPreviewClassic?.invoke(bookOfTheDay)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (doc != null) {
                        if (cover != null) {
                            Image(
                                bitmap = cover!!.asImageBitmap(),
                                contentDescription = doc.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { alpha = coverAlpha }
                            )
                        } else {
                            VeritasCoverPlaceholder(
                                documentId = doc.id,
                                title = doc.title,
                                sourceLabel = doc.sourceLabel,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else if (classicCoverBitmap != null) {
                        Image(
                            bitmap = classicCoverBitmap!!.asImageBitmap(),
                            contentDescription = bookOfTheDay.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "VERN CLASSIC",
                                color = bookOfTheDay.accentColor.copy(alpha = 0.9f),
                                fontSize = 8.sp,
                                fontWeight = MaterialTheme.typography.titleLarge.fontWeight,
                                letterSpacing = 1.sp
                            )
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = bookOfTheDay.title,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = bookOfTheDay.author,
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 9.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(bookOfTheDay.accentColor.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoStories,
                                    contentDescription = null,
                                    tint = bookOfTheDay.accentColor,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }

                // Right Column: exactly 164dp tall to match the cover, top text in line with cover top
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(164.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (doc != null) "Continue Reading" else "✨ Book of the Day",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (doc != null) {
                                IconButton(onClick = { onClear(doc) }, modifier = Modifier.size(20.dp)) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Clear continue reading",
                                        tint = onCardColor.copy(alpha = 0.6f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = doc?.title ?: bookOfTheDay.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold,
                            color = onCardColor,
                            style = MaterialTheme.typography.titleMedium
                        )

                        // Subtitle line (Live insight or book synopsis/quote)
                        Text(
                            text = if (doc != null) insightLine else if (bookOfTheDay.quote.isNotBlank()) "“${bookOfTheDay.quote}”" else "${bookOfTheDay.author} • ${bookOfTheDay.genre}",
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = onCardColor.copy(alpha = 0.90f)
                        )
                    }

                    // Progress indicator for active doc, or tags for Book of the Day
                    if (doc != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = "$percent%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = onCardColor.copy(alpha = 0.92f)
                                )
                            }
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = onCardColor,
                                trackColor = onCardColor.copy(alpha = 0.22f)
                            )
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HeroPill(text = bookOfTheDay.genre, color = onCardColor)
                            HeroPill(text = "${bookOfTheDay.estimatedMinutes}m read", color = onCardColor)
                        }
                    }

                    // Stat pills on the left, Play button on the right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (tracker.currentStreak >= 2) {
                                HeroPill(text = "🔥 ${heroCountUp(tracker.currentStreak)}", color = onCardColor)
                            }
                            HeroPill(text = "${heroCountUp(weeklyMinutes.toInt())}m wk", color = onCardColor)
                        }

                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(onCardColor.copy(alpha = 0.18f))
                                .border(1.dp, onCardColor.copy(alpha = 0.25f), CircleShape)
                                .clickable {
                                    if (doc != null) onOpen(doc)
                                    else onPreviewClassic?.invoke(bookOfTheDay) ?: onAddContent()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(
                                targetState = "read",
                                transitionSpec = {
                                    (scaleIn(if (com.veritas.reader.VeritasThemeState.reduceMotion) androidx.compose.animation.core.snap() else spring(dampingRatio = 0.9f, stiffness = 1400f)) + fadeIn(tween(150)))
                                        .togetherWith(fadeOut(tween(100)))
                                },
                                label = "playPauseMorph"
                            ) { state ->
                                Icon(
                                    imageVector = when (state) {
                                        "read" -> Icons.Filled.AutoStories
                                        "pause" -> Icons.Filled.Pause
                                        else -> Icons.Filled.PlayArrow
                                    },
                                    contentDescription = when (state) {
                                        "read" -> if (doc != null) "Open book" else "Preview classic"
                                        "pause" -> "Pause"
                                        else -> "Play"
                                    },
                                    tint = onCardColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Rolls a stat from 0 (or its previous value) to the target so the pills count up
// like the Insights streak counter instead of popping in fully formed.
@Composable
private fun heroCountUp(value: Int): Int {
    var target by remember { mutableIntStateOf(0) }
    LaunchedEffect(value) { target = value }
    val animated by animateIntAsState(
        targetValue = target,
        animationSpec = com.veritas.reader.ui.VeritasMotion.spatial(),
        label = "heroCountUp"
    )
    return animated
}

@Composable
private fun HeroPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

package com.veritas.desktop.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.desktop.models.DesktopDocument
import com.veritas.desktop.models.HabitTracker
import com.veritas.desktop.ui.components.DesktopCoverView
import com.veritas.desktop.ui.components.getDeterministicBookColor
import kotlinx.coroutines.delay

@Composable
fun DesktopHomeHeroCard(
    continueDocument: DesktopDocument?,
    isPlaying: Boolean,
    activeDocumentId: String?,
    currentIndex: Int,
    habitTracker: HabitTracker,
    onOpenDocument: (DesktopDocument) -> Unit,
    onTogglePlay: (DesktopDocument) -> Unit,
    onClearContinue: () -> Unit,
    onImportNew: () -> Unit,
    modifier: Modifier = Modifier
) {
    val doc = continueDocument
    val isPlayingThis = isPlaying && doc != null && activeDocumentId == doc.id
    val liveIndex = if (isPlayingThis) currentIndex else (doc?.currentIndex ?: 0)
    val totalChunks = (doc?.chunks?.size ?: 1).coerceAtLeast(1)
    val progressFraction = if (doc != null) ((liveIndex + 1).toFloat() / totalChunks).coerceIn(0f, 1f) else 0f
    val percent = (progressFraction * 100).toInt()

    // Base bookcloth color & dynamic gradient
    val baseColor = if (doc != null) getDeterministicBookColor(doc.id) else Color(0xFF1E293B)
    val cardGradient = Brush.horizontalGradient(
        listOf(
            baseColor.copy(alpha = 0.85f),
            baseColor.copy(alpha = 0.95f),
            baseColor
        )
    )

    // Breathing pulse while audio is active
    val pulse = rememberInfiniteTransition(label = "heroCoverPulse")
    val coverScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (isPlayingThis) 1.03f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "coverScale"
    )

    // Rotating Insight lines
    val insights = remember(doc?.id, percent, habitTracker.currentStreak, habitTracker.todayMinutesRead, isPlayingThis) {
        buildList {
            if (doc != null && percent in 1..99) add("You're $percent% through — keep the momentum!")
            if (habitTracker.todayMinutesRead > 0) add("${habitTracker.todayMinutesRead}m read today")
            if (habitTracker.currentStreak >= 2) add("🔥 ${habitTracker.currentStreak}-day streak — keep it going!")
            if (habitTracker.weeklyMinutesRead > 0) add("${habitTracker.weeklyMinutesRead}m read this week")
            if (isEmpty()) add(if (doc != null) "Pick up where you left off" else "Drag & drop a document to begin")
        }
    }

    var insightIndex by remember { mutableStateOf(0) }
    LaunchedEffect(insights.size, isPlayingThis) {
        insightIndex = 0
        if (insights.size > 1 && !isPlayingThis) {
            while (true) {
                delay(5500L)
                insightIndex = (insightIndex + 1) % insights.size
            }
        }
    }

    val activeInsightText = if (isPlayingThis) {
        "🎧 Listening • sentence ${liveIndex + 1} of $totalChunks"
    } else {
        insights[insightIndex % insights.size]
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { if (doc != null) onOpenDocument(doc) else onImportNew() },
        color = Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardGradient)
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Book Cover with scale pulse
                Box(
                    modifier = Modifier
                        .width(130.dp)
                        .height(185.dp)
                        .graphicsLayer {
                            scaleX = coverScale
                            scaleY = coverScale
                        }
                ) {
                    if (doc != null) {
                        DesktopCoverView(
                            documentId = doc.id,
                            title = doc.title,
                            sourceLabel = doc.sourceLabel,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Add, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(36.dp))
                            }
                        }
                    }
                }

                // Right: Document details, progress, stats, and Play button
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(185.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "CONTINUE READING",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8),
                                letterSpacing = 1.2.sp
                            )

                            if (doc != null) {
                                IconButton(
                                    onClick = onClearContinue,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Text(
                            text = doc?.title ?: "No book currently in progress",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = activeInsightText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Progress Bar
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (doc != null) "Sentence ${liveIndex + 1} of $totalChunks" else "",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Text(
                                text = if (doc != null) "$percent%" else "",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.25f)
                        )
                    }

                    // Bottom: Stats Pills on Left + Big Play Button on Right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (habitTracker.currentStreak >= 1) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = Color.White.copy(alpha = 0.12f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                                ) {
                                    Text(
                                        text = "🔥 ${habitTracker.currentStreak}d streak",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color.White.copy(alpha = 0.12f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                            ) {
                                Text(
                                    text = "⏱️ ${habitTracker.todayMinutesRead}m today",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Play / Pause Circle
                        FilledIconButton(
                            onClick = { if (doc != null) onTogglePlay(doc) else onImportNew() },
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            )
                        ) {
                            AnimatedContent(
                                targetState = if (doc == null) "add" else if (isPlayingThis) "pause" else "play",
                                transitionSpec = { fadeIn() togetherWith fadeOut() }
                            ) { state ->
                                Icon(
                                    imageVector = when (state) {
                                        "pause" -> Icons.Default.Pause
                                        "add" -> Icons.Default.Add
                                        else -> Icons.Default.PlayArrow
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

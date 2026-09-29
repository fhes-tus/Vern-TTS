package com.veritas.reader.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.reader.CoverExtractor
import com.veritas.reader.QuizSet
import com.veritas.reader.R
import com.veritas.reader.ReadingHistoryEntry
import com.veritas.reader.SavedDocument
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.ui.rememberVeritasHaptics
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.roundToInt

internal fun LazyListScope.studyQuizSection(
    quizzes: List<QuizSet>,
    onOpenAiStudyTools: () -> Unit,
    onShowPasteQuiz: () -> Unit,
    onShowQuizLabMetrics: () -> Unit,
    onPlayQuiz: (QuizSet) -> Unit,
    onDeleteQuiz: (QuizSet) -> Unit,
    onGoToLibrary: () -> Unit
) {
                    val quizzes = quizzes
                    if (quizzes.isEmpty()) {
                        item {
                            StudyEmptyState(
                                icon = Icons.Outlined.EditNote,
                                title = "No quizzes yet",
                                description = "Take a quiz to test your memory and retention. Create a quiz directly with AI or paste a quiz from ChatGPT, Claude, or Gemini.",
                                onGoToLibrary = { onGoToLibrary() },
                                primaryActionLabel = "Open AI Hub",
                                onPrimaryAction = onOpenAiStudyTools
                            )
                        }
                        item {
                            Button(
                                onClick = { onShowPasteQuiz() },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                shape = VeritasPackStyle.chipShape()
                            ) {
                                Text("Paste Quiz → Start Learning")
                            }
                        }
                    } else {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Saved Quizzes (${quizzes.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    OutlinedButton(
                                        onClick = { onShowQuizLabMetrics() },
                                        shape = RoundedCornerShape(50),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                    ) {
                                        Text("Quiz Lab 📊", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                FilledTonalButton(
                                    onClick = { onShowPasteQuiz() },
                                    shape = RoundedCornerShape(50),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("New Quiz", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        items(quizzes, key = { it.id }) { quiz ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                shape = VeritasPackStyle.compactShape(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = VeritasPackStyle.surfaceAlpha())),
                                border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                        modifier = Modifier.size(42.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("📝", fontSize = 20.sp)
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(quiz.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("${quiz.totalQuestions} Questions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (quiz.bestScore >= 0) {
                                                val calibratedBest = if (quiz.bestScore > quiz.totalQuestions && quiz.totalQuestions > 0) {
                                                    ((quiz.bestScore.toFloat() / 100f) * quiz.totalQuestions.toFloat()).roundToInt().coerceIn(0, quiz.totalQuestions)
                                                } else {
                                                    quiz.bestScore.coerceIn(0, quiz.totalQuestions)
                                                }
                                                val isMastered = calibratedBest == quiz.totalQuestions
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isMastered) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                    modifier = Modifier.padding(start = 4.dp)
                                                ) {
                                                    Text(
                                                        text = if (isMastered) "Mastered 🏆" else "Best: $calibratedBest/${quiz.totalQuestions}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isMastered) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Button(
                                        onClick = { onPlayQuiz(quiz) },
                                        shape = RoundedCornerShape(50),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text("Play", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                    IconButton(onClick = { onDeleteQuiz(quiz) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete quiz", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }

}

internal fun LazyListScope.studyHistorySection(
    readingHistory: List<ReadingHistoryEntry>,
    documents: List<SavedDocument>,
    onClearReadingHistory: () -> Unit,
    onRemoveReadingHistoryEntry: (String) -> Unit,
    onOpenDocumentAt: (SavedDocument, Int) -> Unit,
    onGoToLibrary: () -> Unit,
    onImportFile: () -> Unit
) {
                    val readingHistory = readingHistory
                    if (readingHistory.isEmpty()) {
                        item {
                            StudyEmptyState(
                                icon = Icons.Outlined.History,
                                title = "No reading history yet",
                                description = "Documents you read will show up here.",
                                onGoToLibrary = { onGoToLibrary() },
                                onImportFile = onImportFile
                            )
                        }
                    } else {
                        item {
                            var confirmClearHistory by remember { mutableStateOf(false) }
                            if (confirmClearHistory) {
                                AlertDialog(
                                    onDismissRequest = { confirmClearHistory = false },
                                    title = { Text(stringResource(R.string.clear_history_title)) },
                                    text = { Text(stringResource(R.string.clear_history_message)) },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            confirmClearHistory = false
                                            onClearReadingHistory()
                                        }) { Text(stringResource(R.string.action_clear), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { confirmClearHistory = false }) {
                                            Text(stringResource(R.string.action_cancel))
                                        }
                                    }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Recent history", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                TextButton(onClick = { confirmClearHistory = true }) {
                                    Text("Clear all", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        readingHistory.forEach { historyEntry ->
                            val doc = documents.firstOrNull { it.id == historyEntry.documentId }
                            item(key = "history-entry-${historyEntry.documentId}-${historyEntry.openedAt}") {
                                val isRemoved = doc == null
                                val progress = if (historyEntry.chunkCount > 0)
                                    (historyEntry.currentIndex.toFloat() / historyEntry.chunkCount).coerceIn(0f, 1f)
                                else 0f

                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { newVal ->
                                        if (newVal == SwipeToDismissBoxValue.EndToStart) {
                                            onRemoveReadingHistoryEntry(historyEntry.documentId)
                                            true
                                        } else false
                                    }
                                )

                                // Buzz the moment the swipe passes the point of no return, so the
                                // commit is felt before the finger lifts — this delete has no undo.
                                val swipeHaptics = rememberVeritasHaptics()
                                LaunchedEffect(dismissState.targetValue) {
                                    if (dismissState.targetValue != SwipeToDismissBoxValue.Settled) swipeHaptics.threshold()
                                }

                                SwipeToDismissBox(
                                    state = dismissState,
                                    enableDismissFromStartToEnd = false,
                                    backgroundContent = {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(vertical = 4.dp)
                                                .clip(VeritasPackStyle.compactShape())
                                                .background(MaterialTheme.colorScheme.errorContainer),
                                            contentAlignment = Alignment.CenterEnd
                                        ) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = "Remove from history",
                                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.padding(end = 20.dp)
                                            )
                                        }
                                    },
                                    modifier = Modifier.animateItem()
                                ) {
                                    val context = LocalContext.current
                                    val coverFile = remember(historyEntry.documentId) { CoverExtractor.coverFile(context, historyEntry.documentId) }
                                    val coverBitmap = remember(coverFile) {
                                        coverFile?.let { file ->
                                            if (file.exists()) runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull() else null
                                        }
                                    }
                                    val safeChunkCount = historyEntry.chunkCount.coerceAtLeast(doc?.chunkCount ?: 1).coerceAtLeast(1)
                                    val safeIndex = historyEntry.currentIndex.coerceIn(0, safeChunkCount - 1)
                                    val safeProgress = ((safeIndex + 1).toFloat() / safeChunkCount.toFloat()).coerceIn(0f, 1f)
                                    val percent = (safeProgress * 100f).toInt().coerceIn(0, 100)
                                    val locale = LocalConfiguration.current.locales[0]
                                    val openedTime = remember(historyEntry.openedAt, locale) {
                                        SimpleDateFormat("dd MMM, HH:mm", locale).format(Date(historyEntry.openedAt))
                                    }

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(
                                                if (!isRemoved) {
                                                    Modifier.clickable {
                                                        onOpenDocumentAt(doc!!, historyEntry.currentIndex)
                                                    }
                                                } else Modifier
                                            ),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isRemoved) {
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerLow
                                            }
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isRemoved) {
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                                            } else {
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                            }
                                        ),
                                        elevation = CardDefaults.cardElevation(defaultElevation = if (isRemoved) 0.dp else 2.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(52.dp, 68.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(
                                                        if (isRemoved) MaterialTheme.colorScheme.surfaceVariant
                                                        else MaterialTheme.colorScheme.primaryContainer
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (coverBitmap != null) {
                                                    Image(
                                                        bitmap = coverBitmap.asImageBitmap(),
                                                        contentDescription = historyEntry.title,
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else {
                                                    Text(
                                                        text = (doc?.sourceLabel ?: "DOC").take(3).uppercase(),
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Black,
                                                        color = if (isRemoved) MaterialTheme.colorScheme.onSurfaceVariant
                                                        else MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }

                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = historyEntry.title,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = if (isRemoved) {
                                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    },
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                if (isRemoved) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                                                    ) {
                                                        Text(
                                                            text = "Removed from library",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                } else {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = MaterialTheme.colorScheme.secondaryContainer
                                                        ) {
                                                            Text(
                                                                "$percent%",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                        Text(
                                                            "Sentence ${safeIndex + 1} of $safeChunkCount",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }

                                                    LinearProgressIndicator(
                                                        progress = { safeProgress },
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(4.dp)
                                                            .clip(RoundedCornerShape(2.dp)),
                                                        color = MaterialTheme.colorScheme.primary,
                                                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                                    )
                                                }

                                                Text(
                                                    text = "Opened $openedTime",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isRemoved) 0.3f else 0.8f)
                                                )
                                            }

                                            if (!isRemoved) {
                                                IconButton(
                                                    onClick = { onOpenDocumentAt(doc!!, historyEntry.currentIndex) },
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                                ) {
                                                    Icon(
                                                        Icons.Filled.PlayArrow,
                                                        contentDescription = "Resume",
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

}

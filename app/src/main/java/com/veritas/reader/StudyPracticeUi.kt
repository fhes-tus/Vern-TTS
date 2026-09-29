package com.veritas.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

@Composable
internal fun PasteFlashcardsDialog(
    onImport: (String, List<Flashcard>) -> Unit,
    onDismiss: () -> Unit
) {
    var pasted by remember { mutableStateOf("") }
    var setName by remember { mutableStateOf("") }
    val parsed = remember(pasted) { AiResultParser.parseFlashcards(pasted) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add flashcard set") },
        confirmButton = {
            Button(onClick = { onImport(setName, parsed) }, enabled = parsed.isNotEmpty()) {
                Text(if (parsed.isEmpty()) "Import" else "Import ${parsed.size} card${if (parsed.size == 1) "" else "s"}")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = setName,
                    onValueChange = { setName = it },
                    singleLine = true,
                    label = { Text("Set name (optional)") },
                    placeholder = { Text("Untitled set") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Paste the AI app's reply below. Cards are detected from Q:/A: (or Front:/Back:) pairs.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = pasted,
                    onValueChange = { pasted = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    label = { Text("AI reply") }
                )
                Text(
                    when {
                        pasted.isBlank() -> "Waiting for pasted text…"
                        parsed.isEmpty() -> "No cards recognized yet — check the Q:/A: format."
                        else -> "Found ${parsed.size} card${if (parsed.size == 1) "" else "s"}. First: “${parsed.first().front.take(60)}”"
                    },
                    color = if (parsed.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    )
}

@Composable
internal fun PasteQuizDialog(
    onSaveQuiz: ((QuizSet) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var pasted by remember { mutableStateOf("") }
    var quizSet by remember { mutableStateOf<QuizSet?>(null) }
    val parsed = remember(pasted) { AiResultParser.parseQuiz(pasted) }

    quizSet?.let { qSet ->
        QuizPlayerDialog(
            questions = qSet.questions,
            quizTitle = qSet.title,
            onSaveScore = { score ->
                onSaveQuiz?.invoke(qSet.copy(bestScore = score))
            },
            onDismiss = onDismiss
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Take a pasted quiz") },
        confirmButton = {
            Button(
                onClick = {
                    val created = QuizSet(title = "Pasted Quiz", questions = parsed)
                    onSaveQuiz?.invoke(created)
                    quizSet = created
                },
                enabled = parsed.isNotEmpty()
            ) {
                Text(if (parsed.isEmpty()) "Start quiz" else "Start quiz (${parsed.size})")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Paste the AI app's quiz reply below (Q: / A) B) C) D) / Answer: format).",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = pasted,
                    onValueChange = { pasted = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    label = { Text("AI reply") }
                )
                Text(
                    when {
                        pasted.isBlank() -> "Waiting for pasted text…"
                        parsed.isEmpty() -> "No questions recognized yet — check the format."
                        else -> "Found ${parsed.size} question${if (parsed.size == 1) "" else "s"}."
                    },
                    color = if (parsed.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    )
}

@Composable
internal fun QuizPlayerDialog(
    questions: List<QuizQuestion>,
    quizTitle: String = "Revision Quiz",
    onSaveScore: ((Int) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var index by remember { mutableIntStateOf(0) }
    val userAnswers = remember { mutableStateMapOf<Int, String>() }
    val flagged = remember { mutableStateSetOf<Int>() }
    val skipped = remember { mutableStateSetOf<Int>() }
    var finished by remember { mutableStateOf(false) }
    var hasSavedScore by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    if (questions.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    val safeIndex = index.coerceIn(0, questions.lastIndex)
    val question = questions[safeIndex]
    val selected = userAnswers[safeIndex]
    val isFlagged = flagged.contains(safeIndex)

    val score = remember(userAnswers.toMap(), questions) {
        questions.mapIndexed { idx, q ->
            if (userAnswers[idx] == q.answer) 1 else 0
        }.sum()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close quiz")
                    }
                    Text(
                        text = quizTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (!finished) {
                            IconButton(
                                onClick = {
                                    if (isFlagged) flagged.remove(safeIndex) else flagged.add(safeIndex)
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFlagged) Icons.Filled.Flag else Icons.Outlined.Flag,
                                    contentDescription = if (isFlagged) "Unflag question" else "Flag question for review",
                                    tint = if (isFlagged) Color(0xFFE5A93C) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = if (finished) "Results" else "${safeIndex + 1} / ${questions.size}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Progress Bar
                val answeredCount = userAnswers.size
                LinearProgressIndicator(
                    progress = { if (finished) 1f else answeredCount.toFloat() / questions.size },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                // Exam Question Navigator Row
                if (!finished) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        questions.indices.forEach { qIdx ->
                            val isCurrent = qIdx == safeIndex
                            val hasAns = userAnswers.containsKey(qIdx)
                            val isQFlagged = flagged.contains(qIdx)
                            val isQSkipped = skipped.contains(qIdx)

                            val chipBg = when {
                                isCurrent -> MaterialTheme.colorScheme.primary
                                hasAns -> Color(0xFF10B981).copy(alpha = 0.25f)
                                isQFlagged -> Color(0xFFE5A93C).copy(alpha = 0.25f)
                                isQSkipped -> MaterialTheme.colorScheme.surfaceVariant
                                else -> MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                            val textColor = when {
                                isCurrent -> Color.White
                                hasAns -> Color(0xFF10B981)
                                isQFlagged -> Color(0xFFE5A93C)
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            val border = when {
                                isCurrent -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                isQFlagged -> BorderStroke(1.5.dp, Color(0xFFE5A93C))
                                else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = chipBg,
                                border = border,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { index = qIdx }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${qIdx + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isCurrent || hasAns) FontWeight.Bold else FontWeight.Normal,
                                        color = textColor
                                    )
                                }
                            }
                        }
                    }
                }

                if (finished) {
                    // Completion Summary
                    val percentage = ((score.toFloat() / questions.size) * 100).roundToInt()
                    LaunchedEffect(score) {
                        if (!hasSavedScore) {
                            hasSavedScore = true
                            onSaveScore?.invoke(score)
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .background(
                                    color = if (percentage >= 70) Color(0xFF10B981).copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (percentage >= 80) "🏆" else if (percentage >= 60) "🌟" else "📚",
                                fontSize = 44.sp
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = when {
                                percentage == 100 -> "Flawless Mastery!"
                                percentage >= 80 -> "Excellent Understanding!"
                                percentage >= 60 -> "Good Effort — Keep Reviewing!"
                                else -> "Review Needed"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(6.dp))

                        Text(
                            text = "You scored $score out of ${questions.size} ($percentage%)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )

                        // Exam stats breakdown
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$score", fontWeight = FontWeight.Bold, color = Color(0xFF10B981), style = MaterialTheme.typography.titleMedium)
                                Text("Correct", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${questions.size - score}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
                                Text("Missed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${flagged.size}", fontWeight = FontWeight.Bold, color = Color(0xFFE5A93C), style = MaterialTheme.typography.titleMedium)
                                Text("Flagged", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        OutlinedButton(
                            onClick = {
                                finished = false
                                index = 0
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(50)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Review Exam Questions")
                        }

                        Spacer(Modifier.height(10.dp))

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text("Done", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Active Question Screen
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Question Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (isFlagged) {
                                    Icon(
                                        Icons.Filled.Flag,
                                        contentDescription = "Flagged",
                                        tint = Color(0xFFE5A93C),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(
                                    text = question.question,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Options list
                        val optionLabels = listOf("A", "B", "C", "D", "E", "F")
                        question.options.forEachIndexed { optIndex, optionText ->
                            val isPicked = selected == optionText
                            val isCorrect = optionText == question.answer
                            val showEvaluation = selected != null

                            val containerColor = when {
                                showEvaluation && isCorrect -> Color(0xFF10B981).copy(alpha = 0.18f)
                                showEvaluation && isPicked -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                                isPicked -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surface
                            }

                            val borderColor = when {
                                showEvaluation && isCorrect -> Color(0xFF10B981)
                                showEvaluation && isPicked -> MaterialTheme.colorScheme.error
                                isPicked -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable(enabled = selected == null) {
                                        userAnswers[safeIndex] = optionText
                                        skipped.remove(safeIndex)
                                        if (isCorrect) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        } else {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = containerColor),
                                border = BorderStroke(1.5.dp, borderColor)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = when {
                                            showEvaluation && isCorrect -> Color(0xFF10B981)
                                            showEvaluation && isPicked -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = when {
                                                    showEvaluation && isCorrect -> "✓"
                                                    showEvaluation && isPicked -> "✗"
                                                    else -> optionLabels.getOrElse(optIndex) { "${optIndex + 1}" }
                                                },
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (showEvaluation && (isCorrect || isPicked)) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Text(
                                        text = optionText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (showEvaluation && (isCorrect || isPicked)) FontWeight.SemiBold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // Explanation card
                        if (selected != null && question.explanation.isNotBlank()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("💡", fontSize = 18.sp)
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            "Explanation",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            question.explanation,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Exam Action Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { if (safeIndex > 0) index = safeIndex - 1 },
                            enabled = safeIndex > 0,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.weight(1f).height(48.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous question", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Previous", maxLines = 1)
                        }

                        if (selected == null) {
                            OutlinedButton(
                                onClick = {
                                    skipped.add(safeIndex)
                                    if (safeIndex + 1 < questions.size) {
                                        index = safeIndex + 1
                                    } else {
                                        finished = true
                                    }
                                },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.weight(1f).height(48.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("Skip", maxLines = 1)
                            }
                        }

                        Button(
                            onClick = {
                                if (safeIndex + 1 >= questions.size) {
                                    finished = true
                                } else {
                                    index = safeIndex + 1
                                }
                            },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.weight(1.3f).height(48.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(
                                text = if (safeIndex + 1 >= questions.size) "Finish (${userAnswers.size}/${questions.size})" else "Next →",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

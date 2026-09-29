package com.veritas.reader.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.veritas.reader.QuizSet
import com.veritas.reader.SavedDocument
import kotlin.math.roundToInt


@Composable
internal fun QuizLabMetricsDialog(
    quizzes: List<QuizSet>,
    documents: List<SavedDocument> = emptyList(),
    onPlayQuiz: (QuizSet) -> Unit,
    onNewQuiz: () -> Unit,
    onDismiss: () -> Unit
) {
    fun getBookTitle(q: QuizSet): String {
        val doc = documents.firstOrNull { it.id == q.documentId }
        if (doc != null && doc.title.isNotBlank()) return doc.title
        return "General Quiz Set"
    }
    fun calibrateScore(q: QuizSet): Int {
        if (q.bestScore < 0) return -1
        if (q.totalQuestions <= 0) return 0
        return if (q.bestScore > q.totalQuestions) {
            ((q.bestScore.toFloat() / 100f) * q.totalQuestions.toFloat()).roundToInt().coerceIn(0, q.totalQuestions)
        } else {
            q.bestScore.coerceIn(0, q.totalQuestions)
        }
    }

    val totalQuizzes = quizzes.size
    val playedQuizzes = quizzes.filter { it.bestScore >= 0 }
    val playedCount = playedQuizzes.size
    val totalQuestionsAnswered = playedQuizzes.sumOf { it.totalQuestions }
    val totalCorrectAnswered = playedQuizzes.sumOf { calibrateScore(it) }
    val overallAccuracy = if (totalQuestionsAnswered > 0) {
        ((totalCorrectAnswered.toFloat() / totalQuestionsAnswered.toFloat()) * 100).roundToInt()
    } else 0

    val masteredCount = playedQuizzes.count { calibrateScore(it) == it.totalQuestions && it.totalQuestions > 0 }
    val proficientCount = playedQuizzes.count {
        val score = calibrateScore(it)
        val pct = if (it.totalQuestions > 0) (score * 100 / it.totalQuestions) else 0
        score < it.totalQuestions && pct >= 70
    }
    val reviewCount = playedQuizzes.count {
        val score = calibrateScore(it)
        val pct = if (it.totalQuestions > 0) (score * 100 / it.totalQuestions) else 0
        pct < 70
    }
    val unplayedCount = totalQuizzes - playedCount

    var searchQuery by remember { mutableStateOf("") }
    val filteredQuizzes = remember(quizzes, searchQuery, documents) {
        if (searchQuery.isBlank()) quizzes
        else quizzes.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            getBookTitle(it).contains(searchQuery, ignoreCase = true)
        }
    }

    val quizzesByBook = remember(quizzes, documents) {
        quizzes.groupBy { getBookTitle(it) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
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
            ) {
                // Header Bar
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        Icons.Filled.Insights,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        "Quiz Lab & Performance",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    "Retention, mastery & score calibration",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Button(
                            onClick = {
                                onDismiss()
                                onNewQuiz()
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(50)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Quiz", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // Dashboard Scrollable Body
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // KPI Overview Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Overall Learning Metrics",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (overallAccuracy >= 80) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = if (playedCount > 0) "$overallAccuracy% Accuracy" else "No Data Yet",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (overallAccuracy >= 80) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "$playedCount / $totalQuizzes",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text("Completed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "$masteredCount",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF10B981)
                                    )
                                    Text("Mastered 🏆", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "$totalCorrectAnswered / $totalQuestionsAnswered",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Text("Questions Right", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            // Accuracy Progress Gauge
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Retention Gauge", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("$overallAccuracy%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = { if (totalQuestionsAnswered > 0) (overallAccuracy / 100f).coerceIn(0f, 1f) else 0f },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                                    color = if (overallAccuracy >= 80) Color(0xFF10B981) else if (overallAccuracy >= 60) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }
                    }

                    // Mastery & Score Distribution Chart
                    val masterySlices = remember(masteredCount, proficientCount, reviewCount, unplayedCount) {
                        listOf(
                            DonutSlice("Mastered (100%)", masteredCount.toFloat(), Color(0xFF10B981), "$masteredCount quizzes completed with a perfect score."),
                            DonutSlice("Proficient (70-99%)", proficientCount.toFloat(), Color(0xFF3B82F6), "$proficientCount quizzes passed with 70% or higher."),
                            DonutSlice("Needs Review (<70%)", reviewCount.toFloat(), Color(0xFFEF4444), "$reviewCount quizzes scored below 70% and need review."),
                            DonutSlice("Unplayed", unplayedCount.toFloat(), Color(0xFF94A3B8), "$unplayedCount quizzes waiting to be taken.")
                        ).filter { it.value > 0f }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "Score & Mastery Distribution",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            if (masterySlices.isNotEmpty()) {
                                DashboardDonutChart(
                                    title = "Mastery Breakdown",
                                    slices = masterySlices,
                                    totalLabel = "Quizzes",
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        "Progress Composition",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(10.dp)
                                            .clip(RoundedCornerShape(50))
                                    ) {
                                        if (masteredCount > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .weight(masteredCount.toFloat())
                                                    .fillMaxHeight()
                                                    .background(Color(0xFF10B981))
                                            )
                                        }
                                        if (proficientCount > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .weight(proficientCount.toFloat())
                                                    .fillMaxHeight()
                                                    .background(Color(0xFF3B82F6))
                                            )
                                        }
                                        if (reviewCount > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .weight(reviewCount.toFloat())
                                                    .fillMaxHeight()
                                                    .background(Color(0xFFEF4444))
                                            )
                                        }
                                        if (unplayedCount > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .weight(unplayedCount.toFloat())
                                                    .fillMaxHeight()
                                                    .background(Color(0xFF94A3B8))
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.12f)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("$masteredCount", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                                        Text("100% Perfect", style = MaterialTheme.typography.labelSmall, color = Color(0xFF10B981))
                                    }
                                }
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("$proficientCount", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Text("70-99% Passing", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFEF4444).copy(alpha = 0.12f)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("$reviewCount", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                                        Text("<70% Review", style = MaterialTheme.typography.labelSmall, color = Color(0xFFEF4444))
                                    }
                                }
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("$unplayedCount", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Unplayed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }

                    // Performance By Book / Document
                    if (quizzesByBook.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Performance Per Book / Document",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            for ((bookTitle, bookQuizzes) in quizzesByBook) {
                                val bookPlayed = bookQuizzes.filter { it.bestScore >= 0 }
                                val bookTotalQ = bookPlayed.sumOf { it.totalQuestions }
                                val bookCorrectQ = bookPlayed.sumOf { calibrateScore(it) }
                                val bookAvg = if (bookTotalQ > 0) ((bookCorrectQ.toFloat() / bookTotalQ.toFloat()) * 100).roundToInt() else 0
                                val totalQuizCount = bookQuizzes.size
                                val completedCount = bookPlayed.size

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                bookTitle,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                "$totalQuizCount quiz${if (totalQuizCount == 1) "" else "zes"} • $completedCount completed",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (bookAvg >= 80) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = if (bookPlayed.isNotEmpty()) "$bookAvg% Avg" else "Unplayed",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (bookAvg >= 80) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Complete Quizzes List
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "All Quizzes (${filteredQuizzes.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search quizzes by title or book…") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        if (filteredQuizzes.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("No quizzes found", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text("Create quizzes using AI Study Studio to practice here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        } else {
                            filteredQuizzes.forEach { quiz ->
                                val score = calibrateScore(quiz)
                                val hasPlayed = quiz.bestScore >= 0
                                val pct = if (quiz.totalQuestions > 0 && hasPlayed) (score * 100 / quiz.totalQuestions) else 0
                                val isMastered = hasPlayed && score == quiz.totalQuestions

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .background(
                                                    if (isMastered) Color(0xFF10B981).copy(alpha = 0.15f)
                                                    else if (hasPlayed && pct >= 70) MaterialTheme.colorScheme.primaryContainer
                                                    else MaterialTheme.colorScheme.surfaceVariant,
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(if (isMastered) "🏆" else if (hasPlayed) "📊" else "📝", fontSize = 18.sp)
                                        }

                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(
                                                quiz.title,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val bookTitle = getBookTitle(quiz)
                                            if (bookTitle.isNotBlank() && bookTitle != "General Quiz Set") {
                                                Text(
                                                    bookTitle,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text("${quiz.totalQuestions} Questions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                                if (hasPlayed) {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = if (isMastered) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                    ) {
                                                        Text(
                                                            text = if (isMastered) "Mastered 100%" else "Best: $score/${quiz.totalQuestions} ($pct%)",
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
                                            onClick = {
                                                onDismiss()
                                                onPlayQuiz(quiz)
                                            },
                                            shape = RoundedCornerShape(50),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Text(if (hasPlayed) "Retake" else "Play", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


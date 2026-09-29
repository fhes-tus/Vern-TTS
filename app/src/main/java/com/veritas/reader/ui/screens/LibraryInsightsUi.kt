package com.veritas.reader.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.veritas.reader.GeneralNote
import com.veritas.reader.ReaderSettings
import com.veritas.reader.ReaderTrackerSnapshot
import com.veritas.reader.SavedDocument
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.blendColors
import com.veritas.reader.formatUpdated
import com.veritas.reader.ui.OnboardingController
import com.veritas.reader.ui.OnboardingStep
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
internal fun ReadingStatsDashboardDialog(
    snapshot: ReaderTrackerSnapshot,
    documents: List<SavedDocument>,
    documentReadingTimes: Map<String, Long>,
    onDismiss: () -> Unit,
    readerSettings: ReaderSettings = ReaderSettings(),
    onGoalMinutesChange: (Int) -> Unit = {}
) {
    // Animation for stats count-up
    var targetCurrentStreak by remember { mutableIntStateOf(0) }
    var targetLongestStreak by remember { mutableIntStateOf(0) }
    var prevCurrentStreak by remember { mutableIntStateOf(0) }
    var prevLongestStreak by remember { mutableIntStateOf(0) }
    LaunchedEffect(snapshot) {
        delay(100)
        targetCurrentStreak = snapshot.currentStreak
        targetLongestStreak = snapshot.longestStreak
    }
    // Scale the roll-up duration with the delta (~70ms per step) so a +1 increment animates
    // quickly while the initial 0→N count-up still reads as a roll.
    val currentStreakDuration = (kotlin.math.abs(targetCurrentStreak - prevCurrentStreak) * 70).coerceIn(220, 650)
    val longestStreakDuration = (kotlin.math.abs(targetLongestStreak - prevLongestStreak) * 70).coerceIn(220, 650)
    val currentStreakAnimated by animateIntAsState(
        targetValue = targetCurrentStreak,
        animationSpec = tween(durationMillis = currentStreakDuration, easing = FastOutSlowInEasing),
        label = "currentStreakAnim"
    )
    val longestStreakAnimated by animateIntAsState(
        targetValue = targetLongestStreak,
        animationSpec = tween(durationMillis = longestStreakDuration, easing = FastOutSlowInEasing),
        label = "longestStreakAnim"
    )
    LaunchedEffect(targetCurrentStreak) { prevCurrentStreak = targetCurrentStreak }
    LaunchedEffect(targetLongestStreak) { prevLongestStreak = targetLongestStreak }

    // Pulsing/floating emoji transition
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val emojiScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error
    val pastedColor = MaterialTheme.colorScheme.inversePrimary
    val slidesColor = blendColors(secondaryColor, errorColor, 0.5f)

    // Format Distribution slices — every document lands in exactly one bucket so the
    // chart total always matches the library size. E-Books (EPUB), Slide Decks (PPTX),
    // and Documents (Word/text/scanned) are split into their own slices.
    val formatSlices = remember(documents, primaryColor, secondaryColor, tertiaryColor, errorColor, pastedColor, slidesColor) {
        var pdfCount = 0
        var webCount = 0
        var ebookCount = 0
        var slidesCount = 0
        var docCount = 0
        var pastedCount = 0
        documents.forEach { doc ->
            val mime = doc.originalMimeType.lowercase(Locale.US)
            val title = doc.title.lowercase(Locale.US)
            val label = doc.sourceLabel.lowercase(Locale.US)
            when {
                // sourceLabel is the explicit, reliable classifier set at import time
                // ("PDF", "DOCX", "PPTX", "EPUB", "OCR", "TXT", "Web", ...). Key off it
                // first, then fall back to mime type / filename so older records still bucket.
                label == "pdf" || mime.contains("pdf") || title.endsWith(".pdf") -> pdfCount++
                label.contains("web") || label.contains("http") || label.contains("article") ||
                    mime.contains("html") -> webCount++
                label == "epub" || mime.contains("epub") || title.endsWith(".epub") -> ebookCount++
                label == "pptx" || mime.contains("presentationml") || title.endsWith(".pptx") -> slidesCount++
                label in setOf("docx", "txt", "ocr") ||
                    mime.contains("word") || mime.contains("wordprocessingml") ||
                    mime.startsWith("image/") ||
                    title.endsWith(".docx") || title.endsWith(".txt") -> docCount++
                else -> pastedCount++
            }
        }
        listOf(
            DonutSlice(
                label = "PDF Documents",
                value = pdfCount.toFloat(),
                color = primaryColor,
                description = "PDFs imported from local file storage or other directories. Excellent for study outlines."
            ),
            DonutSlice(
                label = "Web Articles",
                value = webCount.toFloat(),
                color = secondaryColor,
                description = "Online articles, blogs, and papers saved via URL import. Perfect for quick news reading."
            ),
            DonutSlice(
                label = "E-Books",
                value = ebookCount.toFloat(),
                color = tertiaryColor,
                description = "EPUB e-books imported into your library."
            ),
            DonutSlice(
                label = "Slide Decks",
                value = slidesCount.toFloat(),
                color = slidesColor,
                description = "PowerPoint presentations read slide by slide with titles, bullets, and speaker notes."
            ),
            DonutSlice(
                label = "Documents",
                value = docCount.toFloat(),
                color = errorColor,
                description = "Word documents, text files, and scanned (OCR) documents."
            ),
            DonutSlice(
                label = "Pasted Text",
                value = pastedCount.toFloat(),
                color = pastedColor,
                description = "Text pasted directly into the reader interface or manually typed drafts."
            )
        ).filter { it.value > 0f }
    }

    // Time Allocation slices — real listening/reading time recorded per document for
    // the current month, so the chart resets monthly instead of stacking up forever.
    val timeSlices = remember(documents, documentReadingTimes, primaryColor, secondaryColor, tertiaryColor, errorColor) {
        val docTimes = documents.mapNotNull { doc ->
            val readingTime = documentReadingTimes[doc.id] ?: 0L
            if (readingTime > 0L) doc to readingTime else null
        }.sortedByDescending { it.second }

        val totalTime = docTimes.sumOf { it.second }

        if (docTimes.isEmpty()) {
            emptyList()
        } else {
            val top4 = docTimes.take(4)
            val othersTime = if (docTimes.size > 4) docTimes.drop(4).sumOf { it.second } else 0L
            
            val colors = listOf(
                primaryColor,
                secondaryColor,
                tertiaryColor,
                errorColor,
                Color.Gray
            )
            
            val list = mutableListOf<DonutSlice>()
            top4.forEachIndexed { idx, (doc, time) ->
                list.add(
                    DonutSlice(
                        label = doc.title,
                        value = time.toFloat() / 60000f, // convert to minutes
                        color = colors[idx % colors.size],
                        description = "You spent ${time / 60000} minutes reading this document. That's ${(time * 100f / totalTime.coerceAtLeast(1)).toInt()}% of your total time."
                    )
                )
            }
            if (othersTime > 0L) {
                list.add(
                    DonutSlice(
                        label = "Others",
                        value = othersTime.toFloat() / 60000f,
                        color = colors[4],
                        description = "All other documents combined account for ${othersTime / 60000} minutes of your reading sessions."
                    )
                )
            }
            list
        }
    }

    var isCompletionsExpanded by rememberSaveable { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Reading Insights",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                "Personal reading rhythm & library metrics",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = onDismiss,
                            shape = VeritasPackStyle.chipShape()
                        ) {
                            Text("Done")
                        }
                    }
                }

                // Streaks & Stats Card (Modern card with gradient streak pills)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = VeritasPackStyle.cardShape(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            // Streaks display
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                // Current Streak Card
                                val streakMilestone = when {
                                    snapshot.currentStreak >= 30 -> "🏆 30-Day Master"
                                    snapshot.currentStreak >= 14 -> "⚡ 2-Week Pro"
                                    snapshot.currentStreak >= 7 -> "🔥 7-Day Flame"
                                    snapshot.currentStreak >= 3 -> "✨ 3-Day Habit"
                                    snapshot.currentStreak >= 1 -> "🌱 Started"
                                    else -> "Ready to start"
                                }
                                Card(
                                    modifier = Modifier.weight(1f),
                                    shape = VeritasPackStyle.cardShape(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                    ),
                                    border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.LocalFireDepartment,
                                            contentDescription = "Current streak",
                                            tint = Color(0xFFFF7043),
                                            modifier = Modifier.size(32.dp).scale(emojiScale)
                                        )
                                        Text(
                                            text = "$currentStreakAnimated",
                                            style = MaterialTheme.typography.headlineLarge,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = "Current Streak",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = streakMilestone,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                // Longest Streak Card
                                Card(
                                    modifier = Modifier.weight(1f),
                                    shape = VeritasPackStyle.cardShape(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                                    ),
                                    border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.EmojiEvents,
                                            contentDescription = "Longest streak",
                                            tint = Color(0xFFFFC107),
                                            modifier = Modifier.size(32.dp).scale(emojiScale)
                                        )
                                        Text(
                                            text = "$longestStreakAnimated",
                                            style = MaterialTheme.typography.headlineLarge,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = "Best Streak",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "Personal Record",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Weekly Usage Trend (swipe to see previous weeks)
                            Text(
                                "Weekly Reading Rhythm",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            WeeklyReadingBarsPager(snapshot.weeklyHistory, barHeight = 96.dp)

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                BigStat(animatedTrackerDuration(snapshot.weeklyUsageMillis), "This week", Modifier.weight(1f))
                                BigStat(animatedTrackerDuration(snapshot.weeklyAverageMillis), "Daily avg", Modifier.weight(1f))
                            }
                        }
                    }
                }

                // Heatmap and Distribution Donut Charts
                item {
                    CalendarHeatMap(snapshot.activeDateKeys, snapshot.openedDateKeys)
                }

                item {
                    InteractiveDonutChart(
                        title = "Library Source Distribution",
                        slices = formatSlices,
                        totalLabel = "Total docs",
                        titleIcon = Icons.Filled.PieChart
                    )
                }

                if (timeSlices.isNotEmpty()) {
                    item {
                        InteractiveDonutChart(
                            title = "Time Allocation — This Month",
                            slices = timeSlices,
                            totalLabel = "Total min",
                            titleIcon = Icons.Filled.Timer
                        )
                    }
                }

                item {
                    ReadingVelocityAndPaceCard(
                        weeklyMillis = snapshot.weeklyUsageMillis,
                        activeDocsCount = snapshot.documentsReadThisWeek,
                        streakDays = snapshot.currentStreak,
                        dailyGoalMinutes = readerSettings.dailyGoalMinutes,
                        onGoalMinutesChange = onGoalMinutesChange
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        BigStat("${snapshot.documentsReadThisWeek}", "Active docs this week", Modifier.weight(1f))
                        BigStat("${snapshot.documentsCompletedThisMonth}", "Finished this month", Modifier.weight(1f))
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isCompletionsExpanded = !isCompletionsExpanded }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Recent completions",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (snapshot.recentCompletions.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.padding(start = 2.dp)
                                ) {
                                    Text(
                                        "${snapshot.recentCompletions.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = { isCompletionsExpanded = !isCompletionsExpanded },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isCompletionsExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = if (isCompletionsExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (isCompletionsExpanded) {
                    if (snapshot.recentCompletions.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = VeritasPackStyle.cardShape(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
                            ) {
                                Text(
                                    "Finish a book or document and it will be celebrated here.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(snapshot.recentCompletions, key = { it.documentId }) { completion ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = VeritasPackStyle.cardShape(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "Completed",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            completion.title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            "Completed ${formatUpdated(completion.completedAt)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Onboarding tour card: the dashboard dialog window covers the main spotlight
            // overlay, so the insights step renders its own card here.
            if (OnboardingController.activeStep == OnboardingStep.INSIGHTS_PAGE_SPOTLIGHT) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .widthIn(max = 380.dp)
                        .padding(16.dp)
                        .navigationBarsPadding()
                        .shadow(16.dp, RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = OnboardingStep.INSIGHTS_PAGE_SPOTLIGHT.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = {
                                    OnboardingController.activeStep = null
                                    onDismiss()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss Tour",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                            text = OnboardingStep.INSIGHTS_PAGE_SPOTLIGHT.body,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = {
                                onDismiss()
                                OnboardingController.activeStep = OnboardingStep.INSIGHTS_SPOTLIGHT
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Back", style = MaterialTheme.typography.labelMedium)
                            }

                            Button(
                                onClick = {
                                    onDismiss()
                                    OnboardingController.activeStep = OnboardingStep.NOTES_TAB_SPOTLIGHT
                                },
                                shape = RoundedCornerShape(50)
                            ) {
                                Text("Next", style = MaterialTheme.typography.labelMedium)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Next",
                                    modifier = Modifier.size(16.dp)
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

@Composable
internal fun CompactStat(value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = VeritasPackStyle.cardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(value, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun BigStat(value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = VeritasPackStyle.cardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

internal fun shareGeneralNote(context: Context, note: GeneralNote) {
    val body = if (note.isChecklist) {
        note.content.lineSequence().joinToString("\n") { line ->
            when {
                line.startsWith("[x]") -> "☑ " + line.removePrefix("[x]").trim()
                line.startsWith("[ ]") -> "☐ " + line.removePrefix("[ ]").trim()
                else -> line
            }
        }
    } else {
        RichTextFormatter.stripMarkup(note.content)
    }
    val plain = buildString {
        if (note.title.isNotBlank()) {
            append(note.title)
            append("\n\n")
        }
        append(body)
    }.trim()
    if (plain.isBlank()) {
        Toast.makeText(context, "Nothing to share yet", Toast.LENGTH_SHORT).show()
        return
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, note.title.ifBlank { "Vern note" })
        putExtra(Intent.EXTRA_TEXT, plain)
    }
    runCatching { context.startActivity(Intent.createChooser(send, "Share note")) }
}

// Count-up variant of formatTrackerDuration: rolls the minute total up to its
// target on first show / change, so the big stats animate like the streak counter.
@Composable
internal fun animatedTrackerDuration(millis: Long): String {
    val goal = (millis / 60_000L).coerceAtLeast(0L).toInt()
    var target by remember { mutableIntStateOf(0) }
    LaunchedEffect(goal) { target = goal }
    val animated by animateIntAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "trackerDurationCountUp"
    )
    return formatTrackerDuration(animated.toLong() * 60_000L)
}

internal fun formatTrackerDuration(millis: Long): String {
    val minutes = (millis / 60_000L).coerceAtLeast(0L)
    val hours = minutes / 60L
    val remaining = minutes % 60L
    return when {
        hours > 0L && remaining > 0L -> "${hours}h ${remaining}m"
        hours > 0L -> "${hours}h"
        else -> "${remaining}m"
    }
}

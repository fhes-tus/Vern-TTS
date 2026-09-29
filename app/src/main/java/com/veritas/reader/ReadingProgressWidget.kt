package com.veritas.reader

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.util.Calendar

class ReadingProgressWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ReadingProgressWidget()
}

class ReadingProgressWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = DocumentRepository(context)
        val documents = repository.loadDocuments()
        val tracker = repository.loadReaderTrackerSnapshot()
        val settings = repository.loadReaderSettings()

        // Find the active document
        val activeId = PlaybackStateStore.activeDocumentId
        val activeDoc = if (activeId != null) {
            repository.findDocument(activeId)
        } else {
            documents.filter { it.chunkCount > 1 && it.currentIndex in 1 until it.chunkCount }
                .maxByOrNull { it.updatedAt } ?: documents.firstOrNull()
        }

        val hasGoal = settings.dailyGoalMinutes > 0
        val dailyGoalMinutes = settings.dailyGoalMinutes.toLong()

        val cal = Calendar.getInstance()
        val dayIdx = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday = 0 ... Sunday = 6
        val todayMillis = tracker.weeklyUsageByDay.getOrNull(dayIdx) ?: 0L
        val todayMinutes = (todayMillis / 60000L).coerceAtLeast(0L)

        val goalProgress = if (hasGoal && dailyGoalMinutes > 0) {
            (todayMinutes.toFloat() / dailyGoalMinutes.toFloat()).coerceIn(0f, 1f)
        } else 0f

        provideContent {
            val size = androidx.glance.LocalSize.current
            val isCompact = size.height < 120.dp
            val isTall = size.height >= 175.dp
            val isWide = size.width >= 240.dp

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(32.dp)
                    .background(VeritasWidgetColors.widgetBackground)
                    .padding(if (isCompact) 10.dp else 12.dp)
                    .clickable(actionRunCallback<ShowReaderTrackerActionCallback>())
            ) {
                // Header: Title + Streak badge
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Reading Tracker",
                        style = TextStyle(
                            fontSize = if (isWide) 16.sp else 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = VeritasWidgetColors.textPrimary
                        ),
                        modifier = GlanceModifier.defaultWeight()
                    )

                    Box(
                        modifier = GlanceModifier
                            .cornerRadius(12.dp)
                            .background(VeritasWidgetColors.cardBackground)
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🔥 ${tracker.currentStreak}d Streak",
                            style = TextStyle(
                                fontSize = if (isWide) 12.sp else 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = VeritasWidgetColors.streakAccent
                            )
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.height(if (isCompact) 6.dp else 8.dp))

                if (activeDoc == null) {
                    Box(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .fillMaxWidth()
                            .cornerRadius(18.dp)
                            .background(VeritasWidgetColors.cardBackground)
                            .padding(12.dp)
                            .clickable(actionRunCallback<LaunchAppActionCallback>()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No active book • Tap to open library",
                            style = TextStyle(
                                fontSize = if (isWide) 13.sp else 11.sp,
                                color = VeritasWidgetColors.textMuted
                            )
                        )
                    }
                } else {
                    val progressVal = if (activeDoc.chunkCount > 0) {
                        val currIndex = if (PlaybackStateStore.activeDocumentId == activeDoc.id) {
                            PlaybackStateStore.currentIndex
                        } else {
                            activeDoc.currentIndex
                        }
                        (currIndex.toFloat() / activeDoc.chunkCount.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    val progressPercent = (progressVal * 100).toInt()

                    val continueParams = actionParametersOf(
                        ContinueReadingActionCallback.DocIdKey to activeDoc.id
                    )

                    // Book Card
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .then(if (isCompact || !isTall) GlanceModifier.defaultWeight() else GlanceModifier)
                            .cornerRadius(18.dp)
                            .background(VeritasWidgetColors.cardBackground)
                            .padding(if (isCompact) 8.dp else 10.dp)
                            .clickable(actionRunCallback<ContinueReadingActionCallback>(continueParams)),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val coverFile = CoverExtractor.coverFile(context, activeDoc.id)
                            val bitmap = if (coverFile != null && coverFile.exists()) {
                                runCatching { BitmapFactory.decodeFile(coverFile.absolutePath) }.getOrNull()
                            } else null

                            val coverWidth = if (isCompact) 34.dp else 44.dp
                            val coverHeight = if (isCompact) 46.dp else 56.dp

                            if (bitmap != null) {
                                Image(
                                    provider = ImageProvider(bitmap),
                                    contentDescription = "Cover",
                                    modifier = GlanceModifier.size(coverWidth, coverHeight).cornerRadius(10.dp)
                                )
                            } else {
                                Box(
                                    modifier = GlanceModifier
                                        .size(coverWidth, coverHeight)
                                        .cornerRadius(10.dp)
                                        .background(ImageProvider(R.drawable.ic_widget_book_cover_gradient)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        provider = ImageProvider(R.drawable.ic_widget_library),
                                        contentDescription = "Book icon",
                                        modifier = GlanceModifier.size(if (isCompact) 20.dp else 24.dp),
                                        colorFilter = ColorFilter.tint(ColorProvider(Color.White))
                                    )
                                }
                            }

                            Spacer(modifier = GlanceModifier.width(10.dp))

                            Column(modifier = GlanceModifier.defaultWeight()) {
                                Text(
                                    text = activeDoc.title,
                                    style = TextStyle(
                                        fontSize = if (isWide) 15.sp else 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VeritasWidgetColors.textPrimary
                                    ),
                                    maxLines = 1
                                )
                                Spacer(modifier = GlanceModifier.height(2.dp))
                                Text(
                                    text = if (hasGoal) {
                                        "$progressPercent% complete • $todayMinutes/${dailyGoalMinutes}m goal"
                                    } else {
                                        "$progressPercent% complete • $todayMinutes min today"
                                    },
                                    style = TextStyle(
                                        fontSize = if (isWide) 12.sp else 11.sp,
                                        color = VeritasWidgetColors.textMuted
                                    ),
                                    maxLines = 1
                                )
                                Spacer(modifier = GlanceModifier.height(5.dp))
                                LinearProgressIndicator(
                                    progress = progressVal,
                                    modifier = GlanceModifier.fillMaxWidth().height(5.dp),
                                    color = VeritasWidgetColors.primaryAccent,
                                    backgroundColor = VeritasWidgetColors.progressTrack
                                )
                            }

                            Spacer(modifier = GlanceModifier.width(10.dp))

                            Box(
                                modifier = GlanceModifier
                                    .cornerRadius(16.dp)
                                    .background(VeritasWidgetColors.playButtonAccent)
                                    .clickable(actionRunCallback<ContinueReadingActionCallback>(continueParams))
                                    .padding(horizontal = if (isCompact) 10.dp else 14.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Resume",
                                    style = TextStyle(
                                        fontSize = if (isWide) 13.sp else 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VeritasWidgetColors.playIconColor
                                    )
                                )
                            }
                        }
                    }

                    // Medium view extra information
                    if (!isCompact && !isTall) {
                        Spacer(modifier = GlanceModifier.height(8.dp))
                        if (hasGoal) {
                            Column(modifier = GlanceModifier.fillMaxWidth()) {
                                Row(
                                    modifier = GlanceModifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Daily Goal: ${dailyGoalMinutes} min",
                                        style = TextStyle(
                                            fontSize = 11.sp,
                                            color = VeritasWidgetColors.textMuted
                                        ),
                                        modifier = GlanceModifier.defaultWeight()
                                    )
                                    Text(
                                        text = "${(goalProgress * 100).toInt()}% ($todayMinutes min)",
                                        style = TextStyle(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = VeritasWidgetColors.primaryAccent
                                        )
                                    )
                                }
                                Spacer(modifier = GlanceModifier.height(3.dp))
                                LinearProgressIndicator(
                                    progress = goalProgress,
                                    modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                                    color = VeritasWidgetColors.primaryAccent,
                                    backgroundColor = VeritasWidgetColors.progressTrack
                                )
                            }
                        } else {
                            Row(
                                modifier = GlanceModifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = GlanceModifier
                                        .defaultWeight()
                                        .cornerRadius(14.dp)
                                        .background(VeritasWidgetColors.cardBackground)
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "Today: $todayMinutes min",
                                        style = TextStyle(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = VeritasWidgetColors.primaryAccent
                                        )
                                    )
                                }
                                Spacer(modifier = GlanceModifier.width(6.dp))
                                Box(
                                    modifier = GlanceModifier
                                        .defaultWeight()
                                        .cornerRadius(14.dp)
                                        .background(VeritasWidgetColors.cardBackground)
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    val weeklyAvgMin = (tracker.weeklyAverageMillis / 60000L).coerceAtLeast(0L)
                                    Text(
                                        text = "Avg: $weeklyAvgMin min/d",
                                        style = TextStyle(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = VeritasWidgetColors.secondaryAccent
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Tall view: Full 7-day Weekly Activity bar chart
                    if (isTall) {
                        Spacer(modifier = GlanceModifier.height(10.dp))

                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Weekly Activity",
                                style = TextStyle(
                                    fontSize = if (isWide) 13.sp else 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VeritasWidgetColors.textMuted
                                ),
                                modifier = GlanceModifier.defaultWeight()
                            )
                            val totalWeeklyMin = tracker.weeklyUsageByDay.sum() / 60000L
                            Text(
                                text = if (hasGoal) "Goal: ${dailyGoalMinutes}m/d" else "$totalWeeklyMin min this week",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    color = if (hasGoal) VeritasWidgetColors.primaryAccent else VeritasWidgetColors.secondaryAccent,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        Spacer(modifier = GlanceModifier.height(6.dp))

                        // 7-day bar chart container
                        Row(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .defaultWeight()
                                .cornerRadius(16.dp)
                                .background(VeritasWidgetColors.cardBackground)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            val maxVal = tracker.weeklyUsageByDay.maxOrNull()?.coerceAtLeast(1L) ?: 1L
                            val labels = listOf("M", "T", "W", "T", "F", "S", "S")

                            tracker.weeklyUsageByDay.forEachIndexed { i, value ->
                                val isToday = i == dayIdx
                                val fraction = (value.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
                                val barHeight = (fraction * 36f).toInt().coerceAtLeast(4)

                                Column(
                                    modifier = GlanceModifier.defaultWeight(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = GlanceModifier
                                            .width(16.dp)
                                            .height(36.dp)
                                            .cornerRadius(8.dp)
                                            .background(VeritasWidgetColors.cardElevated),
                                        contentAlignment = Alignment.BottomCenter
                                    ) {
                                        Box(
                                            modifier = GlanceModifier
                                                .width(16.dp)
                                                .height(barHeight.dp)
                                                .cornerRadius(8.dp)
                                                .background(
                                                    if (isToday) VeritasWidgetColors.primaryAccent else VeritasWidgetColors.amberAccent
                                                )
                                        ) {}
                                    }
                                    Spacer(modifier = GlanceModifier.height(3.dp))
                                    Text(
                                        text = labels.getOrElse(i) { "" },
                                        style = TextStyle(
                                            fontSize = if (isWide) 11.sp else 9.sp,
                                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isToday) VeritasWidgetColors.primaryAccent else VeritasWidgetColors.textMuted
                                        )
                                    )
                                }
                            }
                        }

                        // Daily goal progress bar ONLY if user configured a goal
                        if (hasGoal) {
                            Spacer(modifier = GlanceModifier.height(8.dp))
                            Row(
                                modifier = GlanceModifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Daily Goal",
                                    style = TextStyle(fontSize = 11.sp, color = VeritasWidgetColors.textMuted),
                                    modifier = GlanceModifier.defaultWeight()
                                )
                                Text(
                                    text = "$todayMinutes / ${dailyGoalMinutes} min (${(goalProgress * 100).toInt()}%)",
                                    style = TextStyle(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VeritasWidgetColors.primaryAccent
                                    )
                                )
                            }
                            Spacer(modifier = GlanceModifier.height(3.dp))
                            LinearProgressIndicator(
                                progress = goalProgress,
                                modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                                color = VeritasWidgetColors.primaryAccent,
                                backgroundColor = VeritasWidgetColors.progressTrack
                            )
                        }
                    }
                }
            }
        }
    }
}

class ShowReaderTrackerActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_WIDGET_ACTION, MainActivity.ACTION_SHOW_READER_TRACKER)
        }
        context.startActivity(intent)
    }
}

class LaunchAppActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(intent)
    }
}

class ContinueReadingActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val docId = parameters[DocIdKey] ?: return
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_WIDGET_ACTION, MainActivity.ACTION_CONTINUE_READING)
            putExtra(MainActivity.EXTRA_DOCUMENT_ID, docId)
        }
        context.startActivity(intent)
    }

    companion object {
        val DocIdKey = ActionParameters.Key<String>("doc_id")
    }
}

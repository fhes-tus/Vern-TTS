package com.veritas.reader

import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
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
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class StudyDashboardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StudyDashboardWidget()
}

class StudyDashboardWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = DocumentRepository(context)
        val tracker = repository.loadReaderTrackerSnapshot()
        val settings = repository.loadReaderSettings()
        val allCards = repository.loadAllFlashcards()
        val sets = repository.loadFlashcardSets()
        val notes = repository.loadGeneralNotes()

        val dueCards = allCards.filter { it.recall.isBlank() || it.recall == "again" || it.recall == "hard" }
        val masteredCards = allCards.filter { it.recall == "good" || it.recall == "easy" }
        val masteryPercent = if (allCards.isNotEmpty()) {
            ((masteredCards.size * 100f) / allCards.size.toFloat()).toInt()
        } else 0

        val cal = Calendar.getInstance()
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        val daysByKey = repository.loadTrackerDays()
        val todayMillis = daysByKey[todayKey]?.usageMillis ?: 0L
        val todayMinutes = (todayMillis / 60000L).coerceAtLeast(0L)

        val hasGoal = settings.dailyGoalMinutes > 0
        val dailyGoalMinutes = settings.dailyGoalMinutes.toLong()
        val goalProgress = if (hasGoal && dailyGoalMinutes > 0) {
            (todayMinutes.toFloat() / dailyGoalMinutes.toFloat()).coerceIn(0f, 1f)
        } else 0f

        provideContent {
            val size = androidx.glance.LocalSize.current
            val isCompact = size.height < 125.dp
            val isTall = size.height >= 180.dp
            val isWide = size.width >= 240.dp

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(32.dp)
                    .background(VeritasWidgetColors.widgetBackground)
                    .padding(if (isCompact) 10.dp else 12.dp)
                    .clickable(actionRunCallback<StudyDashboardClickCallback>())
            ) {
                // Header: Title + Streak badge
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Study Hub",
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

                // Compact layout: Essential info in a single clean row
                if (isCompact) {
                    Box(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .fillMaxWidth()
                            .cornerRadius(18.dp)
                            .background(VeritasWidgetColors.cardBackground)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = GlanceModifier.defaultWeight()) {
                                Text(
                                    text = if (dueCards.isNotEmpty()) "${dueCards.size} Cards Due" else "All Caught Up! 🎉",
                                    style = TextStyle(
                                        fontSize = if (isWide) 15.sp else 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (dueCards.isNotEmpty()) VeritasWidgetColors.streakAccent else VeritasWidgetColors.successAccent
                                    ),
                                    maxLines = 1
                                )
                                Spacer(modifier = GlanceModifier.height(2.dp))
                                Text(
                                    text = if (allCards.isNotEmpty()) "$masteryPercent% mastery • ${sets.size} active deck${if (sets.size == 1) "" else "s"}" else "${notes.size} study notes saved",
                                    style = TextStyle(
                                        fontSize = if (isWide) 12.sp else 10.sp,
                                        color = VeritasWidgetColors.textMuted
                                    ),
                                    maxLines = 1
                                )
                            }

                            Spacer(modifier = GlanceModifier.width(8.dp))

                            Box(
                                modifier = GlanceModifier
                                    .cornerRadius(16.dp)
                                    .background(VeritasWidgetColors.playButtonAccent)
                                    .clickable(actionRunCallback<StudyDashboardClickCallback>())
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Study",
                                    style = TextStyle(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VeritasWidgetColors.playIconColor
                                    )
                                )
                            }
                        }
                    }
                } else {
                    // Stats Row: 3 elevated colorful tiles (Due, Mastery, Decks)
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Due Cards Tile
                        Box(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .cornerRadius(14.dp)
                                .background(VeritasWidgetColors.cardBackground)
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Due",
                                    style = TextStyle(
                                        fontSize = if (isWide) 12.sp else 10.sp,
                                        color = VeritasWidgetColors.textMuted
                                    )
                                )
                                Spacer(modifier = GlanceModifier.height(2.dp))
                                Text(
                                    text = "${dueCards.size}",
                                    style = TextStyle(
                                        fontSize = if (isWide) 18.sp else 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (dueCards.isNotEmpty()) VeritasWidgetColors.streakAccent else VeritasWidgetColors.successAccent
                                    )
                                )
                            }
                        }

                        Spacer(modifier = GlanceModifier.width(8.dp))

                        // Mastery Tile
                        Box(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .cornerRadius(14.dp)
                                .background(VeritasWidgetColors.cardBackground)
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Mastery",
                                    style = TextStyle(
                                        fontSize = if (isWide) 12.sp else 10.sp,
                                        color = VeritasWidgetColors.textMuted
                                    )
                                )
                                Spacer(modifier = GlanceModifier.height(2.dp))
                                Text(
                                    text = "$masteryPercent%",
                                    style = TextStyle(
                                        fontSize = if (isWide) 18.sp else 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VeritasWidgetColors.cyanAccent
                                    )
                                )
                            }
                        }

                        Spacer(modifier = GlanceModifier.width(8.dp))

                        // Decks / Notes Tile
                        Box(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .cornerRadius(14.dp)
                                .background(VeritasWidgetColors.cardBackground)
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = if (sets.isNotEmpty()) "Decks" else "Notes",
                                    style = TextStyle(
                                        fontSize = if (isWide) 12.sp else 10.sp,
                                        color = VeritasWidgetColors.textMuted
                                    )
                                )
                                Spacer(modifier = GlanceModifier.height(2.dp))
                                Text(
                                    text = "${if (sets.isNotEmpty()) sets.size else notes.size}",
                                    style = TextStyle(
                                        fontSize = if (isWide) 18.sp else 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VeritasWidgetColors.secondaryAccent
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(8.dp))

                    // Center Focus Card: Next Card or Start Review
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .then(if (isTall) GlanceModifier.defaultWeight() else GlanceModifier)
                            .cornerRadius(18.dp)
                            .background(VeritasWidgetColors.cardBackground)
                            .padding(12.dp)
                            .clickable(actionRunCallback<StudyDashboardClickCallback>()),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = GlanceModifier.defaultWeight()) {
                                if (dueCards.isNotEmpty()) {
                                    val nextCard = dueCards.first()
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = GlanceModifier
                                                .cornerRadius(6.dp)
                                                .background(VeritasWidgetColors.cyanAccent)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "NEXT CARD",
                                                style = TextStyle(
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color.White)
                                                )
                                            )
                                        }
                                        Spacer(modifier = GlanceModifier.width(6.dp))
                                        Text(
                                            text = nextCard.setName.ifBlank { "Flashcards" },
                                            style = TextStyle(
                                                fontSize = 11.sp,
                                                color = VeritasWidgetColors.textMuted
                                            ),
                                            maxLines = 1
                                        )
                                    }
                                    Spacer(modifier = GlanceModifier.height(4.dp))
                                    Text(
                                        text = nextCard.front,
                                        style = TextStyle(
                                            fontSize = if (isWide) 14.sp else 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = VeritasWidgetColors.textPrimary
                                        ),
                                        maxLines = if (isTall) 2 else 1
                                    )
                                } else if (allCards.isNotEmpty()) {
                                    Text(
                                        text = "All Caught Up!",
                                        style = TextStyle(
                                            fontSize = if (isWide) 14.sp else 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = VeritasWidgetColors.successAccent
                                        )
                                    )
                                    Spacer(modifier = GlanceModifier.height(2.dp))
                                    Text(
                                        text = "All ${allCards.size} cards reviewed • Keep it up!",
                                        style = TextStyle(
                                            fontSize = 11.sp,
                                            color = VeritasWidgetColors.textMuted
                                        )
                                    )
                                } else {
                                    Text(
                                        text = "Study Hub Ready",
                                        style = TextStyle(
                                            fontSize = if (isWide) 14.sp else 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = VeritasWidgetColors.textPrimary
                                        )
                                    )
                                    Spacer(modifier = GlanceModifier.height(2.dp))
                                    Text(
                                        text = "Import cards from reading notes or AI prompts",
                                        style = TextStyle(
                                            fontSize = 11.sp,
                                            color = VeritasWidgetColors.textMuted
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = GlanceModifier.width(10.dp))

                            Box(
                                modifier = GlanceModifier
                                    .cornerRadius(16.dp)
                                    .background(VeritasWidgetColors.playButtonAccent)
                                    .clickable(actionRunCallback<StudyDashboardClickCallback>())
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (dueCards.isNotEmpty()) "Review" else "Open",
                                    style = TextStyle(
                                        fontSize = if (isWide) 13.sp else 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VeritasWidgetColors.playIconColor
                                    )
                                )
                            }
                        }
                    }

                    // For Tall: Active Decks List preview chips
                    if (isTall && sets.isNotEmpty()) {
                        Spacer(modifier = GlanceModifier.height(10.dp))
                        Text(
                            text = "Active Decks",
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = VeritasWidgetColors.textMuted
                            )
                        )
                        Spacer(modifier = GlanceModifier.height(6.dp))
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            sets.take(2).forEachIndexed { index, set ->
                                if (index > 0) Spacer(modifier = GlanceModifier.width(8.dp))
                                val setDue = set.cards.count { it.recall.isBlank() || it.recall == "again" || it.recall == "hard" }
                                Box(
                                    modifier = GlanceModifier
                                        .defaultWeight()
                                        .cornerRadius(14.dp)
                                        .background(VeritasWidgetColors.cardElevated)
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = set.name,
                                            style = TextStyle(
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = VeritasWidgetColors.textPrimary
                                            ),
                                            maxLines = 1
                                        )
                                        Spacer(modifier = GlanceModifier.height(2.dp))
                                        Text(
                                            text = "${set.cards.size} cards • ${if (setDue > 0) "$setDue due" else "caught up"}",
                                            style = TextStyle(
                                                fontSize = 10.sp,
                                                color = if (setDue > 0) VeritasWidgetColors.streakAccent else VeritasWidgetColors.textMuted
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Daily goal (ONLY if configured by user in app settings)
                    if (hasGoal) {
                        Spacer(modifier = GlanceModifier.height(8.dp))
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
                                    text = "${(goalProgress * 100).toInt()}%",
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

class StudyDashboardClickCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_WIDGET_ACTION, MainActivity.ACTION_SHOW_STUDY_DASHBOARD)
        }
        context.startActivity(intent)
    }
}

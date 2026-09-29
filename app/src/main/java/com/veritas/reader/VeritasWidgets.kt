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
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Suppress("RestrictedApi") // Glance's resource-backed ColorProvider is its widget color API.
object VeritasWidgetColors {
    val playerBackground = ColorProvider(R.color.widget_player_background)
    val frostedBackground = ColorProvider(R.color.widget_frosted_background)
    val border = ColorProvider(R.color.widget_border)
    val textPrimary = ColorProvider(R.color.widget_text_primary)
    val textMuted = ColorProvider(R.color.widget_text_muted)
    val cardBackground = ColorProvider(R.color.widget_card_background)
    val cardElevated = ColorProvider(R.color.widget_card_elevated)
    val widgetBackground = ColorProvider(R.color.widget_background)
    val playButtonAccent = ColorProvider(R.color.widget_play_button_accent)
    val playIconColor = ColorProvider(R.color.widget_play_icon_color)
    val primaryAccent = ColorProvider(R.color.widget_primary_accent)
    val secondaryAccent = ColorProvider(R.color.widget_secondary_accent)
    val streakAccent = ColorProvider(R.color.widget_streak_accent)
    val successAccent = ColorProvider(R.color.widget_success_accent)
    val amberAccent = ColorProvider(R.color.widget_amber_accent)
    val cyanAccent = ColorProvider(R.color.widget_cyan_accent)
    val progressTrack = ColorProvider(R.color.widget_progress_track)
}

class VeritasPlayerWidgetProvider : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VeritasPlayerWidget()
}

class VeritasPlayerWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val activeDocId = PlaybackStateStore.activeDocumentId
        val coverBitmap = if (!activeDocId.isNullOrBlank()) {
            val coverFile = CoverExtractor.coverFile(context, activeDocId)
            if (coverFile != null && coverFile.exists()) {
                runCatching { BitmapFactory.decodeFile(coverFile.absolutePath) }.getOrNull()
            } else null
        } else null

        provideContent {
            val size = androidx.glance.LocalSize.current
            val title = PlaybackStateStore.documentTitle.ifBlank { "Vern" }
            val isPlaying = PlaybackStateStore.isPlaying
            val progressPercent = if (PlaybackStateStore.chunkCount > 0) {
                ((PlaybackStateStore.currentIndex + 1) * 100) / PlaybackStateStore.chunkCount
            } else 0

            val progressText = if (PlaybackStateStore.chunkCount > 0) {
                "Sentence ${PlaybackStateStore.currentIndex + 1} of ${PlaybackStateStore.chunkCount} • $progressPercent%"
            } else {
                PlaybackStateStore.statusMessage.ifBlank { "Ready to read" }
            }

            val docFormat = when {
                title.endsWith(".pdf", ignoreCase = true) -> "PDF"
                title.endsWith(".docx", ignoreCase = true) || title.endsWith(".doc", ignoreCase = true) -> "DOCX"
                title.endsWith(".epub", ignoreCase = true) -> "EPUB"
                title.endsWith(".pptx", ignoreCase = true) || title.endsWith(".ppt", ignoreCase = true) -> "PPTX"
                title.endsWith(".txt", ignoreCase = true) -> "TXT"
                else -> "DOC"
            }

            val playParams = actionParametersOf(PlayerActionCallback.ActionKey to (if (isPlaying) PlaybackActions.ACTION_PAUSE else PlaybackActions.ACTION_PLAY))
            val prevParams = actionParametersOf(PlayerActionCallback.ActionKey to PlaybackActions.ACTION_PREVIOUS)
            val nextParams = actionParametersOf(PlayerActionCallback.ActionKey to PlaybackActions.ACTION_NEXT)

            val isCompact = size.width < 185.dp

            // Centered layout with constrained slim vertical height (54dp) matching Samsung One UI system widgets
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(76.dp)
                        .cornerRadius(38.dp)
                        .background(VeritasWidgetColors.playerBackground)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .clickable(actionRunCallback<PlayerWidgetClickCallback>()),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Document Artwork / Thumbnail (prominent 46dp)
                        if (coverBitmap != null) {
                            Image(
                                provider = ImageProvider(coverBitmap),
                                contentDescription = "Cover",
                                modifier = GlanceModifier.size(46.dp).cornerRadius(14.dp)
                            )
                        } else {
                            Box(
                                modifier = GlanceModifier
                                    .size(46.dp)
                                    .cornerRadius(14.dp)
                                    .background(ImageProvider(R.drawable.ic_widget_book_cover_gradient)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    provider = ImageProvider(R.drawable.ic_widget_library),
                                    contentDescription = "Book icon",
                                    modifier = GlanceModifier.size(24.dp),
                                    colorFilter = ColorFilter.tint(ColorProvider(Color.White))
                                )
                            }
                        }

                        Spacer(modifier = GlanceModifier.width(10.dp))

                        if (isCompact) {
                            // 2x1 Compact Capsule View
                            Column(
                                modifier = GlanceModifier.defaultWeight(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = title,
                                    style = TextStyle(
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VeritasWidgetColors.textPrimary
                                    ),
                                    maxLines = 1
                                )
                                Spacer(modifier = GlanceModifier.height(2.dp))
                                Text(
                                    text = if (isPlaying) "🔊 Playing" else "⏸ Ready",
                                    style = TextStyle(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isPlaying) VeritasWidgetColors.successAccent else VeritasWidgetColors.textMuted
                                    )
                                )
                            }

                            Spacer(modifier = GlanceModifier.width(8.dp))

                            Box(
                                modifier = GlanceModifier
                                    .size(42.dp)
                                    .cornerRadius(21.dp)
                                    .background(VeritasWidgetColors.playButtonAccent)
                                    .clickable(actionRunCallback<PlayerActionCallback>(playParams)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    provider = ImageProvider(
                                        if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
                                    ),
                                    contentDescription = "Play/Pause",
                                    modifier = GlanceModifier.size(22.dp),
                                    colorFilter = ColorFilter.tint(VeritasWidgetColors.playIconColor)
                                )
                            }
                        } else {
                            // Full 4x1 Horizontal Capsule View (large typography & prominent controls)
                            Column(
                                modifier = GlanceModifier.defaultWeight(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = GlanceModifier
                                            .cornerRadius(6.dp)
                                            .background(VeritasWidgetColors.cyanAccent)
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = docFormat,
                                            style = TextStyle(
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ColorProvider(Color.White)
                                            )
                                        )
                                    }
                                    Spacer(modifier = GlanceModifier.width(6.dp))
                                    Text(
                                        text = title,
                                        style = TextStyle(
                                            fontSize = if (size.width >= 240.dp) 15.sp else 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = VeritasWidgetColors.textPrimary
                                        ),
                                        maxLines = 1
                                    )
                                }
                                Spacer(modifier = GlanceModifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isPlaying) "● Playing" else "○ Paused",
                                        style = TextStyle(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isPlaying) VeritasWidgetColors.successAccent else VeritasWidgetColors.textMuted
                                        )
                                    )
                                    Spacer(modifier = GlanceModifier.width(6.dp))
                                    Text(
                                        text = "• $progressText",
                                        style = TextStyle(
                                            fontSize = 11.sp,
                                            color = VeritasWidgetColors.textMuted
                                        ),
                                        maxLines = 1
                                    )
                                }
                            }

                            Spacer(modifier = GlanceModifier.width(8.dp))

                            // Controls
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = GlanceModifier
                                        .size(34.dp)
                                        .cornerRadius(17.dp)
                                        .background(VeritasWidgetColors.cardBackground)
                                        .clickable(actionRunCallback<PlayerActionCallback>(prevParams)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        provider = ImageProvider(R.drawable.ic_widget_previous),
                                        contentDescription = "Previous",
                                        modifier = GlanceModifier.size(18.dp),
                                        colorFilter = ColorFilter.tint(VeritasWidgetColors.textPrimary)
                                    )
                                }

                                Spacer(modifier = GlanceModifier.width(8.dp))

                                Box(
                                    modifier = GlanceModifier
                                        .size(44.dp)
                                        .cornerRadius(22.dp)
                                        .background(VeritasWidgetColors.playButtonAccent)
                                        .clickable(actionRunCallback<PlayerActionCallback>(playParams)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        provider = ImageProvider(
                                            if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
                                        ),
                                        contentDescription = "Play/Pause",
                                        modifier = GlanceModifier.size(24.dp),
                                        colorFilter = ColorFilter.tint(VeritasWidgetColors.playIconColor)
                                    )
                                }

                                Spacer(modifier = GlanceModifier.width(8.dp))

                                Box(
                                    modifier = GlanceModifier
                                        .size(34.dp)
                                        .cornerRadius(17.dp)
                                        .background(VeritasWidgetColors.cardBackground)
                                        .clickable(actionRunCallback<PlayerActionCallback>(nextParams)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        provider = ImageProvider(R.drawable.ic_widget_next),
                                        contentDescription = "Next",
                                        modifier = GlanceModifier.size(18.dp),
                                        colorFilter = ColorFilter.tint(VeritasWidgetColors.textPrimary)
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

class PlayerWidgetClickCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val activeDocId = PlaybackStateStore.activeDocumentId
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (!activeDocId.isNullOrBlank()) {
                putExtra(MainActivity.EXTRA_WIDGET_ACTION, MainActivity.ACTION_CONTINUE_READING)
                putExtra(MainActivity.EXTRA_DOCUMENT_ID, activeDocId)
            }
        }
        context.startActivity(intent)
    }
}

class PlayerActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val action = parameters[ActionKey] ?: return
        val intent = Intent(context, PlaybackService::class.java).setAction(action)
        context.startService(intent)
        // Force widgets to refresh immediately
        updateVeritasWidgets(context)
    }

    companion object {
        val ActionKey = ActionParameters.Key<String>("playback_action")
    }
}

/**
 * Widget refreshes are fire-and-forget and outlive whatever triggered them — a receiver,
 * a service callback, a screen leaving composition. They belong to the process, so they
 * get a named process-lifetime scope rather than GlobalScope.
 */
private val widgetRefreshScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

fun updateVeritasWidgets(context: Context) {
    val appContext = context.applicationContext
    widgetRefreshScope.launch {
        runCatching { VeritasPlayerWidget().updateAll(appContext) }
        runCatching { QuickNoteWidget().updateAll(appContext) }
        runCatching { FlashcardWidget().updateAll(appContext) }
        runCatching { QuickCaptureWidget().updateAll(appContext) }
        runCatching { ReadingProgressWidget().updateAll(appContext) }
        runCatching { StudyDashboardWidget().updateAll(appContext) }
    }
}

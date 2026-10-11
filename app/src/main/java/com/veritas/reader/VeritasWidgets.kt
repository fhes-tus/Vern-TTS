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
        val state = kotlinx.coroutines.withContext(Dispatchers.IO) { DocumentRepository(context).widgetReadingState() }
        val cover = kotlinx.coroutines.withContext(Dispatchers.IO) { widgetCover(context, state.document) }
        provideContent {
            val size = androidx.glance.LocalSize.current
            val document = state.document
            val compact = size.width < 320.dp
            val short = size.height < 84.dp
            val title = document?.title ?: "Your next read"
            val subtitle = when {
                document == null -> "Open your library to get started"
                state.isPreparing -> "Preparing reading…"
                state.isPlaying -> "Playing · ${state.index + 1}/${document.chunkCount}"
                state.index > 0 -> "Paused · ${state.index + 1}/${document.chunkCount}"
                else -> "Ready to read"
            }
            val open = androidx.glance.appwidget.action.actionStartActivity(
                widgetActivityIntent(context, if (document == null) MainActivity.ACTION_OPEN_LIBRARY else MainActivity.ACTION_CONTINUE_READING, document?.id))
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth().height(if (short) 64.dp else 80.dp)
                        .cornerRadius(28.dp).background(VeritasWidgetColors.playerBackground)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!compact || size.width >= 210.dp) {
                        Image(provider = ImageProvider(cover ?: widgetCoverPlaceholder()), contentDescription = "Open $title",
                            modifier = GlanceModifier.size(if (short) 40.dp else 48.dp).cornerRadius(12.dp).clickable(open))
                        Spacer(GlanceModifier.width(10.dp))
                    }
                    Column(GlanceModifier.defaultWeight().clickable(open)) {
                        Text(title, style = TextStyle(color = VeritasWidgetColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                        Spacer(GlanceModifier.height(3.dp))
                        Text(subtitle, style = TextStyle(color = VeritasWidgetColors.textMuted, fontSize = 11.sp), maxLines = 1)
                        if (!short && document != null && !compact) {
                            Spacer(GlanceModifier.height(3.dp))
                            Text(widgetFormat(document), style = TextStyle(color = VeritasWidgetColors.primaryAccent, fontSize = 10.sp), maxLines = 1)
                        }
                    }
                    if (document != null && document.chunkCount > 0) {
                        Spacer(GlanceModifier.width(8.dp))
                        if (!compact) PlayerControl(context, state, PlaybackActions.ACTION_PREVIOUS, R.drawable.ic_widget_previous, "Previous sentence", false)
                        PlayerControl(context, state, if (state.isPlaying || state.isPreparing) PlaybackActions.ACTION_PAUSE else PlaybackActions.ACTION_PLAY,
                            if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                            if (state.isPlaying) "Pause reading" else "Resume reading", true)
                        if (!compact) PlayerControl(context, state, PlaybackActions.ACTION_NEXT, R.drawable.ic_widget_next, "Next sentence", false)
                    }
                }
            }
        }
    }
}

private fun widgetCoverPlaceholder(): android.graphics.Bitmap {
    val bitmap = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.rgb(45, 73, 108))
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; style = android.graphics.Paint.Style.STROKE; strokeWidth = 2f }
    canvas.drawRoundRect(12f, 9f, 36f, 39f, 3f, 3f, paint)
    canvas.drawLine(18f, 10f, 18f, 38f, paint)
    return bitmap
}

@androidx.compose.runtime.Composable
private fun PlayerControl(context: Context, state: WidgetReadingState, action: String, icon: Int, description: String, prominent: Boolean) {
    val params = actionParametersOf(PlayerActionCallback.ActionKey to action, PlayerActionCallback.DocumentKey to state.document!!.id)
    Box(GlanceModifier.size(48.dp).cornerRadius(24.dp)
        .background(if (prominent) VeritasWidgetColors.playButtonAccent else VeritasWidgetColors.cardBackground)
        .clickable(actionRunCallback<PlayerActionCallback>(params)), contentAlignment = Alignment.Center) {
        if (prominent && state.isPreparing && !state.reduceMotion) {
            androidx.glance.appwidget.CircularProgressIndicator(
                modifier = GlanceModifier.size(24.dp), color = VeritasWidgetColors.playIconColor)
        } else {
            Image(ImageProvider(if (prominent && state.isPreparing) R.drawable.ic_widget_loading else icon),
                if (prominent && state.isPreparing) "Preparing reading. Tap to cancel" else description, modifier = GlanceModifier.size(24.dp),
                colorFilter = ColorFilter.tint(if (prominent) VeritasWidgetColors.playIconColor else VeritasWidgetColors.textPrimary))
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
        if (action !in setOf(PlaybackActions.ACTION_PLAY, PlaybackActions.ACTION_PAUSE, PlaybackActions.ACTION_NEXT, PlaybackActions.ACTION_PREVIOUS)) return
        val state = kotlinx.coroutines.withContext(Dispatchers.IO) { DocumentRepository(context).widgetReadingState() }
        val document = state.document ?: return
        if (parameters[DocumentKey] != document.id) { updateVeritasWidgets(context); return }
        // Only Play starts a cold foreground service. Skip cannot retarget an unrelated session.
        if (action != PlaybackActions.ACTION_PLAY && PlaybackStateStore.activeDocumentId != document.id &&
            !PlaybackStateStore.isPreparingFor(document.id)) return
        runCatching { sendPlaybackIntent(context, action, document.id, state.index) }
            .onFailure { android.widget.Toast.makeText(context, "Open Vern to resume reading", android.widget.Toast.LENGTH_SHORT).show() }
        updateVeritasWidgets(context)
    }

    companion object {
        val ActionKey = ActionParameters.Key<String>("playback_action")
        val DocumentKey = ActionParameters.Key<String>("playback_document")
    }
}

/**
 * Widget refreshes are fire-and-forget and outlive whatever triggered them — a receiver,
 * a service callback, a screen leaving composition. They belong to the process, so they
 * get a named process-lifetime scope rather than GlobalScope.
 */
private val widgetRefreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private val widgetRefreshRequests = kotlinx.coroutines.channels.Channel<Context>(kotlinx.coroutines.channels.Channel.CONFLATED)
private val widgetRefreshWorker = widgetRefreshScope.launch {
    for (appContext in widgetRefreshRequests) {
        // Merge bursts of saves/progress without overlapping six expensive widget renders.
        kotlinx.coroutines.delay(200)
        val latest = widgetRefreshRequests.tryReceive().getOrNull() ?: appContext
        runCatching { VeritasPlayerWidget().updateAll(latest) }
        runCatching { QuickNoteWidget().updateAll(latest) }
        runCatching { FlashcardWidget().updateAll(latest) }
        runCatching { QuickCaptureWidget().updateAll(latest) }
        runCatching { ReadingProgressWidget().updateAll(latest) }
        runCatching { StudyDashboardWidget().updateAll(latest) }
    }
}

fun updateVeritasWidgets(context: Context) {
    widgetRefreshRequests.trySend(context.applicationContext)
}

// Coalesce sentence progress into one refresh per five-second window. Library
// edits still call updateVeritasWidgets immediately.
private val readingWidgetRefreshPending = java.util.concurrent.atomic.AtomicBoolean(false)
fun scheduleReadingWidgetRefresh(context: Context) {
    if (!readingWidgetRefreshPending.compareAndSet(false, true)) return
    val appContext = context.applicationContext
    widgetRefreshScope.launch {
        try {
            kotlinx.coroutines.delay(5_000)
            updateVeritasWidgets(appContext)
        } finally { readingWidgetRefreshPending.set(false) }
    }
}

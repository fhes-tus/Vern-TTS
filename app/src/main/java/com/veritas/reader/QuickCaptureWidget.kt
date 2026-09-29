package com.veritas.reader

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
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
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

class QuickCaptureWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickCaptureWidget()
}

class QuickCaptureWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val size = androidx.glance.LocalSize.current
            val isCompact = size.width < 180.dp

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(24.dp)
                    .background(VeritasWidgetColors.widgetBackground)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isCompact) {
                    // 2x1 Layout: Modern Google Keep style capsule bar
                    val newNoteParams = actionParametersOf(QuickCaptureActionCallback.ActionKey to MainActivity.ACTION_NEW_NOTE)
                    val checklistParams = actionParametersOf(QuickCaptureActionCallback.ActionKey to MainActivity.ACTION_NEW_CHECKLIST_NOTE)
                    val readingParams = actionParametersOf(QuickCaptureActionCallback.ActionKey to MainActivity.ACTION_ACTIVE_READING)

                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = GlanceModifier
                                .size(36.dp)
                                .cornerRadius(12.dp)
                                .background(VeritasWidgetColors.cardElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                provider = ImageProvider(R.drawable.veritas_reader_icon),
                                contentDescription = "Vern",
                                modifier = GlanceModifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = GlanceModifier.width(10.dp))
                        Text(
                            text = "Take a note...",
                            style = TextStyle(
                                fontSize = if (size.width >= 140.dp) 14.sp else 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = VeritasWidgetColors.textMuted
                            ),
                            maxLines = 1,
                            modifier = GlanceModifier.defaultWeight().clickable(actionRunCallback<QuickCaptureActionCallback>(newNoteParams))
                        )

                        Spacer(modifier = GlanceModifier.width(8.dp))

                        // + Note (Amber Action Button)
                        Box(
                            modifier = GlanceModifier
                                .size(34.dp)
                                .cornerRadius(17.dp)
                                .background(VeritasWidgetColors.playButtonAccent)
                                .clickable(actionRunCallback<QuickCaptureActionCallback>(newNoteParams)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                provider = ImageProvider(R.drawable.ic_widget_notes),
                                contentDescription = "New Note",
                                modifier = GlanceModifier.size(18.dp),
                                colorFilter = ColorFilter.tint(VeritasWidgetColors.playIconColor)
                            )
                        }

                        Spacer(modifier = GlanceModifier.width(6.dp))

                        // Checklist button
                        Box(
                            modifier = GlanceModifier
                                .size(34.dp)
                                .cornerRadius(17.dp)
                                .background(VeritasWidgetColors.cardBackground)
                                .clickable(actionRunCallback<QuickCaptureActionCallback>(checklistParams)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                provider = ImageProvider(R.drawable.ic_widget_checklist),
                                contentDescription = "New Checklist",
                                modifier = GlanceModifier.size(18.dp),
                                colorFilter = ColorFilter.tint(VeritasWidgetColors.textPrimary)
                            )
                        }

                        Spacer(modifier = GlanceModifier.width(6.dp))

                        // Reading / Book button
                        Box(
                            modifier = GlanceModifier
                                .size(34.dp)
                                .cornerRadius(17.dp)
                                .background(VeritasWidgetColors.cardBackground)
                                .clickable(actionRunCallback<QuickCaptureActionCallback>(readingParams)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                provider = ImageProvider(R.drawable.ic_widget_reading),
                                contentDescription = "Resume Reading",
                                modifier = GlanceModifier.size(18.dp),
                                colorFilter = ColorFilter.tint(VeritasWidgetColors.textPrimary)
                            )
                        }
                    }
                } else {
                    // >= 2x2 Grid Visual Layout: Clean Material 3 Quick Actions
                    Column(
                        modifier = GlanceModifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Header
                        Row(
                            modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Quick Capture",
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VeritasWidgetColors.textPrimary
                                ),
                                modifier = GlanceModifier.defaultWeight()
                            )
                        }

                        Spacer(modifier = GlanceModifier.height(8.dp))

                        Row(
                            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ActionCard(
                                iconRes = R.drawable.ic_widget_notes,
                                label = "Text Note",
                                action = MainActivity.ACTION_NEW_NOTE,
                                modifier = GlanceModifier.defaultWeight()
                            )
                            Spacer(modifier = GlanceModifier.width(8.dp))
                            ActionCard(
                                iconRes = R.drawable.ic_widget_checklist,
                                label = "Checklist",
                                action = MainActivity.ACTION_NEW_CHECKLIST_NOTE,
                                modifier = GlanceModifier.defaultWeight()
                            )
                        }
                        Spacer(modifier = GlanceModifier.height(8.dp))
                        Row(
                            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ActionCard(
                                iconRes = R.drawable.ic_widget_reading,
                                label = "Read Now",
                                action = MainActivity.ACTION_ACTIVE_READING,
                                modifier = GlanceModifier.defaultWeight()
                            )
                            Spacer(modifier = GlanceModifier.width(8.dp))
                            ActionCard(
                                iconRes = R.drawable.ic_widget_library,
                                label = "Library",
                                action = MainActivity.ACTION_OPEN_LIBRARY,
                                modifier = GlanceModifier.defaultWeight()
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ActionCard(
        iconRes: Int,
        label: String,
        action: String,
        modifier: GlanceModifier
    ) {
        val params = actionParametersOf(QuickCaptureActionCallback.ActionKey to action)
        Box(
            modifier = modifier
                .fillMaxHeight()
                .cornerRadius(14.dp)
                .background(VeritasWidgetColors.cardBackground)
                .clickable(actionRunCallback<QuickCaptureActionCallback>(params)),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = GlanceModifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(28.dp)
                        .cornerRadius(8.dp)
                        .background(VeritasWidgetColors.cardElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(iconRes),
                        contentDescription = label,
                        modifier = GlanceModifier.size(18.dp),
                        colorFilter = ColorFilter.tint(VeritasWidgetColors.primaryAccent)
                    )
                }
                Spacer(modifier = GlanceModifier.width(6.dp))
                val currentSize = androidx.glance.LocalSize.current
                val fontSize = if (currentSize.width >= 240.dp) 12.sp else 10.sp
                Text(
                    text = label,
                    style = TextStyle(
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        color = VeritasWidgetColors.textPrimary
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

class LaunchWidgetMenuActivityCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = Intent(context, WidgetMenuActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
    }
}

class QuickCaptureActionCallback : ActionCallback {
    companion object {
        val ActionKey = ActionParameters.Key<String>("widget_action")
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val action = parameters[ActionKey] ?: return
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_WIDGET_ACTION, action)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
    }
}

package com.veritas.reader

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
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

class QuickNoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickNoteWidget()
}

class QuickNoteWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = DocumentRepository(context)
        val notes = repository.loadGeneralNotes().take(10)

        provideContent {
            val size = androidx.glance.LocalSize.current
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(24.dp)
                    .background(VeritasWidgetColors.widgetBackground)
                    .padding(12.dp)
            ) {
                    // Header
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Notes & Tasks",
                            style = TextStyle(
                                fontSize = if (size.width >= 240.dp) 16.sp else 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = VeritasWidgetColors.textPrimary
                            ),
                            modifier = GlanceModifier.defaultWeight()
                        )
                        
                        Box(
                            modifier = GlanceModifier
                                .cornerRadius(12.dp)
                                .background(VeritasWidgetColors.playButtonAccent)
                                .clickable(actionRunCallback<NewNoteActionCallback>())
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+ New",
                                style = TextStyle(
                                    fontSize = if (size.width >= 240.dp) 12.sp else 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VeritasWidgetColors.playIconColor
                                )
                            )
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(8.dp))

                    if (notes.isEmpty()) {
                        Box(
                            modifier = GlanceModifier.fillMaxSize().clickable(actionRunCallback<LaunchAppActionCallback>()),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No notes yet • Tap to create",
                                style = TextStyle(
                                    fontSize = if (size.width >= 240.dp) 14.sp else 12.sp,
                                    color = VeritasWidgetColors.textMuted
                                )
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = GlanceModifier.fillMaxSize()
                        ) {
                            items(notes) { note ->
                                val title = note.title.ifBlank { "Untitled Note" }
                                val preview = note.content.ifBlank { "(Empty note)" }
                                
                                val clickParams = actionParametersOf(NoteClickCallback.NoteIdKey to note.id)
                                val pinParams = actionParametersOf(TogglePinActionCallback.NoteIdKey to note.id)
                                
                                Box(
                                    modifier = GlanceModifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                ) {
                                    Row(
                                        modifier = GlanceModifier
                                            .fillMaxWidth()
                                            .cornerRadius(14.dp)
                                            .background(VeritasWidgetColors.cardBackground)
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = GlanceModifier
                                                .defaultWeight()
                                                .clickable(actionRunCallback<NoteClickCallback>(clickParams))
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (note.isChecklist) {
                                                    Text(
                                                        text = "☑ ",
                                                        style = TextStyle(
                                                            fontSize = 11.sp,
                                                            color = VeritasWidgetColors.primaryAccent
                                                        )
                                                    )
                                                }
                                                Text(
                                                    text = title,
                                                    style = TextStyle(
                                                        fontSize = if (size.width >= 240.dp) 14.sp else 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = VeritasWidgetColors.textPrimary
                                                    ),
                                                    maxLines = 1
                                                )
                                            }
                                            Spacer(modifier = GlanceModifier.height(2.dp))
                                            Text(
                                                text = preview,
                                                style = TextStyle(
                                                    fontSize = if (size.width >= 240.dp) 12.sp else 10.sp,
                                                    color = VeritasWidgetColors.textMuted
                                                ),
                                                maxLines = 1
                                            )
                                        }
                                        
                                        Spacer(modifier = GlanceModifier.width(6.dp))
                                        
                                        Box(
                                            modifier = GlanceModifier
                                                .size(28.dp)
                                                .cornerRadius(14.dp)
                                                .background(
                                                    if (note.pinned) VeritasWidgetColors.cardElevated else ColorProvider(Color.Transparent)
                                                )
                                                .clickable(actionRunCallback<TogglePinActionCallback>(pinParams)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (note.pinned) "📌" else "·",
                                                style = TextStyle(
                                                    fontSize = if (note.pinned) 12.sp else 18.sp,
                                                    color = VeritasWidgetColors.textMuted
                                                )
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
}

class NoteClickCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val noteId = parameters[NoteIdKey] ?: return
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_WIDGET_ACTION, MainActivity.ACTION_EDIT_NOTE)
            putExtra(MainActivity.EXTRA_NOTE_ID, noteId)
        }
        context.startActivity(intent)
    }

    companion object {
        val NoteIdKey = ActionParameters.Key<String>("note_id")
    }
}

class NewNoteActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_WIDGET_ACTION, MainActivity.ACTION_NEW_NOTE)
        }
        context.startActivity(intent)
    }
}

class TogglePinActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val noteId = parameters[NoteIdKey] ?: return
        val repository = DocumentRepository(context)
        val notes = repository.loadGeneralNotes()
        val note = notes.firstOrNull { it.id == noteId } ?: return
        
        val updatedNote = note.copy(pinned = !note.pinned)
        val updatedNotes = notes.map { if (it.id == noteId) updatedNote else it }
        repository.saveGeneralNotes(updatedNotes)
        
        updateVeritasWidgets(context)
    }

    companion object {
        val NoteIdKey = ActionParameters.Key<String>("note_id")
    }
}

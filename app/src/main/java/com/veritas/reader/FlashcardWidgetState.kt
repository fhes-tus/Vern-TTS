package com.veritas.reader

import android.content.Context
import android.content.SharedPreferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import java.util.UUID

/** UUID-backed state keeps two placed widgets independent and preserves legacy choices. */
internal fun flashcardWidgetPreferences(context: Context, id: GlanceId): SharedPreferences = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
    val registry = context.getSharedPreferences("veritas_flashcard_widget_instances", Context.MODE_PRIVATE)
    val key = "instance_$widgetId"
    val existing = registry.getString(key, null)
    val uuid = existing ?: UUID.randomUUID().toString().also { check(registry.edit().putString(key, it).commit()) }
    context.getSharedPreferences("flashcard_widget_$uuid", Context.MODE_PRIVATE).also { prefs ->
        if (existing == null) {
            val legacy = context.getSharedPreferences("veritas_flashcard_widget", Context.MODE_PRIVATE)
            prefs.edit().putString("selected_deck_id", legacy.getString("selected_deck_id", ""))
                .putInt("card_index", 0).putBoolean("show_answer", false).commit()
        }
    }
}

internal fun flashcardWidgetDeck(repository: DocumentRepository, prefs: SharedPreferences): List<FlashcardProgress> {
    val selected = prefs.getString("selected_deck_id", "").orEmpty()
    val cards = repository.loadAllFlashcards().filter { selected == "__ALL__" || it.setId == selected }
    return if (prefs.getBoolean("practice_mode", false)) cards else cards.filter { it.isDue() }
}

internal fun removeFlashcardWidgetState(context: Context, ids: IntArray) = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    val registry = context.getSharedPreferences("veritas_flashcard_widget_instances", Context.MODE_PRIVATE)
    ids.forEach { id ->
        registry.getString("instance_$id", null)?.let { context.deleteSharedPreferences("flashcard_widget_$it") }
        registry.edit().remove("instance_$id").commit()
    }
}

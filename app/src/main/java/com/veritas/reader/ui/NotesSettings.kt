package com.veritas.reader.ui

import android.content.Context
import org.json.JSONObject
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class NotesPaperTemplate(val label: String, val iconName: String) {
    BLANK("Blank", "crop_square"),
    RULED("Ruled Lines", "reorder"),
    GRID("Grid", "grid_on"),
    DOTS("Dot Matrix", "grain")
}

enum class QuoteCitationStyle(val label: String) {
    MARKDOWN("Blockquote (> \"Quote\")"),
    CLEAN("Clean Text"),
    ACADEMIC("Formal Citation")
}

data class NotesSettings(
    // Writing
    val paperTemplate: NotesPaperTemplate = NotesPaperTemplate.BLANK,
    val editorFontScale: Float = 1.0f,
    val editorLineSpacing: Float = 1f,
    val newNoteIsChecklist: Boolean = false,
    val previewLines: Int = 5,

    // Checklist behavior; legacy fields remain readable.
    val moveCheckedToBottom: Boolean = true,
    val addNewItemsToTop: Boolean = false,
    val showRichLinkPreviews: Boolean = true,

    // Reading Linkage & Audio (Veritas)
    val showBookSourceBadges: Boolean = true,
    val voiceMemoWaveformPreview: Boolean = true,
    val quoteCitationFormat: QuoteCitationStyle = QuoteCitationStyle.MARKDOWN,

    // Attachments & media
    val showAttachmentBadges: Boolean = true,
    val richAttachmentPreviews: Boolean = true,

    // Layout & Sort
    val isGridView: Boolean = true,
    val defaultSortOrder: String = "date" // "date", "created", "title"
 ) {
    fun normalized() = copy(
        editorFontScale = editorFontScale.takeIf { it.isFinite() }?.coerceIn(0.85f, 1.25f) ?: 1f,
        editorLineSpacing = editorLineSpacing.takeIf { it.isFinite() }?.coerceIn(1f, 1.5f) ?: 1f,
        previewLines = previewLines.coerceIn(0, 12),
        defaultSortOrder = defaultSortOrder.takeIf { it in listOf("date", "created", "title") } ?: "date"
    )
    fun toJson(): JSONObject = JSONObject().put("version", 1)
        .put("paperTemplate", paperTemplate.name).put("editorFontScale", editorFontScale)
        .put("editorLineSpacing", editorLineSpacing).put("newNoteIsChecklist", newNoteIsChecklist)
        .put("previewLines", previewLines).put("moveCheckedToBottom", moveCheckedToBottom)
        .put("addNewItemsToTop", addNewItemsToTop).put("showBookSourceBadges", showBookSourceBadges)
        .put("voiceMemoWaveformPreview", voiceMemoWaveformPreview).put("showAttachmentBadges", showAttachmentBadges)
        .put("richAttachmentPreviews", richAttachmentPreviews).put("isGridView", isGridView)
        .put("defaultSortOrder", defaultSortOrder)
        .put("showRichLinkPreviews", showRichLinkPreviews).put("quoteCitationFormat", quoteCitationFormat.name)
    companion object {
        fun fromJson(json: JSONObject) = NotesSettings(
            paperTemplate = runCatching { NotesPaperTemplate.valueOf(json.optString("paperTemplate")) }.getOrDefault(NotesPaperTemplate.BLANK),
            editorFontScale = json.optDouble("editorFontScale", 1.0).toFloat(),
            editorLineSpacing = json.optDouble("editorLineSpacing", 1.0).toFloat(),
            newNoteIsChecklist = json.optBoolean("newNoteIsChecklist", false),
            previewLines = json.optInt("previewLines", 5),
            moveCheckedToBottom = json.optBoolean("moveCheckedToBottom", true),
            addNewItemsToTop = json.optBoolean("addNewItemsToTop", false),
            showBookSourceBadges = json.optBoolean("showBookSourceBadges", true),
            voiceMemoWaveformPreview = json.optBoolean("voiceMemoWaveformPreview", true),
            showAttachmentBadges = json.optBoolean("showAttachmentBadges", true),
            richAttachmentPreviews = json.optBoolean("richAttachmentPreviews", true),
            isGridView = json.optBoolean("isGridView", true),
            defaultSortOrder = json.optString("defaultSortOrder", "date"),
            showRichLinkPreviews = json.optBoolean("showRichLinkPreviews", true),
            quoteCitationFormat = runCatching { QuoteCitationStyle.valueOf(json.optString("quoteCitationFormat")) }.getOrDefault(QuoteCitationStyle.MARKDOWN)
        ).normalized()
    }
}

object NotesSettingsStore {
    const val PREFS_NAME = "veritas_notes_settings"
    const val LEGACY_PREFS_NAME = "veritas_library_settings"

    const val KEY_PAPER_TEMPLATE = "paper_template"
    const val KEY_EDITOR_FONT_SCALE = "editor_font_scale"
    const val KEY_PREVIEW_LINES = "preview_lines"
    const val KEY_MOVE_CHECKED_TO_BOTTOM = "move_checked_to_bottom"
    const val KEY_ADD_NEW_ITEMS_TO_TOP = "add_new_items_to_top"
    const val KEY_SHOW_RICH_LINK_PREVIEWS = "show_rich_link_previews"
    const val KEY_SHOW_BOOK_SOURCE_BADGES = "show_book_source_badges"
    const val KEY_VOICE_MEMO_WAVEFORM_PREVIEW = "voice_memo_waveform_preview"
    const val KEY_QUOTE_CITATION_FORMAT = "quote_citation_format"
    const val KEY_SHOW_ATTACHMENT_BADGES = "show_attachment_badges"
    const val KEY_RICH_ATTACHMENT_PREVIEWS = "rich_attachment_previews"
    const val KEY_IS_GRID_VIEW = "is_notes_grid_view"
    const val KEY_DEFAULT_SORT_ORDER = "default_sort_order"

    const val SNAPSHOT_KEY = "notes_settings_v1"
    // Use the repository preference file so backup restore and its recovery journal
    // publish or roll back Notes preferences together with the rest of the app.
    fun load(context: Context): NotesSettings {
        val prefs = context.getSharedPreferences("veritas_reader_library", Context.MODE_PRIVATE)
        val raw = prefs.getString(SNAPSHOT_KEY, null)
        if (raw != null) return runCatching { NotesSettings.fromJson(JSONObject(raw)) }.getOrDefault(NotesSettings())
        return loadLegacy(context).normalized().also { save(context, it) }
    }

    private fun loadLegacy(context: Context): NotesSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)

        // Migrate is_notes_grid_view from legacy library settings if not present
        val isGrid = if (prefs.contains(KEY_IS_GRID_VIEW)) {
            prefs.getBoolean(KEY_IS_GRID_VIEW, true)
        } else if (legacyPrefs.contains(KEY_IS_GRID_VIEW)) {
            val migrated = legacyPrefs.getBoolean(KEY_IS_GRID_VIEW, true)
            prefs.edit().putBoolean(KEY_IS_GRID_VIEW, migrated).apply()
            migrated
        } else {
            true
        }

        val templateName = prefs.getString(KEY_PAPER_TEMPLATE, NotesPaperTemplate.BLANK.name)
        val paperTemplate = runCatching { NotesPaperTemplate.valueOf(templateName ?: "") }.getOrDefault(NotesPaperTemplate.BLANK)

        val citationName = prefs.getString(KEY_QUOTE_CITATION_FORMAT, QuoteCitationStyle.MARKDOWN.name)
        val citationFormat = runCatching { QuoteCitationStyle.valueOf(citationName ?: "") }.getOrDefault(QuoteCitationStyle.MARKDOWN)

        return NotesSettings(
            paperTemplate = paperTemplate,
            editorFontScale = prefs.getFloat(KEY_EDITOR_FONT_SCALE, 1.0f).coerceIn(0.85f, 1.25f),
            previewLines = prefs.getInt(KEY_PREVIEW_LINES, 5),
            moveCheckedToBottom = prefs.getBoolean(KEY_MOVE_CHECKED_TO_BOTTOM, true),
            addNewItemsToTop = prefs.getBoolean(KEY_ADD_NEW_ITEMS_TO_TOP, false),
            showRichLinkPreviews = prefs.getBoolean(KEY_SHOW_RICH_LINK_PREVIEWS, true),
            showBookSourceBadges = prefs.getBoolean(KEY_SHOW_BOOK_SOURCE_BADGES, true),
            voiceMemoWaveformPreview = prefs.getBoolean(KEY_VOICE_MEMO_WAVEFORM_PREVIEW, true),
            quoteCitationFormat = citationFormat,
            showAttachmentBadges = prefs.getBoolean(KEY_SHOW_ATTACHMENT_BADGES, true),
            richAttachmentPreviews = prefs.getBoolean(KEY_RICH_ATTACHMENT_PREVIEWS, true),
            isGridView = isGrid,
            defaultSortOrder = prefs.getString(KEY_DEFAULT_SORT_ORDER, "date") ?: "date"
        )
    }

    fun save(context: Context, settings: NotesSettings) = synchronized(com.veritas.reader.DocumentRepository.LIBRARY_WRITE_LOCK) {
        check(context.getSharedPreferences("veritas_reader_library", Context.MODE_PRIVATE).edit()
            .putString(SNAPSHOT_KEY, settings.normalized().toJson().toString()).commit()) {
            "Could not save Notes preferences."
        }
    }

}

/**
 * Modifier extension to draw textured background patterns (Ruled lines, Grid, Dot matrix)
 * for notes canvas and preview cards.
 */
fun Modifier.notesPaperBackground(
    template: NotesPaperTemplate,
    lineColor: Color
): Modifier = this.then(
    Modifier.drawBehind {
        when (template) {
            NotesPaperTemplate.BLANK -> { /* clean slate */ }
            NotesPaperTemplate.RULED -> {
                val step = 32.dp.toPx()
                var y = step
                while (y < size.height) {
                    drawLine(
                        color = lineColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                    y += step
                }
            }
            NotesPaperTemplate.GRID -> {
                val step = 24.dp.toPx()
                var x = step
                while (x < size.width) {
                    drawLine(
                        color = lineColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                    x += step
                }
                var y = step
                while (y < size.height) {
                    drawLine(
                        color = lineColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                    y += step
                }
            }
            NotesPaperTemplate.DOTS -> {
                val step = 24.dp.toPx()
                val radius = 1.2.dp.toPx()
                var x = step
                while (x < size.width) {
                    var y = step
                    while (y < size.height) {
                        drawCircle(
                            color = lineColor,
                            radius = radius,
                            center = Offset(x, y)
                        )
                        y += step
                    }
                    x += step
                }
            }
        }
    }
)

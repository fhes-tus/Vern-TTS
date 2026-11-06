package com.veritas.reader.ui.screens

import androidx.compose.runtime.Composable
import com.veritas.reader.R

/** Display labels for app-owned enums only; identifiers and serialized values remain unchanged. */
@Composable
internal fun localizedCatalogLabel(label: String): String {
    val resource = when (label) {
        "current sentence" -> R.string.l10n_catalog_0
        "current page" -> R.string.l10n_catalog_1
        "current section" -> R.string.l10n_catalog_2
        "whole document" -> R.string.l10n_catalog_3
        "custom page range" -> R.string.ui_custom_page_range
        "Summary" -> R.string.l10n_catalog_4
        "Key points" -> R.string.l10n_catalog_5
        "Explain current section" -> R.string.l10n_catalog_6
        "Study notes" -> R.string.l10n_catalog_7
        "Simplify" -> R.string.l10n_catalog_8
        "Section-by-section" -> R.string.l10n_catalog_9
        "Quiz" -> R.string.l10n_catalog_10
        "Flashcards" -> R.string.ui_flashcards
        "Custom prompt" -> R.string.l10n_catalog_11
        "Translate full document" -> R.string.l10n_translate_full_document
        "Translate current section" -> R.string.l10n_catalog_12
        "Bilingual full document" -> R.string.l10n_bilingual_full_document
        "Bilingual current section" -> R.string.l10n_catalog_13
        "ALL" -> R.string.ui_all
        "All" -> R.string.ui_all
        "All Notes" -> R.string.filter_all_notes
        "Pinned" -> R.string.ui_pinned
        "Voice Memos" -> R.string.filter_voice_memos
        "Checklists" -> R.string.settings_checklists
        "Reminders" -> R.string.filter_reminders
        "Highlights" -> R.string.ui_highlights
        "In progress" -> R.string.l10n_in_progress
        "Unread" -> R.string.l10n_unread
        "Completed" -> R.string.ui_completed
        "Favorites" -> R.string.ui_favorites
        "Queue" -> R.string.ui_queue
        "Overview" -> R.string.filter_overview
        "Bookmarks" -> R.string.ui_bookmarks
        "Booknotes" -> R.string.ui_booknotes
        "Vocab" -> R.string.ui_vocabulary
        "Quizzes" -> R.string.ui_quizzes
        "History" -> R.string.filter_history
        "Last edited" -> R.string.settings_last_edited
        "Newest" -> R.string.settings_newest
        "Updated" -> R.string.filter_updated
        "Title" -> R.string.ui_title
        "Author" -> R.string.ui_author
        "Progress" -> R.string.ui_progress
        "Type" -> R.string.filter_type
        "Featured" -> R.string.filter_featured
        "Shortest" -> R.string.filter_shortest
        "Family & Youth" -> R.string.filter_family_youth
        "Mystery" -> R.string.filter_mystery
        "Adventure" -> R.string.filter_adventure
        "Romance & Drama" -> R.string.filter_romance_drama
        "Life & Habits" -> R.string.filter_life_habits
        "Quick Reads" -> R.string.filter_quick_reads
        "Epic Classics" -> R.string.filter_epic_classics
        "Faith & Spirit" -> R.string.filter_faith_spirit
        "EPUB" -> R.string.ui_epub
        "PDF" -> R.string.ui_pdf
        "DOCX" -> R.string.ui_docx
        "PPTX" -> R.string.l10n_catalog_14
        "WEB" -> R.string.l10n_catalog_15
        "TXT" -> R.string.l10n_catalog_16
        "OCR" -> R.string.l10n_catalog_17
        "File name" -> R.string.l10n_catalog_18
        "Date/time" -> R.string.l10n_catalog_19
        "Size" -> R.string.l10n_catalog_20
        "Path" -> R.string.l10n_catalog_21
        "Pause playback" -> R.string.l10n_catalog_22
        "Stop playback" -> R.string.l10n_catalog_23
        "Medium-sized icons" -> R.string.l10n_catalog_24
        "Small icons" -> R.string.l10n_catalog_25
        "List" -> R.string.settings_list
        "Details" -> R.string.ui_details
        "Tiles" -> R.string.l10n_catalog_26
        "Choose each time" -> R.string.l10n_catalog_27
        "Choose when I share" -> R.string.l10n_catalog_28
        else -> return localizedSettingsOption(label)
    }
    return menuString(resource)
}

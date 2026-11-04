package com.veritas.reader.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
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
    return stringResource(resource)
}

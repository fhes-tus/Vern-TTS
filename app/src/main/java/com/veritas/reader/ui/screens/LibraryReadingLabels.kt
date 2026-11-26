package com.veritas.reader.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import com.veritas.reader.R
import com.veritas.reader.SavedDocument
import com.veritas.reader.estimatedReadMinutes
import com.veritas.reader.estimatedRemainingReadMinutes
import com.veritas.reader.progressPercent

@Composable
internal fun localizedLibraryReadingSummary(document: SavedDocument): String {
    val percent = progressPercent(document)
    return when {
        percent >= 100 -> menuString(R.string.ui_finished)
        document.currentIndex <= 0 || percent <= 0 -> menuString(
            R.string.library_read_not_started,
            localizedReadTimeEstimate(estimatedReadMinutes(document))
        )
        else -> menuString(
            R.string.library_read_in_progress,
            localizedUiPercent(percent),
            localizedReadTimeEstimate(estimatedRemainingReadMinutes(document))
        )
    }
}

@Composable
internal fun localizedReadTimeEstimate(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0 -> menuString(R.string.library_read_estimate_minutes, minutes)
        minutes == 0 -> menuString(R.string.library_read_estimate_hours, hours)
        else -> menuString(R.string.library_read_estimate_hours_minutes, hours, minutes)
    }
}

@Composable
internal fun localizedReadingLength(document: SavedDocument): String =
    if (document.pageCount > 0) {
        pluralStringResource(R.plurals.library_page_count, document.pageCount, document.pageCount)
    } else {
        pluralStringResource(R.plurals.library_sentence_count, document.chunkCount, document.chunkCount)
    }

package com.veritas.reader.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.veritas.reader.NoteAudioDurationReader
import java.util.Locale

private val noteAudioDurations = NoteAudioDurationReader()

@Composable
internal fun rememberNoteAudioDurationMillis(
    path: String?,
    knownMillis: Long = 0,
    revision: Long = 0,
    reader: NoteAudioDurationReader = noteAudioDurations
): Long? {
    // A new state cell also prevents a previous card's value flashing before its effect restarts.
    var duration by remember(path, knownMillis, revision, reader) {
        mutableStateOf(knownMillis.takeIf { it > 0 })
    }
    LaunchedEffect(path, knownMillis, revision, reader) {
        if (knownMillis <= 0 && !path.isNullOrBlank()) duration = reader.read(path, revision)
    }
    return duration
}

internal fun formatNoteAudioDuration(millis: Long, padMinutes: Boolean = false): String {
    val seconds = millis.coerceAtLeast(0) / 1000
    return String.format(Locale.US, if (padMinutes) "%02d:%02d" else "%d:%02d", seconds / 60, seconds % 60)
}

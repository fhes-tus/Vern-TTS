package com.veritas.reader.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.viewModelScope
import com.veritas.reader.PlaybackActions
import com.veritas.reader.PlaybackService
import com.veritas.reader.PlaybackStateStore
import com.veritas.reader.VeritasReadingListSortMode
import com.veritas.reader.VeritasSleepTimerRequest
import com.veritas.reader.addDocumentToReadingList
import com.veritas.reader.archiveReadingList
import com.veritas.reader.createReadingList
import com.veritas.reader.deleteReadingList
import com.veritas.reader.moveReadingListDocument
import com.veritas.reader.removeDocumentFromReadingList
import com.veritas.reader.sendPlaybackIntent
import com.veritas.reader.setReadingListSortMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun ReaderViewModel.startSleepTimerTicker() {
    sleepTimerJob?.cancel()
    sleepTimerJob = viewModelScope.launch {
        while (true) {
            delay(1000L)
            val snapshot = PlaybackStateStore.activeSleepTimerSnapshot()
            if (snapshot == null) break
            if (!snapshot.stopAtEndOfSection) {
                val remaining = snapshot.remainingMillis()
                if (remaining <= 0L) {
                    PlaybackStateStore.clearSleepTimer()
                    repository.clearSleepTimerState()
                    sendPlaybackIntent(getApplication(), PlaybackActions.ACTION_PAUSE)
                    _uiState.update { it.copy(importMessage = "Sleep timer finished. Playback paused.") }
                    break
                }
            }
        }
    }
}

fun ReaderViewModel.setSleepTimer(request: VeritasSleepTimerRequest) {
    val durationMillis = request.durationMillis
    val action = request.action
    val stopAtEndOfSection = request.stopAtEndOfSection
    PlaybackStateStore.setSleepTimer(request, System.currentTimeMillis())
    repository.saveSleepTimerState(
        durationMillis = PlaybackStateStore.sleepTimerDurationMillis,
        endsAtMillis = PlaybackStateStore.sleepTimerEndsAtMillis,
        actionName = PlaybackStateStore.sleepTimerActionName,
        stopAtEndOfSection = PlaybackStateStore.sleepTimerStopAtEndOfSection
    )
    startSleepTimerTicker()
    val intent = Intent(getApplication(), PlaybackService::class.java)
        .setAction(PlaybackActions.ACTION_SET_SLEEP_TIMER)
        .putExtra(PlaybackActions.EXTRA_SLEEP_TIMER_DURATION_MILLIS, durationMillis)
        .putExtra(PlaybackActions.EXTRA_SLEEP_TIMER_ACTION, action.name)
        .putExtra(PlaybackActions.EXTRA_SLEEP_TIMER_STOP_AT_END_OF_SECTION, stopAtEndOfSection)
    getApplication<Application>().startService(intent)
}

fun ReaderViewModel.cancelSleepTimer() {
    sleepTimerJob?.cancel()
    sleepTimerJob = null
    PlaybackStateStore.clearSleepTimer()
    repository.clearSleepTimerState()
    val intent = Intent(getApplication(), PlaybackService::class.java)
        .setAction(PlaybackActions.ACTION_CANCEL_SLEEP_TIMER)
    getApplication<Application>().startService(intent)
}

fun ReaderViewModel.createReadingList(title: String, documentId: String? = null) {
    val cleanTitle = title.trim().ifBlank { "New List" }
    viewModelScope.launch(Dispatchers.IO) {
        var catalog = repository.createReadingList(cleanTitle)
        if (documentId != null) {
            val newList = catalog.activeLists.firstOrNull { it.title == cleanTitle }
                ?: catalog.lists.maxByOrNull { it.createdAt }
            if (newList != null) {
                catalog = repository.addDocumentToReadingList(newList.id, documentId)
            }
        }
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(readingListCatalog = catalog) }
        }
    }
}

fun ReaderViewModel.addDocumentToReadingList(listId: String, documentId: String) {
    viewModelScope.launch(Dispatchers.IO) {
        val catalog = repository.addDocumentToReadingList(listId, documentId)
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(readingListCatalog = catalog) }
        }
    }
}

fun ReaderViewModel.removeDocumentFromReadingList(listId: String, documentId: String) {
    viewModelScope.launch(Dispatchers.IO) {
        val catalog = repository.removeDocumentFromReadingList(listId, documentId)
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(readingListCatalog = catalog) }
        }
    }
}

fun ReaderViewModel.moveReadingListDocument(listId: String, documentId: String, offset: Int) {
    viewModelScope.launch(Dispatchers.IO) {
        val catalog = repository.moveReadingListDocument(listId, documentId, offset)
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(readingListCatalog = catalog) }
        }
    }
}

fun ReaderViewModel.setReadingListSortMode(listId: String, sortMode: VeritasReadingListSortMode) {
    viewModelScope.launch(Dispatchers.IO) {
        val catalog = repository.setReadingListSortMode(listId, sortMode)
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(readingListCatalog = catalog) }
        }
    }
}

fun ReaderViewModel.archiveReadingList(listId: String) {
    viewModelScope.launch(Dispatchers.IO) {
        val catalog = repository.archiveReadingList(listId)
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(readingListCatalog = catalog) }
        }
    }
}

fun ReaderViewModel.deleteReadingList(listId: String) {
    viewModelScope.launch(Dispatchers.IO) {
        val catalog = repository.deleteReadingList(listId)
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(readingListCatalog = catalog) }
        }
    }
}


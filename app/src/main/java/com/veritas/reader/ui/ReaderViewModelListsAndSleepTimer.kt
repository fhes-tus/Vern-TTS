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
import com.veritas.reader.DocumentRepository
import com.veritas.reader.VeritasReadingListCatalog
import com.veritas.reader.queueFromCollection
import com.veritas.reader.renameReadingList
import com.veritas.reader.transferCollectionDocument
import com.veritas.reader.archiveReadingList
import com.veritas.reader.createReadingList
import com.veritas.reader.deleteReadingList
import com.veritas.reader.loadReadingListCatalog
import com.veritas.reader.moveReadingListDocument
import com.veritas.reader.removeDocumentFromReadingList
import com.veritas.reader.setReadingListSortMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val intent = Intent(getApplication(), PlaybackService::class.java)
        .setAction(PlaybackActions.ACTION_SET_SLEEP_TIMER)
        .putExtra(PlaybackActions.EXTRA_SLEEP_TIMER_DURATION_MILLIS, durationMillis)
        .putExtra(PlaybackActions.EXTRA_SLEEP_TIMER_ACTION, action.name)
        .putExtra(PlaybackActions.EXTRA_SLEEP_TIMER_STOP_AT_END_OF_SECTION, stopAtEndOfSection)
    getApplication<Application>().startService(com.veritas.reader.PlaybackCommandSecurity.authorize(getApplication(), intent))
}

fun ReaderViewModel.cancelSleepTimer() {
    PlaybackStateStore.clearSleepTimer()
    repository.clearSleepTimerState()
    val intent = Intent(getApplication(), PlaybackService::class.java)
        .setAction(PlaybackActions.ACTION_CANCEL_SLEEP_TIMER)
    getApplication<Application>().startService(com.veritas.reader.PlaybackCommandSecurity.authorize(getApplication(), intent))
}

fun ReaderViewModel.createReadingList(title: String, documentId: String? = null) =
    changeCollections { createReadingList(title, documentId = documentId) }

fun ReaderViewModel.renameReadingList(listId: String, title: String) =
    changeCollections { renameReadingList(listId, title) }

fun ReaderViewModel.addDocumentToReadingList(listId: String, documentId: String) =
    changeCollections { addDocumentToReadingList(listId, documentId) }

fun ReaderViewModel.removeDocumentFromReadingList(listId: String, documentId: String) =
    changeCollections { removeDocumentFromReadingList(listId, documentId) }

fun ReaderViewModel.moveReadingListDocument(listId: String, documentId: String, offset: Int) =
    changeCollections { moveReadingListDocument(listId, documentId, offset) }

fun ReaderViewModel.transferCollectionDocument(from: String, to: String, documentId: String) =
    changeCollections { transferCollectionDocument(from, to, documentId) }

fun ReaderViewModel.setReadingListSortMode(listId: String, sortMode: VeritasReadingListSortMode) =
    changeCollections { setReadingListSortMode(listId, sortMode) }

fun ReaderViewModel.archiveReadingList(listId: String) =
    changeCollections { archiveReadingList(listId) }

fun ReaderViewModel.deleteReadingList(listId: String) =
    changeCollections { deleteReadingList(listId) }

fun ReaderViewModel.queueFromCollection(listId: String) {
    viewModelScope.launch {
        val queue = withContext(Dispatchers.IO) { repository.queueFromCollection(listId) }
        _uiState.update { it.copy(queuedDocuments = queue) }
    }
}

private fun ReaderViewModel.changeCollections(
    action: DocumentRepository.() -> VeritasReadingListCatalog
) {
    viewModelScope.launch {
        withContext(Dispatchers.IO) { repository.action() }
        val catalog = withContext(Dispatchers.IO) { repository.loadReadingListCatalog() }
        _uiState.update { it.copy(readingListCatalog = catalog) }
    }
}

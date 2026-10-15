package com.veritas.reader.ui

import com.veritas.reader.PlaybackStateStore

/** Reading another book has its own position until the user explicitly starts its audio. */
val ReaderViewModel.currentReaderIndex: Int
    get() = if (uiState.value.activeDocument?.id == PlaybackStateStore.activeDocumentId)
        PlaybackStateStore.currentIndex else uiState.value.readerPosition

val ReaderViewModel.isReaderPlaying: Boolean
    get() = PlaybackStateStore.isPlaying && uiState.value.activeDocument?.id == PlaybackStateStore.activeDocumentId

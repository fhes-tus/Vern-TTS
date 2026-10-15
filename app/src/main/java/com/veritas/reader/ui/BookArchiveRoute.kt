package com.veritas.reader.ui

/** Open the selected archive without losing the library/reader state underneath it. */
internal fun ReaderUiState.withBookArchive(url: String, title: String, query: String): ReaderUiState = copy(
    showBookBrowser = true,
    showOceanOfPdfBrowser = false,
    bookBrowserUrl = url,
    bookBrowserTitle = title,
    bookBrowserQuery = query.trim(),
    oceanOfPdfQuery = ""
)

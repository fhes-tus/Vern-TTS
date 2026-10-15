package com.veritas.reader

enum class ClassicDownloadPhase { IDLE, QUEUED, DOWNLOADING, ADDING, AVAILABLE, FAILED, CANCELLED }

data class ClassicDownloadState(
    val phase: ClassicDownloadPhase = ClassicDownloadPhase.IDLE,
    val percent: Int? = null,
    val message: String = ""
) {
    val busy: Boolean get() = phase in setOf(
        ClassicDownloadPhase.QUEUED, ClassicDownloadPhase.DOWNLOADING, ClassicDownloadPhase.ADDING
    )
    val actionLabel: String get() = when (phase) {
        ClassicDownloadPhase.IDLE -> "Add to library"
        ClassicDownloadPhase.QUEUED -> "Waiting for download"
        ClassicDownloadPhase.DOWNLOADING -> percent?.let { "Downloading $it%" } ?: "Downloading…"
        ClassicDownloadPhase.ADDING -> "Adding to library…"
        ClassicDownloadPhase.AVAILABLE -> "Open book"
        ClassicDownloadPhase.FAILED -> "Retry download"
        ClassicDownloadPhase.CANCELLED -> "Download again"
    }
}

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
}

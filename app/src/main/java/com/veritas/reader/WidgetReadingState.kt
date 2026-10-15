package com.veritas.reader

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

/** A widget must have a valid, durable document even after its process was reclaimed. */
internal data class WidgetReadingState(
    val document: SavedDocument?,
    val index: Int = 0,
    val isPlaying: Boolean = false,
    val isPreparing: Boolean = false,
    val reduceMotion: Boolean = false
) {
    val progress: Float get() = document?.let {
        if (it.chunkCount > 0) index.toFloat().div(it.chunkCount).coerceIn(0f, 1f) else 0f
    } ?: 0f
}

internal fun DocumentRepository.widgetReadingState(): WidgetReadingState {
    val liveId = PlaybackStateStore.activeDocumentId
    val resume = loadPersistedResumePoint()
    val live = liveId?.let(::findDocument)
    val persisted = resume?.documentId?.let(::findDocument)
    val document = live ?: persisted ?: loadDocuments().maxByOrNull { it.updatedAt }
    val index = when {
        document == null -> 0
        document.id == live?.id -> PlaybackStateStore.currentIndex
        document.id == persisted?.id -> resume?.chunkIndex ?: document.currentIndex
        else -> document.currentIndex
    }.coerceIn(0, document?.chunkCount?.coerceAtLeast(1)?.minus(1) ?: 0)
    return WidgetReadingState(document, index, live != null && PlaybackStateStore.isPlaying,
        document != null && PlaybackStateStore.isPreparingFor(document.id), loadReaderSettings().reduceMotion)
}

internal fun widgetActivityIntent(context: Context, action: String? = null, documentId: String? = null, noteId: String? = null): Intent =
    Intent(context, MainActivity::class.java).apply {
        // Extras don't distinguish PendingIntents. Give each destination/item an identity.
        data = Uri.Builder().scheme("vern-widget").authority("open")
            .appendPath(action ?: "home").appendPath(documentId ?: noteId ?: "root").build()
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        action?.let { putExtra(MainActivity.EXTRA_WIDGET_ACTION, it) }
        documentId?.let { putExtra(MainActivity.EXTRA_DOCUMENT_ID, it) }
        noteId?.let { putExtra(MainActivity.EXTRA_NOTE_ID, it) }
    }

internal fun widgetCover(context: Context, document: SavedDocument?): Bitmap? {
    val file = document?.let { CoverExtractor.coverFile(context, it.id) }?.takeIf { it.exists() } ?: return null
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 256 || bounds.outHeight / sample > 256) sample *= 2
        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}

internal fun widgetFormat(document: SavedDocument): String = when {
    document.originalMimeType.contains("pdf", true) || document.sourceLabel.equals("PDF", true) -> "PDF"
    document.originalMimeType.contains("epub", true) || document.sourceLabel.contains("CLASSIC", true) || document.sourceLabel.equals("EPUB", true) -> "BOOK"
    document.originalMimeType.contains("presentation", true) || document.sourceLabel.startsWith("PPT", true) -> "SLIDES"
    document.originalMimeType.contains("word", true) || document.sourceLabel.startsWith("DOC", true) -> "DOC"
    document.sourceLabel.contains("web", true) -> "WEB"
    else -> "TEXT"
}

package com.veritas.reader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.graphics.createBitmap
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import java.io.File
import kotlin.math.min
import com.veritas.reader.PlaybackService.Companion.CHANNEL_ID
import com.veritas.reader.PlaybackService.Companion.NOTIFICATION_ID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
internal fun PlaybackService.refreshForegroundNotification() {
    if (PlaybackStateStore.isForegroundActive) {
        startForegroundNow()
    }
}

internal fun PlaybackService.estimatedDurationMs(): Long {
    if (chunks.isEmpty()) return 0L
    val totalChars = chunks.sumOf { it.length.coerceAtLeast(1) }.coerceAtLeast(1)
    val charsPerSecond = (14.0 * PlaybackStateStore.rate.coerceIn(0.5f, 2.0f)).coerceAtLeast(7.0)
    return ((totalChars / charsPerSecond) * 1000.0)
        .toLong()
        .coerceAtLeast(chunks.size * 800L)
        .coerceAtLeast(1000L)
}

internal fun PlaybackService.estimatedPositionMs(includeCurrentSection: Boolean = true): Long {
    if (chunks.isEmpty()) return 0L
    val duration = estimatedDurationMs().coerceAtLeast(1L)
    val totalChars = chunks.sumOf { it.length.coerceAtLeast(1) }.coerceAtLeast(1)
    val safeIndex = PlaybackStateStore.currentIndex.coerceIn(0, chunks.lastIndex)
    val completedChars = chunks.take(safeIndex).sumOf { it.length.coerceAtLeast(1) } +
        if (includeCurrentSection) (chunks.getOrNull(safeIndex)?.length?.coerceAtLeast(1) ?: 0) else 0
    return ((completedChars.toDouble() / totalChars.toDouble()) * duration.toDouble())
        .toLong()
        .coerceIn(0L, duration)
}

internal fun PlaybackService.indexForEstimatedPosition(positionMs: Long): Int {
    if (chunks.isEmpty()) return 0
    val duration = estimatedDurationMs().coerceAtLeast(1L)
    val totalChars = chunks.sumOf { it.length.coerceAtLeast(1) }.coerceAtLeast(1)
    val targetChars = ((positionMs.coerceIn(0L, duration).toDouble() / duration.toDouble()) * totalChars.toDouble()).toInt()
    var running = 0
    chunks.forEachIndexed { index, chunk ->
        running += chunk.length.coerceAtLeast(1)
        if (targetChars <= running) return index
    }
    return chunks.lastIndex
}

@OptIn(UnstableApi::class)
internal fun PlaybackService.seekToEstimatedPosition(positionMs: Long) {
    if (chunks.isEmpty()) {
        val documentId = PlaybackStateStore.activeDocumentId ?: return
        if (!loadDocument(documentId, PlaybackStateStore.currentIndex)) return
    }
    val targetIndex = indexForEstimatedPosition(positionMs).coerceIn(0, chunks.lastIndex)
    clearResumePoint()
    PlaybackStateStore.currentIndex = targetIndex
    activeDocument?.let { repository.updateProgress(it.id, targetIndex, chunks.size) }
    if (PlaybackStateStore.isPlaying) {
        speakCurrent()
    } else {
        updateMediaSessionMetadata()
        updateMediaSessionState()
        refreshForegroundNotification()
    }
}

@OptIn(UnstableApi::class)
internal fun PlaybackService.startForegroundNow() {
    updateMediaSessionMetadata()
    updateMediaSessionState()
    val notification = buildNotification()
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        PlaybackStateStore.isForegroundActive = true
    } catch (e: Exception) {
        // Handles ForegroundServiceStartNotAllowedException on Android 12+/14+
        // and SecurityException if background execution is restricted.
        android.util.Log.w("PlaybackService", "Could not promote to foreground service: ${e.message}")
    }
}

@OptIn(UnstableApi::class)
internal fun PlaybackService.buildNotification(): Notification {
    val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)
        ?: Intent(this, MainActivity::class.java)
    val contentIntent = PendingIntent.getActivity(
        this,
        100,
        openAppIntent,
        pendingIntentFlags()
    )

    val playPauseAction = if (PlaybackStateStore.isPlaying) {
        NotificationCompat.Action(android.R.drawable.ic_media_pause, "Pause", servicePendingIntent(PlaybackActions.ACTION_PAUSE, 101))
    } else {
        NotificationCompat.Action(android.R.drawable.ic_media_play, "Play", servicePendingIntent(PlaybackActions.ACTION_PLAY, 102))
    }

    val nowMillis = System.currentTimeMillis()
    val progressText = if (PlaybackStateStore.chunkCount > 0) {
        val queueText = if (PlaybackStateStore.queueCount > 0) " • ${PlaybackStateStore.queueCount} queued" else ""
        val timerText = PlaybackStateStore.activeSleepTimerSnapshot(nowMillis)
            ?.let { " • ${it.menuLabel(nowMillis)}" }
            .orEmpty()
        "Sentence ${PlaybackStateStore.currentIndex + 1} of ${PlaybackStateStore.chunkCount}$queueText$timerText"
    } else {
        PlaybackStateStore.statusMessage
    }

    val durationMs = estimatedDurationMs()
    val positionMs = estimatedPositionMs()
    val progressMax = 1000
    val progressValue = if (durationMs > 0L) {
        ((positionMs.toDouble() / durationMs.toDouble()) * progressMax).toInt().coerceIn(0, progressMax)
    } else {
        0
    }
    val artwork = currentNotificationArtwork()

    val builder = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_veritas)
        .setLargeIcon(artwork)
        .setContentTitle(PlaybackStateStore.documentTitle.ifBlank { getString(R.string.app_name) })
        .setContentText(progressText)
        .setSubText(PlaybackStateStore.sourceLabel.ifBlank { "Text-to-speech" })
        .setContentIntent(contentIntent)
        .setOngoing(PlaybackStateStore.isPlaying)
        .setOnlyAlertOnce(true)
        .setShowWhen(false)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setProgress(progressMax, progressValue, durationMs <= 0L)
        .addAction(NotificationCompat.Action(android.R.drawable.ic_media_previous, "Previous", servicePendingIntent(PlaybackActions.ACTION_PREVIOUS, 103)))
        .addAction(playPauseAction)
        .addAction(NotificationCompat.Action(android.R.drawable.ic_media_next, "Next", servicePendingIntent(PlaybackActions.ACTION_NEXT, 104)))
        .addAction(NotificationCompat.Action(android.R.drawable.ic_menu_close_clear_cancel, "Stop", servicePendingIntent(PlaybackActions.ACTION_STOP, 105)))
    val session = mediaSession
    if (session != null) {
        builder.setStyle(
            MediaStyleNotificationHelper.MediaStyle(session)
                .setShowActionsInCompactView(0, 1, 2)
        )
    }
    return builder.build()
}

@OptIn(UnstableApi::class)
internal fun PlaybackService.setupMediaSession() {
    val player = VeritasMediaSessionPlayer(
        applicationLooper = Looper.getMainLooper(),
        snapshot = { mediaSessionSnapshot() },
        controller = object : VeritasMediaSessionPlayer.Controller {
            override fun play() {
                startService(Intent(this@setupMediaSession, PlaybackService::class.java).setAction(PlaybackActions.ACTION_PLAY))
            }

            override fun pause() {
                startService(Intent(this@setupMediaSession, PlaybackService::class.java).setAction(PlaybackActions.ACTION_PAUSE))
            }

            override fun stop() {
                startService(Intent(this@setupMediaSession, PlaybackService::class.java).setAction(PlaybackActions.ACTION_STOP))
            }

            override fun next() {
                startService(Intent(this@setupMediaSession, PlaybackService::class.java).setAction(PlaybackActions.ACTION_NEXT))
            }

            override fun previous() {
                startService(Intent(this@setupMediaSession, PlaybackService::class.java).setAction(PlaybackActions.ACTION_PREVIOUS))
            }

            override fun seekTo(positionMs: Long) {
                seekToEstimatedPosition(positionMs)
            }

            override fun setPlaybackParameters(rate: Float, pitch: Float) {
                PlaybackStateStore.rate = rate
                PlaybackStateStore.pitch = pitch
                if (PlaybackStateStore.isPlaying) speakCurrent()
                updateMediaSessionState()
                refreshForegroundNotification()
            }
        }
    )
    mediaSessionPlayer = player
    mediaSession = MediaSession.Builder(this, player)
        .setId("VernReaderSession")
        .build()
    updateMediaSessionMetadata()
    updateMediaSessionState()
}

internal fun PlaybackService.updateMediaSessionMetadata() {
    mediaSessionPlayer?.notifyStateChanged()
}

@OptIn(UnstableApi::class)
internal fun PlaybackService.updateMediaSessionState() {
    mediaSessionPlayer?.notifyStateChanged()
    updateVeritasWidgets(this)
}

internal fun PlaybackService.mediaSessionSnapshot(): VeritasMediaSessionPlayer.PlaybackSnapshot {
    val sectionLabel = if (PlaybackStateStore.chunkCount > 0) {
        "Sentence ${PlaybackStateStore.currentIndex + 1} of ${PlaybackStateStore.chunkCount}"
    } else {
        PlaybackStateStore.statusMessage
    }
    val documentId = PlaybackStateStore.activeDocumentId.orEmpty()
    // Load artwork bytes lazily and cache per-document
    if (artworkBytesDocumentId != documentId) {
        artworkBytes = documentId.takeIf { it.isNotBlank() }?.let { id ->
            CoverExtractor.coverFile(this, id)?.takeIf { it.exists() }?.let { file ->
                runCatching { file.readBytes() }.getOrNull()
            }
        }
        artworkBytesDocumentId = documentId
    }
    return VeritasMediaSessionPlayer.PlaybackSnapshot(
        documentId = documentId,
        title = PlaybackStateStore.documentTitle.ifBlank { getString(R.string.app_name) },
        sourceLabel = PlaybackStateStore.sourceLabel.ifBlank { "Text-to-speech reader" },
        sectionLabel = sectionLabel,
        isPlaying = PlaybackStateStore.isPlaying,
        isForegroundActive = PlaybackStateStore.isForegroundActive,
        chunkCount = PlaybackStateStore.chunkCount,
        currentIndex = PlaybackStateStore.currentIndex,
        durationMs = estimatedDurationMs(),
        positionMs = estimatedPositionMs(),
        rate = PlaybackStateStore.rate,
        pitch = PlaybackStateStore.pitch,
        artworkData = artworkBytes
    )
}

internal fun PlaybackService.servicePendingIntent(action: String, requestCode: Int): PendingIntent {
    val intent = Intent(this, PlaybackService::class.java).setAction(action)
    return PendingIntent.getService(this, requestCode, intent, pendingIntentFlags())
}

internal fun PlaybackService.pendingIntentFlags(): Int {
    return PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
}

internal fun PlaybackService.getDefaultArtwork(): Bitmap {
    return defaultAppArtwork ?: run {
        val resBmp = BitmapFactory.decodeResource(resources, R.drawable.veritas_reader_icon)
            ?: createBitmap(512, 512).also {
                Canvas(it).drawColor(Color.rgb(18, 23, 27))
            }
        val squared = squareFitBitmap(resBmp)
        defaultAppArtwork = squared
        squared
    }
}

internal fun PlaybackService.currentNotificationArtwork(): Bitmap {
    val documentId = activeDocument?.id ?: PlaybackStateStore.activeDocumentId
    if (documentId == null) return getDefaultArtwork()
    notificationArtwork?.takeIf { artworkDocumentId == documentId }?.let { return it }

    // Fast fallback: return existing artwork or default immediately without blocking the main thread
    val fallback = notificationArtwork ?: getDefaultArtwork()

    if (!isCoverLoading) {
        val document = activeDocument ?: repository.findDocument(documentId)
        if (document != null) {
            isCoverLoading = true
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val source = loadNotificationCover(document)
                    val artwork = if (source != null) squareFitBitmap(source) else getDefaultArtwork()
                    withContext(Dispatchers.Main) {
                        artworkDocumentId = documentId
                        notificationArtwork = artwork
                        isCoverLoading = false
                        refreshForegroundNotification()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isCoverLoading = false
                    }
                }
            }
        }
    }
    return fallback
}

internal fun PlaybackService.loadNotificationCover(document: SavedDocument): Bitmap? {
    // 1. Try to load pre-extracted cover if it exists
    CoverExtractor.coverFile(this, document.id)?.let { file ->
        runCatching {
            BitmapFactory.decodeFile(file.absolutePath)
        }.getOrNull()?.let { return it }
    }

    // 2. Fallback to on-the-fly loading/rendering
    val original = repository.originalUri(document) ?: return null
    val mime = document.originalMimeType.lowercase()
    return when {
        mime.startsWith("image/") -> {
            contentResolver.openInputStream(original)?.use { BitmapFactory.decodeStream(it) }
        }
        mime == "application/pdf" || document.originalFileName.endsWith(".pdf", ignoreCase = true) -> renderPdfFirstPage(original)
        else -> null
    }
}

internal fun PlaybackService.renderPdfFirstPage(uri: Uri): Bitmap? {
    return runCatching {
        contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount <= 0) return@use null
                renderer.openPage(0).use { page ->
                    val width = 512
                    val height = ((width.toFloat() / page.width.toFloat()) * page.height).toInt().coerceAtLeast(1)
                    createBitmap(width, height).also { bitmap ->
                        Canvas(bitmap).drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }
    }.getOrNull()
}

internal fun PlaybackService.squareFitBitmap(source: Bitmap, targetSize: Int = 512): Bitmap {
    val output = createBitmap(targetSize, targetSize)
    val canvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.drawColor(Color.rgb(18, 23, 27))
    val scale = min(
        targetSize.toFloat() / source.width.toFloat().coerceAtLeast(1f),
        targetSize.toFloat() / source.height.toFloat().coerceAtLeast(1f)
    )
    val width = source.width * scale
    val height = source.height * scale
    val left = (targetSize - width) / 2f
    val top = (targetSize - height) / 2f
    canvas.drawBitmap(source, null, RectF(left, top, left + width, top + height), paint)
    return output
}

internal fun PlaybackService.createNotificationChannel() {
    val channel = NotificationChannel(
        CHANNEL_ID,
        "Reader playback",
        NotificationManager.IMPORTANCE_LOW
    ).apply {
        description = "Background text-to-speech playback controls"
        setShowBadge(false)
    }
    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.createNotificationChannel(channel)
}

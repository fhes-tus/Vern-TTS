package com.veritas.reader.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.veritas.reader.GeneralNote
import com.veritas.reader.VeritasPackStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Bounded local previews; no network requests or full-size image decoding on the UI thread. */
@Composable
internal fun NoteCardThumbnail(note: GeneralNote) {
    val context = LocalContext.current
    val media = remember(note.content, note.imageUrl) {
        val block = VeritasNoteEditing.parseNoteBlocks(note.content).firstOrNull { it is NoteBlock.Image || it is NoteBlock.Video }
        when (block) {
            is NoteBlock.Image -> block.path to false
            is NoteBlock.Video -> block.path to true
            else -> note.imageUrl?.takeIf { it.isNotBlank() }?.let { it to false }
        }
    } ?: return
    val bitmap by produceState<Bitmap?>(null, media) {
        value = withContext(Dispatchers.IO) { loadNoteThumbnail(context, media.first, media.second) }
    }
    // Reserve a bounded slot immediately, including missing/unreadable files. Loading a
    // thumbnail must not move the note text or make a card disappear from the viewport.
    Box(Modifier.fillMaxWidth().height(112.dp).clip(VeritasPackStyle.cardShape())
        .background(MaterialTheme.colorScheme.surfaceContainerHigh).testTag("note_thumbnail_${note.id}"),
        contentAlignment = Alignment.Center) {
        val loaded = bitmap
        if (loaded != null) {
            Image(loaded.asImageBitmap(), if (media.second) "Video preview" else "Image preview",
                Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(if (media.second) Icons.Outlined.Videocam else Icons.Outlined.Image, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (media.second) "Video attachment" else "Image attachment", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

internal fun loadNoteThumbnail(context: Context, path: String, video: Boolean): Bitmap? = runCatching {
    val uri = Uri.parse(path)
    // Remote URLs and unsupported providers are represented by the existing attachment badges.
    if (uri.scheme !in listOf(null, "file", "content")) return@runCatching null
    if (video) {
        val retriever = MediaMetadataRetriever()
        try {
            if (uri.scheme == "content" || uri.scheme == "file") retriever.setDataSource(context, uri)
            else retriever.setDataSource(path)
            retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 512, 384)
        } finally { retriever.release() }
    } else {
        fun stream() = if (uri.scheme == "content" || uri.scheme == "file") context.contentResolver.openInputStream(uri)
            else File(path).takeIf { it.isFile }?.inputStream()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        stream()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (bounds.outWidth / inSampleSize > 512 || bounds.outHeight / inSampleSize > 512) inSampleSize *= 2
        }
        stream()?.use { BitmapFactory.decodeStream(it, null, options) }
    }
}.getOrNull()

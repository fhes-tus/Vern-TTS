package com.veritas.reader.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veritas.reader.R
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.copyTextToClipboard
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

private const val MAX_LINK_IMAGE_BYTES = 1024 * 1024
private val linkImages = NotePreviewCache<Bitmap>(12)

private fun thumbnailFile(context: android.content.Context, url: String): File {
    val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
    val key = digest.joinToString("") { "%02x".format(it) }
    return File(File(context.cacheDir, "note-link-thumbnails"), "$key.png")
}

private suspend fun cachedLinkImage(context: android.content.Context, url: String): Bitmap? {
    if (url.isBlank()) return null
    return linkImages.cached(url) ?: withContext(Dispatchers.IO) {
        val file = thumbnailFile(context, url)
        if (file.isFile) BitmapFactory.decodeFile(file.absolutePath) else null
    }
}

private suspend fun loadLinkImage(
    context: android.content.Context, url: String, referrer: String, automatic: Boolean
): Bitmap? = linkImages.load(url) {
    cachedLinkImage(context, url) ?: fetchLinkImage(context, url, referrer, automatic)
}

private suspend fun fetchLinkImage(
    context: android.content.Context, url: String, referrer: String, automatic: Boolean
): Bitmap? {
    return NoteLinks.requestPreview(url, referrer = referrer, automatic = automatic) { connection ->
        val bytes = connection.inputStream.use { NoteLinks.readLimited(it, MAX_LINK_IMAGE_BYTES + 1) }
        if (bytes.size > MAX_LINK_IMAGE_BYTES) return@requestPreview null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@requestPreview null
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (bounds.outWidth / inSampleSize > 512 || bounds.outHeight / inSampleSize > 512) inSampleSize *= 2
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }?.also { bitmap ->
        withContext(Dispatchers.IO) {
            runCatching {
                val file = thumbnailFile(context, url)
                check(file.parentFile?.let { it.isDirectory || it.mkdirs() } == true)
                val staging = File.createTempFile("note-thumb-", ".tmp", file.parentFile)
                try {
                    staging.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    check(staging.renameTo(file) || file.exists())
                } finally {
                    staging.delete()
                }
                file.parentFile?.listFiles { candidate -> candidate.extension == "png" }
                    ?.sortedByDescending(File::lastModified)?.drop(32)?.forEach(File::delete)
            }
        }
    }
}

@Composable
internal fun NoteLinkCard(
    link: NoteLink,
    onRemove: (() -> Unit)? = null,
    footer: Boolean = false,
    onMetadataLoaded: ((NoteLink) -> Unit)? = null
) = key(link.url) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val source by rememberUpdatedState(link)
    val onDetailsChanged by rememberUpdatedState(onMetadataLoaded)
    val openLabel = menuString(R.string.ui_open_link)
    var menuOpen by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var metadata by remember { mutableStateOf<NoteLink?>(null) }
    val preview = NoteLinks.withMetadata(link, metadata ?: NoteLinks.cachedMetadata(link.url))
    var loadedImage by remember(link.url) { mutableStateOf<Bitmap?>(null) }
    val bitmap = linkImages.cached(preview.image) ?: loadedImage
    var showLoadStatus by remember { mutableStateOf(false) }

    fun openLink() {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url))) }
    }

    suspend fun loadFromWebsite(automatic: Boolean) {
        if (loading) return
        loading = true
        showLoadStatus = !automatic
        loadFailed = false
        try {
            suspend fun loadImage(image: String): Bitmap? = image.takeIf(NoteLinks::validUrl)?.let {
                loadLinkImage(context.applicationContext, it, source.url, automatic)
            }
            val savedImage = preview.image
            if (automatic) {
                loadedImage = cachedLinkImage(context, savedImage) ?: loadImage(savedImage)
                if (loadedImage != null) return
            }
            val fetched = NoteLinks.loadMetadata(link.url, automatic)
            val enriched = NoteLinks.withMetadata(source, fetched)
            val picture = if (automatic && enriched.image == savedImage) null else loadImage(enriched.image)
            if (picture != null) loadedImage = picture
            if (fetched != null) {
                metadata = fetched
                if (!automatic && enriched != source) onDetailsChanged?.invoke(enriched)
            }
            loadFailed = !automatic && picture == null && (fetched == null || enriched.image.isNotBlank())
        } finally {
            loading = false
        }
    }

    LaunchedEffect(context, link.url) { loadFromWebsite(automatic = true) }

    Surface(Modifier.fillMaxWidth().testTag("note_link_preview")
        .clickable(role = Role.Button, onClickLabel = openLabel, onClick = ::openLink),
        shape = if (footer) androidx.compose.ui.graphics.RectangleShape else VeritasPackStyle.compactShape(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                bitmap?.let {
                    Image(it.asImageBitmap(), null, Modifier.fillMaxSize().testTag("note-link-thumbnail"), contentScale = ContentScale.Crop)
                }
                    ?: Icon(Icons.Default.Link, null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f).padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(preview.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(NoteLinks.host(link.url), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                if (showLoadStatus && (loading || loadFailed)) {
                    Text(menuString(if (loading) R.string.notes_link_loading else R.string.notes_link_unavailable),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box {
                IconButton({ menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, menuString(R.string.ui_link_preview_options), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(menuOpen, { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(openLabel) }, onClick = { menuOpen = false; openLink() })
                    DropdownMenuItem(text = { Text(menuString(R.string.notes_link_load_from_website)) },
                        enabled = !loading, onClick = {
                            menuOpen = false
                            scope.launch { loadFromWebsite(automatic = false) }
                        })
                    DropdownMenuItem(text = { Text(menuString(R.string.ui_copy_url)) }, onClick = {
                        menuOpen = false
                        copyTextToClipboard(context, "Link", link.url)
                    })
                    if (onRemove != null) {
                        DropdownMenuItem(text = { Text(menuString(R.string.ui_remove)) },
                            onClick = { menuOpen = false; onRemove() })
                    }
                }
            }
        }
    }
}

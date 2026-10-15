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
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veritas.reader.VeritasPackStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private val linkImages = android.util.LruCache<String, Bitmap>(12)
private val linkMetadata = android.util.LruCache<String, NoteLink>(40)

@Composable
internal fun NoteLinkCard(link: NoteLink, onRemove: (() -> Unit)? = null, footer: Boolean = false) {
    val context = LocalContext.current
    var menuOpen by remember(link.url) { mutableStateOf(false) }
    // Older notes may have a title but no image. Enrich only the visible card, without
    // rewriting the note or changing its last-edited time.
    val metadata by produceState(linkMetadata.get(link.url) ?: link, link) {
        if (value.image.isEmpty() && linkMetadata.get(link.url) == null) {
            value = withContext(Dispatchers.IO) { NoteLinks.fetch(link.url) }
            linkMetadata.put(link.url, value)
        }
    }
    val imageUrl = link.image.ifEmpty { metadata.image }
    val bitmap by produceState<Bitmap?>(linkImages.get(imageUrl), imageUrl) {
        if (value == null && NoteLinks.validUrl(imageUrl)) value = withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(imageUrl).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 4000; connection.readTimeout = 4000
                    require(connection.responseCode in 200..299)
                    val bytes = connection.inputStream.use { NoteLinks.readLimited(it, 1024 * 1024 + 1) }
                    require(bytes.size <= 1024 * 1024)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    require(bounds.outWidth > 0 && bounds.outHeight > 0)
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 1
                        while (bounds.outWidth / inSampleSize > 512 || bounds.outHeight / inSampleSize > 512) inSampleSize *= 2
                    }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.also { linkImages.put(imageUrl, it) }
                } finally { connection.disconnect() }
            }.getOrNull()
        }
    }
    Surface(Modifier.fillMaxWidth().testTag("note_link_preview").clickable {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url))) }
    }, shape = if (footer) androidx.compose.ui.graphics.RectangleShape else VeritasPackStyle.compactShape(), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    ?: Icon(Icons.Default.Link, null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f).padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(link.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(NoteLinks.host(link.url), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (onRemove != null) Box {
                IconButton({ menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, "Link preview options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(menuOpen, { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Remove") }, onClick = { menuOpen = false; onRemove() })
                    DropdownMenuItem(text = { Text("Copy URL") }, onClick = {
                        menuOpen = false
                        com.veritas.reader.copyTextToClipboard(context, "Link", link.url)
                    })
                }
            }
            else Icon(Icons.Default.OpenInNew, "Open link", Modifier.padding(end = 10.dp).size(18.dp))
        }
    }
}

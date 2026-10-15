package com.veritas.reader

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

/** Decode only displayed media, off the UI thread, with a bounded pixel size. */
@Composable
internal fun OriginalDocumentImage(owner: Any, index: Int, description: String, load: () -> ByteArray) {
    val identity = System.identityHashCode(owner)
    var failed by remember(identity, index) { mutableStateOf(false) }
    val bitmap by produceState<android.graphics.Bitmap?>(null, identity, index) {
        value = withContext(Dispatchers.IO) {
            try {
                val bytes = load()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0)
                var sample = 1
                while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600 || bounds.outWidth.toLong() / sample * (bounds.outHeight / sample) > 2500000) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true; null }
        }
    }
    Box(modifier = Modifier.fillMaxWidth().height(240.dp).padding(vertical = 8.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = description.ifBlank { "Document illustration" }, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            ?: Text(if (failed) "Illustration unavailable" else "Loading illustration…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

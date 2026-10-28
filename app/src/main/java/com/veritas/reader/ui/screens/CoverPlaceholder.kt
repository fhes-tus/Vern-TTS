package com.veritas.reader.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.veritas.reader.BookCoverLoader
import com.veritas.reader.GeneratedBookCover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun VeritasCoverPlaceholder(documentId: String, title: String, sourceLabel: String,
    modifier: Modifier = Modifier, compact: Boolean = false,
    @Suppress("UNUSED_PARAMETER") contentPadding: PaddingValues = PaddingValues(0.dp)) {
    val context = LocalContext.current
    val cover by produceState<Bitmap?>(null, documentId, title, sourceLabel) {
        value = withContext(Dispatchers.IO) { BookCoverLoader.generated(context, documentId, title, sourceLabel) }
    }
    Box(modifier.clip(RoundedCornerShape(if (compact) 6.dp else 10.dp))
        .background(Color(GeneratedBookCover.palette(documentId.ifBlank { title }).paper)), contentAlignment = Alignment.Center) {
        cover?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
    }
}

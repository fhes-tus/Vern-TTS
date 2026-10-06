package com.veritas.reader.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import com.veritas.reader.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun LibrarySearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, onDone: (() -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    BasicTextField(value, onChange, modifier.height(38.dp)
        .background(scheme.surfaceVariant, VeritasPackStyle.chipShape()), singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onDone?.invoke() }),
        cursorBrush = SolidColor(scheme.primary), textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface),
        decorationBox = { innerField ->
            Row(Modifier.fillMaxWidth().height(38.dp).padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Search, null, Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    innerField()
                }
                if (value.isNotEmpty()) IconButton({ onChange("") }, Modifier.size(28.dp)) { Icon(Icons.Default.Close, "Clear search", tint = scheme.onSurfaceVariant) }
            }
        })
}

@Composable
internal fun LibraryBookSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier, shape = VeritasPackStyle.cardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = VeritasPackStyle.surfaceAlpha())),
        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme), content = content)
}

@Composable
internal fun DocumentArtwork(document: SavedDocument, modifier: Modifier = Modifier, compact: Boolean = false) {
    val context = LocalContext.current
    val cover by produceState<android.graphics.Bitmap?>(null, document.id, document.catalogId) {
        value = withContext(Dispatchers.IO) { BookCoverLoader.document(context, document.id, document.catalogId) }
    }
    Box(modifier.clip(RoundedCornerShape(6.dp))
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .3f), RoundedCornerShape(6.dp))) {
        if (cover != null) Image(cover!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else VeritasCoverPlaceholder(document.id, document.title, document.sourceLabel, Modifier.fillMaxSize(), compact = compact)
    }
}

@Composable
internal fun BookCoverStage(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 166.dp,
    inset: androidx.compose.ui.unit.Dp = 8.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val stageShape = VeritasPackStyle.coverStageShape(inset = inset)
    Box(modifier.fillMaxWidth().height(height).clip(stageShape)
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f)),
        contentAlignment = Alignment.Center, content = content)
}

package com.veritas.reader.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.veritas.reader.*
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class QuotePreview(val spec: QuoteCardSpec, val image: Bitmap?, val error: String? = null)

@Composable
internal fun QuoteShareDialog(visible: Boolean, quote: String, title: String, documentId: String?, coverFile: File?,
    onDismiss: () -> Unit, onShareReady: ((File) -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var paletteId by rememberSaveable(documentId, title) { mutableStateOf("cream") }
    var credit by rememberSaveable(documentId, title) { mutableStateOf("") }
    var creditEdited by rememberSaveable(documentId, title) { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf<String?>(null) }
    val book by produceState<QuoteBookDetails?>(null, documentId, title, coverFile) {
        value = withContext(Dispatchers.IO) {
            runCatching { loadQuoteBookDetails(context, documentId, title, coverFile) }
                .getOrElse { QuoteBookDetails(getBookAndAuthor(title).first, "", null) }
        }
    }
    LaunchedEffect(book?.author) {
        if (!creditEdited) credit = book?.author.orEmpty()
    }
    if (!visible) return
    val palette = quotePalettes.firstOrNull { it.id == paletteId } ?: quotePalettes.first()
    val spec = QuoteCardSpec(quote, book?.title ?: getBookAndAuthor(title).first, credit, palette, book?.cover)
    val preview by produceState<QuotePreview?>(null, spec) {
        value = try {
            QuotePreview(spec, withContext(Dispatchers.IO) { QuoteCardRenderer.render(context, spec) })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) { QuotePreview(spec, null, error.message ?: "Could not create this quote image.") }
    }
    val current = preview?.takeIf { it.spec == spec }
    Dialog(onDismissRequest = { if (!sharing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss, enabled = !sharing) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to sentence selection")
                    }
                    Text("Share quote", style = MaterialTheme.typography.titleLarge)
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (current?.image != null) Image(current.image.asImageBitmap(),
                        contentDescription = "Quote image preview: $quote. Source: ${credit.ifBlank { spec.title }}",
                        modifier = Modifier.fillMaxSize().testTag("quote-image-preview"), contentScale = ContentScale.Fit)
                    else if (current?.error != null) Text(current.error, color = MaterialTheme.colorScheme.error)
                    else CircularProgressIndicator()
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quotePalettes.forEach { option ->
                        FilterChip(selected = palette.id == option.id, enabled = !sharing,
                            onClick = { paletteId = option.id; shareError = null },
                            label = { Text(option.name) }, modifier = Modifier.testTag("quote-color-${option.id}"),
                            leadingIcon = if (palette.id == option.id) { { Icon(Icons.Filled.Check, contentDescription = null,
                                modifier = Modifier.size(18.dp), tint = Color(option.ink)) } } else null,
                            colors = FilterChipDefaults.filterChipColors(containerColor = Color(option.background),
                                labelColor = Color(option.ink), selectedContainerColor = Color(option.background), selectedLabelColor = Color(option.ink)))
                    }
                }
                OutlinedTextField(value = credit, onValueChange = {
                    creditEdited = true; credit = it.replace('\n', ' ').take(140); shareError = null
                }, label = { Text("Quote source") }, placeholder = { Text("Author or character name") },
                    supportingText = { Text("Credit the author, narrator or character who said it.") },
                    singleLine = true, enabled = !sharing, modifier = Modifier.fillMaxWidth().testTag("quote-source"))
                shareError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = {
                    val image = current?.image ?: return@Button
                    sharing = true
                    shareError = null
                    scope.launch {
                        try {
                            val file = withContext(Dispatchers.IO) { QuoteCardRenderer.save(context, image) }
                            if (onShareReady != null) onShareReady(file)
                            else context.startActivity(Intent.createChooser(quoteShareIntent(context, file), "Share quote"))
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) { shareError = "Could not share the image: ${error.message ?: "Please try again."}" }
                        finally { sharing = false }
                    }
                }, enabled = book != null && current?.image != null && !sharing, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (sharing) "Preparing image…" else "Share image")
                }
            }
        }
    }
}

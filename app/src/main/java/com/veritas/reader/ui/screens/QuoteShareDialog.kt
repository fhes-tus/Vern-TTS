package com.veritas.reader.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ContentCut
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
import com.veritas.reader.VernDialog as Dialog
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
    var activeQuote by rememberSaveable(quote) { mutableStateOf(quote) }
    var history by remember(quote) { mutableStateOf(listOf(quote)) }
    var historyIndex by remember(quote) { mutableIntStateOf(0) }
    var sharing by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf<String?>(null) }

    fun pushEdit(newText: String) {
        val clean = newText.trim()
        if (clean == activeQuote || clean.isEmpty()) return
        val trimmed = history.take(historyIndex + 1)
        history = trimmed + clean
        historyIndex = history.size - 1
        activeQuote = clean
        shareError = null
    }

    fun undo() {
        if (historyIndex > 0) {
            historyIndex--
            activeQuote = history[historyIndex]
            shareError = null
        }
    }

    fun redo() {
        if (historyIndex < history.size - 1) {
            historyIndex++
            activeQuote = history[historyIndex]
            shareError = null
        }
    }

    fun reset() {
        pushEdit(quote)
    }

    val initialSentences = remember(quote) {
        com.veritas.reader.tts.SentenceSplitter.splitIntoSentences(quote)
            .map { it.text.trim() }
            .filter { it.isNotEmpty() }
    }

    fun omitSentence(sentence: String) {
        val current = activeQuote
        val idx = current.indexOf(sentence)
        if (idx < 0) return
        val before = current.substring(0, idx).trimEnd()
        val after = current.substring(idx + sentence.length).trimStart()

        val result = when {
            before.isEmpty() && after.isNotEmpty() -> {
                val cleanAfter = after.removePrefix("...").removePrefix("…").trimStart()
                "... $cleanAfter"
            }
            before.isNotEmpty() && after.isEmpty() -> {
                val cleanBefore = before.removeSuffix("...").removeSuffix("…").trimEnd()
                "$cleanBefore..."
            }
            before.isNotEmpty() && after.isNotEmpty() -> {
                val cleanBefore = before.removeSuffix("...").removeSuffix("…").trimEnd()
                val cleanAfter = after.removePrefix("...").removePrefix("…").trimStart()
                "$cleanBefore ... $cleanAfter"
            }
            else -> current
        }
        pushEdit(result)
    }

    fun onTextEdited(input: String) {
        val nonEllipsisInput = input.replace("...", "").replace("…", "").trim()
        val nonEllipsisActive = activeQuote.replace("...", "").replace("…", "").trim()
        if (nonEllipsisInput.length > nonEllipsisActive.length) {
            return
        }
        var formatted = input
        val rawOriginal = quote.trim()
        val rawNew = formatted.trim().removePrefix("...").removePrefix("…").trimStart()
        if (rawNew.isNotEmpty() && !rawOriginal.startsWith(rawNew) && !formatted.startsWith("...") && !formatted.startsWith("…")) {
            formatted = "... $formatted"
        }
        val rawEnd = formatted.trim().removeSuffix("...").removeSuffix("…").trimEnd()
        if (rawEnd.isNotEmpty() && !rawOriginal.endsWith(rawEnd) && !formatted.endsWith("...") && !formatted.endsWith("…")) {
            formatted = "$formatted..."
        }
        pushEdit(formatted)
    }

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
    val spec = QuoteCardSpec(activeQuote, book?.title ?: getBookAndAuthor(title).first, credit, palette, book?.cover)
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_back_to_sentence_selection))
                    }
                    Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_share_quote), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { undo() }, enabled = historyIndex > 0 && !sharing, modifier = Modifier.size(36.dp).testTag("quote-undo")) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { redo() }, enabled = historyIndex < history.size - 1 && !sharing, modifier = Modifier.size(36.dp).testTag("quote-redo")) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { reset() }, enabled = activeQuote != quote && !sharing, modifier = Modifier.size(36.dp).testTag("quote-reset")) {
                        Icon(Icons.Outlined.ContentCut, contentDescription = "Reset quote", modifier = Modifier.size(20.dp))
                    }
                }
                Box(Modifier.weight(1f, fill = false).fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
                    if (current?.image != null) Image(current.image.asImageBitmap(),
                        contentDescription = "Quote image preview: $activeQuote. Source: ${credit.ifBlank { spec.title }}",
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
                if (initialSentences.size > 1) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        initialSentences.forEachIndexed { i, s ->
                            val isPresent = activeQuote.contains(s)
                            FilterChip(
                                selected = isPresent,
                                enabled = !sharing,
                                onClick = {
                                    if (isPresent) {
                                        omitSentence(s)
                                    } else {
                                        reset()
                                    }
                                },
                                label = { Text("Sentence ${i + 1}", maxLines = 1) },
                                leadingIcon = if (!isPresent) {
                                    { Icon(Icons.Outlined.ContentCut, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = activeQuote,
                    onValueChange = { onTextEdited(it) },
                    label = { Text("Quote text (subtraction only)") },
                    supportingText = { Text("Delete text to condense quote • Ellipses (...) added automatically") },
                    maxLines = 3,
                    enabled = !sharing,
                    modifier = Modifier.fillMaxWidth().testTag("quote-text-edit")
                )
                OutlinedTextField(value = credit, onValueChange = {
                    creditEdited = true; credit = it.replace('\n', ' ').take(140); shareError = null
                }, label = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_quote_source)) }, placeholder = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_author_or_character_name)) },
                    supportingText = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_credit_the_author_narrator_or_character_who_said_it)) },
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
                    Text(if (sharing) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_preparing_image) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_share_image))
                }
            }
        }
    }
}

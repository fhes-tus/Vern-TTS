package com.veritas.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.Locale

internal data class BookExportName(val filename: String, val extension: String, val mimeType: String)

internal fun bookExportName(title: String, extension: String): BookExportName {
    val ext = extension.lowercase(Locale.ROOT).takeIf { it.matches(Regex("[a-z0-9]{1,8}")) } ?: "txt"
    val base = title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trimEnd('.')
        .ifBlank { "Vern book" }.take(100).removeSuffix(".$ext")
    val mime = when (ext) {
        "pdf" -> "application/pdf"
        "epub" -> "application/epub+zip"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "txt", "md" -> "text/plain"
        "html", "htm" -> "text/html"
        else -> "application/octet-stream"
    }
    return BookExportName("$base.$ext", ext, mime)
}

private class CreateBookDocument : ActivityResultContract<BookExportName, Uri?>() {
    override fun createIntent(context: Context, input: BookExportName) = Intent(Intent.ACTION_CREATE_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE).setType(input.mimeType)
        .putExtra(Intent.EXTRA_TITLE, input.filename)
    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent?.data?.takeIf { resultCode == android.app.Activity.RESULT_OK }
}

private data class BookExportSource(val name: BookExportName, val file: File? = null, val uri: Uri? = null) {
    fun open(context: Context): InputStream = file?.inputStream()
        ?: context.contentResolver.openInputStream(requireNotNull(uri))
        ?: error("The source book is no longer accessible.")
}

private fun DocumentRepository.bookExportSource(document: SavedDocument): BookExportSource {
    val original = originalFile(document)
    if (original?.isFile == true) {
        return BookExportSource(bookExportName(document.title, detectExtension(original, document.sourceLabel, document.originalMimeType)), file = original)
    }
    if (document.originalFileName.startsWith("content://")) {
        val uri = Uri.parse(document.originalFileName)
        val readable = runCatching { appContext.contentResolver.openInputStream(uri)?.use { true } ?: false }.getOrDefault(false)
        if (readable) {
            val mime = appContext.contentResolver.getType(uri) ?: document.originalMimeType
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
                ?: detectExtensionFromNameOrType("", document.sourceLabel, mime)
            return BookExportSource(bookExportName(document.title, extension), uri = uri)
        }
    }
    // Catalog downloads contain real plain text. Never label extracted text as a PDF or EPUB.
    val text = File(docsDir, document.fileName)
    check(text.isFile) { "The book file is no longer available." }
    return BookExportSource(bookExportName(document.title, "txt"), file = text)
}

/** Saves through Android's document picker into the user's chosen storage, outside app data. */
@Composable
internal fun rememberSaveBookToPhone(document: SavedDocument): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingExtension by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun message(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    val launcher = rememberLauncherForActivityResult(CreateBookDocument()) { destination ->
        val id = pendingId
        val extension = pendingExtension
        pendingId = null
        pendingExtension = null
        if (destination != null && id != null) {
            busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val repository = DocumentRepository(context)
                        val saved = repository.loadDocuments().firstOrNull { it.id == id }
                            ?: error("The book was removed before it could be saved.")
                        val source = repository.bookExportSource(saved)
                        check(source.name.extension == extension) { "The original book is no longer accessible. Please try saving again." }
                        source.open(context).use { input ->
                            val output = context.contentResolver.openOutputStream(destination, "wt")
                                ?: error("Cannot write to this location.")
                            output.use { input.copyTo(it) }
                        }
                    }
                    message("Book saved to your chosen location.")
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    message("Could not save the book. Choose a writable location and try again.")
                } finally { busy = false }
            }
        }
    }
    return {
        if (!busy && pendingId == null) {
            busy = true
            scope.launch {
                try {
                    val source = withContext(Dispatchers.IO) { DocumentRepository(context).bookExportSource(document) }
                    pendingId = document.id
                    pendingExtension = source.name.extension
                    launcher.launch(source.name)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    pendingId = null
                    pendingExtension = null
                    message("This book cannot be saved right now. Please try again.")
                } finally { busy = false }
            }
        }
    }
}

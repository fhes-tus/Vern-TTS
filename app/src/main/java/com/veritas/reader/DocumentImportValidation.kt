package com.veritas.reader

import android.content.Context
import android.net.Uri
import java.io.File

internal data class ValidatedImport(val name: String, val mimeType: String, val format: ImportFormat, val sizeBytes: Long)

/** Call on IO, including in workers: picker filters and MIME metadata are only hints. */
internal fun validateDocumentImport(context: Context, uri: Uri): ValidatedImport {
    require(uri.scheme == "content" || uri.scheme == "file") { "Choose a file stored on your device or in a document provider." }
    val name = getDisplayName(context, uri).ifBlank { "Imported document" }
    val mime = context.contentResolver.getType(uri).orEmpty()
    val format = ImportFilePolicy.detect(name, mime) ?: throw IllegalArgumentException(ImportFilePolicy.UNSUPPORTED_MESSAGE)
    val size = if (uri.scheme == "file") File(requireNotNull(uri.path)).length()
        else context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
    require(size != 0L) { "This file is empty." }
    val byteLimit = ImportFilePolicy.byteLimit(format)
    require(size <= byteLimit) { "File exceeds the ${byteLimit / (1024 * 1024)} MB safety limit." }
    val header = context.contentResolver.openInputStream(uri)?.use { input ->
        val buffer = ByteArray(8192)
        var length = 0
        while (length < buffer.size) {
            val count = input.read(buffer, length, buffer.size - length)
            if (count < 0) break
            if (count == 0) break
            length += count
        }
        buffer.copyOf(length)
    } ?: error("The selected file is no longer available. Please choose it again.")
    ImportFilePolicy.validateHeader(format, header)
    return ValidatedImport(name, mime, format, size.coerceAtLeast(0))
}

package com.veritas.reader

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

/** A duplicate match requires both complete streams to fit their reported size. */
internal fun importContentFingerprint(
    expectedBytes: Long,
    checkActive: () -> Unit = {},
    open: () -> InputStream?
): ByteArray? {
    return readImportDigest(expectedBytes, checkActive, open)?.takeIf { it.first == expectedBytes }?.second
}

internal data class ImportFileIdentity(val size: Long, val digest: String)

internal fun importFileIdentity(context: Context, uri: Uri, validated: ValidatedImport, checkActive: () -> Unit): ImportFileIdentity? =
    readImportDigest(ImportFilePolicy.byteLimit(validated.format), checkActive) { context.contentResolver.openInputStream(uri) }
        ?.let { (size, bytes) -> ImportFileIdentity(size, bytes.toHexString()) }

private fun readImportDigest(maxBytes: Long, checkActive: () -> Unit, open: () -> InputStream?): Pair<Long, ByteArray>? {
    if (maxBytes <= 0L) return null
    return try {
        checkActive()
        open()?.use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            var size = 0L
            while (true) {
                checkActive()
                val count = input.read(buffer)
                if (count < 0) break
                size += count
                if (count == 0 || size > maxBytes) return null
                digest.update(buffer, 0, count)
            }
            checkActive()
            if (size > 0L) size to digest.digest() else null
        }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }
}

internal fun DocumentRepository.findImportedCopy(identity: ImportFileIdentity, checkActive: () -> Unit): SavedDocument? =
    loadDocuments().sortedBy { it.partial }.firstOrNull { document ->
        val original = originalFile(document)
        val reading = File(docsDir, document.fileName)
        original != null && original.length() == identity.size && reading.isFile && reading.length() > 0L &&
            importContentFingerprint(identity.size, checkActive, original::inputStream)?.toHexString() == identity.digest
    }

private class ActiveFileImport(val mutex: Mutex = Mutex(), var users: Int = 0)
private val activeFileImports = mutableMapOf<ImportFileIdentity, ActiveFileImport>()

/** Check for a saved copy and finish publishing under the same per-source gate. */
internal suspend fun <T> withImportIdentity(identity: ImportFileIdentity, action: suspend () -> T): T {
    val state = synchronized(activeFileImports) {
        activeFileImports.getOrPut(identity) { ActiveFileImport() }.also { it.users++ }
    }
    try {
        return state.mutex.withLock { action() }
    } finally {
        synchronized(activeFileImports) {
            state.users--
            if (state.users == 0) activeFileImports.remove(identity)
        }
    }
}

package com.veritas.reader

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.UUID
import kotlin.coroutines.coroutineContext

/** Publish only complete, app-owned attachments; copying never blocks the editor's UI thread. */
internal object NoteAttachmentStore {
    suspend fun copy(context: Context, uri: Uri, video: Boolean): File {
        val name = UUID.randomUUID().toString()
        val directory = File(context.filesDir, "notes_media")
        val staging = File(directory, ".$name.partial")
        var destination: File? = null
        try {
            return withContext(Dispatchers.IO) {
                check(directory.isDirectory || directory.mkdirs()) { "Could not create attachment folder" }
                val mime = context.contentResolver.getType(uri).orEmpty()
                val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
                    ?.takeIf { it.matches(Regex("[a-zA-Z0-9]{1,10}")) } ?: if (video) "mp4" else "jpg"
                val published = File(directory, "$name.$extension")
                destination = published
                val input = context.contentResolver.openInputStream(uri) ?: error("The selected attachment is unavailable")
                input.use { source ->
                    FileOutputStream(staging).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = source.read(buffer)
                            if (count < 0) break
                            if (count > 0) output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                }
                require(staging.length() > 0) { "The selected attachment is empty" }
                coroutineContext.ensureActive()
                Files.move(staging.toPath(), published.toPath())
                published
            }
        } catch (failure: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) { staging.delete(); destination?.delete() }
            throw failure
        }
    }

    suspend fun copyDocument(context: Context, uri: Uri): Triple<File, String, Long> {
        val name = UUID.randomUUID().toString()
        val directory = File(context.filesDir, "notes_media")
        val staging = File(directory, ".$name.partial")
        var destination: File? = null
        try {
            return withContext(Dispatchers.IO) {
                check(directory.isDirectory || directory.mkdirs()) { "Could not create attachment folder" }
                var originalName = ""
                var originalSize = 0L
                try {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIdx >= 0) originalName = cursor.getString(nameIdx) ?: ""
                            val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                            if (sizeIdx >= 0) originalSize = cursor.getLong(sizeIdx)
                        }
                    }
                } catch (_: Exception) {}

                val mime = context.contentResolver.getType(uri).orEmpty()
                val mimeExt = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
                val originalExt = if (originalName.contains('.')) originalName.substringAfterLast('.') else ""
                val extension = (if (originalExt.isNotBlank()) originalExt else mimeExt)
                    ?.takeIf { it.matches(Regex("[a-zA-Z0-9]{1,10}")) } ?: "bin"

                val published = File(directory, "$name.$extension")
                destination = published
                val input = context.contentResolver.openInputStream(uri) ?: error("The selected file is unavailable")
                input.use { source ->
                    FileOutputStream(staging).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = source.read(buffer)
                            if (count < 0) break
                            if (count > 0) output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                }
                require(staging.length() > 0) { "The selected file is empty" }
                coroutineContext.ensureActive()
                Files.move(staging.toPath(), published.toPath())
                val finalName = originalName.ifBlank { published.name }
                val finalSize = published.length()
                Triple(published, finalName, finalSize)
            }
        } catch (failure: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) { staging.delete(); destination?.delete() }
            throw failure
        }
    }
}

package com.veritas.reader

import android.content.Context
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Camera writes only to a temporary owned file; existing attachments are never removed. */
internal object NoteCameraCapture {
    fun create(context: Context): File {
        val directory = File(context.cacheDir, "note_camera").apply { mkdirs() }
        return File.createTempFile("capture_", ".jpg", directory)
    }

    private fun owned(context: Context, path: String): File? = File(path).canonicalFile.takeIf {
        it.parentFile == File(context.cacheDir, "note_camera").canonicalFile && it.name.startsWith("capture_")
    }

    fun discard(context: Context, path: String) { owned(context, path)?.delete() }

    suspend fun finish(context: Context, path: String, captured: Boolean): File? {
        val temporary = owned(context, path) ?: return null
        try {
            if (!captured || temporary.length() == 0L) return null
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", temporary)
            return NoteAttachmentStore.copy(context, uri, video = false)
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { temporary.delete() }
        }
    }
}

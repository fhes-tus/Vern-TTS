package com.veritas.reader

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Retain camera output across process death until the import worker copies the original. */
internal object ScanCaptureStore {
    fun create(context: Context): File {
        val directory = File(context.filesDir, "scan_imports").apply { check(isDirectory || mkdirs()) }
        // Recover abandoned captures without touching other app files or recent work.
        directory.listFiles()?.filter { it.name.startsWith("scan_") && it.extension == "jpg" &&
            it.lastModified() < System.currentTimeMillis() - 7 * 24 * 3600_000L }?.forEach { it.delete() }
        return File.createTempFile("scan_", ".jpg", directory)
    }

    private fun owned(context: Context, uri: Uri): File? = runCatching {
        if (uri.scheme != "file") return null
        val file = uri.path?.let { File(it).canonicalFile } ?: return null
        file.takeIf { it.parentFile == File(context.filesDir, "scan_imports").canonicalFile &&
            it.name.startsWith("scan_") && it.extension == "jpg" }
    }.getOrNull()

    fun cameraUri(context: Context, file: File): Uri {
        require(owned(context, Uri.fromFile(file)) != null)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun finish(context: Context, path: String, captured: Boolean): Uri? {
        val uri = Uri.fromFile(File(path))
        val file = owned(context, uri) ?: return null
        if (!captured || file.length() == 0L) {
            file.delete()
            return null
        }
        return uri
    }

    fun discard(context: Context, uri: Uri) { runCatching { owned(context, uri)?.delete() } }
}

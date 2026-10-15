package com.veritas.reader

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Share only managed media/originals/captures or explicitly staged exports. */
internal object SafeFileSharing {
    fun uri(context: Context, authority: String, file: File, displayName: String? = null): Uri {
        require(authority == "${context.packageName}.fileprovider") { "Invalid share provider." }
        val source = file.canonicalFile
        fun within(directory: File) = source.toPath().startsWith(directory.canonicalFile.toPath())
        val direct = within(File(context.filesDir, "notes_media")) || within(File(context.filesDir, "original_documents")) ||
            within(File(context.cacheDir, "note_camera")) || within(File(context.cacheDir, "shared_exports")) ||
            context.getExternalFilesDir(null)?.let { external ->
                within(File(external, "Music/VernExports")) || within(File(external, "Music/VeritasExports"))
            } == true
        val shared = if (direct) source else {
            val privateData = File(context.applicationInfo.dataDir).canonicalFile
            require(!source.toPath().startsWith(privateData.toPath()) || within(context.cacheDir)) {
                "This private app file cannot be shared."
            }
            require(source.isFile) { "Share file is missing." }
            val directory = File(context.cacheDir, "shared_exports").apply { mkdirs() }
            // Expire only old owned exports, never the original attachment.
            directory.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 24 * 3600_000L }?.forEach { it.delete() }
            val extension = source.extension.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) } ?: "bin"
            File.createTempFile("share_", ".$extension", directory).also { target ->
                try { source.inputStream().use { input -> target.outputStream().use { ResourceLimits.copy(input, it, ResourceLimits.BOOK_DOWNLOAD_BYTES) } } }
                catch (error: Throwable) { target.delete(); throw error }
            }
        }
        return FileProvider.getUriForFile(context, authority, shared, displayName ?: source.name)
    }
}

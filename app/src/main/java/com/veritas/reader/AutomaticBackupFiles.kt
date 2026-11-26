package com.veritas.reader

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.Serializable
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal enum class AutomaticBackupLocation { APP_STORAGE, EXTERNAL_COPY }

internal data class AutomaticBackupEntry(
    val location: AutomaticBackupLocation,
    val name: String,
    val date: LocalDate,
    val size: Long,
    val modifiedAt: Long
) : Serializable

internal object AutomaticBackupFiles {
    private const val KEEP_COUNT = 4
    private val names = Regex("(?:vern|veritas)_auto_backup_([0-9]{8})\\.json")
    private val order = compareByDescending<AutomaticBackupEntry> { it.date }
        .thenBy { it.location }
        .thenByDescending { it.modifiedAt }
        .thenBy { it.name }

    fun directory(context: Context, location: AutomaticBackupLocation): File? = when (location) {
        AutomaticBackupLocation.APP_STORAGE -> File(context.filesDir, "auto_backups")
        AutomaticBackupLocation.EXTERNAL_COPY -> context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?.let { File(it, "VernBackups") }
    }

    fun list(context: Context): List<AutomaticBackupEntry> = AutomaticBackupLocation.entries.flatMap { location ->
        directory(context, location)?.let { list(it, location) }.orEmpty()
    }.sortedWith(order)

    fun list(directory: File, location: AutomaticBackupLocation): List<AutomaticBackupEntry> =
        directory.listFiles().orEmpty().mapNotNull { file ->
            try {
                describe(directory, file, location)
            } catch (_: IOException) {
                null
            }
        }.sortedWith(order)

    private fun describe(directory: File, file: File, location: AutomaticBackupLocation): AutomaticBackupEntry? {
        val stamp = names.matchEntire(file.name)?.groupValues?.get(1) ?: return null
        val date = runCatching { LocalDate.parse(stamp, DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull() ?: return null
        if (!Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) ||
            file.canonicalFile.parentFile != directory.canonicalFile) return null
        val size = file.length().takeIf { it in 1..BackupRestoreSafety.MAX_JSON_BYTES } ?: return null
        return AutomaticBackupEntry(location, file.name, date, size, file.lastModified())
    }

    fun open(context: Context, entry: AutomaticBackupEntry): InputStream =
        open(directory(context, entry.location) ?: throw IOException("The backup location is unavailable."), entry)

    fun open(directory: File, entry: AutomaticBackupEntry): InputStream {
        require(names.matches(entry.name)) { "Invalid automatic backup name." }
        val file = File(directory, entry.name)
        fun matches() = describe(directory, file, entry.location) == entry
        if (!matches()) throw IOException("The selected backup changed or is no longer available.")
        val stream = file.inputStream()
        try {
            if (!matches()) throw IOException("The selected backup changed or is no longer available.")
            return stream
        } catch (error: Exception) {
            stream.close()
            throw error
        }
    }

    fun publish(
        directory: File,
        location: AutomaticBackupLocation,
        date: LocalDate,
        json: String,
        beforePublish: () -> Unit = {}
    ) {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Could not create the backup directory.")
        val stamp = date.format(DateTimeFormatter.BASIC_ISO_DATE)
        val destination = File(directory, "vern_auto_backup_$stamp.json")
        val temporary = File.createTempFile(".vern-backup-", ".tmp", directory)
        try {
            FileOutputStream(temporary).use { output ->
                val writer = output.writer(Charsets.UTF_8)
                writer.write(json)
                writer.flush()
                output.fd.sync()
            }
            BackupRestoreSafety.requireJsonSize(temporary.length())
            beforePublish()
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }

        list(directory, location).drop(KEEP_COUNT).forEach { old ->
            File(directory, old.name).delete()
        }
    }
}

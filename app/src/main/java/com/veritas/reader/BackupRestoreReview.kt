package com.veritas.reader

import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

internal enum class BackupReadingAction { ADD, MATCH, CONFLICT }

internal data class BackupReadingReview(val id: String, val title: String, val action: BackupReadingAction)

internal class BackupRestoreInspection(
    val snapshot: File,
    val sourceName: String,
    val createdAt: Long?,
    val isArchive: Boolean,
    val includesSettings: Boolean,
    val allowSettings: Boolean,
    val readings: List<BackupReadingReview>
) {
    val byteCount = snapshot.length()
    val newReadings = readings.count { it.action == BackupReadingAction.ADD }
    val matchingReadings = readings.count { it.action == BackupReadingAction.MATCH }
    private val conflicts = readings.filter { it.action == BackupReadingAction.CONFLICT }
    val conflictCount = conflicts.size
    val conflictTitles = conflicts.take(6).map { it.title }
}

internal class BackupReviewChangedException : IllegalStateException("The reading library changed after you reviewed this backup.")

internal fun backupReadingMatches(incoming: JSONObject, local: SavedDocument, readText: (SavedDocument) -> String): Boolean =
    incoming.optInt("sentenceIndexVersion", 1) == local.sentenceIndexVersion && readText(local) == incoming.optString("text")

internal fun classifyBackupReadings(
    root: JSONObject,
    localDocuments: List<SavedDocument>,
    readText: (SavedDocument) -> String
): List<BackupReadingReview> {
    val existingById = localDocuments.associateBy { it.id }
    val incoming = root.getJSONArray("documents")
    return (0 until incoming.length()).mapNotNull { index ->
        val reading = incoming.getJSONObject(index)
        if (reading.optString("text").isBlank()) return@mapNotNull null
        val id = reading.optString("id").trim()
        val local = existingById[id]
        val status = when {
            local == null -> BackupReadingAction.ADD
            backupReadingMatches(reading, local, readText) -> BackupReadingAction.MATCH
            else -> BackupReadingAction.CONFLICT
        }
        BackupReadingReview(id, (local?.title ?: reading.optString("title")).take(200), status)
    }
}

internal fun DocumentRepository.inspectBackup(
    snapshot: File,
    sourceName: String,
    checkActive: () -> Unit,
    allowSettings: Boolean = true
): BackupRestoreInspection {
    val (root, zip) = snapshot.inputStream().use { raw ->
        val input = BufferedInputStream(raw)
        val archive = isBackupZip(input)
        val json = if (archive) readArchiveManifest(input, checkActive) else readReviewJson(input, checkActive)
        BackupRestoreSafety.parseAndValidate(json) to archive
    }
    val settings = containsBackupDeviceSettings(root)
    require(allowSettings || !settings) { "This shared snapshot contains settings. Review it as a regular backup instead." }
    val readings = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
        classifyBackupReadings(root, loadDocuments()) {
            checkActive()
            readText(it)
        }
    }
    val createdAt = root.optLong("createdAt", 0).takeIf { it > 0 }
    return BackupRestoreInspection(snapshot, sourceName, createdAt, zip, settings, allowSettings, readings)
}

internal fun containsBackupDeviceSettings(root: JSONObject): Boolean =
    deviceSettingsSections.any(root::has)

private val deviceSettingsSections = listOf(
    "readerSettings", "voiceSettings", "narrationSettings", "notesSettings", "askAiSettings", "readingTrackingSettings"
)

internal fun stageBackupForReview(
    directory: File,
    source: () -> InputStream,
    expectedSha256: String?,
    checkActive: () -> Unit
): File {
    check(directory.isDirectory || directory.mkdirs()) { "Could not prepare backup review storage." }
    val cutoff = System.currentTimeMillis() - 24 * 60 * 60_000L
    directory.listFiles()?.filter { it.name.startsWith("review-") && it.lastModified() < cutoff }?.forEach { it.delete() }
    val snapshot = File.createTempFile("review-", ".backup", directory)
    try {
        source().use { raw ->
            val digest = expectedSha256?.let { MessageDigest.getInstance("SHA-256") }
            val input = BufferedInputStream(if (digest == null) raw else DigestInputStream(raw, digest))
            val archive = isBackupZip(input)
            require(!archive || digest == null) { "A shared snapshot must contain JSON data." }
            val limit = if (archive) {
                BackupRestoreSafety.MAX_ARCHIVE_BYTES + 32L * 1024 * 1024
            } else BackupRestoreSafety.MAX_JSON_BYTES
            snapshot.outputStream().use { output -> BackupRestoreSafety.copyBounded(input, output, limit, checkActive) }
            if (digest != null) {
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                require(actual.equals(expectedSha256, ignoreCase = true)) {
                    "The shared snapshot is incomplete or changed while it was being received."
                }
            }
        }
        return snapshot
    } catch (error: Throwable) {
        snapshot.delete()
        throw error
    }
}

internal fun isBackupZip(input: BufferedInputStream): Boolean {
    input.mark(2)
    val isZip = input.read() == 'P'.code && input.read() == 'K'.code
    input.reset()
    return isZip
}

private fun readReviewJson(input: InputStream, checkActive: () -> Unit): String {
    val output = ByteArrayOutputStream()
    BackupRestoreSafety.copyBounded(input, output, BackupRestoreSafety.MAX_JSON_BYTES, checkActive)
    return output.toString(Charsets.UTF_8.name())
}

private fun readArchiveManifest(input: InputStream, checkActive: () -> Unit): String {
    val discard = object : OutputStream() {
        override fun write(value: Int) = Unit
        override fun write(bytes: ByteArray, offset: Int, length: Int) = Unit
    }
    var remaining = BackupRestoreSafety.MAX_ARCHIVE_BYTES
    ZipInputStream(input).use { zip ->
        repeat(BackupRestoreSafety.MAX_ENTRIES) {
            val entry = zip.nextEntry ?: throw IllegalArgumentException("This ZIP does not contain a Vern backup.json.")
            if (!entry.isDirectory && entry.name.trimStart('/') == "backup.json") return readReviewJson(zip, checkActive)
            remaining -= BackupRestoreSafety.copyBounded(zip, discard, remaining, checkActive)
        }
    }
    throw IllegalArgumentException("The backup contains too many archive entries.")
}

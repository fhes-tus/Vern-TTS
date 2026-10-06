package com.veritas.reader

import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Validation happens before a restore changes files or metadata. */
internal object BackupRestoreSafety {
    const val MAX_JSON_BYTES = 64L * 1024 * 1024
    const val MAX_ARCHIVE_BYTES = 2L * 1024 * 1024 * 1024
    const val MAX_ENTRIES = 20_000

    fun requireFileName(name: String) {
        require(name.isNotBlank() && name != "." && name != ".." &&
            name.none { it == '/' || it == '\\' || it == ':' || it.isISOControl() }
        ) { "The backup contains an unsafe file name." }
    }

    fun parseAndValidate(raw: String): JSONObject {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_JSON_BYTES) { "Backup JSON is too large." }
        val root = runCatching { JSONObject(raw) }
            .getOrElse { throw IllegalArgumentException("This is not a valid Vern backup file.", it) }
        val schema = root.optString("schema")
        require(schema.isBlank() || schema == "veritas.reader.backup.v1") { "Unsupported backup schema: $schema" }
        val documents = root.optJSONArray("documents")
            ?: throw IllegalArgumentException("The backup does not contain a documents section.")
        require(documents.length() <= MAX_ENTRIES) { "The backup contains too many documents." }
        val ids = mutableSetOf<String>()
        for (index in 0 until documents.length()) {
            val document = documents.optJSONObject(index)
                ?: throw IllegalArgumentException("Invalid document at position ${index + 1}.")
            val id = document.optString("id").trim()
            if (id.isNotEmpty()) {
                requireFileName(id)
                require(ids.add(id)) { "The backup contains duplicate document IDs." }
            }
            val original = document.optString("originalFileName")
            // Legacy content URIs are references, not paths written by the restore.
            if (original.isNotBlank() && !original.startsWith("content://")) requireFileName(original)
            require(document.has("text") && document.opt("text") is String) { "A backed-up document is missing its text." }
        }
        val arrays = listOf("annotations", "queue", "readingLists", "readingHistory", "pronunciationRules",
            "documentNotes", "aiPromptTemplates", "aiPromptHistory", "generalNotes", "trackerDays", "flashcards", "noteMedia")
        arrays.forEach { key ->
            if (root.has(key)) {
                val array = root.optJSONArray(key) ?: throw IllegalArgumentException("Invalid backup section: $key")
                require(array.length() <= MAX_ENTRIES) { "Too many entries in $key." }
                for (i in 0 until array.length()) require(array.optJSONObject(i) != null) { "Invalid entry in $key." }
            }
        }
        val mediaNames = mutableSetOf<String>()
        val mediaPaths = mutableSetOf<String>()
        root.optJSONArray("noteMedia")?.let { media ->
            for (i in 0 until media.length()) {
                val entry = media.getJSONObject(i)
                val name = entry.optString("fileName")
                requireFileName(name)
                require(mediaNames.add(name)) { "Duplicate note attachment in backup." }
                val path = entry.optString("path")
                require(path.length in 1..4096 && path.none { it.isISOControl() } && mediaPaths.add(path)) { "Invalid note attachment reference." }
                requireFileName(path.substringAfterLast('/').substringAfterLast('\\'))
            }
        }
        listOf("readerSettings", "voiceSettings", "narrationSettings", "askAiSettings", "docReadingTimesThisMonth").forEach { key ->
            require(!root.has(key) || root.optJSONObject(key) != null) { "Invalid backup section: $key" }
        }
        return root
    }

    fun copyBounded(input: InputStream, output: OutputStream, limit: Long): Long {
        var total = 0L
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return total
            total += count
            require(total <= limit) { "The backup exceeds the supported size limit." }
            output.write(buffer, 0, count)
        }
    }
}

/** Keeps existing files until all staged output has been written and metadata has succeeded. */
internal class RestoreFileTransaction(private val root: File) : AutoCloseable {
    val id = UUID.randomUUID().toString()
    internal val staging = File(root, "restore_transactions/$id").apply {
        check(mkdirs()) { "Could not create restore staging directory." }
    }
    private data class Entry(val staged: File, val target: File, val previous: File, var published: Boolean = false)
    private val entries = mutableListOf<Entry>()
    private var committed = false

    fun stage(target: File, write: (OutputStream) -> Unit) {
        val canonical = target.canonicalFile
        require(canonical.toPath().startsWith(root.canonicalFile.toPath()) && canonical != root.canonicalFile) {
            "Unsafe restore destination."
        }
        require(entries.none { it.target == canonical }) { "Duplicate restore destination." }
        val staged = File(staging, "new_${entries.size}")
        java.io.FileOutputStream(staged).use { output -> write(output); output.fd.sync() }
        val previous = File(staging, "old_${entries.size}")
        if (canonical.exists()) {
            canonical.copyTo(previous)
            java.io.RandomAccessFile(previous, "rw").use { it.fd.sync() }
        }
        entries.add(Entry(staged, canonical, previous))
    }

    fun publish() {
        val manifest = JSONObject().put("id", id).put("entries", JSONArray().apply {
            entries.forEach { entry -> put(JSONObject()
                .put("target", root.canonicalFile.toPath().relativize(entry.target.toPath()).toString())
                .put("previous", entry.previous.name)
                .put("hadPrevious", entry.previous.exists())) }
        })
        writeDurable(File(staging, "manifest.json"), manifest.toString())
        entries.forEach { entry ->
            entry.target.parentFile?.mkdirs()
            // A failed move can still have changed the destination on some file
            // systems. Include it in rollback before attempting publication.
            entry.published = true
            Files.move(entry.staged.toPath(), entry.target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun commit() { committed = true }

    override fun close() {
        if (!committed) {
            entries.asReversed().filter { it.published }.forEach { entry ->
                if (entry.previous.exists()) {
                    Files.copy(entry.previous.toPath(), entry.target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                } else {
                    check(!entry.target.exists() || entry.target.delete()) { "Could not roll back restored file." }
                }
            }
        }
        staging.deleteRecursively()
        staging.parentFile?.delete() // only succeeds when no other transaction remains
    }

    companion object {
        internal fun writeDurable(file: File, text: String) {
            val temp = File(file.parentFile, "${file.name}.tmp")
            java.io.FileOutputStream(temp).use { it.write(text.toByteArray(Charsets.UTF_8)); it.fd.sync() }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

        fun recoverPending(root: File, isCommitted: (String) -> Boolean, restorePreferences: (File) -> Unit = {}) {
            val parent = File(root, "restore_transactions")
            parent.listFiles()?.filter { it.isDirectory }?.forEach { folder ->
                val manifestFile = File(folder, "manifest.json")
                if (!manifestFile.exists()) { folder.deleteRecursively(); return@forEach }
                require(manifestFile.length() <= BackupRestoreSafety.MAX_JSON_BYTES) { "Recovery journal is too large." }
                val manifest = JSONObject(manifestFile.readText())
                val id = manifest.getString("id")
                require(id == folder.name) { "Invalid recovery journal identity." }
                if (!isCommitted(id)) {
                    val entries = manifest.getJSONArray("entries")
                    for (index in entries.length() - 1 downTo 0) {
                        val entry = entries.getJSONObject(index)
                        val target = File(root, entry.getString("target")).canonicalFile
                        require(target.toPath().startsWith(root.canonicalFile.toPath()) && target != root.canonicalFile) { "Unsafe recovery destination." }
                        val previousName = entry.getString("previous")
                        BackupRestoreSafety.requireFileName(previousName)
                        val previous = File(folder, previousName)
                        if (entry.getBoolean("hadPrevious")) {
                            check(previous.isFile) { "Missing previous file during recovery." }
                            target.parentFile?.mkdirs()
                            val temporary = File(target.parentFile, ".restore_recovery_$id")
                            previous.copyTo(temporary, overwrite = true)
                            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        } else check(!target.exists() || target.delete()) { "Could not recover restored file." }
                    }
                    restorePreferences(folder)
                }
                check(folder.deleteRecursively()) { "Could not finish restore recovery." }
            }
            parent.delete()
        }
    }
}

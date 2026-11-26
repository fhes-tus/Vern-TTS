package com.veritas.reader

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class SharedBackupFolderEntry(
    val uri: Uri,
    val name: String,
    val expectedSha256: String,
    val createdAt: Instant,
    val sizeBytes: Long?,
    internal val folderUri: Uri
)

internal data class SharedBackupFolderListing(
    val entries: List<SharedBackupFolderEntry>,
    val providerLoading: Boolean
)

internal data class SharedBackupSnapshotName(val createdAt: Instant, val sha256: String) {
    companion object {
        private val timestamp = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmssSSS'Z'", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT)
        private val sha256Pattern = Regex("[0-9a-f]{64}")
        private val filePattern = Regex("vern-sync-([0-9]{8}T[0-9]{9}Z)-[0-9a-f]{32}-([0-9a-f]{64})\\.json")

        fun create(createdAt: Instant, sha256: String): String {
            require(sha256Pattern.matches(sha256))
            val date = timestamp.format(createdAt.atOffset(ZoneOffset.UTC))
            return "vern-sync-$date-${UUID.randomUUID().toString().replace("-", "")}-$sha256.json"
        }

        fun parse(name: String): SharedBackupSnapshotName? {
            val match = filePattern.matchEntire(name) ?: return null
            val createdAt = try {
                LocalDateTime.parse(match.groupValues[1], timestamp).toInstant(ZoneOffset.UTC)
            } catch (_: DateTimeParseException) {
                return null
            }
            return SharedBackupSnapshotName(createdAt, match.groupValues[2])
        }
    }
}

internal object SharedBackupFolder {
    private const val MIME_TYPE = "application/json"
    private const val MAX_CHILDREN = 4_096
    private const val MAX_DOCUMENT_ID = 4_096
    private val acceptedMimeTypes = setOf(MIME_TYPE, "text/plain", "application/octet-stream")
    private val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_FLAGS)

    private class Folder(val uri: Uri) {
        val authority = requireNotNull(uri.authority)
        val id: String

        init {
            require(uri.scheme == ContentResolver.SCHEME_CONTENT && DocumentsContract.isTreeUri(uri))
            id = DocumentsContract.getTreeDocumentId(uri)
            require(id.length in 1..MAX_DOCUMENT_ID && uri == DocumentsContract.buildTreeDocumentUri(authority, id))
        }

        val root: Uri get() = document(id)
        val children: Uri get() = DocumentsContract.buildChildDocumentsUriUsingTree(uri, id)
        fun document(documentId: String): Uri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId)
    }

    suspend fun list(context: Context, folderUri: Uri): SharedBackupFolderListing = withContext(Dispatchers.IO) {
        val io = currentCoroutineContext()
        val folder = Folder(folderUri)
        val resolver = context.contentResolver
        requireDirectory(resolver, folder)
        queryChildren(resolver, folder) { cursor ->
            val entries = mutableListOf<SharedBackupFolderEntry>()
            var scanned = 0
            while (cursor.moveToNext()) {
                io.ensureActive()
                require(++scanned <= MAX_CHILDREN) { "Choose a folder with fewer files." }
                entry(cursor, folder)?.let(entries::add)
            }
            SharedBackupFolderListing(
                entries.distinctBy { it.uri }.sortedByDescending { it.createdAt },
                cursor.extras.getBoolean(DocumentsContract.EXTRA_LOADING)
            )
        }
    }

    suspend fun send(context: Context, folderUri: Uri, json: String): SharedBackupFolderEntry = withContext(Dispatchers.IO) {
        val io = currentCoroutineContext()
        val bytes = json.toByteArray(Charsets.UTF_8)
        BackupRestoreSafety.requireJsonSize(bytes.size.toLong())
        require(bytes.isNotEmpty())
        val expected = MessageDigest.getInstance("SHA-256").digest(bytes).hex()
        val createdAt = Instant.ofEpochMilli(Instant.now().toEpochMilli())
        val name = SharedBackupSnapshotName.create(createdAt, expected)
        val folder = Folder(folderUri)
        val resolver = context.contentResolver
        requireDirectory(resolver, folder)
        io.ensureActive()
        val created = checkNotNull(DocumentsContract.createDocument(resolver, folder.root, MIME_TYPE, name)) {
            "The selected folder could not create the snapshot."
        }
        // Rebuild the returned document as a descendant of the selected tree. Never write a bare provider URI.
        val id = DocumentsContract.getDocumentId(created)
        require(id != folder.id && id.length in 1..MAX_DOCUMENT_ID)
        val scopedUri = folder.document(id)
        require(created == scopedUri || created == DocumentsContract.buildDocumentUri(folder.authority, id))
        requireNewDocument(resolver, scopedUri, id, name)
        checkNotNull(resolver.openOutputStream(scopedUri)) { "The snapshot could not be opened for writing." }.use { output ->
            var offset = 0
            while (offset < bytes.size) {
                io.ensureActive()
                val count = minOf(64 * 1024, bytes.size - offset)
                output.write(bytes, offset, count)
                offset += count
            }
        }
        io.ensureActive()
        checkNotNull(resolver.openInputStream(scopedUri)) { "The saved snapshot could not be reopened." }.use { input ->
            verifyWrittenBytes(input, bytes.size.toLong(), expected) { io.ensureActive() }
        }
        SharedBackupFolderEntry(scopedUri, name, expected, createdAt, bytes.size.toLong(), folderUri)
    }

    /** The caller must hash the same bounded bytes it stages for review. */
    fun open(context: Context, selected: SharedBackupFolderEntry): InputStream {
        val folder = Folder(selected.folderUri)
        val id = DocumentsContract.getDocumentId(selected.uri)
        require(id != folder.id && id.length in 1..MAX_DOCUMENT_ID && selected.uri == folder.document(id))
        val resolver = context.contentResolver
        requireDirectory(resolver, folder)
        val current = queryChildren(resolver, folder) { cursor ->
            var scanned = 0
            while (cursor.moveToNext()) {
                require(++scanned <= MAX_CHILDREN) { "Choose a folder with fewer files." }
                if (cursor.string(Document.COLUMN_DOCUMENT_ID) == id) return@queryChildren entry(cursor, folder)
            }
            null
        }
        require(current != null && current.name == selected.name && current.expectedSha256 == selected.expectedSha256) {
            "The selected snapshot is unavailable or has changed."
        }
        return checkNotNull(resolver.openInputStream(current.uri)) { "The selected snapshot could not be opened." }
    }

    private fun requireDirectory(resolver: ContentResolver, folder: Folder) {
        require(resolver.getType(folder.root) == Document.MIME_TYPE_DIR) { "Choose a document folder." }
    }

    private fun requireNewDocument(resolver: ContentResolver, uri: Uri, id: String, name: String) {
        val matches = resolver.query(uri, projection, null, null, null)?.use { cursor ->
            cursor.moveToFirst() && cursor.string(Document.COLUMN_DOCUMENT_ID) == id &&
                cursor.string(Document.COLUMN_DISPLAY_NAME) == name &&
                cursor.string(Document.COLUMN_MIME_TYPE) in acceptedMimeTypes &&
                (cursor.long(Document.COLUMN_SIZE) ?: 0L) <= 0L &&
                (cursor.long(Document.COLUMN_FLAGS) ?: 0L) and Document.FLAG_VIRTUAL_DOCUMENT.toLong() == 0L
        }
        require(matches == true) { "The provider could not create a uniquely named snapshot." }
    }

    private fun <T> queryChildren(resolver: ContentResolver, folder: Folder, read: (Cursor) -> T): T =
        checkNotNull(resolver.query(folder.children, projection, null, null, null)) { "The selected folder is unavailable." }.use(read)

    private fun entry(cursor: Cursor, folder: Folder): SharedBackupFolderEntry? {
        val id = cursor.string(Document.COLUMN_DOCUMENT_ID)?.takeIf { it != folder.id && it.length in 1..MAX_DOCUMENT_ID } ?: return null
        val name = cursor.string(Document.COLUMN_DISPLAY_NAME) ?: return null
        val parsed = SharedBackupSnapshotName.parse(name) ?: return null
        if (cursor.string(Document.COLUMN_MIME_TYPE) !in acceptedMimeTypes) return null
        val flags = cursor.long(Document.COLUMN_FLAGS) ?: 0L
        if (flags and (Document.FLAG_VIRTUAL_DOCUMENT or Document.FLAG_PARTIAL).toLong() != 0L) return null
        val size = cursor.long(Document.COLUMN_SIZE)?.takeIf { it >= 0 }
        if (size != null && size !in 1..BackupRestoreSafety.MAX_JSON_BYTES) return null
        return SharedBackupFolderEntry(folder.document(id), name, parsed.sha256, parsed.createdAt, size, folder.uri)
    }

    private fun Cursor.string(column: String): String? = getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getString)

    private fun Cursor.long(column: String): Long? = getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getLong)

    internal fun verifyWrittenBytes(input: InputStream, length: Long, hash: String, checkActive: () -> Unit) {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            checkActive()
            val count = input.read(buffer)
            if (count < 0) break
            check(count > 0) { "The provider returned an empty read before the snapshot ended." }
            total += count
            check(total <= length) { "The provider returned unexpected snapshot data." }
            digest.update(buffer, 0, count)
        }
        check(total == length && digest.digest().hex() == hash) { "The provider returned an incomplete or changed snapshot." }
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(Locale.ROOT, it) }
}

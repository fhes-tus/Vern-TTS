package com.veritas.reader

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Slideshow
import java.io.File
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

enum class VeritasBrowserTab(val label: String, val emoji: String) {
    ALL("ALL", "📁"),
    BOOKS("EPUB", "📕"),
    PDF("PDF", "📄"),
    DOC("DOCX", "📘"),
    SLIDES("PPTX", "📙"),
    HTML("WEB", "🌐"),
    TXT("TXT", "📝"),
    OCR("OCR", "📷");

    val icon: androidx.compose.ui.graphics.vector.ImageVector
        get() = when (this) {
            ALL -> Icons.Outlined.Folder
            BOOKS -> Icons.Outlined.Book
            PDF -> Icons.Outlined.PictureAsPdf
            DOC -> Icons.Outlined.Description
            SLIDES -> Icons.Outlined.Slideshow
            HTML -> Icons.Outlined.Language
            TXT -> Icons.AutoMirrored.Outlined.Article
            OCR -> Icons.Outlined.PhotoCamera
        }
}

enum class VeritasBrowserSort(val label: String) {
    NAME("File name"),
    DATE("Date/time"),
    SIZE("Size"),
    PATH("Path")
}

data class VeritasBrowserRoot(
    val uri: Uri,
    val label: String
)

data class VeritasBrowserLocation(
    val rootLabel: String,
    val relativePath: String = "",
    val filePath: String? = null,
    val rootUri: Uri? = null,
    val documentId: String? = null
) {
    val label: String
        get() = if (relativePath.isBlank()) rootLabel else "$rootLabel/$relativePath"
}

data class VeritasBrowserFile(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val rootLabel: String,
    val relativePath: String,
    val type: VeritasBrowserTab = VeritasBrowserTab.ALL,
    val isDirectory: Boolean = false,
    val isSupported: Boolean = true,
    val targetLocation: VeritasBrowserLocation? = null,
    val filePath: String? = null
)

data class VeritasFileBrowserScanResult(
    val files: List<VeritasBrowserFile>,
    val location: VeritasBrowserLocation? = null,
    val diagnostics: List<String> = emptyList()
)

object VeritasFileBrowserScanner {
    private val childProjection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED
    )
    private val hiddenSafExtensions = setOf(
        "bin", "apk", "exe", "so", "class", "dex", "tmp", "temp", "db", "sqlite", "sys", "dll", "dat"
    )

    fun persistedRoots(
        context: Context,
        knownRoots: List<VeritasBrowserRoot> = emptyList(),
        checkCancelled: () -> Unit = {}
    ): List<VeritasBrowserRoot> {
        val knownLabels = knownRoots.associate { it.uri to it.label }
        return context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { permission ->
                checkCancelled()
                VeritasBrowserRoot(
                    uri = permission.uri,
                    label = knownLabels[permission.uri] ?: displayNameForRoot(context, permission.uri)
                )
            }
            .distinctBy { it.uri }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    fun initialLocation(
        context: Context,
        roots: List<VeritasBrowserRoot>,
        includeAllFilesAccess: Boolean
    ): VeritasBrowserLocation? {
        if (includeAllFilesAccess) {
            val storageRoot = Environment.getExternalStorageDirectory()
            return VeritasBrowserLocation(
                rootLabel = "Phone storage",
                filePath = storageRoot.absolutePath
            )
        }
        val root = roots.firstOrNull() ?: return null
        return VeritasBrowserLocation(
            rootLabel = root.label,
            rootUri = root.uri,
            documentId = runCatching { DocumentsContract.getTreeDocumentId(root.uri) }.getOrNull()
        )
    }

    fun scan(
        context: Context,
        roots: List<VeritasBrowserRoot>,
        includeAllFilesAccess: Boolean = false,
        location: VeritasBrowserLocation? = null,
        onProgress: ((VeritasFileBrowserScanResult) -> Unit)? = null,
        checkCancelled: () -> Unit = {}
    ): VeritasFileBrowserScanResult {
        val diagnostics = mutableListOf<String>()
        val activeLocation = location ?: initialLocation(context, roots, includeAllFilesAccess)
        if (activeLocation == null) {
            return VeritasFileBrowserScanResult(
                emptyList(),
                null,
                listOf("Grant All Files access or approve a folder to browse files.")
            )
        }
        val progress = ScanProgress(onProgress?.let { publish ->
            { files -> publish(VeritasFileBrowserScanResult(files, activeLocation, diagnostics.toList())) }
        })
        val entries = when {
            activeLocation.filePath != null -> listFileDirectory(context, activeLocation, diagnostics,
                progress, checkCancelled)
            activeLocation.rootUri != null && activeLocation.documentId != null -> {
                val root = roots.firstOrNull { it.uri == activeLocation.rootUri }
                if (root == null) {
                    diagnostics.add("This approved folder is no longer available. Add it again from Folders to scan.")
                    emptyList()
                } else {
                    listSafDirectory(
                        context = context,
                        root = root,
                        location = activeLocation,
                        diagnostics = diagnostics,
                        progress = progress,
                        checkCancelled = checkCancelled
                    )
                }
            }

            else -> emptyList()
        }
        return VeritasFileBrowserScanResult(entries, activeLocation, diagnostics)
    }

    private class ScanProgress(private val publish: ((List<VeritasBrowserFile>) -> Unit)?) {
        private var publishedCount = -1
        private var lastPublished = 0L
        private val canonicalPaths = HashMap<String, String>()

        fun canonicalPath(path: String): String = canonicalPaths.getOrPut(path) { canonicalBrowserPath(path) }

        fun snapshot(entries: List<VeritasBrowserFile>): List<VeritasBrowserFile> =
            deduplicateBrowserFiles(entries, ::canonicalPath)

        fun report(count: Int, immediate: Boolean = false, entries: () -> List<VeritasBrowserFile>) {
            if (publish == null || count == publishedCount) return
            val now = SystemClock.elapsedRealtime()
            if (publishedCount >= 0 && !immediate && now - lastPublished < PROGRESS_INTERVAL_MS) return
            val files = entries()
            publishedCount = count
            lastPublished = now
            if (files.isNotEmpty()) publish(files)
        }
    }

    /**
     * Every readable storage volume, not just the built-in one. getExternalFilesDirs reports
     * one entry per mounted volume, including SD cards and USB OTG; four parents up from the
     * app-private directory is the volume root, which All Files access can read. The browser
     * previously clamped everything to primary storage, so a document on an SD card could not
     * be reached at all.
     */
    private fun storageVolumeRoots(context: Context): List<File> {
        val seen = LinkedHashSet<String>()
        val roots = mutableListOf<File>()

        fun addRoot(dir: File?) {
            if (dir == null) return
            val canonical = runCatching { dir.canonicalFile }.getOrDefault(dir)
            if (canonical.exists() && canonical.isDirectory && canonical.canRead()) {
                if (seen.add(canonical.absolutePath.lowercase(Locale.ROOT))) {
                    roots.add(canonical)
                }
            }
        }

        runCatching { Environment.getExternalStorageDirectory() }.getOrNull()?.let { addRoot(it) }
        runCatching {
            context.getExternalFilesDirs(null).filterNotNull().forEach { dir ->
                var candidate: File? = dir
                repeat(4) { candidate = candidate?.parentFile }
                addRoot(candidate)
            }
        }
        return roots.ifEmpty { listOf(Environment.getExternalStorageDirectory()) }
    }

    /**
     * Streams files already indexed by MediaStore across mounted volumes. The storage walk
     * still discovers supported files the index has not picked up.
     */
    private fun queryDeviceWideFiles(
        context: Context,
        diagnostics: MutableList<String>,
        results: MutableList<VeritasBrowserFile>,
        checkCancelled: () -> Unit,
        canonicalPathFor: (String) -> String,
        onFileFound: (VeritasBrowserTab) -> Unit
    ) {
        val projection = buildList {
            add(MediaStore.Files.FileColumns._ID)
            add(MediaStore.Files.FileColumns.DISPLAY_NAME)
            add(MediaStore.Files.FileColumns.MIME_TYPE)
            add(MediaStore.Files.FileColumns.SIZE)
            add(MediaStore.Files.FileColumns.DATE_MODIFIED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Files.FileColumns.RELATIVE_PATH)
            }
            add(MediaStore.Files.FileColumns.DATA)
        }.toTypedArray()
        var docCount = 0
        var imageCount = 0
        val seen = HashSet<String>()
        // Every mounted volume, collapsing external aggregate if present
        val rawVolumes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { MediaStore.getExternalVolumeNames(context) }
                .getOrDefault(setOf(EXTERNAL_AGGREGATE_VOLUME))
                .ifEmpty { setOf(EXTERNAL_AGGREGATE_VOLUME) }
        } else {
            setOf(EXTERNAL_AGGREGATE_VOLUME)
        }
        val volumes = if (rawVolumes.contains(EXTERNAL_AGGREGATE_VOLUME)) {
            setOf(EXTERNAL_AGGREGATE_VOLUME)
        } else {
            rawVolumes
        }
        volumes.forEach { volume ->
            checkCancelled()
            if (docCount >= MAX_DOCUMENT_RESULTS && imageCount >= MAX_IMAGE_RESULTS) return
            val uri = runCatching { MediaStore.Files.getContentUri(volume) }.getOrNull() ?: return@forEach
            runCatching {
                context.contentResolver.query(
                    uri, projection, null, null,
                    "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                    val pathCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
                    val dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                    while (cursor.moveToNext()) {
                        checkCancelled()
                        val name = cursor.getString(nameCol) ?: continue
                        val mime = cursor.getString(mimeCol).orEmpty()
                        val type = fileTypeFor(name, mime) ?: continue
                        val isImage = type == VeritasBrowserTab.OCR
                        if (isImage && imageCount >= MAX_IMAGE_RESULTS ||
                            !isImage && docCount >= MAX_DOCUMENT_RESULTS
                        ) continue
                        val dataPath = if (dataCol >= 0 && !cursor.isNull(dataCol)) cursor.getString(dataCol) else null
                        val diskFile = dataPath?.let(::File)
                        val relative = if (pathCol >= 0 && !cursor.isNull(pathCol)) {
                            cursor.getString(pathCol).orEmpty().trimEnd('/')
                        } else {
                            diskFile?.parent.orEmpty()
                        }

                        // If the path points to a file that doesn't exist on disk, skip stale MediaStore records
                        if (diskFile != null && !diskFile.exists()) continue

                        val fileUri = if (diskFile != null) {
                            Uri.fromFile(diskFile)
                        } else {
                            ContentUris.withAppendedId(uri, cursor.getLong(idCol))
                        }

                        val fullRelative = if (relative.isNotBlank()) {
                            if (relative.endsWith(name, ignoreCase = true)) relative else "$relative/$name"
                        } else {
                            name
                        }

                        val dedupKey = dataPath?.let(canonicalPathFor)
                            ?: "${fullRelative.lowercase(Locale.ROOT)}/$name"
                        if (!seen.add(dedupKey)) continue

                        val browserFile = VeritasBrowserFile(
                            uri = fileUri,
                            name = name,
                            mimeType = mime.ifBlank { mimeTypeForFileName(name) },
                            sizeBytes = cursor.getLong(sizeCol).takeIf { it > 0L } ?: (diskFile?.length() ?: 0L),
                            // MediaStore stores seconds; the rest of the browser uses millis.
                            modifiedAt = cursor.getLong(dateCol) * 1000L,
                            rootLabel = "Phone storage",
                            relativePath = fullRelative,
                            type = type,
                            isDirectory = false,
                            isSupported = true,
                            filePath = diskFile?.absolutePath
                        )
                        results.add(browserFile)
                        if (isImage) imageCount++ else docCount++
                        onFileFound(type)
                        if (docCount >= MAX_DOCUMENT_RESULTS && imageCount >= MAX_IMAGE_RESULTS) break
                    }
                }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                diagnostics.add("Could not index $volume: ${e.message ?: "unavailable"}.")
            }
        }
    }

    private fun listFileDirectory(
        context: Context,
        location: VeritasBrowserLocation,
        diagnostics: MutableList<String>,
        progress: ScanProgress,
        checkCancelled: () -> Unit
    ): List<VeritasBrowserFile> {
        val storageRoot = Environment.getExternalStorageDirectory()
        val volumeRoots = storageVolumeRoots(context)
        val current = location.filePath?.let(::File) ?: storageRoot
        val volumeRoot = volumeRoots.firstOrNull {
            current == it || current.absolutePath.startsWith(it.absolutePath + File.separator)
        }
        val safeCurrent = if (volumeRoot == null) storageRoot else current
        val effectiveVolumeRoot = volumeRoot ?: storageRoot
        if (!safeCurrent.exists()) {
            diagnostics.add("${location.label} no longer exists.")
            return emptyList()
        }
        val children = runCatching { safeCurrent.listFiles().orEmpty() }.getOrElse { error ->
            diagnostics.add("Android blocked access to ${location.label}: ${error.message ?: "folder is protected"}.")
            emptyArray()
        }
        if (children.isEmpty() && safeCurrent.isDirectory && !safeCurrent.canRead()) {
            diagnostics.add("Android protects this folder. Shared storage can be browsed, but private system/app folders may remain unavailable.")
        }
        val folders = mutableListOf<VeritasBrowserFile>()
        val documents = mutableListOf<VeritasBrowserFile>()
        val images = mutableListOf<VeritasBrowserFile>()
        val indexedFiles = mutableListOf<VeritasBrowserFile>()
        var immediateFiles = emptyMap<String?, VeritasBrowserFile>()
        var foundDocument = false
        fun report(immediate: Boolean = false) {
            val count = folders.size + documents.size + indexedFiles.size + images.size + immediateFiles.size
            progress.report(count, immediate) {
                progress.snapshot(folders + documents + indexedFiles + images + immediateFiles.values)
            }
        }
        fun limitsReached() = documents.size >= MAX_DOCUMENT_RESULTS && images.size >= MAX_IMAGE_RESULTS

        fun reportFile(type: VeritasBrowserTab) {
            val firstDocument = !foundDocument && type != VeritasBrowserTab.OCR
            if (firstDocument) foundDocument = true
            report(immediate = firstDocument || documents.size + indexedFiles.size + images.size == 1)
        }

        fun addFile(child: File, publish: Boolean, alreadyRead: VeritasBrowserFile? = null) {
            val name = child.name
            val type = alreadyRead?.type ?: fileTypeFor(name, "") ?: return
            val isImage = type == VeritasBrowserTab.OCR
            val files = if (isImage) images else documents
            if (files.size >= (if (isImage) MAX_IMAGE_RESULTS else MAX_DOCUMENT_RESULTS)) return
            files.add(alreadyRead ?: VeritasBrowserFile(
                uri = Uri.fromFile(child),
                name = name,
                mimeType = mimeTypeForFileName(name),
                sizeBytes = child.length(),
                modifiedAt = child.lastModified(),
                rootLabel = location.rootLabel,
                relativePath = child.relativeToOrSelf(effectiveVolumeRoot).path.replace(File.separatorChar, '/'),
                type = type,
                filePath = child.absolutePath
            ))
            if (publish) reportFile(type)
        }

        fun walk(directory: File, depth: Int) {
            checkCancelled()
            if (depth > MAX_SCAN_DEPTH || limitsReached() ||
                shouldSkipRecursiveDirectory(directory, effectiveVolumeRoot)
            ) return
            val children = directory.listFiles() ?: return
            for (child in children) {
                checkCancelled()
                if (limitsReached()) break
                if (child.isDirectory) {
                    if (depth < MAX_SCAN_DEPTH) walk(child, depth + 1)
                } else {
                    addFile(child, publish = true)
                }
            }
        }

        // Publish the current folder and its files before querying or walking the rest of storage.
        val skipCurrent = shouldSkipRecursiveDirectory(safeCurrent, effectiveVolumeRoot)
        val subdirectories = mutableSetOf<File>()
        for (child in children) {
            checkCancelled()
            if (!child.isDirectory) {
                if (!skipCurrent) addFile(child, publish = false)
                continue
            }
            subdirectories.add(child)
            if (child.name.startsWith(".")) continue
            val relativePath = child.relativeToOrSelf(effectiveVolumeRoot).path.replace(File.separatorChar, '/')
            val readable = child.canRead()
            folders.add(VeritasBrowserFile(
                uri = Uri.fromFile(child),
                name = child.name.ifBlank { "Folder" },
                mimeType = DocumentsContract.Document.MIME_TYPE_DIR,
                sizeBytes = 0L,
                modifiedAt = child.lastModified(),
                rootLabel = location.rootLabel,
                relativePath = relativePath,
                isDirectory = true,
                isSupported = readable,
                filePath = child.absolutePath,
                targetLocation = if (readable) {
                    VeritasBrowserLocation(location.rootLabel, relativePath, child.absolutePath)
                } else null
            ))
        }
        foundDocument = documents.isNotEmpty()
        immediateFiles = (documents + images).associateBy { it.filePath }
        documents.clear()
        images.clear()
        report(immediate = true)
        if (volumeRoots.any { it == safeCurrent }) {
            queryDeviceWideFiles(context, diagnostics, indexedFiles, checkCancelled, progress::canonicalPath, ::reportFile)
        }
        if (!skipCurrent) {
            // Keep the established depth-first order for the capped result sets. The
            // initial folder preview is available independently of that order.
            for (child in children) {
                checkCancelled()
                if (limitsReached()) break
                if (child in subdirectories) {
                    walk(child, depth = 1)
                } else {
                    addFile(child, publish = true, alreadyRead = immediateFiles[child.absolutePath])
                }
            }
        }
        checkCancelled()
        val orderedIndex = indexedFiles.sortedBy { it.type == VeritasBrowserTab.OCR }
        return progress.snapshot(folders + documents + orderedIndex + images)
    }

    internal fun deduplicateBrowserFiles(
        files: List<VeritasBrowserFile>,
        canonicalPathFor: (String) -> String = ::canonicalBrowserPath
    ): List<VeritasBrowserFile> {
        return deduplicateFiles(
            items = files,
            isDirectory = { it.isDirectory },
            filePath = { it.filePath },
            uriString = { runCatching { it.uri.toString() }.getOrDefault("") },
            name = { it.name },
            sizeBytes = { it.sizeBytes },
            relativePath = { it.relativePath },
            targetLocationFilePath = { it.targetLocation?.filePath },
            canonicalPathFor = canonicalPathFor
        )
    }

    internal fun <T> deduplicateFiles(
        items: List<T>,
        isDirectory: (T) -> Boolean,
        filePath: (T) -> String?,
        uriString: (T) -> String,
        name: (T) -> String,
        sizeBytes: (T) -> Long,
        relativePath: (T) -> String,
        targetLocationFilePath: (T) -> String? = { null },
        canonicalPathFor: (String) -> String = ::canonicalBrowserPath
    ): List<T> {
        val seenDirectories = HashSet<String>()
        val result = mutableListOf<T>()

        val seenPaths = HashMap<String, Int>()
        val seenNameFolders = HashMap<String, Int>()
        val seenNameSizes = HashMap<String, Int>()
        val seenUris = HashMap<String, Int>()
        val folderForIndex = HashMap<Int, String>()

        for (item in items) {
            val isDir = isDirectory(item)
            val path = filePath(item)
            val uriStr = uriString(item)
            val itemName = name(item)
            val size = sizeBytes(item)

            if (isDir) {
                val dirTarget = targetLocationFilePath(item)
                val rawDir = path ?: dirTarget ?: uriStr
                val dirKey = "dir:" + if (path != null || dirTarget != null) canonicalPathFor(rawDir) else rawDir
                if (seenDirectories.add(dirKey)) {
                    result.add(item)
                }
                continue
            }

            val canonicalPath = when {
                !path.isNullOrBlank() -> canonicalPathFor(path)
                uriStr.startsWith("file://", ignoreCase = true) -> {
                    val p = uriStr.removePrefix("file://").substringBefore('?').substringBefore('#')
                    canonicalPathFor(p)
                }
                else -> null
            }

            val normName = itemName.trim().lowercase(Locale.ROOT)
            val rawRel = relativePath(item).replace('\\', '/').trim('/')
            val parentFolder = when {
                rawRel.endsWith(itemName, ignoreCase = true) -> {
                    rawRel.dropLast(itemName.length).trim('/').lowercase(Locale.ROOT)
                }
                rawRel.isNotBlank() -> rawRel.lowercase(Locale.ROOT)
                !canonicalPath.isNullOrBlank() -> File(canonicalPath).parentFile?.name?.lowercase(Locale.ROOT).orEmpty()
                else -> ""
            }

            val pathKey = canonicalPath?.let { "path:$it" }
            val uriKey = if (uriStr.isNotBlank()) "uri:$uriStr" else null
            val nameFolderKey = if (parentFolder.isNotBlank()) "name_folder:$normName|$parentFolder" else null
            val nameSizeKey = if (size > 0L) "name_size:$normName|$size" else null

            // Check if this item is a duplicate of an already seen item:
            // 1. Direct path match
            // 2. Name + parent folder match (e.g. same filename in same folder)
            // 3. Name + size match (if either item has an unknown/blank folder, or folders match)
            // 4. URI match
            val sizeMatchIndex = nameSizeKey?.let { seenNameSizes[it] }
            val isCompatibleFolder = sizeMatchIndex != null && (
                parentFolder.isBlank() ||
                folderForIndex[sizeMatchIndex].isNullOrBlank() ||
                folderForIndex[sizeMatchIndex] == parentFolder
            )

            val existingIndex = (pathKey?.let { seenPaths[it] })
                ?: (nameFolderKey?.let { seenNameFolders[it] })
                ?: (if (isCompatibleFolder) sizeMatchIndex else null)
                ?: (uriKey?.let { seenUris[it] })

            if (existingIndex == null) {
                val newIndex = result.size
                result.add(item)
                pathKey?.let { seenPaths[it] = newIndex }
                nameFolderKey?.let { seenNameFolders[it] = newIndex }
                nameSizeKey?.let { seenNameSizes[it] = newIndex }
                uriKey?.let { seenUris[it] = newIndex }
                folderForIndex[newIndex] = parentFolder
            } else {
                // If the new item has a real direct file path while the existing one doesn't (e.g. disk scan vs MediaStore),
                // replace the existing item with the richer disk item!
                val existingItem = result[existingIndex]
                val existingHasPath = !filePath(existingItem).isNullOrBlank()
                val newHasPath = !path.isNullOrBlank()
                if (!existingHasPath && newHasPath) {
                    result[existingIndex] = item
                    pathKey?.let { seenPaths[it] = existingIndex }
                    nameFolderKey?.let { seenNameFolders[it] = existingIndex }
                    nameSizeKey?.let { seenNameSizes[it] = existingIndex }
                    uriKey?.let { seenUris[it] = existingIndex }
                    folderForIndex[existingIndex] = parentFolder
                }
            }
        }
        return result
    }

    internal fun shouldSkipRecursiveDirectory(folder: File, storageRoot: File): Boolean {
        val relative = folder.relativeToOrSelf(storageRoot).path.replace(File.separatorChar, '/')
            .lowercase(Locale.ROOT)
        if (relative.isBlank() || relative == ".") return false
        val nameLower = folder.name.lowercase(Locale.ROOT)
        if (nameLower.startsWith(".") && nameLower != ".documents") return true
        if (nameLower == "thumbnails" || nameLower == ".thumbnails" || nameLower == "cache" || nameLower == ".cache") return true
        if (nameLower == ".git" || nameLower == ".gradle" || nameLower == "node_modules" || nameLower == ".idea") return true
        // android/data and android/obb are unreadable on Android 11+ whatever permission we hold.
        if (relative.startsWith("android/data") || relative.startsWith("android/obb")) return true
        if (relative.contains("/cache") || relative.endsWith("/cache")) return true
        // Skip audio/sticker message dumps that never contain readable books/documents
        if (nameLower.contains("voice notes") || nameLower.contains("stickers") || nameLower.contains("animated gifs")) return true
        if (nameLower == "wallpapers" || nameLower == ".trash" || nameLower == ".trashed") return true
        // Skip descending into pure camera burst / DCIM camera rolls during recursive walks,
        // unless the user specifically started browsing inside DCIM
        if ((relative.startsWith("dcim/camera") || relative.contains("/dcim/camera")) &&
            !storageRoot.absolutePath.lowercase(Locale.ROOT).contains("dcim")) return true
        return false
    }

    private fun listSafDirectory(
        context: Context,
        root: VeritasBrowserRoot,
        location: VeritasBrowserLocation,
        diagnostics: MutableList<String>,
        progress: ScanProgress,
        checkCancelled: () -> Unit
    ): List<VeritasBrowserFile> {
        val documentId = location.documentId ?: return emptyList()
        val entries = mutableListOf<VeritasBrowserFile>()
        val documents = mutableListOf<VeritasBrowserFile>()
        val images = mutableListOf<VeritasBrowserFile>()
        fun snapshot() = progress.snapshot(entries + documents + images)
        fun report(immediate: Boolean = false) =
            progress.report(entries.size + documents.size + images.size, immediate, ::snapshot)
        fun limitsReached() = documents.size >= MAX_DOCUMENT_RESULTS && images.size >= MAX_IMAGE_RESULTS

        fun walk(parentId: String, parentPath: String, depth: Int) {
            checkCancelled()
            val immediate = depth == 0
            if (depth > MAX_SCAN_DEPTH || (!immediate && limitsReached())) return
            val subdirectories = mutableListOf<Pair<String, String>>()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(root.uri, parentId)
            try {
                context.contentResolver.query(childrenUri, childProjection, null, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    val dateCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    while (cursor.moveToNext()) {
                        checkCancelled()
                        if (!immediate && limitsReached()) break
                        val childId = cursor.stringValue(idCol) ?: continue
                        val name = cursor.stringValue(nameCol).orEmpty()
                        if (name.startsWith(".")) continue
                        val mimeType = cursor.stringValue(mimeCol).orEmpty()
                        val childPath = if (parentPath.isBlank()) name else "$parentPath/$name"
                        val isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
                        val type = if (isDirectory) null else fileTypeFor(name, mimeType)
                        val destination = when {
                            isDirectory -> {
                                if (depth < MAX_SCAN_DEPTH) subdirectories.add(childId to childPath)
                                if (!immediate) continue
                                entries
                            }
                            immediate -> {
                                if (name.substringAfterLast('.', "").lowercase(Locale.ROOT) in hiddenSafExtensions) continue
                                entries
                            }
                            type == VeritasBrowserTab.OCR && images.size < MAX_IMAGE_RESULTS -> images
                            type != null && type != VeritasBrowserTab.OCR && documents.size < MAX_DOCUMENT_RESULTS -> documents
                            else -> continue
                        }
                        destination.add(VeritasBrowserFile(
                            uri = DocumentsContract.buildDocumentUriUsingTree(root.uri, childId),
                            name = name.ifBlank { if (isDirectory) "Folder" else "Untitled file" },
                            mimeType = mimeType,
                            sizeBytes = if (isDirectory) 0L else cursor.longValue(sizeCol),
                            modifiedAt = cursor.longValue(dateCol),
                            rootLabel = root.label,
                            relativePath = childPath,
                            type = type ?: VeritasBrowserTab.ALL,
                            isDirectory = isDirectory,
                            isSupported = isDirectory || type != null,
                            targetLocation = if (isDirectory) {
                                VeritasBrowserLocation(
                                    rootLabel = root.label,
                                    relativePath = childPath,
                                    rootUri = root.uri,
                                    documentId = childId
                                )
                            } else null
                        ))
                        if (!immediate) report()
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (immediate) {
                    diagnostics.add("Android blocked access to ${location.label}: ${error.message ?: "folder is protected"}.")
                }
            }
            // Release each provider cursor before opening any of its children.
            if (immediate) report(immediate = true)
            for ((childId, childPath) in subdirectories) {
                if (limitsReached()) break
                walk(childId, childPath, depth + 1)
            }
        }

        walk(documentId, location.relativePath, depth = 0)
        checkCancelled()
        return snapshot()
    }

    internal fun fileTypeFor(name: String, mimeType: String): VeritasBrowserTab? =
        when (ImportFilePolicy.detect(name, mimeType)) {
            ImportFormat.PDF -> VeritasBrowserTab.PDF
            ImportFormat.DOCX -> VeritasBrowserTab.DOC
            ImportFormat.PPTX, ImportFormat.PPT -> VeritasBrowserTab.SLIDES
            ImportFormat.EPUB -> VeritasBrowserTab.BOOKS
            ImportFormat.HTML -> VeritasBrowserTab.HTML
            ImportFormat.TEXT -> VeritasBrowserTab.TXT
            ImportFormat.IMAGE -> VeritasBrowserTab.OCR
            null -> null
        }
    internal fun mimeTypeForFileName(name: String): String {
        val lowerName = name.lowercase(Locale.getDefault())
        return when {
            lowerName.endsWith(".pdf") -> "application/pdf"
            lowerName.endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            lowerName.endsWith(".docm") -> "application/vnd.ms-word.document.macroEnabled.12"
            lowerName.endsWith(".doc") || lowerName.endsWith(".dot") -> "application/msword"
            lowerName.endsWith(".dotx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.template"
            lowerName.endsWith(".rtf") -> "application/rtf"
            lowerName.endsWith(".odt") -> "application/vnd.oasis.opendocument.text"
            lowerName.endsWith(".wpd") -> "application/wordperfect"
            lowerName.endsWith(".wps") -> "application/vnd.ms-works"
            lowerName.endsWith(".pptx") -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            lowerName.endsWith(".pptm") -> "application/vnd.ms-powerpoint.presentation.macroEnabled.12"
            lowerName.endsWith(".ppt") || lowerName.endsWith(".pps") -> "application/vnd.ms-powerpoint"
            lowerName.endsWith(".ppsx") -> "application/vnd.openxmlformats-officedocument.presentationml.slideshow"
            lowerName.endsWith(".potx") -> "application/vnd.openxmlformats-officedocument.presentationml.template"
            lowerName.endsWith(".pot") -> "application/vnd.ms-powerpoint"
            lowerName.endsWith(".odp") -> "application/vnd.oasis.opendocument.presentation"
            lowerName.endsWith(".epub") -> "application/epub+zip"
            lowerName.endsWith(".mobi") -> "application/x-mobipocket-ebook"
            lowerName.endsWith(".azw") || lowerName.endsWith(".azw3") -> "application/vnd.amazon.ebook"
            lowerName.endsWith(".fb2") -> "application/x-fictionbook+xml"
            lowerName.endsWith(".ibooks") -> "application/x-ibooks+zip"
            lowerName.endsWith(".cbz") -> "application/vnd.comicbook+zip"
            lowerName.endsWith(".cbr") -> "application/vnd.comicbook-rar"
            lowerName.endsWith(".xhtml") -> "application/xhtml+xml"
            lowerName.endsWith(".html") || lowerName.endsWith(".htm") || lowerName.endsWith(".mhtml") -> "text/html"
            lowerName.endsWith(".txt") || lowerName.endsWith(".text") || lowerName.endsWith(".md") ||
                lowerName.endsWith(".markdown") || lowerName.endsWith(".csv") || lowerName.endsWith(".tsv") ||
                lowerName.endsWith(".log") || lowerName.endsWith(".json") ||
                lowerName.endsWith(".yml") || lowerName.endsWith(".yaml") || lowerName.endsWith(".rst") ||
                lowerName.endsWith(".srt") || lowerName.endsWith(".vtt") || lowerName.endsWith(".tex") ||
                lowerName.endsWith(".latex") || lowerName.endsWith(".ini") || lowerName.endsWith(".conf") ||
                lowerName.endsWith(".properties") || lowerName.endsWith(".asciidoc") || lowerName.endsWith(".adoc") -> "text/plain"
            lowerName.endsWith(".png") -> "image/png"
            lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") -> "image/jpeg"
            lowerName.endsWith(".webp") -> "image/webp"
            lowerName.endsWith(".heic") -> "image/heic"
            lowerName.endsWith(".heif") -> "image/heif"
            lowerName.endsWith(".avif") -> "image/avif"
            lowerName.endsWith(".gif") -> "image/gif"
            lowerName.endsWith(".bmp") -> "image/bmp"
            lowerName.endsWith(".tif") || lowerName.endsWith(".tiff") -> "image/tiff"
            else -> "application/octet-stream"
        }
    }

    private fun displayNameForRoot(context: Context, uri: Uri): String {
        return runCatching {
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(
                uri,
                DocumentsContract.getTreeDocumentId(uri)
            )
            context.contentResolver.query(
                documentUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.stringValue(DocumentsContract.Document.COLUMN_DISPLAY_NAME) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Approved folder"
    }

    private fun canonicalBrowserPath(path: String): String =
        runCatching { File(path).canonicalPath }.getOrDefault(path).lowercase(Locale.ROOT)

    private fun Cursor.stringValue(columnName: String): String? = stringValue(getColumnIndex(columnName))

    private fun Cursor.stringValue(index: Int): String? = if (index >= 0 && !isNull(index)) getString(index) else null

    private fun Cursor.longValue(index: Int): Long = if (index >= 0 && !isNull(index)) getLong(index) else 0L
}

/** Walk stops at ten levels. Android/media/<app>/<app>/Media/<folder>/Sent is seven, so
 *  messaging-app documents are comfortably inside it. */
private const val MAX_SCAN_DEPTH = 10
private const val MAX_DOCUMENT_RESULTS = 5000
private const val EXTERNAL_AGGREGATE_VOLUME = "external"
private const val MAX_IMAGE_RESULTS = 300
private const val PROGRESS_INTERVAL_MS = 400L

internal fun readableImportMimeTypes(): Array<String> = ImportFilePolicy.chooserMimeTypes()

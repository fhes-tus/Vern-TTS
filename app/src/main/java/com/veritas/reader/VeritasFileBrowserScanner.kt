package com.veritas.reader

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
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

    fun persistedRoots(context: Context): List<VeritasBrowserRoot> {
        return context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { permission ->
                VeritasBrowserRoot(
                    uri = permission.uri,
                    label = displayNameForRoot(context, permission.uri)
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
        location: VeritasBrowserLocation? = null
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
        val entries = when {
            activeLocation.filePath != null -> listFileDirectory(context, activeLocation, diagnostics)
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
                        diagnostics = diagnostics
                    )
                }
            }

            else -> emptyList()
        }
        return VeritasFileBrowserScanResult(entries, activeLocation, diagnostics)
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
     * Every compatible file on the device in one pass, wherever it lives.
     *
     * Folder-by-folder walking only finds what the user thinks to look for — a document in
     * Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents is six levels down and
     * effectively invisible. MediaStore already indexes all of shared storage, across every
     * volume, so one query reaches everything a recursive walk would, in a fraction of the
     * time. Rows whose extension we cannot parse are dropped rather than listed as unopenable.
     */
    private fun queryDeviceWideFiles(
        context: Context,
        diagnostics: MutableList<String>
    ): List<VeritasBrowserFile> {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
            MediaStore.Files.FileColumns.DATA
        )
        val docResults = mutableListOf<VeritasBrowserFile>()
        val imageResults = mutableListOf<VeritasBrowserFile>()
        val seen = HashSet<String>()
        // Every mounted volume, collapsing external aggregate if present
        val rawVolumes = runCatching { MediaStore.getExternalVolumeNames(context) }
            .getOrDefault(setOf(MediaStore.VOLUME_EXTERNAL))
            .ifEmpty { setOf(MediaStore.VOLUME_EXTERNAL) }
        val volumes = if (rawVolumes.contains(MediaStore.VOLUME_EXTERNAL)) {
            setOf(MediaStore.VOLUME_EXTERNAL)
        } else {
            rawVolumes
        }
        volumes.forEach { volume ->
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
                    val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)
                    val dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameCol) ?: continue
                        val mime = cursor.getString(mimeCol).orEmpty()
                        val type = fileTypeFor(name, mime) ?: continue
                        val relative = cursor.getString(pathCol).orEmpty().trimEnd('/')
                        val dataPath = if (dataCol >= 0 && !cursor.isNull(dataCol)) cursor.getString(dataCol) else null
                        val diskFile = dataPath?.let(::File)

                        // If the path points to a file that doesn't exist on disk, skip stale MediaStore records
                        if (diskFile != null && !diskFile.exists()) continue

                        val fileUri = if (diskFile != null && diskFile.exists()) {
                            Uri.fromFile(diskFile)
                        } else {
                            ContentUris.withAppendedId(uri, cursor.getLong(idCol))
                        }

                        val fullRelative = if (relative.isNotBlank()) {
                            if (relative.endsWith(name, ignoreCase = true)) relative else "$relative/$name"
                        } else {
                            name
                        }

                        val dedupKey = diskFile?.canonicalPath?.lowercase(Locale.ROOT)
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
                        if (type == VeritasBrowserTab.OCR) {
                            if (imageResults.size < MAX_IMAGE_RESULTS) {
                                imageResults.add(browserFile)
                            }
                        } else {
                            if (docResults.size < MAX_DOCUMENT_RESULTS) {
                                docResults.add(browserFile)
                            }
                        }
                        if (docResults.size >= MAX_DOCUMENT_RESULTS && imageResults.size >= MAX_IMAGE_RESULTS) {
                            break
                        }
                    }
                }
            }.onFailure { e ->
                diagnostics.add("Could not index $volume: ${e.message ?: "unavailable"}.")
            }
        }
        return docResults + imageResults
    }

    private fun listFileDirectory(
        context: Context,
        location: VeritasBrowserLocation,
        diagnostics: MutableList<String>
    ): List<VeritasBrowserFile> {
        val storageRoot = Environment.getExternalStorageDirectory()
        val volumeRoots = storageVolumeRoots(context)
        val current = location.filePath?.let(::File) ?: storageRoot
        val safeCurrent =
            if (volumeRoots.any { current.absolutePath.startsWith(it.absolutePath) }) current else storageRoot
        val effectiveVolumeRoot = volumeRoots.firstOrNull { safeCurrent.absolutePath.startsWith(it.absolutePath) } ?: storageRoot
        if (!safeCurrent.exists()) {
            diagnostics.add("${location.label} no longer exists.")
            return emptyList()
        }
        val children =
            runCatching { safeCurrent.listFiles()?.toList().orEmpty() }.getOrElse { error ->
                diagnostics.add("Android blocked access to ${location.label}: ${error.message ?: "folder is protected"}.")
                emptyList()
            }
        if (children.isEmpty() && safeCurrent.isDirectory && !safeCurrent.canRead()) {
            diagnostics.add("Android protects this folder. Shared storage can be browsed, but private system/app folders may remain unavailable.")
        }
        val atVolumeRoot = volumeRoots.any { it.absolutePath == safeCurrent.absolutePath }
        
        // Navigation folder entries for the current folder (excluding hidden folders)
        val folderEntries = children
            .filter { it.isDirectory && it.name != "." && it.name != ".." && !it.name.startsWith(".") }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
            .mapNotNull { child ->
                val relativePath =
                    child.relativeToOrSelf(effectiveVolumeRoot).path.replace(File.separatorChar, '/')
                VeritasBrowserFile(
                    uri = Uri.fromFile(child),
                    name = child.name.ifBlank { "Folder" },
                    mimeType = DocumentsContract.Document.MIME_TYPE_DIR,
                    sizeBytes = 0L,
                    modifiedAt = child.lastModified(),
                    rootLabel = location.rootLabel,
                    relativePath = relativePath,
                    isDirectory = true,
                    isSupported = child.canRead(),
                    filePath = child.absolutePath,
                    targetLocation = if (child.canRead()) {
                        VeritasBrowserLocation(
                            rootLabel = location.rootLabel,
                            relativePath = relativePath,
                            filePath = child.absolutePath
                        )
                    } else {
                        null
                    }
                )
            }

        val docResults = mutableListOf<VeritasBrowserFile>()
        val imageResults = mutableListOf<VeritasBrowserFile>()

        // Always scan up to 10 levels deep from safeCurrent down through all subfolders!
        // At volume root, this traverses the whole device up to 10 levels deep.
        // Inside a subfolder (e.g. Download), it traverses all subfolders of that folder.
        collectSupportedDocumentFiles(
            storageRoot = effectiveVolumeRoot,
            current = safeCurrent,
            diagnostics = diagnostics,
            depth = 0,
            docResults = docResults,
            imageResults = imageResults
        )

        // Supplement with MediaStore results when at storage root to catch any indexed external files
        val mediaStoreResults = if (atVolumeRoot) {
            queryDeviceWideFiles(context, diagnostics)
        } else {
            emptyList()
        }

        // Deduplicate everything so every file and folder appears exactly once!
        // Direct disk results come before MediaStore results to ensure real file paths are preserved.
        val allEntries = folderEntries + docResults + mediaStoreResults + imageResults
        return deduplicateBrowserFiles(allEntries)
            .sortedWith(
                compareBy<VeritasBrowserFile> { !it.isDirectory }
                    .thenBy { it.type == VeritasBrowserTab.OCR }
                    .thenByDescending { it.modifiedAt }
            )
    }

    private fun collectSupportedDocumentFiles(
        storageRoot: File,
        current: File,
        diagnostics: MutableList<String>,
        depth: Int = 0,
        docResults: MutableList<VeritasBrowserFile> = mutableListOf(),
        imageResults: MutableList<VeritasBrowserFile> = mutableListOf()
    ): List<VeritasBrowserFile> {
        if (depth > MAX_SCAN_DEPTH ||
            (docResults.size >= MAX_DOCUMENT_RESULTS && imageResults.size >= MAX_IMAGE_RESULTS) ||
            shouldSkipRecursiveDirectory(current, storageRoot)
        ) return docResults + imageResults

        val children = current.listFiles() ?: return docResults + imageResults
        for (child in children) {
            if (docResults.size >= MAX_DOCUMENT_RESULTS && imageResults.size >= MAX_IMAGE_RESULTS) break
            if (child.isDirectory) {
                collectSupportedDocumentFiles(storageRoot, child, diagnostics, depth + 1, docResults, imageResults)
            } else {
                val type = fileTypeFor(child.name, "")
                if (type != null) {
                    val relativePath =
                        child.relativeToOrSelf(storageRoot).path.replace(File.separatorChar, '/')
                    val browserFile = VeritasBrowserFile(
                        uri = Uri.fromFile(child),
                        name = child.name.ifBlank { "Untitled file" },
                        mimeType = mimeTypeForFileName(child.name),
                        sizeBytes = child.length(),
                        modifiedAt = child.lastModified(),
                        rootLabel = "Phone storage",
                        relativePath = relativePath,
                        type = type,
                        isSupported = true,
                        filePath = child.absolutePath
                    )
                    if (type == VeritasBrowserTab.OCR) {
                        if (imageResults.size < MAX_IMAGE_RESULTS) {
                            imageResults.add(browserFile)
                        }
                    } else {
                        if (docResults.size < MAX_DOCUMENT_RESULTS) {
                            docResults.add(browserFile)
                        }
                    }
                }
            }
        }
        return docResults + imageResults
    }

    internal fun deduplicateBrowserFiles(files: List<VeritasBrowserFile>): List<VeritasBrowserFile> {
        return deduplicateFiles(
            items = files,
            isDirectory = { it.isDirectory },
            filePath = { it.filePath },
            uriString = { runCatching { it.uri.toString() }.getOrDefault("") },
            name = { it.name },
            sizeBytes = { it.sizeBytes },
            relativePath = { it.relativePath },
            targetLocationFilePath = { it.targetLocation?.filePath }
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
        targetLocationFilePath: (T) -> String? = { null }
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
                val dirKey = "dir:" + runCatching { File(rawDir).canonicalPath.lowercase(Locale.ROOT) }
                    .getOrDefault(rawDir.lowercase(Locale.ROOT))
                if (seenDirectories.add(dirKey)) {
                    result.add(item)
                }
                continue
            }

            val canonicalPath = when {
                !path.isNullOrBlank() -> {
                    runCatching { File(path).canonicalPath.lowercase(Locale.ROOT) }
                        .getOrDefault(path.lowercase(Locale.ROOT))
                }
                uriStr.startsWith("file://", ignoreCase = true) -> {
                    val p = uriStr.removePrefix("file://").substringBefore('?').substringBefore('#')
                    runCatching { File(p).canonicalPath.lowercase(Locale.ROOT) }
                        .getOrDefault(p.lowercase(Locale.ROOT))
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

    private fun collectSafSupportedDocumentFiles(
        context: Context,
        root: VeritasBrowserRoot,
        parentDocumentId: String,
        parentPath: String,
        diagnostics: MutableList<String>,
        depth: Int,
        docResults: MutableList<VeritasBrowserFile>,
        imageResults: MutableList<VeritasBrowserFile>
    ) {
        if (depth > MAX_SCAN_DEPTH ||
            (docResults.size >= MAX_DOCUMENT_RESULTS && imageResults.size >= MAX_IMAGE_RESULTS)
        ) return

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(root.uri, parentDocumentId)
        runCatching {
            context.contentResolver.query(childrenUri, childProjection, null, null, null)?.use { cursor ->
                val subdirs = mutableListOf<Pair<String, String>>()
                while (cursor.moveToNext()) {
                    val childId = cursor.stringValue(DocumentsContract.Document.COLUMN_DOCUMENT_ID) ?: continue
                    val name = cursor.stringValue(DocumentsContract.Document.COLUMN_DISPLAY_NAME).orEmpty()
                    if (name.startsWith(".")) continue
                    val mimeType = cursor.stringValue(DocumentsContract.Document.COLUMN_MIME_TYPE).orEmpty()
                    val childPath = if (parentPath.isBlank()) name else "$parentPath/$name"
                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        subdirs.add(childId to childPath)
                    } else {
                        val type = fileTypeFor(name, mimeType)
                        if (type != null) {
                            val size = cursor.longValue(DocumentsContract.Document.COLUMN_SIZE)
                            val modified = cursor.longValue(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                            val browserFile = VeritasBrowserFile(
                                uri = DocumentsContract.buildDocumentUriUsingTree(root.uri, childId),
                                name = name.ifBlank { "Untitled file" },
                                mimeType = mimeType,
                                sizeBytes = size,
                                modifiedAt = modified,
                                rootLabel = root.label,
                                relativePath = childPath,
                                type = type,
                                isSupported = true
                            )
                            if (type == VeritasBrowserTab.OCR) {
                                if (imageResults.size < MAX_IMAGE_RESULTS) {
                                    imageResults.add(browserFile)
                                }
                            } else {
                                if (docResults.size < MAX_DOCUMENT_RESULTS) {
                                    docResults.add(browserFile)
                                }
                            }
                        }
                    }
                }
                for ((subId, subPath) in subdirs) {
                    if (docResults.size >= MAX_DOCUMENT_RESULTS && imageResults.size >= MAX_IMAGE_RESULTS) break
                    collectSafSupportedDocumentFiles(
                        context, root, subId, subPath, diagnostics, depth + 1, docResults, imageResults
                    )
                }
            }
        }
    }

    private fun listSafDirectory(
        context: Context,
        root: VeritasBrowserRoot,
        location: VeritasBrowserLocation,
        diagnostics: MutableList<String>
    ): List<VeritasBrowserFile> {
        val documentId = location.documentId ?: return emptyList()
        val entries = mutableListOf<VeritasBrowserFile>()
        val immediateSubdirs = mutableListOf<Pair<String, String>>()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(root.uri, documentId)
        runCatching {
            context.contentResolver.query(childrenUri, childProjection, null, null, null)
                ?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val childId =
                            cursor.stringValue(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                                ?: continue
                        val name =
                            cursor.stringValue(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                                .orEmpty()
                        if (name.startsWith(".")) continue
                        val mimeType =
                            cursor.stringValue(DocumentsContract.Document.COLUMN_MIME_TYPE)
                                .orEmpty()
                        val isDir = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
                        if (!isDir) {
                            val nameLower = name.lowercase(Locale.US)
                            val isBinaryOrSystem = nameLower.endsWith(".bin") ||
                                    nameLower.endsWith(".apk") ||
                                    nameLower.endsWith(".exe") ||
                                    nameLower.endsWith(".so") ||
                                    nameLower.endsWith(".class") ||
                                    nameLower.endsWith(".dex") ||
                                    nameLower.endsWith(".tmp") ||
                                    nameLower.endsWith(".temp") ||
                                    nameLower.endsWith(".db") ||
                                    nameLower.endsWith(".sqlite") ||
                                    nameLower.endsWith(".sys") ||
                                    nameLower.endsWith(".dll") ||
                                    nameLower.endsWith(".dat")
                            if (isBinaryOrSystem) continue
                        }
                        val size = cursor.longValue(DocumentsContract.Document.COLUMN_SIZE)
                        val modified =
                            cursor.longValue(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                        val childPath =
                            if (location.relativePath.isBlank()) name else "${location.relativePath}/$name"
                        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                            immediateSubdirs.add(childId to childPath)
                            entries.add(
                                VeritasBrowserFile(
                                    uri = DocumentsContract.buildDocumentUriUsingTree(
                                        root.uri,
                                        childId
                                    ),
                                    name = name.ifBlank { "Folder" },
                                    mimeType = mimeType,
                                    sizeBytes = 0L,
                                    modifiedAt = modified,
                                    rootLabel = root.label,
                                    relativePath = childPath,
                                    isDirectory = true,
                                    targetLocation = VeritasBrowserLocation(
                                        rootLabel = root.label,
                                        relativePath = childPath,
                                        rootUri = root.uri,
                                        documentId = childId
                                    )
                                )
                            )
                        } else {
                            val type = fileTypeFor(name, mimeType)
                            entries.add(
                                VeritasBrowserFile(
                                    uri = DocumentsContract.buildDocumentUriUsingTree(
                                        root.uri,
                                        childId
                                    ),
                                    name = name.ifBlank { "Untitled file" },
                                    mimeType = mimeType,
                                    sizeBytes = size,
                                    modifiedAt = modified,
                                    rootLabel = root.label,
                                    relativePath = childPath,
                                    type = type ?: VeritasBrowserTab.ALL,
                                    isSupported = type != null
                                )
                            )
                        }
                    }
                }
        }.onFailure { error ->
            diagnostics.add("Android blocked access to ${location.label}: ${error.message ?: "folder is protected"}.")
        }

        // Recurse into SAF subfolders up to 10 levels deep to uncover all nested documents
        val docResults = mutableListOf<VeritasBrowserFile>()
        val imageResults = mutableListOf<VeritasBrowserFile>()
        for ((subId, subPath) in immediateSubdirs) {
            collectSafSupportedDocumentFiles(
                context = context,
                root = root,
                parentDocumentId = subId,
                parentPath = subPath,
                diagnostics = diagnostics,
                depth = 1,
                docResults = docResults,
                imageResults = imageResults
            )
        }

        return deduplicateBrowserFiles(entries + docResults + imageResults)
            .sortedWith(
                compareBy<VeritasBrowserFile> { it.isDirectory }
                    .thenBy { it.type == VeritasBrowserTab.OCR }
                    .thenByDescending { it.modifiedAt }
            )
    }

    internal fun fileTypeFor(name: String, mimeType: String): VeritasBrowserTab? {
        val lowerName = name.lowercase(Locale.getDefault())
        val lowerMime = mimeType.lowercase(Locale.getDefault())
        // Explicitly exclude .xml files from being added to the file browser
        if (lowerName.endsWith(".xml") || lowerMime == "text/xml" || lowerMime == "application/xml") {
            return null
        }
        fun named(vararg suffixes: String) = suffixes.any { lowerName.endsWith(it) }
        return when {
            lowerMime.contains("pdf") || named(".pdf") -> VeritasBrowserTab.PDF
            // Word & Office documents
            lowerMime.contains("wordprocessingml") ||
                lowerMime.contains("msword") ||
                lowerMime.contains("opendocument.text") ||
                lowerMime.contains("rtf") ||
                named(".docx", ".docm", ".doc", ".dot", ".dotx", ".rtf", ".odt", ".wpd", ".wps") -> VeritasBrowserTab.DOC
            // Presentations
            lowerMime.contains("presentationml") ||
                lowerMime.contains("ms-powerpoint") ||
                lowerMime.contains("opendocument.presentation") ||
                named(".pptx", ".pptm", ".ppt", ".pps", ".ppsx", ".odp", ".pot", ".potx") -> VeritasBrowserTab.SLIDES
            // E-Books
            lowerMime.contains("epub") ||
                lowerMime.contains("mobipocket") ||
                lowerMime.contains("amazon.ebook") ||
                lowerMime.contains("fictionbook") ||
                named(".epub", ".mobi", ".azw", ".azw3", ".fb2", ".ibooks", ".cbz", ".cbr") -> VeritasBrowserTab.BOOKS
            // Web documents
            lowerMime.contains("html") || named(".html", ".htm", ".xhtml", ".mhtml") -> VeritasBrowserTab.HTML
            // Text & Markdown & Config & Source docs
            lowerMime.startsWith("text/") ||
                lowerMime.contains("json") ||
                lowerMime.contains("yaml") ||
                lowerMime.contains("latex") ||
                named(
                    ".txt", ".text", ".md", ".markdown", ".csv", ".tsv", ".log",
                    ".json", ".yml", ".yaml", ".rst", ".srt", ".vtt",
                    ".tex", ".latex", ".ini", ".conf", ".properties", ".asciidoc", ".adoc"
                ) -> VeritasBrowserTab.TXT
            // Images (least priority in browser)
            lowerMime.startsWith("image/") ||
                named(
                    ".png", ".jpg", ".jpeg", ".webp", ".bmp", ".tif", ".tiff",
                    ".heic", ".heif", ".avif", ".gif"
                ) -> VeritasBrowserTab.OCR

            else -> null
        }
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

    private fun Cursor.stringValue(columnName: String): String? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getString(index) else null
    }

    private fun Cursor.longValue(columnName: String): Long {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getLong(index) else 0L
    }
}

/** Walk stops at ten levels. Android/media/<app>/<app>/Media/<folder>/Sent is seven, so
 *  messaging-app documents are comfortably inside it. */
private const val MAX_SCAN_DEPTH = 10
private const val MAX_DOCUMENT_RESULTS = 5000
private const val MAX_IMAGE_RESULTS = 300
private const val MAX_SCAN_RESULTS = 5000

internal fun readableImportMimeTypes(): Array<String> = arrayOf(
    "text/plain",
    "text/*",
    "text/html",
    "text/rtf",
    "application/rtf",
    "application/pdf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "application/vnd.ms-powerpoint",
    "application/vnd.ms-powerpoint.presentation.macroEnabled.12",
    "application/vnd.ms-word.document.macroEnabled.12",
    "application/vnd.oasis.opendocument.text",
    "application/vnd.oasis.opendocument.presentation",
    "application/epub+zip",
    "application/x-mobipocket-ebook",
    "application/vnd.amazon.ebook",
    "application/x-fictionbook+xml",
    "application/xhtml+xml",
    "application/octet-stream",
    "image/*"
)

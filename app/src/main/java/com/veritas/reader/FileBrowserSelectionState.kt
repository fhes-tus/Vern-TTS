package com.veritas.reader

import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList

// Restore selected files before the scanner returns, including their import metadata.
internal val BrowserSelectionSaver = listSaver<SnapshotStateList<VeritasBrowserFile>, Bundle>(
    save = { files -> files.map { file -> Bundle().apply {
        putString("uri", file.uri.toString()); putString("name", file.name)
        putString("mime", file.mimeType); putLong("size", file.sizeBytes); putLong("modified", file.modifiedAt)
        putString("root", file.rootLabel); putString("relative", file.relativePath); putString("type", file.type.name)
        putBoolean("directory", file.isDirectory); putBoolean("supported", file.isSupported); putString("path", file.filePath)
        file.targetLocation?.let { location -> putBundle("location", Bundle().apply {
            putString("root", location.rootLabel); putString("relative", location.relativePath)
            putString("path", location.filePath); putString("uri", location.rootUri?.toString()); putString("id", location.documentId)
        }) }
    } } },
    restore = { saved -> saved.map { bundle ->
        VeritasBrowserFile(
            uri = Uri.parse(bundle.getString("uri").orEmpty()), name = bundle.getString("name").orEmpty(),
            mimeType = bundle.getString("mime").orEmpty(), sizeBytes = bundle.getLong("size"), modifiedAt = bundle.getLong("modified"),
            rootLabel = bundle.getString("root").orEmpty(), relativePath = bundle.getString("relative").orEmpty(),
            type = runCatching { VeritasBrowserTab.valueOf(bundle.getString("type").orEmpty()) }.getOrDefault(VeritasBrowserTab.ALL),
            isDirectory = bundle.getBoolean("directory"), isSupported = bundle.getBoolean("supported", true), filePath = bundle.getString("path"),
            targetLocation = bundle.getBundle("location")?.let { location -> VeritasBrowserLocation(
                rootLabel = location.getString("root").orEmpty(), relativePath = location.getString("relative").orEmpty(),
                filePath = location.getString("path"), rootUri = location.getString("uri")?.let(Uri::parse), documentId = location.getString("id")
            ) }
        )
    }.toMutableStateList() }
)

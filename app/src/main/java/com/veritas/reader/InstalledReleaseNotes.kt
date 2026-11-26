package com.veritas.reader

import com.veritas.reader.ui.ReaderViewModel
import org.json.JSONObject

internal fun isInstalledUpgrade(previousName: String, previousCode: Int, currentName: String, currentCode: Int): Boolean =
    when {
        previousCode > 0 -> currentCode > previousCode
        previousName.isNotBlank() -> ReaderViewModel.isVersionNewer(previousName, currentName)
        else -> false
    }

internal fun releaseNotesForVersion(contents: String, versionName: String): List<String> = buildList {
    val notes = JSONObject(contents).optJSONArray(versionName) ?: return@buildList
    for (index in 0 until notes.length()) {
        (notes.opt(index) as? String)?.trim()?.takeIf(String::isNotEmpty)?.let(::add)
    }
}

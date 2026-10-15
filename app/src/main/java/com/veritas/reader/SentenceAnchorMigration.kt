package com.veritas.reader

import org.json.JSONArray
import org.json.JSONObject

/** Source-order mapping avoids matching the wrong occurrence of repeated words in a novel. */
internal class SentenceAnchorMap(old: List<ReaderSentence>, private val fresh: List<ReaderSentence>) {
    private val oldText = old.map { it.text }
    private val oldStarts = starts(oldText)
    private val newStarts = starts(fresh.map { it.text })

    init {
        require(oldText.joinToString("").filterNot(Char::isWhitespace) ==
            fresh.joinToString("") { it.text }.filterNot(Char::isWhitespace))
    }

    fun position(index: Int, offset: Int = 0): Pair<Int, Int> {
        if (fresh.isEmpty() || oldText.isEmpty()) return 0 to 0
        val safe = index.coerceIn(oldText.indices)
        val rank = oldStarts[safe] + oldText[safe].take(offset.coerceIn(0, oldText[safe].length)).count { !it.isWhitespace() }
        var low = 0
        var high = fresh.lastIndex
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (newStarts[mid] <= rank) low = mid else high = mid - 1
        }
        val text = fresh[low].text
        val remaining = rank - newStarts[low]
        var chars = 0
        var nonSpace = 0
        while (chars < text.length && nonSpace < remaining) {
            if (!text[chars].isWhitespace()) nonSpace++
            chars++
        }
        while (chars < text.length && text[chars].isWhitespace()) chars++
        return low to chars
    }

    private fun starts(texts: List<String>): IntArray {
        var rank = 0
        return IntArray(texts.size) { index -> rank.also { rank += texts[index].count { !it.isWhitespace() } } }
    }
}

private const val INDEX_JOURNAL = "sentence_index_migration"

internal fun DocumentRepository.ensureSentenceIndex(document: SavedDocument): SavedDocument =
    synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
        // Finish the preferences side of an already committed database transaction after interruption.
        val pending = prefs.getString(INDEX_JOURNAL, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (pending != null) {
            val committed = dbHelper.getDocumentById(pending.getString("id"), docsDir)?.sentenceIndexVersion == 2
            if (committed) finishSentenceIndexPreferences(pending)
            else check(prefs.edit().remove(INDEX_JOURNAL).commit())
        }
        val current = dbHelper.getDocumentById(document.id, docsDir) ?: document
        if (current.sentenceIndexVersion >= 2) return@synchronized current
        val raw = readText(current)
        if (raw.isBlank()) return@synchronized current
        val old = ReaderTextIndex.build(raw, current.pageCount, legacySentenceBoundaries = true)
        val fresh = ReaderTextIndex.build(raw, current.pageCount)
        val map = SentenceAnchorMap(old.sentences, fresh.sentences)
        val updated = current.copy(currentIndex = map.position(current.currentIndex).first,
            chunkCount = fresh.sentences.size, sentenceIndexVersion = 2)
        val storedAnnotations = dbHelper.getAllAnnotations()
        val allAnnotations = storedAnnotations.ifEmpty { loadAllAnnotationsFromPrefsRaw() }
        if (storedAnnotations.isEmpty() && allAnnotations.isNotEmpty()) dbHelper.replaceAllAnnotations(allAnnotations)
        val mapped = allAnnotations.filter { it.documentId == current.id }
            .map { it.copy(chunkIndex = map.position(it.chunkIndex).first) }
            .groupBy { it.stableKey }.values.map { overlapping ->
                val latest = overlapping.maxBy { it.updatedAt }
                latest.copy(note = overlapping.map { it.note }.filter { it.isNotBlank() }.distinct().joinToString("\n\n"))
            }
        val journal = JSONObject().put("id", current.id)
        val documents = JSONArray()
        dbHelper.getAllDocuments(docsDir).forEach { documents.put((if (it.id == current.id) updated else it).toJson()) }
        journal.put("documents", documents.toString())
        val annotations = JSONArray()
        (allAnnotations.filter { it.documentId != current.id } + mapped).forEach { annotations.put(it.toJson()) }
        journal.put("annotations", annotations.toString())
        val history = runCatching { JSONArray(prefs.getString(DocumentRepository.KEY_READING_HISTORY, "[]")) }.getOrDefault(JSONArray())
        for (i in 0 until history.length()) {
            val entry = history.optJSONObject(i) ?: continue
            if (entry.optString("documentId") == current.id) {
                entry.put("currentIndex", map.position(entry.optInt("currentIndex")).first)
                entry.put("chunkCount", fresh.sentences.size)
            }
        }
        journal.put("history", history.toString())
        loadPersistedResumePoint()?.takeIf { it.documentId == current.id }?.let {
            val (index, offset) = map.position(it.chunkIndex, it.charOffset)
            journal.put("resumeIndex", index).put("resumeOffset", offset)
        }
        check(prefs.edit().putString(INDEX_JOURNAL, journal.toString()).commit())
        dbHelper.migrateSentenceAnchors(updated, mapped)
        finishSentenceIndexPreferences(journal)
        updated
    }

private fun DocumentRepository.finishSentenceIndexPreferences(journal: JSONObject) {
    val documents = journal.getString("documents")
    val editor = prefs.edit().putString(DocumentRepository.KEY_DOCUMENTS, documents)
        .putString("${DocumentRepository.KEY_DOCUMENTS}__bak", documents)
        .putString(DocumentRepository.KEY_ANNOTATIONS, journal.getString("annotations"))
        .putString(DocumentRepository.KEY_READING_HISTORY, journal.getString("history"))
        .remove(INDEX_JOURNAL)
    if (journal.has("resumeIndex") && prefs.getString("resume_document_id", null) == journal.getString("id")) {
        editor.putInt("resume_chunk_index", journal.getInt("resumeIndex"))
            .putInt("resume_char_offset", journal.getInt("resumeOffset"))
            .putInt("resume_word_count", 0)
    }
    check(editor.commit())
}

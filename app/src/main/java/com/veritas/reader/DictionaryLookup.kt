package com.veritas.reader

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal const val DICTIONARY_VOCAB_ID = "dictionary"
internal data class DictionaryLookupAnchor(val documentId: String? = null, val sentenceIndex: Int = -1, val contextSentence: String? = null)
internal data class SavedDictionaryLookup(
    val word: String,
    val source: DictionaryLookupSource,
    val anchor: DictionaryLookupAnchor,
    val entry: DictionaryEntry?,
    val meaning: VocabularyDefinition,
    val notes: List<GeneralNote>,
    val saved: Boolean
) {
    val displayedWord: String get() = entry?.headword ?: word

    fun vocabularyEntry() = VocabularyEntry(
        displayedWord, meaning.definition,
        if (anchor.sentenceIndex >= 0) "(looked up: sentence ${anchor.sentenceIndex + 1})" else "(looked up: Dictionary)",
        anchor.sentenceIndex, anchor.contextSentence, pronunciation = meaning.pronunciation,
        meaningSourceUrl = meaning.sourceUrl, examples = meaning.examples
    )
}

private fun DictionaryLookupSource.resolvedSource(entry: DictionaryEntry?) = when {
    this != DictionaryLookupSource.AUTOMATIC -> this
    entry != null -> DictionaryLookupSource.OFFLINE
    else -> DictionaryLookupSource.ONLINE
}

internal fun DictionaryEntry.vocabularyMeaning() = VocabularyDefinition(
    senses.take(3).joinToString("; ") { "(${it.partOfSpeech}) ${it.definition}" },
    sourceUrl = EnglishDictionaryPack.SOURCE,
    examples = senses.take(3).flatMap { it.examples }.distinct().take(4), senses = senses)

/** Study Hub searches do not create notes or enqueue vocabulary work. */
internal suspend fun lookupDictionaryWord(context: Context, input: String,
    lookupSource: DictionaryLookupSource = DictionaryLookupSource.AUTOMATIC,
    anchor: DictionaryLookupAnchor = DictionaryLookupAnchor()): SavedDictionaryLookup = withContext(Dispatchers.IO) {
    val word = requireNotNull(WordNetDictionary.query(input))
    var offline: DictionaryEntry? = null
    val meaning = lookupSource.resolve(
        offline = { OfflineDictionaryStore.lookup(context, word).also { offline = it }?.vocabularyMeaning() },
        online = { VocabularyLookup.fetch(word) }) ?: VocabularyDefinition(VocabularyLookup.UNAVAILABLE)
    ensureActive()
    SavedDictionaryLookup(word, lookupSource.resolvedSource(offline), anchor, offline, meaning, emptyList(), false)
}

internal suspend fun saveDictionaryLookupResult(context: Context, result: SavedDictionaryLookup): SavedDictionaryLookup = withContext(Dispatchers.IO) {
    if (result.saved) return@withContext result
    require(!VocabularyLookup.needsDefinition(result.meaning.definition))
    val notes = DocumentRepository(context).saveVocabularyLookup(context, result.anchor.documentId ?: DICTIONARY_VOCAB_ID,
        result.vocabularyEntry())
    result.copy(notes = notes, saved = true)
}

/** Persist intent first; delayed durable work finishes it if the lookup screen is closed. */
internal suspend fun lookupAndSaveDictionaryWord(context: Context, input: String,
    anchor: DictionaryLookupAnchor = DictionaryLookupAnchor(), requestId: String = java.util.UUID.randomUUID().toString(),
    lookupSource: DictionaryLookupSource = DictionaryLookupSource.AUTOMATIC): SavedDictionaryLookup = withContext(Dispatchers.IO) {
    val word = requireNotNull(WordNetDictionary.query(input))
    val repository = DocumentRepository(context)
    val docId = anchor.documentId ?: DICTIONARY_VOCAB_ID
    val source = if (anchor.sentenceIndex >= 0) "(looked up: sentence ${anchor.sentenceIndex + 1})" else "(looked up: Dictionary)"
    val notes = repository.saveVocabularyLookup(context, docId,
        VocabularyEntry(word, VocabularyLookup.PENDING, source, anchor.sentenceIndex, anchor.contextSentence), lookupDelayMillis = 15_000, lookupIdOverride = requestId,
        lookupSource = lookupSource)
    val note = notes.first { it.title == "__vocab__$docId" }
    val lookupId = requestId
    ensureActive()
    var offline: DictionaryEntry? = null
    val network = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    val connected = network.getNetworkCapabilities(network.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    val meaning = lookupSource.resolve(
        offline = { runCatching { OfflineDictionaryStore.lookup(context, word) }.getOrNull().also { offline = it }?.vocabularyMeaning() },
        online = { if (connected) VocabularyLookup.fetch(word) else null }) ?: VocabularyDefinition(VocabularyLookup.UNAVAILABLE)
    ensureActive()
    val updated = repository.mutateGeneralNotes { current -> current.map { saved ->
        if (saved.id != note.id) saved else {
            val content = updateVocabularyMeaning(saved.content, lookupId, meaning)
            if (content == saved.content) saved else saved.copy(content = content, updatedAt = System.currentTimeMillis())
        }
    } }
    val saved = updated.firstOrNull { it.id == note.id }?.content?.let { content -> parseVocabularyNoteContent(content).any { it.lookupId == lookupId } } == true
    SavedDictionaryLookup(word, lookupSource.resolvedSource(offline), anchor, offline, meaning, updated, saved)
}

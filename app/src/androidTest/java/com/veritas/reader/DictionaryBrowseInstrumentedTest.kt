package com.veritas.reader

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class DictionaryBrowseInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun installDictionaryInDisposablePackage() {
        org.junit.Assume.assumeTrue("Dictionary checks require their own disposable package.", context.packageName.endsWith(".dictionarychecks"))
        if (OfflineDictionaryStore.info(context) != null) return
        val archive = File(context.cacheDir, "browse-dictionary-fixture.zip")
        InstrumentationRegistry.getInstrumentation().context.assets.open("english-wordnet-2025.zip").use { input ->
            archive.outputStream().use { input.copyTo(it) }
        }
        try { OfflineDictionaryStore.install(context, archive) } finally { archive.delete() }
    }
    @Test fun allWordsHaveStableDistinctPagesAndBrowsingDoesNotSaveVocabulary() {
        val notes = DocumentRepository(context).loadGeneralNotes()
        val first = OfflineDictionaryStore.browse(context)
        val next = OfflineDictionaryStore.browse(context, after = first.words.last())
        assertEquals(40, first.words.size)
        assertEquals(40, next.words.size)
        assertTrue(first.hasMore && next.hasMore)
        assertEquals(80, (first.words + next.words).distinct().size)
        assertTrue(first.words.last() < next.words.first())
        assertEquals(first, OfflineDictionaryStore.browse(context))
        first.words.take(5).forEach { assertNotNull(OfflineDictionaryStore.lookup(context, it)) }
        assertEquals(notes, DocumentRepository(context).loadGeneralNotes())
    }
    @Test fun lettersPrefixesLimitsAndLiteralWildcardsWork() {
        val letter = OfflineDictionaryStore.browse(context, "Z")
        assertTrue(letter.words.isNotEmpty())
        assertTrue(letter.words.all { it.startsWith("z") })
        val prefix = OfflineDictionaryStore.browse(context, " AB ", pageSize = 7)
        assertEquals(7, prefix.words.size)
        assertTrue(prefix.words.all { it.startsWith("ab") })
        val next = OfflineDictionaryStore.browse(context, "ab", prefix.words.last(), 7)
        assertTrue(next.words.all { it.startsWith("ab") && it > prefix.words.last() })
        assertTrue(prefix.words.intersect(next.words.toSet()).isEmpty())
        assertTrue(OfflineDictionaryStore.browse(context, "%").words.all { it.startsWith("%") })
        assertFalse(OfflineDictionaryStore.browse(context, "zzzzzzzzzzzzzz").hasMore)
        assertTrue(OfflineDictionaryStore.browse(context, "zzzzzzzzzzzzzz").words.isEmpty())
        assertThrows(IllegalArgumentException::class.java) { OfflineDictionaryStore.browse(context, pageSize = 0) }
        assertThrows(IllegalArgumentException::class.java) { OfflineDictionaryStore.browse(context, pageSize = 101) }
    }
    @Test fun openingABrowsedWordSavesItsMeaningAndExamples() = runBlocking {
        val word = OfflineDictionaryStore.browse(context, "abandon").words.first { it == "abandon" }
        val result = lookupAndSaveDictionaryWord(context, word, lookupSource = DictionaryLookupSource.OFFLINE)
        assertTrue(result.saved)
        val saved = parseVocabularyNoteContent(DocumentRepository(context).loadGeneralNotes()
            .first { it.title == "__vocab__$DICTIONARY_VOCAB_ID" }.content).last()
        assertEquals(word, saved.word)
        assertEquals(result.meaning.definition, saved.explanation)
        assertTrue(saved.examples.isNotEmpty())
        assertEquals(result.meaning.examples, saved.examples)
    }
}

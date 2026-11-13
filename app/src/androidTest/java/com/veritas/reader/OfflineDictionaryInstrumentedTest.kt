package com.veritas.reader

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.File
import java.util.UUID
import java.util.concurrent.CancellationException

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class OfflineDictionaryInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also {
        org.junit.Assume.assumeTrue("Dictionary checks require their own disposable package.", it.packageName.endsWith(".dictionarychecks"))
    }
    private fun archive(): File = File(context.cacheDir, "dictionary-fixture.zip").also { file ->
        InstrumentationRegistry.getInstrumentation().context.assets.open("english-wordnet-2025.zip").use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
    }

    @Test fun a_realPackInstallsAndLookupsRetainMeaningExamplesAndBookContext() = runBlocking {
        val ctx = context
        val fixture = archive()
        OfflineDictionaryStore.remove(ctx)
        val installed = OfflineDictionaryStore.install(ctx, fixture)
        android.util.Log.i("DictionaryChecks", "Installed ${installed.words} headwords, ${installed.bytes} bytes")
        assertEquals(127311, installed.words)
        assertTrue(installed.bytes > 0)
        assertEquals("entity", OfflineDictionaryStore.lookup(ctx, "entity")!!.headword)
        assertTrue(OfflineDictionaryStore.lookup(ctx, "entity")!!.senses.any { it.definition.contains("existence") })
        assertEquals("aardwolf", OfflineDictionaryStore.lookup(ctx, "aardwolves")!!.headword)
        assertEquals("child", OfflineDictionaryStore.lookup(ctx, "“children,”")!!.headword)
        assertTrue("entity" in OfflineDictionaryStore.suggestions(ctx, "entit"))
        val repository = DocumentRepository(ctx)
        val document = repository.createDocument("Dictionary fixture", "The children read a book.", "TXT")
        val token = UUID.randomUUID().toString()
        val anchor = DictionaryLookupAnchor(document.id, 0, "The children read a book.")
        val result = lookupAndSaveDictionaryWord(ctx, "children", anchor, token)
        assertNotNull(result.entry)
        assertTrue(result.saved)
        lookupAndSaveDictionaryWord(ctx, "children", anchor, token)
        val entries = parseVocabularyNoteContent(repository.loadGeneralNotes().first { it.title == "__vocab__${document.id}" }.content)
        assertEquals(1, entries.count { it.lookupId == token })
        assertEquals(0, entries.single().sentenceIndex)
        assertEquals("The children read a book.", entries.single().contextSentence)
        assertEquals(EnglishDictionaryPack.SOURCE, entries.single().meaningSourceUrl)
        val standalone = lookupAndSaveDictionaryWord(ctx, "abandon")
        assertTrue(standalone.saved)
        assertTrue(standalone.meaning.examples.isNotEmpty())
        val saved = parseVocabularyNoteContent(repository.loadGeneralNotes().first { it.title == "__vocab__$DICTIONARY_VOCAB_ID" }.content).last()
        assertEquals(standalone.meaning.definition, saved.explanation)
        assertEquals(standalone.meaning.examples, saved.examples)
        fixture.delete()
        Unit
    }

    @Test fun b_cancelledAndInvalidImportsNeverReplaceTheInstalledDictionary() {
        val ctx = context
        val fixture = archive()
        if (OfflineDictionaryStore.info(ctx) == null) OfflineDictionaryStore.install(ctx, fixture)
        val before = OfflineDictionaryStore.info(ctx)
        var checks = 0
        assertTrue(runCatching { OfflineDictionaryStore.install(ctx, fixture, checkActive = {
            if (++checks >= 200) throw CancellationException("Fixture cancellation")
        }) }.exceptionOrNull() is CancellationException)
        assertEquals(before, OfflineDictionaryStore.info(ctx))
        val invalid = File(ctx.cacheDir, "invalid-dictionary.zip").apply { writeText("Not the pinned dictionary") }
        assertTrue(runCatching { OfflineDictionaryStore.install(ctx, invalid) }.isFailure)
        assertEquals(before, OfflineDictionaryStore.info(ctx))
        assertNotNull(OfflineDictionaryStore.lookup(ctx, "entity"))
        assertFalse(File(ctx.noBackupFilesDir, "offline-dictionary").listFiles().orEmpty().any { it.name.startsWith("staging-") })
        fixture.delete(); invalid.delete()
    }

    @Test fun c_removingThePackPreservesSavedVocabularyAndExamples() {
        val ctx = context
        val repository = DocumentRepository(ctx)
        val entry = VocabularyEntry("retained dictionary fixture", "A retained meaning.", "Dictionary", -1,
            meaningSourceUrl = EnglishDictionaryPack.SOURCE, examples = listOf("A retained example."))
        repository.saveVocabularyLookup(ctx, DICTIONARY_VOCAB_ID, entry)
        OfflineDictionaryStore.remove(ctx)
        assertNull(OfflineDictionaryStore.info(ctx))
        val saved = parseVocabularyNoteContent(repository.loadGeneralNotes().first { it.title == "__vocab__$DICTIONARY_VOCAB_ID" }.content).last()
        assertEquals(entry, saved)
    }
}

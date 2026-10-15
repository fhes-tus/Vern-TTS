package com.veritas.reader

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import org.junit.Assert.*
import org.junit.Test

class VocabularyLookupInstrumentedTest {
    @Test fun backgroundLookupPersistsARealMeaningWithItsOriginalSentence() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks"))
        val repository = DocumentRepository(context)
        val original = repository.loadGeneralNotes()
        var lookupId: String? = null
        try {
            val notes = repository.saveVocabularyLookup(context, "lookup-instrumented",
                VocabularyEntry("serendipity", "Looked up definition / web references.", "source sentence 1", 0,
                    "It was a moment of serendipity."))
            val pending = parseVocabularyNoteContent(notes.single { it.title == "__vocab__lookup-instrumented" }.content).last()
            lookupId = pending.lookupId
            assertNotNull(lookupId)
            assertEquals(VocabularyLookup.PENDING, pending.explanation)
            val deadline = SystemClock.elapsedRealtime() + 90_000
            var saved = pending
            while (VocabularyLookup.needsDefinition(saved.explanation) && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(250)
                saved = parseVocabularyNoteContent(repository.loadGeneralNotes().single {
                    it.title == "__vocab__lookup-instrumented"
                }.content).last()
                if (saved.explanation == VocabularyLookup.UNAVAILABLE) break
            }
            assertFalse("No real dictionary meaning was saved: ${saved.explanation}", VocabularyLookup.needsDefinition(saved.explanation))
            assertEquals(pending.contextSentence, saved.contextSentence)
            assertEquals(pending.source, saved.source)
            assertEquals(0, saved.sentenceIndex)
            val reloaded = DocumentRepository(context).loadGeneralNotes().single { it.title == "__vocab__lookup-instrumented" }
            assertEquals(saved, parseVocabularyNoteContent(reloaded.content).last())
        } finally {
            lookupId?.let { WorkManager.getInstance(context).cancelUniqueWork("${VocabularyLookupWorker.TAG}:$it").result.get() }
            repository.saveGeneralNotes(original)
        }
    }
}

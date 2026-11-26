package com.veritas.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class SavedDictionaryLookupTest {
    @Test fun explicitSaveUsesDisplayedHeadwordSourceAndPassage() {
        val result = SavedDictionaryLookup(
            word = "running",
            source = DictionaryLookupSource.OFFLINE,
            anchor = DictionaryLookupAnchor("book", 7, "They kept running."),
            entry = DictionaryEntry("running", "run", emptyList()),
            meaning = VocabularyDefinition("To move quickly", "/rʌn/", "https://example.test/run", listOf("Run fast")),
            notes = emptyList(),
            saved = false
        )

        val savedEntry = result.vocabularyEntry()
        assertEquals("run", savedEntry.word)
        assertEquals(7, savedEntry.sentenceIndex)
        assertEquals("They kept running.", savedEntry.contextSentence)
        assertEquals("(looked up: sentence 8)", savedEntry.source)
        assertEquals("/rʌn/", savedEntry.pronunciation)
        assertEquals("https://example.test/run", savedEntry.meaningSourceUrl)
        assertEquals(listOf("Run fast"), savedEntry.examples)
    }
}

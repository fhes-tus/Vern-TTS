package com.veritas.reader

import org.junit.Assert.*
import org.junit.Test

class DictionaryVocabularyTest {
    @Test fun offlineMeaningExamplesSourceAndBookContextRoundTripTogether() {
        val entry = VocabularyEntry("affairs", "An undertaking.", "(looked up: sentence 3)", 2, "The affairs of the book.",
            lookupId = "lookup", meaningSourceUrl = EnglishDictionaryPack.SOURCE, examples = listOf("A difficult affair.", "Her own affair."))
        assertEquals(entry, parseVocabularyNoteContent(formatVocabularyEntry(entry)).single())
    }
    @Test fun onlineDictionaryExamplesAreParsedAndStored() {
        val meaning = VocabularyLookup.parse("""[{"meanings":[{"partOfSpeech":"noun","definitions":[{"definition":"An undertaking.","example":"A difficult affair."}]}]}]""")!!
        assertEquals(listOf("A difficult affair."), meaning.examples)
        val saved = parseVocabularyNoteContent(formatVocabularyEntry(VocabularyEntry("affair", meaning.definition, "Dictionary", -1, examples = meaning.examples))).single()
        assertEquals(meaning.examples, saved.examples)
    }
    @Test fun aMeaningUpdateChangesExamplesOnlyForItsMatchingLookup() {
        val first = VocabularyEntry("word", VocabularyLookup.PENDING, "sentence 1", 0, "Book passage.", lookupId = "first", examples = listOf("Old example."))
        val second = VocabularyEntry("other", "Keep this.", "source", -1, lookupId = "second", examples = listOf("Keep this example."))
        val body = formatVocabularyEntry(first) + "\n\n" + formatVocabularyEntry(second)
        val updated = parseVocabularyNoteContent(updateVocabularyMeaning(body, "first", VocabularyDefinition("A meaning.",
            sourceUrl = EnglishDictionaryPack.SOURCE, examples = listOf("A new example."))))
        assertEquals(listOf("A new example."), updated[0].examples)
        assertEquals("Book passage.", updated[0].contextSentence)
        assertEquals(second, updated[1])
    }
    @Test fun wiktionaryExamplesPreserveTextAndRemoveHtml() {
        val meaning = VocabularyLookup.parseWiktionary("""{"en":[{"partOfSpeech":"Noun","definitions":[{"definition":"An undertaking.","examples":["A <i>difficult</i> affair.",{"text":"Her &amp; his affair."},{"example":"A third example."},null]}]}]}""", "affair")!!
        assertEquals(listOf("A difficult affair.", "Her & his affair.", "A third example."), meaning.examples)
        val saved = parseVocabularyNoteContent(formatVocabularyEntry(VocabularyEntry("affair", meaning.definition, "Dictionary", -1, examples = meaning.examples))).single()
        assertEquals(meaning.examples, saved.examples)
    }
    @Test fun newExamplesCannotCreateAnotherVocabularyEntryThroughLineBreaks() {
        val entry = VocabularyEntry("word", "Meaning.", "source", -1, examples = listOf("Example.\n\nOther text."))
        val saved = parseVocabularyNoteContent(formatVocabularyEntry(entry))
        assertEquals(1, saved.size)
        assertEquals(listOf("Example. Other text."), saved.single().examples)
    }
    @Test fun missingExamplesAreNeverInventedAndOldEntriesStillRead() {
        assertTrue(parseVocabularyNoteContent("word\n  Meaning.\n  source").single().examples.isEmpty())
        assertTrue(VocabularyLookup.parse("""[{"meanings":[{"definitions":[{"definition":"Meaning only."}]}]}]""")!!.examples.isEmpty())
    }
}

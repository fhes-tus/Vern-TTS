package com.veritas.reader

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class VocabularyLookupTest {
    @Test fun alternateDictionaryKeepsRealEnglishDefinitionsAndStripsHtml() {
        val meaning = VocabularyLookup.parseWiktionary("""{"fr":[{"definitions":[{"definition":"Wrong language"}]}],"en":[{"partOfSpeech":"Noun","definitions":[{}, {"definition":"An <a href=\"/wiki/unplanned\">unplanned</a>, fortunate discovery &amp; its result."}]}]}""", "serendipity")!!
        assertEquals("(noun) An unplanned, fortunate discovery & its result.", meaning.definition)
        assertEquals("https://en.wiktionary.org/wiki/serendipity", meaning.sourceUrl)
        val entry = VocabularyEntry("serendipity", meaning.definition, "source sentence 1", 0, "Context.", lookupId = "lookup", meaningSourceUrl = meaning.sourceUrl)
        assertEquals(entry, parseVocabularyNoteContent(formatVocabularyEntry(entry)).single())
        assertNull(VocabularyLookup.parseWiktionary("""{"fr":[]}""", "word"))
    }
    @Test fun pendingAndLegacyEntriesAreRetriedButRealMeaningsAreKept() {
        assertTrue(VocabularyLookup.needsDefinition("looked up definition / web references."))
        assertTrue(VocabularyLookup.needsDefinition(VocabularyLookup.PENDING))
        assertTrue(VocabularyLookup.needsDefinition(VocabularyLookup.UNAVAILABLE))
        assertFalse(VocabularyLookup.needsDefinition("A fortunate discovery."))
    }

    @Test fun lateMeaningUpdatesOnlyItsEntryAndPreservesSourceContextAndTimestamp() {
        val first = formatVocabularyEntry(VocabularyEntry("word", VocabularyLookup.PENDING, "source sentence 3", 2,
            "The source sentence.", lookupId = "first"), "10 Oct 2026, 12:00")
        val second = formatVocabularyEntry(VocabularyEntry("word", "Already defined.", "other source", 5,
            "Another sentence.", lookupId = "second"))
        val updated = updateVocabularyMeaning(first + "\n\n" + second, "first", VocabularyDefinition("A meaning.", "/word/"))
        val entries = parseVocabularyNoteContent(updated)
        assertEquals("A meaning.", entries.first().explanation)
        assertEquals("The source sentence.", entries.first().contextSentence)
        assertEquals(2, entries.first().sentenceIndex)
        assertEquals("/word/", entries.first().pronunciation)
        assertTrue(updated.contains("[10 Oct 2026, 12:00]"))
        assertEquals(second, updated.substringAfter("\n\n"))
        assertEquals(second, updateVocabularyMeaning(second, "first", VocabularyDefinition("Late response.")))
    }
    @Test fun skipsEmptyFirstMeaningAndDefinitionAndFindsPhonetics() {
        val parsed = VocabularyLookup.parse("""[{"phonetics":[{"audio":"a"},{"text":"/wɜːd/"}],"meanings":[{"definitions":[]},{"partOfSpeech":"noun","definitions":[{}, {"definition":"A meaningful unit of language."}]}]}]""")!!
        assertEquals("(noun) A meaningful unit of language.", parsed.definition)
        assertEquals("/wɜːd/", parsed.pronunciation)
    }
    @Test fun readsLaterEntriesAndKeepsDistinctSenses() {
        val parsed = VocabularyLookup.parse("""[{"meanings":[]},{"meanings":[{"partOfSpeech":"noun","definitions":[{"definition":"A place to sit."}]},{"partOfSpeech":"verb","definitions":[{"definition":"To preside over a meeting."}]}]}]""")!!
        assertTrue(parsed.definition.contains("(noun) A place to sit."))
        assertTrue(parsed.definition.contains("(verb) To preside over a meeting."))
    }
    @Test fun preservesWordBoundariesAndApostrophesWithoutLocaleCorruption() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("ice cream", VocabularyLookup.query("  “ICE   CREAM!” "))
            assertEquals("don't", VocabularyLookup.query("don’t"))
            assertEquals("well-being", VocabularyLookup.query("well-being"))
            assertNull(VocabularyLookup.query("word/other"))
            assertNull(VocabularyLookup.query("..."))
        } finally { Locale.setDefault(previous) }
    }
    @Test fun formattedMeaningSurvivesVocabularyStorage() {
        val meaning = VocabularyLookup.parse("""[{"meanings":[{"definitions":[{"definition":"First line.\nSecond line."}]}]}]""")!!
        val text = formatVocabularyEntry(VocabularyEntry("word", meaning.definition, "source", 4, "Some context."))
        val saved = parseVocabularyNoteContent(text).single()
        assertEquals("First line. Second line.", saved.explanation)
        assertEquals("Some context.", saved.contextSentence)
    }
}

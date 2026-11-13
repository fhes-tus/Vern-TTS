package com.veritas.reader

import org.junit.Assert.*
import org.junit.Test

class WordNetDictionaryTest {
    @Test fun realWordNetRecordsKeepSynonymsAndGlosses() {
        val record = WordNetDictionary.synset("00002137 03 n 02 abstraction 0 abstract_entity 0 000 | a general concept formed by extracting common features", "n")!!
        assertEquals(2137, record.offset.toInt())
        assertEquals(listOf("abstraction", "abstract entity"), record.words)
        assertEquals("a general concept formed by extracting common features", record.gloss)
    }
    @Test fun indexPointerCountIsSkippedBeforeReadingSenseOrder() {
        val record = WordNetDictionary.index("abandon n 2 3 ! @ ~ 2 1 02220624 02679306", "n")!!
        assertEquals(listOf(2220624L, 2679306L), record.offsets)
        assertEquals("abandon", record.lemma)
    }
    @Test fun satelliteAdjectivesUseTheAdjectiveIndex() {
        val record = WordNetDictionary.synset("00001740 00 s 01 afraid(p) 0 000 | filled with fear", "a")!!
        assertEquals("a", record.pos)
        assertEquals(listOf("afraid"), record.words)
    }
    @Test fun headersAreNotTreatedAsDefinitions() {
        assertNull(WordNetDictionary.synset("  1 Copyright notice", "n"))
        assertNull(WordNetDictionary.index("  1 Copyright notice", "n"))
    }
    @Test fun malformedAndOversizedRecordsCannotBeInstalled() {
        assertTrue(runCatching { WordNetDictionary.synset("not a definition", "n") }.isFailure)
        assertTrue(runCatching { WordNetDictionary.synset("00001740 00 v 01 run 0 000 | go quickly", "n") }.isFailure)
        assertTrue(runCatching { WordNetDictionary.index("word n 2 0 2 0 123", "n") }.isFailure)
        val oversized = "00001740 03 n 01 entity 0 000 | " + "x".repeat(WordNetDictionary.MAX_RECORD_LENGTH)
        assertTrue(runCatching { WordNetDictionary.synset(oversized, "n") }.isFailure)
    }
    @Test fun examplesRemainSeparateFromTheMeaningAndCurrentHeadword() {
        val sense = WordNetDictionary.sense("n", "an undertaking; \"a difficult affair\"; \"her own affair\"", listOf("affair", "matter"), "affair")
        assertEquals("an undertaking", sense.definition)
        assertEquals(listOf("a difficult affair", "her own affair"), sense.examples)
        assertEquals(listOf("matter"), sense.synonyms)
    }
    @Test fun normalizedKeysAndConservativeInflectionsAreBounded() {
        assertEquals("ice cream", WordNetDictionary.query(" ICE_CREAM "))
        assertEquals("don't", WordNetDictionary.query("don’t"))
        assertNull(WordNetDictionary.query("a".repeat(81)))
        assertNull(WordNetDictionary.query("a\u0000b"))
        assertTrue("bag" in WordNetDictionary.candidateForms("bags"))
        assertTrue("study" in WordNetDictionary.candidateForms("studies"))
        assertFalse("" in WordNetDictionary.candidateForms("s"))
    }
}

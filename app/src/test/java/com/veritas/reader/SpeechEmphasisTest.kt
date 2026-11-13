package com.veritas.reader

import com.veritas.reader.tts.NeuralSpeechText
import org.junit.Assert.*
import org.junit.Test

class SpeechEmphasisTest {
    @Test fun underscoreEmphasisKeepsTheWordsWithoutSpeakingTheMarkers() {
        assertEquals("Carry the bag, then the other bag.",
            SpeechSanitizer.forSpeech("Carry the _bag_, then the _other bag_."))
        assertEquals("bag", SpeechSanitizer.forSpeech("_bag_"))
        assertEquals("bag", SpeechSanitizer.forSpeech("__bag__"))
        assertEquals("“bag!”", SpeechSanitizer.forSpeech("“_bag_!”"))
    }

    @Test fun literalUnderscoresAndIncompleteMarkupStayIntact() {
        for (source in listOf("bag_name", "a_b_c", "_unfinished", "unfinished_",
            "_ spaced _", "_first\nsecond_", "\\_bag\\_", "name_", "_bag__")) {
            assertEquals(source, source, SpeechSanitizer.forSpeech(source))
        }
    }

    @Test fun emphasisCleanupPreservesExactOffsetsInsideAndAfterTheMarkedWords() {
        val source = "Carry _bag_ and __other bag__ next."
        val spoken = SpokenText.identity(source).sanitize()
        assertEquals("Carry bag and other bag next.", spoken.text)
        for (word in listOf("Carry", "bag", "and", "other", "next")) {
            val start = spoken.text.indexOf(word)
            assertEquals(word, source.indexOf(word), spoken.sourceOffset(start))
            assertEquals(word, source.indexOf(word) + word.length, spoken.sourceEndOffset(start + word.length))
        }
        assertEquals(source.length, spoken.sourceOffset(spoken.text.length))
    }

    @Test fun cleanupIsLanguageIndependentAndPreservesCombiningAndSupplementaryCharacters() {
        val source = "_cafe\u0301_ _バッグ_ _😀_ next"
        val spoken = ReadingSymbols.prepare(source, english = false)
        assertEquals("cafe\u0301 バッグ 😀 next", spoken.text)
        for (word in listOf("cafe\u0301", "バッグ", "😀", "next")) {
            val start = spoken.text.indexOf(word)
            assertEquals(source.indexOf(word), spoken.sourceOffset(start))
            assertEquals(source.indexOf(word) + word.length, spoken.sourceEndOffset(start + word.length))
        }
        assertEquals("pré_bag_post", SpeechSanitizer.forSpeech("pré_bag_post"))
    }

    @Test fun emphasisIsAlsoCleanedInsideTableCells() {
        assertEquals("bag. Other words.", SpeechSanitizer.forSpeech("| _bag_ | __Other words__ |"))
    }

    @Test fun savedRulesStillApplyBeforeAutomaticEmphasisCleanup() {
        val skip = PronunciationRule("skip", "_{text}_", "", matchKind = SpeechRuleKind.PATTERN)
        val replace = skip.copy(id = "replace", replaceWith = "satchel")
        fun spoken(rule: PronunciationRule) = SpokenText.identity("Carry _bag_ next.")
            .applyRules(PronunciationRulesEngine.compile(listOf(rule))).sanitize().text
        assertEquals("Carry  next.", spoken(skip))
        assertEquals("Carry satchel next.", spoken(replace))
    }

    @Test fun neuralPreparationUsesTheSameDefaultCleanupWithoutASavedRule() {
        val source = "Carry _bag_ next."
        val spoken = NeuralSpeechText.prepare(SpokenText.identity(source))
        assertEquals("Carry bag next.", spoken.text)
        assertEquals(source.indexOf("bag"), spoken.sourceOffset(spoken.text.indexOf("bag")))
        assertEquals(source.indexOf("next"), spoken.sourceOffset(spoken.text.indexOf("next")))
        assertEquals(spoken.text, NeuralSpeechText.prepare(spoken).text)
    }
}

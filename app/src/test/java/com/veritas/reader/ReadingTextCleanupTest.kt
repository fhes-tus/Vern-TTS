package com.veritas.reader

import org.junit.Assert.*
import org.junit.Test

class ReadingTextCleanupTest {
    @Test fun singleDoubleAndTripleEmphasisKeepTheirContent() {
        for (marker in listOf("*", "**", "***", "_", "__", "___")) {
            val source = "Read ${marker}kofi$marker next."
            assertEquals("Read kofi next.", SpeechSanitizer.forSpeech(source))
            assertEquals("Read kofi next.", ReadingTextCleanup.forDisplay(source))
        }
    }

    @Test fun listenPresentationHidesHeadingMarkersAndStandaloneDividers() {
        assertEquals("Chapter one", ReadingTextCleanup.forDisplay("## Chapter one ##"))
        assertEquals("Chapter one\n\nText", ReadingTextCleanup.forDisplay("# Chapter one\n---\nText"))
        for (source in listOf("---", "***", "___", "- - -", "  *****  ")) {
            assertEquals("", ReadingTextCleanup.forDisplay(source))
            assertFalse(SpeechSanitizer.isSpeakable(source))
        }
    }

    @Test fun punctuationArithmeticAndIdentifiersArePreserved() {
        for (source in listOf("2 * 3", "2*3*4", "5 - 2", "a---b", "bag_name", "C#", "#tag", "*unfinished", "\\*kofi\\*")) {
            assertEquals(source, source, ReadingTextCleanup.forDisplay(source))
        }
    }

    @Test fun inlineNumericCitationsAreSkippedWithoutSavedRules() {
        assertEquals("The result was useful.", SpeechSanitizer.forSpeech("The result[12] was useful."))
        assertEquals("Alpha Beta", SpeechSanitizer.forSpeech("Alpha[12]Beta"))
        assertEquals("The result was useful.", SpeechSanitizer.forSpeech("The result[12][13] was useful."))
        assertEquals("", SpeechSanitizer.forSpeech("[12]."))
    }

    @Test fun ordinaryBracketsArraysMathIndicesAndLinksArePreserved() {
        for (source in listOf("Read [sic] and [Kofi].", "[1, 2, 3]", "[12–14]", "[2026]", "[0]",
            "a[12] = 4", "array[12]", "values[12]", "records[12]", "records[12].name",
            "The value [12] is recorded.", "The matrix [12] is shown.", "[12] people arrived.",
            "He read [12] books.", "It took [12] days.", "The number [12] is shown.", "Exercise [12].",
            "The answer is [12].", "The values were [12].",
            "[12](https://example.com)", "[12][source]", "[12]: https://example.com", "[[12]]", "\\[12]")) {
            assertEquals(source, source, SpeechSanitizer.forSpeech(source))
        }
    }

    @Test fun codeDoesNotLoseItsEmphasisLikeSymbolsOrIndices() {
        val source = "Use `_bag_ *kofi* values[12]` here."
        assertEquals(source, ReadingTextCleanup.forDisplay(source))
        assertEquals(source, SpeechSanitizer.forSpeech(source))
        assertEquals("```\n# title\n---\n*literal* [12]\n```", ReadingTextCleanup.forDisplay("```\n# title\n---\n*literal* [12]\n```"))
    }

    @Test fun numericBracketedTableCellsAreDataRatherThanAssumedCitations() {
        assertEquals("[12]. [13].", SpeechSanitizer.forSpeech("| [12] | [13] |"))
        assertEquals("Value. [12].", SpeechSanitizer.forSpeech("| Value | [12] |"))
        assertEquals("bag. [12].", SpeechSanitizer.forSpeech("| *bag* | [12] |"))
    }

    @Test fun listenKeepsReferenceTextWhileSpeechSkipsTheCitation() {
        assertEquals("The result[12].", ReadingTextCleanup.forDisplay("The *result*[12]."))
        assertEquals("The result.", SpeechSanitizer.forSpeech("The *result*[12]."))
    }

    @Test fun builtInsComposeWithCustomRulesAndRetainOriginalWordPositions() {
        val source = "# Read *kofi*[12] and _bag_ next."
        val rules = PronunciationRulesEngine.compile(listOf(PronunciationRule("name", "kofi", "Koffee")))
        val spoken = SpokenText.identity(source).applyRules(rules).sanitize()
        assertEquals("Read Koffee and bag next.", spoken.text)
        for ((spokenWord, original) in listOf("Read" to "Read", "Koffee" to "kofi", "bag" to "bag", "next" to "next")) {
            val start = spoken.text.indexOf(spokenWord)
            assertEquals(source.indexOf(original), spoken.sourceOffset(start))
            assertEquals(source.indexOf(original) + original.length, spoken.sourceEndOffset(start + spokenWord.length))
        }
    }

    @Test fun cleanupIsIdempotentAndNeverChangesStoredSource() {
        val source = "# Read *kofi*[12] and _bag_ next."
        val spoken = SpokenText.identity(source).sanitize()
        assertEquals(spoken.text, spoken.sanitize().text)
        assertEquals("# Read *kofi*[12] and _bag_ next.", source)
    }
}

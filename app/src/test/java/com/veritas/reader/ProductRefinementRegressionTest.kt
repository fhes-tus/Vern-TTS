package com.veritas.reader

import com.veritas.reader.ui.screens.buildReaderTextSelection
import org.junit.Assert.*
import org.junit.Test

class ProductRefinementRegressionTest {
    private fun rule(find: String, replacement: String) = PronunciationRule(find, find, replacement)

    @Test fun pronunciationMatchesOriginalTextWithoutCascading() {
        assertEquals("sequel changed", PronunciationRulesEngine.apply("SQL sequel", listOf(rule("SQL", "sequel"), rule("sequel", "changed"))))
    }

    @Test fun longestLiteralPhraseWinsAndAcceptsHorizontalWhitespace() {
        assertEquals("doctor A plus B", PronunciationRulesEngine.apply("DR.\u00a0Mensah C++", listOf(
            rule("Dr.", "short"), rule("Dr. Mensah", "doctor"), rule("C++", "A plus B")
        )))
    }

    @Test fun pronunciationRespectsUnicodeWordsAndContractions() {
        assertEquals("CAN'T can't cannot tin CANé Écan tin", PronunciationRulesEngine.apply(
            "CAN'T can't cannot can CANé Écan CAN", listOf(rule("can", "tin"))
        ))
    }

    @Test fun expandedAndSkippedSpeechKeepsOriginalOffsets() {
        val source = "SQL skip tail"
        val spoken = SpokenText.identity(source).applyRules(PronunciationRulesEngine.compile(listOf(
            rule("SQL", "sequel"), rule("skip", "")
        )))
        assertEquals("sequel  tail", spoken.text)
        assertEquals(0, spoken.sourceOffset(4))
        assertEquals(source.indexOf("tail"), spoken.sourceOffset(spoken.text.indexOf("tail")))
        assertEquals(source.length, spoken.sourceOffset(spoken.text.length))
    }

    @Test fun dialogueAttributionDoesNotTreatContractionsOrListsAsSpeech() {
        assertFalse(NarrationAnalyzer.isDialogue("He said it isn't ready."))
        assertFalse(NarrationAnalyzer.isDialogue("- An ordinary list item."))
        assertEquals("Alice", NarrationAnalyzer.extractSpeakerName("Alice said, “Ready?”"))
        assertEquals("Alice", NarrationAnalyzer.extractSpeakerName("“Ready?” Alice said."))
        val profiles = listOf(BookCharacter("narrator", "Narrator"), BookCharacter("dialogue", "Dialogue"), BookCharacter("alice", "Alice"))
        val settings = NarrationSettings(enabled = true, characterProfiles = profiles)
        assertEquals("alice", NarrationAnalyzer.getActiveCharacter("Alice said, “Ready?”", settings).id)
        assertEquals("dialogue", NarrationAnalyzer.getActiveCharacter("Alice said, “Ready?”", settings.copy(fullCastEnabled = false)).id)
    }

    @Test fun sourceLookupDoesNotMatchInsideWordsAndRetainsLigatureOffsets() {
        assertNull(PdfSelectionLocator.findMatch("he", listOf("The school.")))
        assertEquals(PdfSelectionMatch(0, 4), PdfSelectionLocator.findMatch("final", listOf("The ﬁnal word.")))
    }

    @Test fun exactPhraseOnAdjacentPageWinsOverPartialCurrentPage() {
        val sentences = listOf(
            ReaderSentence(0, "A long sentence selected here ends differently.", 1),
            ReaderSentence(1, "A long sentence selected here ends correctly.", 2)
        )
        val model = ReaderTextModel(sentences, emptyList(), 2, sentences.joinToString("\n") { it.text })
        assertEquals(PdfSelectionMatch(1, 0), PdfSelectionLocator.findMatch(sentences[1].text, model, 1))
    }

    @Test fun selectionAfterInlineContentPreservesSentenceOffset() {
        val part = ReaderPart(0, ReaderPageRange(0, 1, 1), 2, 3, "  after image.", listOf(ReaderPartSentenceRange(2, 0, 14, 24)))
        val selection = buildReaderTextSelection(part, 0, 7)!!
        assertEquals("after", selection.text)
        assertEquals(26, selection.sentenceCharOffset)
        assertEquals(listOf(2), selection.sentenceIndexes)
    }
}

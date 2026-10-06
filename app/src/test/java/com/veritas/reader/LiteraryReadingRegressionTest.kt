package com.veritas.reader

import org.junit.Assert.*
import org.junit.Test

class LiteraryReadingRegressionTest {
    private fun model(text: String) = ReaderTextIndex.build(text)

    @Test fun closingQuotesKeepOneHighlightPerSentence() {
        assertEquals(listOf("“He left.", "She stayed.”", "Then I went home."),
            model("“He left. She stayed.” Then I went home.").sentences.map { it.text })
        assertEquals(listOf("\"Go!\"", "They left.", "then he returned."),
            model("\"Go!\" They left. then he returned.").sentences.map { it.text })
    }

    @Test fun speechTagsTitlesAndInitialsRemainWithinTheirSentence() {
        assertEquals(listOf("“Seven!” I answered.", "Dr. Watson met Mr. Holmes.", "They left."),
            model("“Seven!” I answered. Dr. Watson met Mr. Holmes. They left.").sentences.map { it.text })
    }

    @Test fun modernPilgrimLabelsCarryAcrossSpeechAndReturnToProse() {
        val reading = model("Christian: “I am afraid. Where should I go?”\nEvangelist: “Follow that light.”\n\nHe walked towards the gate.")
        val cues = LiteraryDialogue.cues(reading.sentences)
        assertEquals(listOf("Christian", "Christian", "Evangelist", null), cues.map { it.speaker })
        assertEquals(listOf(true, true, true, false), cues.map { it.dialogue })
        assertEquals("“I am afraid.", reading.sentences[0].text.drop(cues[0].labelEnd))
    }

    @Test fun builtInPilgrimAbbreviationsAreStageCuesNotSpokenNames() {
        val reading = model("Chris. I carry a burden. It is heavy.\nEvan. Go towards the light.\n\nThen Christian began to walk.")
        val cues = LiteraryDialogue.cues(reading.sentences)
        assertEquals(listOf("Christian", "Christian", "Evangelist", null), cues.map { it.speaker })
        assertEquals("I carry a burden.", reading.sentences[0].text.drop(cues[0].labelEnd))
        assertEquals("Go towards the light.", reading.sentences[2].text.drop(cues[2].labelEnd))
    }

    @Test fun sherlockSpeechKeepsItsRoleButDoesNotGuessAnUnknownSpeaker() {
        val reading = model("Holmes said, “The door was open. I knew it at once.”\n\n“Seven!” I answered.\n\nHe looked at the letter.")
        val cues = LiteraryDialogue.cues(reading.sentences)
        assertEquals("Holmes", cues[0].speaker)
        assertEquals("Holmes", cues[1].speaker)
        assertTrue(cues[2].dialogue)
        assertNull(cues[2].speaker)
        assertFalse(cues[3].dialogue)
        val settings = NarrationSettings(enabled = true, characterProfiles = listOf(BookCharacter("holmes", "Sherlock Holmes")))
        assertEquals("holmes", NarrationAnalyzer.getActiveCharacter(reading.sentences[1].text, settings, cues[1]).id)
    }

    @Test fun contractionsAndShortQuotedTermsAreProse() {
        assertFalse(NarrationAnalyzer.isDialogue("The title ‘A Study in Scarlet’ was written on the paper beside the door."))
        assertFalse(NarrationAnalyzer.isDialogue("He couldn't tell whether it's ready."))
        assertNull(NarrationAnalyzer.extractSpeakerName("“I will go,” said he."))
    }

    @Test fun indexMigrationUsesSourceOrderForRepeatedPassagesAndResumeOffsets() {
        val source = "“Go. Go.” He went. “Go. Go.” They went."
        val old = ReaderTextIndex.build(source, legacySentenceBoundaries = true)
        val fresh = model(source)
        val anchors = SentenceAnchorMap(old.sentences, fresh.sentences)
        old.sentences.forEach { sentence ->
            sentence.text.forEachIndexed { offset, char ->
                if (!char.isWhitespace()) {
                    val (index, newOffset) = anchors.position(sentence.index, offset)
                    assertEquals(char, fresh.sentences[index].text[newOffset])
                }
            }
        }
    }

    @Test fun nativeSelectionRangesRemainExactAfterDialogueSplits() {
        val reading = model("Christian: “I am afraid. Where should I go?”\n\nHe walked onwards.")
        reading.parts.forEach { part -> part.sentenceRanges.forEach { range ->
            val rendered = part.text.substring(range.start, range.endExclusive)
            val source = reading.sentences[range.sentenceIndex].text
            assertEquals(rendered, source.substring(range.sentenceCharOffset, range.sentenceCharOffset + rendered.length))
        } }
    }

    @Test fun customHandoffKeepsUserInstructionsAndOptionalContext() {
        assertEquals("Compare the characters.", AiPromptLauncher.customPromptBody("  Compare the characters.  "))
        assertEquals("Explain this.\n\nPassage:\nA quotation.", AiPromptLauncher.customPromptBody("Explain this.", " A quotation. "))
    }
}

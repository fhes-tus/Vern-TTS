package com.veritas.reader

import org.junit.Assert.*
import org.junit.Test

class SpeechPartSequenceTest {
    private fun parts() = (0..2).map { index -> SpeechSegment("doc", "revision", 5, 0, 3, "ABC",
        SpokenText.identity("ABC").slice(index, index + 1), BookCharacter("narrator", "Narrator"),
        VoiceSettings(), SpeechEngineFamily.ANDROID, 1f, 1f, 0L) }

    @Test fun partsAdvanceInOrderAndFinishExactlyOnce() {
        var number = 0
        val sequence = SpeechPartSequence(parts(), false, "chunk:") { (++number).toString() }
        val output = StringBuilder()
        repeat(3) { index ->
            val id = sequence.utteranceId
            output.append(sequence.current!!.spoken.text)
            assertEquals(if (index == 2) SpeechPartSequence.Completion.FINISHED else SpeechPartSequence.Completion.NEXT,
                sequence.complete(id))
            assertEquals(SpeechPartSequence.Completion.IGNORED, sequence.complete(id))
        }
        assertEquals("ABC", output.toString())
        assertNull(sequence.current)
        assertNull(sequence.utteranceId)
    }

    @Test fun unrelatedCallbacksCannotSkipAPart() {
        val sequence = SpeechPartSequence(parts(), false, "chunk:")
        val id = sequence.utteranceId
        for (stale in listOf(null, "old", "selection:old")) {
            assertEquals(SpeechPartSequence.Completion.IGNORED, sequence.complete(stale))
            assertEquals(id, sequence.utteranceId)
            assertEquals("A", sequence.current!!.spoken.text)
        }
    }

    @Test fun cancellationRejectsLateCompletionWithoutFinishingOrAdvancing() {
        val sequence = SpeechPartSequence(parts(), true, "selection:")
        val abandoned = sequence.utteranceId
        sequence.cancel()
        assertFalse(sequence.accepts(abandoned))
        assertEquals(SpeechPartSequence.Completion.IGNORED, sequence.complete(abandoned))
        assertNull(sequence.current)
        sequence.cancel()
    }

    @Test fun aReplacementSelectionDoesNotAcceptThePreviousSelectionsEvents() {
        val first = SpeechPartSequence(parts(), true, "selection:")
        val old = first.utteranceId
        first.cancel()
        val second = SpeechPartSequence(parts(), true, "selection:")
        assertTrue(second.selection)
        assertEquals(SpeechPartSequence.Completion.IGNORED, second.complete(old))
        assertEquals("A", second.current!!.spoken.text)
    }

    @Test fun nativeAndOfflineBudgetsAreExplicitAndDifferentFromTokenCounts() {
        assertEquals(4000, SpeechInputBudget.limit(SpeechEngineFamily.ANDROID, 4000))
        assertEquals(800, SpeechInputBudget.limit(SpeechEngineFamily.PIPER, 4000))
        assertEquals(800, SpeechInputBudget.limit(SpeechEngineFamily.KOKORO, 4000))
        assertThrows(IllegalArgumentException::class.java) { SpeechInputBudget.limit(SpeechEngineFamily.ANDROID, 0) }
    }
}

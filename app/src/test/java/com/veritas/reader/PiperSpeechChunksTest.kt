package com.veritas.reader

import com.veritas.reader.tts.PiperSpeechChunks
import com.veritas.reader.tts.VoiceModelManager
import org.junit.Assert.*
import org.junit.Test

class PiperSpeechChunksTest {
    @Test fun ordinarySentencesReachTheModelUnchanged() {
        val text = "She asked, ‘Are you sure?’ He replied, ‘Yes—absolutely.’"
        assertEquals(listOf(text),PiperSpeechChunks.split(text))
    }
    @Test fun longPassagesKeepEveryCharacterAndUseWordBoundaries() {
        val text = "The path crossed the river, past the school and through the village. ".repeat(20)
        val parts = PiperSpeechChunks.split(text)
        assertEquals(text,parts.joinToString(""))
        assertTrue(parts.all { it.length <= PiperSpeechChunks.LIMIT })
        assertTrue(parts.dropLast(1).all { it.last().isWhitespace() })
    }
    @Test fun boundaryCannotBreakCombiningCharactersOrSurrogatePairs() {
        for(text in listOf("x".repeat(319)+"😀"+"y".repeat(15),"x".repeat(319)+"a\u0301"+"y".repeat(15))) {
            val parts = PiperSpeechChunks.split(text)
            assertEquals(text,parts.joinToString(""))
            assertTrue(parts.none { Character.isLowSurrogate(it.first()) || Character.isHighSurrogate(it.last()) || it.first() == '\u0301' })
        }
    }
    @Test fun everyOfferedArchiveHasAPublishedDigestAndRealSize() {
        for(voice in VoiceModelManager.availableVoices) {
            assertTrue(voice.id,voice.expectedChecksum?.matches(Regex("[a-f0-9]{64}")) == true)
            assertTrue(voice.id,voice.downloadSizeMb > 50f)
        }
        assertEquals(8,VoiceModelManager.availableVoices.count { it.id.startsWith("kitten_") })
        assertEquals(VoiceManager.KITTEN,SpeechEngineRouting.engineForVoice("kitten_0"))
        assertTrue(SpeechEngineRouting.isVeritasEngine(VoiceManager.KITTEN))
    }
}

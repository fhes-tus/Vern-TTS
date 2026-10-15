package com.veritas.reader

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile

class StreamingWavMergerTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun wav(name: String, samples: Int, sampleRate: Int = 24000): File {
        val file = temporary.newFile(name)
        RandomAccessFile(file, "rw").use { out ->
            fun tag(text: String) = out.write(text.toByteArray())
            fun int(value: Int) = out.writeInt(Integer.reverseBytes(value))
            fun short(value: Int) = out.writeShort(java.lang.Short.reverseBytes(value.toShort()).toInt())
            tag("RIFF"); int(36 + samples * 2); tag("WAVE"); tag("fmt "); int(16)
            short(1); short(1); int(sampleRate); int(sampleRate * 2); short(2); short(16)
            tag("data"); int(samples * 2)
            repeat(samples) { short(it) }
        }
        return file
    }
    @Test fun streamsCompatiblePartsIntoOneWav() {
        val destination = temporary.newFile("merged.wav")
        StreamingWavMerger.merge(listOf(wav("a.wav", 80_000), wav("b.wav", 90_000)), destination)
        assertEquals(44 + 170_000 * 2L, destination.length())
        RandomAccessFile(destination, "r").use { it.seek(40); assertEquals(340_000, Integer.reverseBytes(it.readInt())) }
    }
    @Test fun rejectsMismatchedVoicesBeforeWriting() {
        val destination = File(temporary.root, "merged.wav")
        assertThrows(IllegalArgumentException::class.java) {
            StreamingWavMerger.merge(listOf(wav("a.wav", 2), wav("b.wav", 2, 22050)), destination)
        }
        assertFalse(destination.exists())
    }
    @Test fun cancelledMergeRemovesIncompleteOutput() {
        val destination = File(temporary.root, "merged.wav")
        assertThrows(IllegalStateException::class.java) {
            StreamingWavMerger.merge(listOf(wav("a.wav", 80_000)), destination) { error("Cancelled") }
        }
        assertFalse(destination.exists())
    }
    @Test fun truncatedSynthesisIsRejected() {
        val part = wav("a.wav", 10)
        RandomAccessFile(part, "rw").use { it.setLength(50) }
        assertThrows(IllegalArgumentException::class.java) { StreamingWavMerger.merge(listOf(part), File(temporary.root, "merged.wav")) }
    }
}

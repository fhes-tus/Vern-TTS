package com.veritas.reader

import com.veritas.reader.tts.PcmCompletion
import com.veritas.reader.tts.PcmOutput
import com.veritas.reader.tts.PlaybackGeneration
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NeuralOutputReliabilityTest {
    @Test fun unsignedPlaybackHeadWrapStillWaitsForAllFrames() = runBlocking {
        var head = 0xfffffffcL
        var waits = 0
        val output = object : PcmOutput {
            override fun write(samples: ShortArray, offset: Int, count: Int) = count
            override fun renderedFrames() = head
        }
        assertTrue(PcmCompletion.play(ShortArray(10), 1000, output, { true }, { 0L }) {
            head = (head + 2) and 0xffffffffL
            waits++
            check(waits <= 5) { "Counter wrap stalled playback completion" }
        })
        assertEquals(5, waits)
    }
    @Test fun lateSynthesisCannotPublishAfterSeekOrPause() {
        val generation = PlaybackGeneration()
        val old = generation.current()
        var published = false
        generation.advance()
        assertFalse(generation.publishIfCurrent(old) { published = true })
        assertFalse(published)
        assertTrue(generation.publishIfCurrent(generation.current()) { published = true })
        assertTrue(published)
    }
    @Test fun partialWritesAreCompletedBeforeReportingSuccess() = runBlocking {
        var written = 0
        val output = object : PcmOutput {
            override fun write(samples: ShortArray, offset: Int, count: Int): Int {
                assertEquals(written, offset)
                val result = minOf(3, count)
                written += result
                return result
            }
            override fun renderedFrames() = written.toLong()
        }
        assertTrue(PcmCompletion.play(ShortArray(10), 1000, output, { true }, { 0L }) {})
        assertEquals(10, written)
    }
    @Test fun failedWriteNeverCompletes() = runBlocking {
        val output = object : PcmOutput {
            override fun write(samples: ShortArray, offset: Int, count: Int) = -6
            override fun renderedFrames() = 0L
        }
        assertFalse(PcmCompletion.play(ShortArray(10), 1000, output, { true }, { 0L }) {})
    }
    @Test fun stalledHeadTimesOutWithoutAdvancing() = runBlocking {
        var time = 0L
        val output = object : PcmOutput {
            override fun write(samples: ShortArray, offset: Int, count: Int) = count
            override fun renderedFrames() = 0L
        }
        assertFalse(PcmCompletion.play(ShortArray(10), 1000, output, { true }, { time }) { time += 1000L })
    }
    @Test fun cancellationAfterSubmissionDoesNotComplete() = runBlocking {
        var active = true
        val output = object : PcmOutput {
            override fun write(samples: ShortArray, offset: Int, count: Int): Int { active = false; return count }
            override fun renderedFrames() = 0L
        }
        assertFalse(PcmCompletion.play(ShortArray(10), 1000, output, { active }, { 0L }) {})
    }
}

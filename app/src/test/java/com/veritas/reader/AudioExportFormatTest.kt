package com.veritas.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioExportFormatTest {
    @Test
    fun estimatesUseThePublishedSampleRateAndBitRate() {
        assertEquals(48_000L, AudioExportFormat.WAV.estimateBytes(1.0))
        assertEquals(8_000L, AudioExportFormat.M4A.estimateBytes(1.0))
        assertEquals("audio/mp4", AudioExportFormat.forFileName("Vern.M4A").mimeType)
    }

    @Test
    fun estimateAccountsForRateAndUnspacedScripts() {
        val text = listOf("The reader can choose a smaller audio file.")
        assertTrue(estimateAudioExportSeconds(text, 1f) > estimateAudioExportSeconds(text, 2f))
        assertTrue(estimateAudioExportSeconds(listOf("漫長的故事可以靜靜地閱讀"), 1f) > 1.0)
    }
}

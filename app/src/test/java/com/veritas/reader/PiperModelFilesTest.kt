package com.veritas.reader

import com.veritas.reader.tts.PiperModelFiles
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PiperModelFilesTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun preferQualityButRetainLegacyAndIgnoreEmptyQualityFiles() {
        val dir = temporary.newFolder()
        val legacy = File(dir, "voice.onnx").apply { writeText("legacy") }
        assertEquals(legacy, PiperModelFiles.preferred(dir, "voice"))
        val quality = File(dir, "voice.fp32.onnx").apply { writeBytes(byteArrayOf()) }
        assertEquals(legacy, PiperModelFiles.preferred(dir, "voice"))
        quality.writeText("quality")
        assertEquals(quality, PiperModelFiles.preferred(dir, "voice"))
        assertEquals("legacy", legacy.readText())
    }
    @Test fun publishingQualityDoesNotReplaceTheActiveLegacyPackage() {
        val stage = temporary.newFolder()
        val dest = temporary.newFolder()
        File(stage, "voice.onnx").writeText("full precision")
        File(stage, "tokens.txt").writeText("same tokens")
        val legacy = File(dest, "voice.onnx").apply { writeText("active legacy") }
        File(dest, "tokens.txt").writeText("same tokens")
        PiperModelFiles.publishQuality(stage, dest, "voice")
        assertEquals("active legacy", legacy.readText())
        assertEquals("full precision", PiperModelFiles.preferred(dest, "voice")!!.readText())
    }
    @Test fun incompatibleTokensAndIncompleteUpdatesLeaveWorkingVoiceUnchanged() {
        val stage = temporary.newFolder()
        val dest = temporary.newFolder()
        File(stage, "voice.onnx").writeText("new")
        File(stage, "tokens.txt").writeText("different")
        val legacy = File(dest, "voice.onnx").apply { writeText("old") }
        File(dest, "tokens.txt").writeText("original")
        assertTrue(runCatching { PiperModelFiles.publishQuality(stage, dest, "voice") }.isFailure)
        assertEquals("old", legacy.readText())
        assertFalse(File(dest, "voice.fp32.onnx").exists())
        File(stage, "tokens.txt").writeText("original")
        File(stage, "voice.onnx").writeBytes(byteArrayOf())
        assertTrue(runCatching { PiperModelFiles.publishQuality(stage, dest, "voice") }.isFailure)
        assertEquals(legacy, PiperModelFiles.preferred(dest, "voice"))
    }
}
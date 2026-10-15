package com.veritas.reader

import org.junit.Assert.*
import org.junit.Test
import java.io.RandomAccessFile
import java.nio.file.Files

class PcmWavWriterTest {
    @Test fun streamsSamplesAndProducesAMergeableHeader() {
        val folder = Files.createTempDirectory("vern_pcm_writer").toFile()
        try {
            val part = java.io.File(folder, "part.wav")
            PcmWavWriter(part, 24000).use { writer ->
                repeat(4) { writer.append(ShortArray(40000) { i -> (i - 20000).toShort() }) }
            }
            assertEquals(44L + 320000, part.length())
            RandomAccessFile(part, "r").use {
                it.seek(24); assertEquals(24000, Integer.reverseBytes(it.readInt()))
                it.seek(40); assertEquals(320000, Integer.reverseBytes(it.readInt()))
                assertEquals((-20000).toShort(), java.lang.Short.reverseBytes(it.readShort()))
            }
            val merged = java.io.File(folder, "merged.wav")
            StreamingWavMerger.merge(listOf(part, part), merged)
            assertEquals(44L + 640000, merged.length())
        } finally { folder.deleteRecursively() }
    }
}

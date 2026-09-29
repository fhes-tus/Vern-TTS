package com.veritas.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

class PptLegacyExtractorTest {

    @Test
    fun `isPptFile detects OLE2 compound file magic bytes`() {
        val magicBytes = byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(),
            0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte(),
            0x00, 0x00, 0x00, 0x00
        )
        assertTrue(PptLegacyExtractor.isPptFile(magicBytes))

        val nonPptBytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04) // ZIP header
        assertFalse(PptLegacyExtractor.isPptFile(nonPptBytes))
    }

    @Test
    fun `extract extracts text records from simulated binary PPT`() {
        // Construct a small simulated PPT binary payload
        val header = ByteArray(512)
        header[0] = 0xD0.toByte()
        header[1] = 0xCF.toByte()
        header[2] = 0x11.toByte()
        header[3] = 0xE0.toByte()
        header[4] = 0xA1.toByte()
        header[5] = 0xB1.toByte()
        header[6] = 0x1A.toByte()
        header[7] = 0xE1.toByte()

        val text1 = "Slide 1 Title"
        val textBytes1 = text1.toByteArray(StandardCharsets.ISO_8859_1)

        val recordBuffer = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN)
        // Record 1: TextBytesAtom (0x0FA8)
        recordBuffer.putShort(0.toShort()) // ver / inst
        recordBuffer.putShort(0x0FA8.toShort()) // type
        recordBuffer.putInt(textBytes1.size) // len
        recordBuffer.put(textBytes1)

        val fullPayload = header + recordBuffer.array().copyOf(recordBuffer.position())
        val result = PptLegacyExtractor.extract(fullPayload)

        assertTrue(result.text.contains("Slide 1 Title"))
    }
}

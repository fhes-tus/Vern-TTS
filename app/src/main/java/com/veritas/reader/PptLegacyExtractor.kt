package com.veritas.reader

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Lightweight binary parser for legacy Microsoft PowerPoint 97-2003 (.ppt) presentations.
 * Scans OLE2 / Record stream atoms for slide text, titles, notes, and bullet runs without
 * external dependencies.
 */
object PptLegacyExtractor {

    private const val RECORD_TYPE_SLIDE = 0x03EE
    private const val RECORD_TYPE_NOTES = 0x03F8
    private const val RECORD_TYPE_SLIDE_LIST_WITH_TEXT = 0x03FA
    private const val RECORD_TYPE_TEXT_CHARS_ATOM = 0x0FA0
    private const val RECORD_TYPE_TEXT_BYTES_ATOM = 0x0FA8
    private const val RECORD_TYPE_CSTRING = 0x0FBA

    data class LegacySlide(
        val slideNumber: Int,
        val textBlocks: List<String>
    )

    fun isPptFile(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        // OLE2 Compound Document magic bytes: D0 CF 11 E0 A1 B1 1A E1
        return bytes[0] == 0xD0.toByte() &&
                bytes[1] == 0xCF.toByte() &&
                bytes[2] == 0x11.toByte() &&
                bytes[3] == 0xE0.toByte() &&
                bytes[4] == 0xA1.toByte() &&
                bytes[5] == 0xB1.toByte() &&
                bytes[6] == 0x1A.toByte() &&
                bytes[7] == 0xE1.toByte()
    }

    /**
     * Extracts text blocks from a legacy .ppt binary file, grouped by slide.
     */
    fun extract(bytes: ByteArray): ExtractionBody {
        if (!isPptFile(bytes)) {
            // Fallback: heuristic scan over raw bytes for UTF-16LE / ASCII text runs
            return ExtractionBody(extractHeuristic(bytes))
        }

        val textRuns = scanRecordsForText(bytes)
        if (textRuns.isEmpty()) {
            return ExtractionBody(extractHeuristic(bytes))
        }

        val slides = groupIntoSlides(textRuns)
        val builder = StringBuilder()
        slides.forEachIndexed { index, slide ->
            if (slide.textBlocks.isNotEmpty()) {
                if (builder.isNotEmpty()) builder.append("\n\n")
                builder.append(ReaderTextIndex.pageMarker(index + 1)).append('\n')
                slide.textBlocks.forEach { block ->
                    builder.append(block).append('\n')
                }
            }
        }

        val result = builder.toString().trim()
        return ExtractionBody(
            text = if (result.isNotBlank()) result else extractHeuristic(bytes),
            diagnostics = listOf("Extracted ${slides.size} slides from legacy PowerPoint binary file.")
        )
    }

    private data class ScannedText(
        val text: String,
        val isSlideBoundary: Boolean = false
    )

    private fun scanRecordsForText(bytes: ByteArray): List<ScannedText> {
        val results = mutableListOf<ScannedText>()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        var pos = 512 // Skip OLE2 header
        while (pos + 8 <= bytes.size) {
            buffer.position(pos)
            buffer.short.toInt() and 0xFFFF
            val recType = buffer.short.toInt() and 0xFFFF
            val recLen = buffer.int

            if (recLen < 0 || pos + 8 + recLen > bytes.size) {
                pos += 2
                continue
            }

            when (recType) {
                RECORD_TYPE_SLIDE, RECORD_TYPE_SLIDE_LIST_WITH_TEXT -> {
                    results.add(ScannedText(text = "", isSlideBoundary = true))
                    pos += 8
                }
                RECORD_TYPE_TEXT_CHARS_ATOM -> {
                    if (recLen > 0 && recLen % 2 == 0 && recLen <= 65536) {
                        val textBytes = ByteArray(recLen)
                        buffer.get(textBytes)
                        val text = String(textBytes, StandardCharsets.UTF_16LE).cleanPptText()
                        if (text.isNotBlank()) {
                            results.add(ScannedText(text = text))
                        }
                    }
                    pos += 8 + recLen
                }
                RECORD_TYPE_TEXT_BYTES_ATOM -> {
                    if (recLen > 0 && recLen <= 65536) {
                        val textBytes = ByteArray(recLen)
                        buffer.get(textBytes)
                        val text = String(textBytes, StandardCharsets.ISO_8859_1).cleanPptText()
                        if (text.isNotBlank()) {
                            results.add(ScannedText(text = text))
                        }
                    }
                    pos += 8 + recLen
                }
                RECORD_TYPE_CSTRING -> {
                    if (recLen > 0 && recLen % 2 == 0 && recLen <= 16384) {
                        val textBytes = ByteArray(recLen)
                        buffer.get(textBytes)
                        val text = String(textBytes, StandardCharsets.UTF_16LE).cleanPptText()
                        if (text.isNotBlank() && text.length > 2) {
                            results.add(ScannedText(text = text))
                        }
                    }
                    pos += 8 + recLen
                }
                else -> {
                    pos += 2 // Scan forward
                }
            }
        }
        return results
    }

    private fun groupIntoSlides(scanned: List<ScannedText>): List<LegacySlide> {
        val slides = mutableListOf<LegacySlide>()
        var currentSlideNumber = 1
        var currentBlocks = mutableListOf<String>()

        for (item in scanned) {
            if (item.isSlideBoundary && currentBlocks.isNotEmpty()) {
                slides.add(LegacySlide(currentSlideNumber, currentBlocks))
                currentSlideNumber++
                currentBlocks = mutableListOf()
            } else if (item.text.isNotBlank()) {
                currentBlocks.add(item.text)
            }
        }
        if (currentBlocks.isNotEmpty()) {
            slides.add(LegacySlide(currentSlideNumber, currentBlocks))
        }
        return if (slides.isNotEmpty()) slides else listOf(LegacySlide(1, scanned.mapNotNull { it.text.ifBlank { null } }))
    }

    private fun extractHeuristic(bytes: ByteArray): String {
        val chunks = mutableListOf<String>()
        var i = 0
        while (i < bytes.size - 4) {
            // Check for UTF-16LE readable strings
            if (bytes[i] in 32..126 && bytes[i + 1] == 0.toByte() &&
                bytes[i + 2] in 32..126 && bytes[i + 3] == 0.toByte()
            ) {
                val start = i
                while (i + 1 < bytes.size && bytes[i] in 32..126 && bytes[i + 1] == 0.toByte()) {
                    i += 2
                }
                val length = i - start
                if (length >= 8) {
                    val str = String(bytes, start, length, StandardCharsets.UTF_16LE).trim()
                    if (str.isNotBlank() && !isGarbageString(str)) {
                        chunks.add(str)
                    }
                }
            } else {
                i++
            }
        }
        return chunks.distinct().joinToString("\n\n")
    }

    private fun isGarbageString(s: String): Boolean {
        if (s.length < 3) return true
        val letters = s.count { it.isLetterOrDigit() || it.isWhitespace() }
        return letters.toFloat() / s.length.toFloat() < 0.7f
    }

    private fun String.cleanPptText(): String {
        return this
            .replace('\r', '\n')
            .replace(Regex("""[\u0000-\u0008\u000B\u000C\u000E-\u001F]"""), "")
            .trim()
    }
}

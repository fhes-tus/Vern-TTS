package com.veritas.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfColumnExtractorTest {

    @Test
    fun positionedPdfLine_calculatesCenterAndWidthCorrectly() {
        val line = PositionedPdfLine(
            text = "Hello world",
            minX = 50f,
            maxX = 250f,
            y = 100f,
            height = 12f
        )
        assertEquals(150f, line.centerX, 0.001f)
        assertEquals(200f, line.width, 0.001f)
    }

    @Test
    fun positionedPdfSegment_calculatesCenterAndWidthCorrectly() {
        val segment = PositionedPdfSegment(
            minX = 40f,
            maxX = 140f,
            y = 80f,
            height = 10f,
            glyphCount = 15
        )
        assertEquals(90f, segment.centerX, 0.001f)
        assertEquals(100f, segment.width, 0.001f)
        assertEquals(15, segment.glyphCount)
    }

    @Test
    fun pdfPageProbe_holdsLinesAndSegments() {
        val line = PositionedPdfLine("Line 1", 50f, 300f, 100f, 10f)
        val seg = PositionedPdfSegment(50f, 300f, 100f, 10f, 6)
        val probe = PdfPageProbe(
            plainText = "Line 1\n",
            lines = listOf(line),
            segments = listOf(seg)
        )
        assertEquals("Line 1\n", probe.plainText)
        assertEquals(1, probe.lines.size)
        assertEquals(1, probe.segments.size)
    }

    @Test
    fun pdfColumnLayout_defaultRowBreaksEmpty() {
        val layout = PdfColumnLayout(
            splitX = 300f,
            columnTopY = 50f,
            columnBottomY = 700f,
            pageWidth = 600f,
            pageHeight = 800f
        )
        assertEquals(300f, layout.splitX, 0.001f)
        assertTrue(layout.rowBreaks.isEmpty())
    }
}

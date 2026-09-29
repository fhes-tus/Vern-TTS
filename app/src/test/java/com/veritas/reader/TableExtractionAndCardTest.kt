package com.veritas.reader

import com.veritas.reader.ui.screens.PageContentSegment
import com.veritas.reader.ui.screens.parsePageContentSegments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TableExtractionAndCardTest {


    @Test
    fun `parsePageContentSegments returns single prose segment when no table exists`() {
        val part = ReaderPart(
            index = 0,
            pageRange = ReaderPageRange(0, 1, 1),
            sentenceStartIndex = 0,
            sentenceEndIndexExclusive = 2,
            text = "This is sentence one. This is sentence two.",
            sentenceRanges = listOf(
                ReaderPartSentenceRange(0, 0, 21),
                ReaderPartSentenceRange(1, 22, 43)
            )
        )

        val segments = parsePageContentSegments(part)
        assertEquals(1, segments.size)
        assertTrue(segments[0] is PageContentSegment.Prose)
        assertEquals(part.text, (segments[0] as PageContentSegment.Prose).subPart.text)
    }

    @Test
    fun `parsePageContentSegments splits prose and tables into structured segments`() {
        val fullText = buildString {
            append("Before the table intro.\n\n")
            append("| Item | Qty | Price |\n")
            append("| --- | --- | --- |\n")
            append("| Apple | 5 | $2.50 |\n")
            append("| Banana | 10 | $3.00 |\n\n")
            append("After the table conclusion.")
        }

        val part = ReaderPart(
            index = 0,
            pageRange = ReaderPageRange(0, 1, 1),
            sentenceStartIndex = 0,
            sentenceEndIndexExclusive = 5,
            text = fullText,
            sentenceRanges = listOf(
                ReaderPartSentenceRange(0, 0, 23),
                ReaderPartSentenceRange(1, 25, 49),
                ReaderPartSentenceRange(2, 51, 74),
                ReaderPartSentenceRange(3, 76, 100),
                ReaderPartSentenceRange(4, 102, fullText.length)
            )
        )

        val segments = parsePageContentSegments(part)
        assertEquals(3, segments.size)

        // Segment 0: Prose
        assertTrue("Segment 0 is Prose", segments[0] is PageContentSegment.Prose)
        val prose1 = segments[0] as PageContentSegment.Prose
        assertTrue(prose1.subPart.text.contains("Before the table intro"))

        // Segment 1: Table
        assertTrue("Segment 1 is Table", segments[1] is PageContentSegment.Table)
        val table = segments[1] as PageContentSegment.Table
        assertEquals(3, table.rows.size) // header + 2 data rows (separator skipped)
        assertEquals(listOf("Item", "Qty", "Price"), table.rows[0])
        assertEquals(listOf("Apple", "5", "$2.50"), table.rows[1])
        assertEquals(listOf("Banana", "10", "$3.00"), table.rows[2])
        assertEquals("Row sentence indices should skip markdown separator and match exactly", listOf(1, 3, 4), table.rowSentenceIndices)

        // Segment 2: Prose
        assertTrue("Segment 2 is Prose", segments[2] is PageContentSegment.Prose)
        val prose2 = segments[2] as PageContentSegment.Prose
        assertTrue(prose2.subPart.text.contains("After the table conclusion"))
    }

    @Test
    fun `parsePageContentSegments bridges empty lines between table rows into a single table card`() {
        val fullText = buildString {
            append("Introductory paragraph.\n\n")
            append("| Header 1 | Header 2 |\n")
            append("| --- | --- |\n")
            append("| Row 1 Col 1 | Row 1 Col 2 |\n\n")
            append("| Row 2 Col 1 | Row 2 Col 2 |\n\n")
            append("| Row 3 Col 1 | Row 3 Col 2 |\n\n")
            append("Concluding paragraph.")
        }

        val part = ReaderPart(
            index = 0,
            pageRange = ReaderPageRange(0, 1, 1),
            sentenceStartIndex = 0,
            sentenceEndIndexExclusive = 6,
            text = fullText,
            sentenceRanges = listOf(
                ReaderPartSentenceRange(0, 0, 23),
                ReaderPartSentenceRange(1, 25, 48),
                ReaderPartSentenceRange(2, 50, 63),
                ReaderPartSentenceRange(3, 65, 96),
                ReaderPartSentenceRange(4, 98, 129),
                ReaderPartSentenceRange(5, 131, fullText.length)
            )
        )

        val segments = parsePageContentSegments(part)
        assertEquals("Should be exactly 3 segments: Prose, Table, Prose", 3, segments.size)
        assertTrue("Segment 1 should be a unified Table", segments[1] is PageContentSegment.Table)
        val table = segments[1] as PageContentSegment.Table
        assertEquals("Should have all 4 rows in ONE table", 4, table.rows.size)
        assertEquals(listOf("Header 1", "Header 2"), table.rows[0])
        assertEquals(listOf("Row 1 Col 1", "Row 1 Col 2"), table.rows[1])
        assertEquals(listOf("Row 2 Col 1", "Row 2 Col 2"), table.rows[2])
        assertEquals(listOf("Row 3 Col 1", "Row 3 Col 2"), table.rows[3])
    }

    @Test
    fun `parsePageContentSegments keeps preceding text strictly as prose and does not absorb into table`() {
        val fullText = buildString {
            append("Thesis text preceding table.\n\n")
            append("Chapter 1: The Investigation Begins\n")
            append("| Item | Qty | Price |\n")
            append("| --- | --- | --- |\n")
            append("| CDC 2021 | 4 hours | 4 days |\n")
            append("| FDA | 4 hours | 4 days |\n\n")
            append("Discussion continues here.")
        }

        val part = ReaderPart(
            index = 0,
            pageRange = ReaderPageRange(0, 1, 1),
            sentenceStartIndex = 0,
            sentenceEndIndexExclusive = 6,
            text = fullText,
            sentenceRanges = listOf(
                ReaderPartSentenceRange(0, 0, 28),
                ReaderPartSentenceRange(1, 30, 65),
                ReaderPartSentenceRange(2, 66, 90),
                ReaderPartSentenceRange(3, 91, 114),
                ReaderPartSentenceRange(4, 115, 149),
                ReaderPartSentenceRange(5, 151, fullText.length)
            )
        )

        val segments = parsePageContentSegments(part)
        assertEquals("Should be 3 segments: Prose, Table, Prose", 3, segments.size)
        val table = segments[1] as PageContentSegment.Table
        assertEquals(3, table.rows.size) // header + 2 data rows
        assertEquals(listOf("Item", "Qty", "Price"), table.rows[0])
        assertEquals(listOf("CDC 2021", "4 hours", "4 days"), table.rows[1])
        assertEquals(listOf("FDA", "4 hours", "4 days"), table.rows[2])

        // Verify preceding chapter title remains strictly in the prose segment
        val prose1 = (segments[0] as PageContentSegment.Prose).subPart.text
        val prose2 = (segments[2] as PageContentSegment.Prose).subPart.text
        assertTrue(prose1.contains("Chapter 1: The Investigation Begins"))
        org.junit.Assert.assertFalse(prose1.contains("---"))
        org.junit.Assert.assertFalse(prose2.contains("---"))
    }
}

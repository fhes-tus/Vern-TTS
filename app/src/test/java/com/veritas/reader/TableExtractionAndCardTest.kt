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

    @Test
    fun `PdfTableDetector detects 5-column table and formats markdown cleanly without interleaving`() {
        // Col 0: 50..120
        // Col 1: 130..240
        // Col 2: 250..360
        // Col 3: 370..440
        // Col 4: 450..550
        val lines = listOf(
            // Top prose
            PositionedPdfLine("VISION", 50f, 100f, 100f, 12f, listOf(PositionedPdfSegment(50f, 100f, 100f, 12f, 6, "VISION"))),
            PositionedPdfLine("To challenge a young generation to believe in their gifted purpose and passionately pursue Jesus Christ.", 50f, 550f, 120f, 12f, listOf(PositionedPdfSegment(50f, 550f, 120f, 12f, 90, "To challenge a young generation to believe in their gifted purpose and passionately pursue Jesus Christ."))),
            
            // Table Header lines (y = 200, 212, 224)
            PositionedPdfLine("COMMON HOW JESUS SAID WHAT", 50f, 340f, 200f, 12f, listOf(
                PositionedPdfSegment(50f, 110f, 200f, 12f, 6, "COMMON"),
                PositionedPdfSegment(130f, 230f, 200f, 12f, 14, "HOW JESUS SAID"),
                PositionedPdfSegment(250f, 290f, 200f, 12f, 4, "WHAT")
            )),
            PositionedPdfLine("THE HOW RICK TAF VISION'S", 250f, 540f, 212f, 12f, listOf(
                PositionedPdfSegment(250f, 280f, 212f, 12f, 3, "THE"),
                PositionedPdfSegment(370f, 430f, 212f, 12f, 8, "HOW RICK"),
                PositionedPdfSegment(450f, 540f, 212f, 12f, 12, "TAF VISION'S")
            )),
            PositionedPdfLine("NAME IT (Matt 22:37) APOSTLES DID CALLS IT VERSION", 50f, 540f, 224f, 12f, listOf(
                PositionedPdfSegment(50f, 90f, 224f, 12f, 4, "NAME"),
                PositionedPdfSegment(130f, 220f, 224f, 12f, 15, "IT (Matt 22:37)"),
                PositionedPdfSegment(250f, 350f, 224f, 12f, 12, "APOSTLES DID"),
                PositionedPdfSegment(370f, 430f, 224f, 12f, 8, "CALLS IT"),
                PositionedPdfSegment(450f, 510f, 224f, 12f, 7, "VERSION")
            )),

            // Row 1 (y = 250, 262) - Gap of 26 from 224
            PositionedPdfLine("The Great Love the LORD your Praising God ...Passionately", 50f, 550f, 250f, 12f, listOf(
                PositionedPdfSegment(50f, 110f, 250f, 12f, 9, "The Great"),
                PositionedPdfSegment(130f, 240f, 250f, 12f, 18, "Love the LORD your"),
                PositionedPdfSegment(250f, 330f, 250f, 12f, 12, "Praising God"),
                PositionedPdfSegment(450f, 550f, 250f, 12f, 15, "...Passionately")
            )),
            PositionedPdfLine("Commandment God with all your (Acts 2:47) Worship Pursue Jesus Christ heart (Matt 22:37)", 50f, 550f, 262f, 12f, listOf(
                PositionedPdfSegment(50f, 120f, 262f, 12f, 11, "Commandment"),
                PositionedPdfSegment(130f, 240f, 262f, 12f, 25, "God with all your heart (Matt 22:37)"),
                PositionedPdfSegment(250f, 320f, 262f, 12f, 11, "(Acts 2:47)"),
                PositionedPdfSegment(370f, 420f, 262f, 12f, 7, "Worship"),
                PositionedPdfSegment(450f, 550f, 262f, 12f, 19, "Pursue Jesus Christ")
            )),

            // Row 2 (y = 290) - Gap of 28 from 262
            PositionedPdfLine("The Great Commandment Love your neighbour as yourself (Matt 22:39) ...enjoying the favour of all the people (Acts 2:47) Ministry ...believe in their gifted purpose", 50f, 550f, 290f, 12f, listOf(
                PositionedPdfSegment(50f, 120f, 290f, 12f, 22, "The Great Commandment"),
                PositionedPdfSegment(130f, 240f, 290f, 12f, 35, "Love your neighbour as yourself (Matt 22:39)"),
                PositionedPdfSegment(250f, 360f, 290f, 12f, 35, "...enjoying the favour of all the people (Acts 2:47)"),
                PositionedPdfSegment(370f, 420f, 290f, 12f, 8, "Ministry"),
                PositionedPdfSegment(450f, 550f, 290f, 12f, 25, "...believe in their gifted purpose")
            )),

            // Bottom prose
            PositionedPdfLine("MISSION", 50f, 100f, 350f, 12f, listOf(PositionedPdfSegment(50f, 100f, 350f, 12f, 7, "MISSION"))),
            PositionedPdfLine("To accomplish our vision, we set out to worship God intimately whilst using our talents.", 50f, 550f, 370f, 12f, listOf(PositionedPdfSegment(50f, 550f, 370f, 12f, 80, "To accomplish our vision, we set out to worship God intimately whilst using our talents.")))
        )

        val result = PdfTableDetector.detectAndFormat(lines, 600f, 800f)
        org.junit.Assert.assertNotNull("Should detect table", result)
        val text = result!!
        println("=== EXTRACTED RESULT ===\n$text\n========================")
        
        // Assert prose preserved
        assertTrue(text.contains("VISION"))
        assertTrue(text.contains("To challenge a young generation"))
        assertTrue(text.contains("MISSION"))
        assertTrue(text.contains("To accomplish our vision"))

        // Assert table formatted as markdown
        assertTrue(text.contains("| COMMON NAME | HOW JESUS SAID IT (Matt 22:37) | WHAT THE APOSTLES DID | HOW RICK CALLS IT | TAF VISION'S VERSION |"))
        assertTrue(text.contains("| --- | --- | --- | --- | --- |"))
        assertTrue(text.contains("| The Great Commandment | Love the LORD your God with all your heart (Matt 22:37) | Praising God (Acts 2:47) | Worship | ...Passionately Pursue Jesus Christ |"))
        assertTrue(text.contains("| The Great Commandment | Love your neighbour as yourself (Matt 22:39) | ...enjoying the favour of all the people (Acts 2:47) | Ministry | ...believe in their gifted purpose |"))
    }
}

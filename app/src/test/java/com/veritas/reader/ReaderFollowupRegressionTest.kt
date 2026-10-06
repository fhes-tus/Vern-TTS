package com.veritas.reader

import com.veritas.reader.ui.screens.PageContentSegment
import com.veritas.reader.ui.screens.parsePageContentSegments
import org.junit.Assert.*
import org.junit.Test

class ReaderFollowupRegressionTest {
    private fun segment(x: Float, right: Float, y: Float) = PositionedPdfSegment(x, right, y, 10f, 25)

    @Test fun sparseUnequalColumnsAreDetected() {
        val segments = (0..5).map { segment(40f, 270f, 150f + it * 15) } +
            (0..2).map { segment(285f, 560f, 180f + it * 15) }
        val layout = PdfColumnDetector.detect(segments, 600f, 800f)
        assertNotNull(layout)
        assertEquals(277.5f, layout!!.splitX, 0.1f)
    }
    @Test fun headingAcrossGutterDoesNotRejectColumns() {
        val segments = (0..6).flatMap { listOf(segment(40f, 270f, 150f + it * 15), segment(285f, 560f, 150f + it * 15)) } +
            segment(200f, 400f, 100f)
        val layout = PdfColumnDetector.detect(segments, 600f, 800f)!!
        assertTrue(layout.columnTopY > 100f)
    }
    @Test fun singleColumnAndIndentedParagraphsStaySingle() {
        val segments = (0..12).map { segment(if (it % 3 == 0) 70f else 40f, 560f, 150f + it * 15) }
        assertNull(PdfColumnDetector.detect(segments, 600f, 800f))
    }
    @Test fun repeatedWordUsesSelectedSentenceEvenWhenPlaybackIsElsewhere() {
        val sentences = listOf("A curious event.", "The curious detective returned.")
        val source = resolveVocabularySource(sentences, "curious", 1)
        assertEquals(1, source.sentenceIndex)
        assertEquals(sentences[1], source.contextSentence)
        assertNull(resolveVocabularySource(sentences, "curious").sentenceIndex)
    }
    @Test fun unknownWordsDoNotAcquireAnUnrelatedSentence() {
        assertNull(resolveVocabularySource(listOf("A painting."), "paint").sentenceIndex)
        assertEquals(0, resolveVocabularySource(listOf("A painting."), "painting").sentenceIndex)
    }
    @Test fun vocabularyRoundTripRetainsContextAndPronunciation() {
        val entry = VocabularyEntry("curious", "First line\nSecond line", "(looked up: Section 2, sentence 8)", 7, "A curious event.", "/test/")
        val parsed = parseVocabularyNoteContent(formatVocabularyEntry(entry)).single()
        assertEquals(7, parsed.sentenceIndex)
        assertEquals(entry.contextSentence, parsed.contextSentence)
        assertEquals(entry.pronunciation, parsed.pronunciation)
        assertEquals("First line Second line", parsed.explanation)
        assertEquals(-1, parseVocabularyNoteContent(formatVocabularyEntry(entry.copy(source = "(looked up: source location unavailable)", sentenceIndex = -1))).single().sentenceIndex)
    }
    @Test fun inlineSlotsExistBeforeBitmapsAndPreserveSentenceIndexes() {
        val model = ReaderTextIndex.build("Before the illustration.\n\n[[VERITAS_IMAGE:0]]\n\nAfter the illustration.")
        val part = model.parts.single()
        val segments = parsePageContentSegments(part)
        assertTrue(segments[1] is PageContentSegment.Image)
        assertEquals("After the illustration.", (segments.last() as PageContentSegment.Prose).subPart.text.trim())
        val ranges = segments.filterIsInstance<PageContentSegment.Prose>().flatMap { it.subPart.sentenceRanges }
        assertEquals(part.sentenceRanges.last().sentenceIndex, ranges.last().sentenceIndex)
    }
    @Test fun geometryAnchorsRequireAnUnambiguousMatch() {
        val part = ReaderTextIndex.build("Before the diagram. After the diagram is described here.").parts.single()
        val anchors = InlineIllustrationPlanner.anchors(part, listOf("After the diagram is described here."))
        assertEquals(part.sentenceRanges[1].start, anchors.single().start)
        assertTrue(InlineIllustrationPlanner.anchors(part, listOf("unrelated diagram caption")).isEmpty())
    }
}

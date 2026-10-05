package com.veritas.reader

import androidx.compose.ui.text.font.FontWeight
import com.veritas.reader.ui.screens.RichTextFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RichTextFormatter powers the notes editor's WYSIWYG rendering: it hides markup characters
 * while styling the text, with a bidirectional offset mapping that keeps the caret in sync.
 * An off-by-one here desyncs the caret from the visible text, so the mapping is pinned hard.
 */
class RichTextFormatterTest {

    @Test
    fun boldMarkersAreHiddenAndStyled() {
        val out = RichTextFormatter.transform("**bold**")
        assertEquals("bold", out.text.text)
        assertTrue(out.text.spanStyles.any { it.item.fontWeight == FontWeight.Bold && it.start == 0 && it.end == 4 })
    }

    @Test
    fun inlineMarkersAreHiddenInContext() {
        assertEquals("a b c", RichTextFormatter.transform("a **b** c").text.text)
        assertEquals("i", RichTextFormatter.transform("*i*").text.text)
        assertEquals("u", RichTextFormatter.transform("__u__").text.text)
        assertEquals("s", RichTextFormatter.transform("~~s~~").text.text)
        assertEquals("m", RichTextFormatter.transform("`m`").text.text)
    }

    @Test
    fun headingPrefixIsHidden() {
        assertEquals("Head\nbody", RichTextFormatter.transform("# Head\nbody").text.text)
        assertEquals("Sub\nbody", RichTextFormatter.transform("## Sub\nbody").text.text)
    }

    @Test
    fun unbalancedMarkersAreLeftVisible() {
        assertEquals("**bold", RichTextFormatter.transform("**bold").text.text)
        assertEquals("`code", RichTextFormatter.transform("`code").text.text)
    }

    @Test
    fun offsetMappingRoundTripsAroundHiddenMarkers() {
        val mapping = RichTextFormatter.transform("a **b** c").offsetMapping
        assertEquals(0, mapping.originalToTransformed(0))
        assertEquals(2, mapping.originalToTransformed(4))
        assertEquals(4, mapping.originalToTransformed(8))
        assertEquals(8, mapping.transformedToOriginal(4))
        assertEquals(9, mapping.transformedToOriginal(5))
    }

    @Test
    fun offsetMappingClampsOutOfRangeOffsets() {
        val mapping = RichTextFormatter.transform("**x**").offsetMapping
        assertEquals(0, mapping.originalToTransformed(0))
        assertEquals(2, mapping.transformedToOriginal(0))
        assertEquals(1, mapping.originalToTransformed(100))
        assertEquals(3, mapping.transformedToOriginal(100))
    }

    @Test fun studyNoteFormattingHasNoVisibleSyntaxAndPreservesLiteralPunctuation() {
        assertEquals("Introduction\n• Definition: Standards\nAn italic and bold italic word.",
            RichTextFormatter.transform("## Introduction\n* **Definition**: Standards\nAn _italic_ and ***bold italic*** word.").text.text)
        val literal = "C# costs $5; file_name.txt; 2 * 3; unfinished **bold"
        assertEquals(literal, RichTextFormatter.transform(literal).text.text)
    }

    @Test fun everyVisibleCaretRoundTripsAndSelectionReplacesOnlyTheSelectedWords() {
        val raw = "## A heading\n**Read 📚 this** and _keep this_."
        val result = RichTextFormatter.transform(raw)
        val map = result.offsetMapping
        for (i in 0..result.text.length) {
            assertEquals(i, map.originalToTransformed(map.transformedToOriginal(i)))
        }
        val offsets = (0..raw.length).map { map.originalToTransformed(it) }
        assertTrue(offsets.zipWithNext().all { (a, b) -> a <= b })
        val start = result.text.text.indexOf("📚")
        val end = start + "📚".length
        val edited = raw.replaceRange(map.transformedToOriginal(start), map.transformedToOriginal(end), "books")
        assertTrue(edited.contains("**Read books this**"))
    }

    @Test
    fun stripMarkupRemovesInlineMarkersAndHeadings() {
        assertEquals(
            "a and b and c and d and e",
            RichTextFormatter.stripMarkup("**a** and *b* and `c` and ~~d~~ and __e__")
        )
        assertEquals("Title\n- item", RichTextFormatter.stripMarkup("## Title\n- item"))
        assertEquals("plain text", RichTextFormatter.stripMarkup("plain text"))
    }
}

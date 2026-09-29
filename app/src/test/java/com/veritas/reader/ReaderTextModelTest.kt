package com.veritas.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTextModelTest {

    @Test
    fun `academic et al citations do not trigger false sentence breaks`() {
        val input = "According to Johnson et al. (2020), the results were conclusive."
        val sentences = ReaderTextIndex.sentences(input)
        assertEquals(1, sentences.size)
        assertEquals(input, sentences[0])
    }

    @Test
    fun `soft-wrapped newline within sentence does not split mid-sentence`() {
        val input = "The lead researcher at\nHarvard University discovered a cure."
        val sentences = ReaderTextIndex.sentences(input)
        assertEquals(1, sentences.size)
        assertEquals(input, sentences[0])
    }

    @Test
    fun `distinct paragraphs with blank lines split correctly`() {
        val input = "First paragraph ends here.\n\nSecond paragraph begins here."
        val sentences = ReaderTextIndex.sentences(input)
        assertEquals(2, sentences.size)
        assertEquals("First paragraph ends here.", sentences[0])
        assertEquals("Second paragraph begins here.", sentences[1])
    }

    @Test
    fun `reader text model page count matches max sentence page number`() {
        val multilineText = (1..10).joinToString("\n\n") { "[[VERITAS_PAGE:$it]]\nThis is sentence on page $it." }
        val model = ReaderTextIndex.build(multilineText)
        assertEquals(10, model.pageCount)
        assertEquals(10, model.sentences.maxOf { it.pageNumber })
        assertEquals(10, model.sentences.size)
    }

    @Test
    fun `displaySeparator preserves newline between table of contents rows with dot leaders`() {
        val s1 = ReaderSentence(0, "1.2 Problem Statement . . . . . 11", 1)
        val s2 = ReaderSentence(1, "1.3 Research Objectives . . . . . 12", 1)
        val sep = ReaderTextIndex.displaySeparator("\n", s1, s2)
        assertEquals("\n", sep)
    }

    @Test
    fun `displaySeparator collapses regular soft newlines in prose to single space`() {
        val s1 = ReaderSentence(0, "This is the first sentence of standard prose.", 1)
        val s2 = ReaderSentence(1, "This is the second sentence of standard prose.", 1)
        val sep = ReaderTextIndex.displaySeparator("\n", s1, s2)
        assertEquals(" ", sep)
    }

    @Test
    fun `table of contents lines with leader dots and page numbers do not split on leader dots and preserve line breaks`() {
        val input = "Chapter 1: The Beginning ...... 10\nChapter 2: The Journey ...... 25"
        val model = ReaderTextIndex.build(input)
        assertEquals(2, model.sentences.size)
        assertEquals("Chapter 1: The Beginning ...... 10", model.sentences[0].text)
        assertEquals("Chapter 2: The Journey ...... 25", model.sentences[1].text)
        val rendered = model.parts.joinToString("\n") { it.text }
        assertTrue(rendered.contains("Chapter 1: The Beginning ...... 10\nChapter 2: The Journey ...... 25"))
    }

    @Test
    fun `rapid processing of large novel-sized text document`() {
        val paragraph = "Sherlock Holmes took his bottle from the corner of the shelf and his hypodermic syringe from its neat morocco case. With his long, white, nervous fingers he adjusted the delicate needle, and rolled back his left shirt-cuff. For some little time his eyes rested thoughtfully upon the sinewy forearm and wrist all dotted and scarred with innumerable puncture-points. Finally he thrust the sharp point home, pressed down the tiny piston, and sank back into the velvet-lined arm-chair with a long sigh of satisfaction.\n\nThree times a day for many months I had witnessed this performance, but my custom had not reconciled my mind to it. On the contrary, from day to day I had become more irritable at the sight, and my conscience swelled nightly within me at the thought that I had lacked the courage to protest.\n\n"
        val largeBookText = paragraph.repeat(500) // ~1,000 paragraphs, ~3,000 sentences
        val start = System.currentTimeMillis()
        val model = ReaderTextIndex.build(largeBookText)
        val elapsed = System.currentTimeMillis() - start
        assertTrue("Expected build to take under 2500ms, took ${elapsed}ms", elapsed < 2500)
        assertTrue(model.sentences.size >= 2500)
        assertTrue(model.parts.isNotEmpty())
    }

    @Test
    fun `numbered list items in text preserve distinct visual lines and do not split after item number`() {
        val input = """
            # PART I. TWO SYSTEMS

            1. The Characters of the Story
            2. Attention and Effort
            3. The Lazy Controller
            4. The Associative Machine
            5. Cognitive Ease

            # PART II. HEURISTICS AND BIASES
        """.trimIndent()
        val model = ReaderTextIndex.build(input)
        val rendered = model.parts.joinToString("\n") { it.text }

        assertTrue("Item 1 should be intact", model.sentences.any { it.text == "1. The Characters of the Story" })
        assertTrue("Item 2 should be intact", model.sentences.any { it.text == "2. Attention and Effort" })
        assertTrue("Item 3 should be intact", model.sentences.any { it.text == "3. The Lazy Controller" })

        // Assert that items are NOT joined into a single space-separated line
        org.junit.Assert.assertFalse("Numbered items should not be jumbled into one prose line", rendered.contains("1. The Characters of the Story 2. Attention and Effort"))
        assertTrue("Numbered items should be on distinct lines", rendered.contains("1. The Characters of the Story\n2. Attention and Effort\n3. The Lazy Controller"))
    }
}


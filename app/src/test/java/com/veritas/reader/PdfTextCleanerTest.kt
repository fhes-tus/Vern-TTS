package com.veritas.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfTextCleanerTest {
    @Test fun `page metadata never becomes spoken prose`() {
        val text = PdfTextCleaner.cleanPages(listOf("A readable paragraph."), listOf(47),
            PdfImportOptions(includePageMarkers = true)).text
        assertTrue(text.contains(ReaderTextIndex.pageMarker(47)))
        assertFalse(text.contains("Page 47"))
    }

    @Test fun `legacy page labels are silent without shifting offsets`() {
        val original = "Page 102\nHe would hardly reply to my questions, and waited."
        val spoken = SpeechSanitizer.forSpeech(original)
        assertTrue(spoken.length == original.length)
        assertFalse(spoken.contains("Page 102"))
        assertTrue(spoken.indexOf("questions") == original.indexOf("questions"))
    }

    @Test
    fun `cleanPages does not turn book text with wide spacing or tabs into pipe tables`() {
        val page = listOf(
            "me    to    a    larger    hospital    in    Cincinnati.",
            "I    was    rolled    out    of    the    emergency    room    doors",
            "and    toward    the    helipad    across    the    street.",
            "",
            "The stretcher rattled on a bumpy sidewalk."
        ).joinToString("\n")

        val result = PdfTextCleaner.cleanPages(listOf(page), listOf(1), PdfImportOptions(includePageMarkers = false))
        val text = result.text

        assertFalse("Should never contain pipe symbols in book prose", text.contains("|"))
        assertTrue("Should merge paragraph continuously without pipes", text.contains("me to a larger hospital in Cincinnati. I was rolled out of the emergency room doors and toward the helipad across the street."))
        assertTrue("Summary paragraph should be separated by double newline", text.contains("\n\nThe stretcher rattled on a bumpy sidewalk."))
    }

    @Test
    fun `cleanPages preserves existing markdown pipe tables`() {
        val page = listOf(
            "| Parameter | Value |",
            "| Speed | 100 km/h |",
            "| Temperature | 25 C |",
            "",
            "End of specs."
        ).joinToString("\n")

        val result = PdfTextCleaner.cleanPages(listOf(page), listOf(1), PdfImportOptions(includePageMarkers = false))
        val text = result.text

        assertTrue("Should preserve existing markdown table", text.contains("| Parameter | Value |\n| Speed | 100 km/h |\n| Temperature | 25 C |"))
    }

    @Test
    fun `cleanPages does not break regular sentences with spacing after period`() {
        val page = listOf(
            "This is the first sentence.   Then here is another sentence on the same line.",
            "And another normal paragraph follows."
        ).joinToString("\n")

        val result = PdfTextCleaner.cleanPages(listOf(page), listOf(1), PdfImportOptions(includePageMarkers = false))
        val text = result.text

        assertFalse("Should not treat sentence with space after period as table", text.contains("|"))
    }

    @Test
    fun `cleanPages does not treat continuous words in paragraph as headings`() {
        val page = listOf(
            "In his groundbreaking work, James Clear introduced",
            "The Four Laws of Behavior Change",
            "which explain how human habits are formed and maintained."
        ).joinToString("\n")

        val result = PdfTextCleaner.cleanPages(listOf(page), listOf(1), PdfImportOptions(includePageMarkers = false))
        val text = result.text

        assertFalse("Should not treat title-cased words in middle of sentence as heading", text.contains("# The Four Laws"))
        assertTrue("Should merge paragraph continuously", text.contains("In his groundbreaking work, James Clear introduced The Four Laws of Behavior Change which explain"))
    }

    @Test
    fun `cleanPages preserves genuine chapter headings and subheadings`() {
        val page = listOf(
            "CHAPTER 1",
            "The Fundamentals of Atomic Habits",
            "A habit is a routine or practice performed regularly.",
            "An automatic response to a specific type of situation."
        ).joinToString("\n")

        val result = PdfTextCleaner.cleanPages(listOf(page), listOf(1), PdfImportOptions(includePageMarkers = false))
        val text = result.text

        assertTrue("Should recognize Chapter heading", text.contains("# CHAPTER 1"))
        assertTrue("Should preserve paragraph body", text.contains("A habit is a routine or practice performed regularly. An automatic response"))
    }

    @Test
    fun `cleanPages recognizes isolated uppercase headings but excludes shouting dialogue`() {
        val page = listOf(
            "RACHE",
            "The body lay in the centre of the room.",
            "\"STOP!\" cried the inspector.",
            "He pointed at the wall."
        ).joinToString("\n")

        val result = PdfTextCleaner.cleanPages(listOf(page), listOf(1), PdfImportOptions(includePageMarkers = false))
        val text = result.text

        assertTrue("Should recognize isolated uppercase heading RACHE", text.contains("# RACHE"))
        assertFalse("Should not treat exclamation dialogue as heading", text.contains("# \"STOP!\""))
        assertFalse("Should not treat dialogue as heading", text.contains("# STOP!"))
    }

    @Test
    fun `cleanPages does not chop sentences starting with Part into headings`() {
        val page = listOf(
            "Part 2 updates the study of judgment heuristics and explores a major puzzle: Why is it",
            "so difficult for us to think statistically?",
            "",
            "Part 5 offers some reflections on a distinction between two selves."
        ).joinToString("\n")

        val result = PdfTextCleaner.cleanPages(listOf(page), listOf(1), PdfImportOptions(includePageMarkers = false))
        val text = result.text

        assertFalse("Should not make Part 2 sentence into a heading", text.contains("# Part 2"))
        assertFalse("Should not make Part 5 sentence into a heading", text.contains("# Part 5"))
        assertTrue("Sentence wrapped across lines should be merged seamlessly", text.contains("Part 2 updates the study of judgment heuristics and explores a major puzzle: Why is it so difficult for us to think statistically?"))
    }

    @Test
    fun `cleanPages recognizes isolated topic headings like Plot Synopsis and Conflict`() {
        val page = listOf(
            "The story began on a dark and stormy night.",
            "",
            "Plot Synopsis",
            "",
            "The protagonist embarks on a dangerous journey across the desert.",
            "",
            "Conflict",
            "",
            "A sudden sandstorm separates the travelers."
        ).joinToString("\n")

        val result = PdfTextCleaner.cleanPages(listOf(page), listOf(1), PdfImportOptions(includePageMarkers = false))
        val text = result.text

        assertTrue("Should recognize Plot Synopsis as heading", text.contains("# Plot Synopsis"))
        assertTrue("Should recognize Conflict as heading", text.contains("# Conflict"))
    }

    @Test
    fun `cleanPages preserves bullet list items on separate lines`() {
        val page = listOf(
            "Here are the key takeaways:",
            "• First important principle of the book",
            "• Second important principle with details",
            "• Third conclusion to consider",
            "",
            "Following paragraph continues normally."
        ).joinToString("\n")

        val result = PdfTextCleaner.cleanPages(listOf(page), listOf(1), PdfImportOptions(includePageMarkers = false))
        val text = result.text

        assertTrue("Bullet items should not be flattened with single spaces", text.contains("• First important principle"))
        assertTrue("Subsequent bullet should be on separate line", text.contains("• First important principle of the book\n• Second important principle"))
        assertTrue("Third bullet should be on separate line", text.contains("• Second important principle with details\n• Third conclusion to consider"))
    }


    @Test
    fun `cleanPages reassembles chapter prefix and title across lines into unified heading`() {
        val page = listOf(
            "CHAPTER 1",
            "The Concept of Attention",
            "",
            "Attention is an active cognitive process that focuses awareness."
        ).joinToString("\n")

        val result = PdfTextCleaner.cleanPages(listOf(page), listOf(1), PdfImportOptions(includePageMarkers = false))
        val text = result.text

        assertTrue("Should reassemble chapter prefix and title", text.contains("# CHAPTER 1: The Concept of Attention"))
        assertTrue("Prose should be separated below heading", text.contains("Attention is an active cognitive process"))
    }

    @Test
    fun `cleanPages preserves table of contents items on distinct lines`() {
        val page = listOf(
            "CONTENTS",
            "1 The Sign of the Four . . . . . 63",
            "2 A Study in Scarlet . . . . . 1",
            "3 The Hound of the Baskervilles . . . . . 150"
        ).joinToString("\n")

        val result = PdfTextCleaner.cleanPages(listOf(page), listOf(1), PdfImportOptions(includePageMarkers = false))
        val text = result.text

        assertTrue("TOC lines should not be glued into one paragraph", text.contains("1 The Sign of the Four . . . . . 63\n2 A Study in Scarlet . . . . . 1"))
        assertTrue("Subsequent TOC line should be on next line", text.contains("2 A Study in Scarlet . . . . . 1\n3 The Hound of the Baskervilles . . . . . 150"))
    }

    @Test
    fun `cleanPages preserves subheading followed by dialogue quote as separate blocks`() {
        val page = listOf(
            "Speaking of Attention and Effort",
            "“I had to turn my mind away from the noise in the street,” she said."
        ).joinToString("\n")

        val result = PdfTextCleaner.cleanPages(listOf(page), listOf(1), PdfImportOptions(includePageMarkers = false))
        val text = result.text

        assertTrue("Subheading should be recognized as heading", text.contains("# Speaking of Attention and Effort"))
        assertTrue("Dialogue quote should start on a new paragraph below the heading", text.contains("\n\n“I had to turn my mind away"))
    }
}

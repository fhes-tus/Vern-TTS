package com.veritas.reader

import com.veritas.reader.ui.screens.buildSmartOutline
import com.veritas.reader.ui.screens.extractPageMilestones
import com.veritas.reader.ui.screens.extractSceneBreaks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartOutlineSynthesisTest {

    @Test
    fun `buildSmartOutline extracts printed table of contents with page numbers`() {
        val chunks = listOf(
            "TABLE OF CONTENTS\nChapter 1 . . . . . 1\nChapter 2 . . . . . 5\nChapter 3 . . . . . 10",
            "This is the start of Chapter 1 on page 1.",
            "Chapter 2 begins here on page 5.",
            "Chapter 3 is here on page 10."
        )
        val textModel = ReaderTextIndex.build(chunks.joinToString("\n\n"), storedPageCount = 10)
        val outline = buildSmartOutline(chunks, textModel)

        assertTrue("Should extract TOC entries", outline.isNotEmpty())
        assertEquals("Chapter 1", outline[0].title)
        assertEquals(1, outline[0].pageNumber)
    }

    @Test
    fun `buildSmartOutline extracts explicit headings with 1-to-1 page numbers`() {
        val chunks = listOf(
            "CHAPTER I\nMr. Sherlock Holmes sat at the table.",
            "He inspected the letter closely.",
            "CHAPTER II\nThe Science of Deduction."
        )
        val textModel = ReaderTextIndex.build(chunks.joinToString("\n\n"), storedPageCount = 2)
        val outline = buildSmartOutline(chunks, textModel)

        assertTrue("Should extract chapter headings", outline.size >= 2)
        assertTrue("Contains Chapter I", outline.any { it.title.contains("CHAPTER I") })
        assertTrue("Contains Chapter II", outline.any { it.title.contains("CHAPTER II") })
    }

    @Test
    fun `extractSceneBreaks detects asterisks and dashes as scene breaks`() {
        val chunks = listOf(
            "They talked late into the night.",
            "* * *",
            "The morning sun broke over Baker Street.",
            "---",
            "Three days later, news arrived."
        )
        val textModel = ReaderTextIndex.build(chunks.joinToString("\n\n"), storedPageCount = 3)
        val breaks = extractSceneBreaks(chunks, textModel)

        assertEquals(2, breaks.size)
        assertEquals("§ Scene Break", breaks[0].title)
        assertEquals("§ Scene Break", breaks[1].title)
    }

    @Test
    fun `extractPageMilestones generates clean page milestones for uninterrupted documents`() {
        val rawText = (1..30).joinToString("\n\n") { page ->
            "[[VERITAS_PAGE:$page]]\nThis is paragraph content on page $page of the treatise."
        }
        val textModel = ReaderTextIndex.build(rawText, storedPageCount = 30)
        val chunks = textModel.sentences.map { it.text }
        val milestones = extractPageMilestones(chunks, textModel)

        assertTrue("Should generate milestones", milestones.isNotEmpty())
        assertEquals("Page 1", milestones.first().title)
        assertEquals(1, milestones.first().pageNumber)
        assertTrue("Should include Page 30", milestones.any { it.title == "Page 30" })
    }

    @Test
    fun `buildSmartOutline extracts unnumbered TOC page and maps to body pages`() {
        val rawText = buildString {
            append("[[VERITAS_PAGE:4]]\n")
            append("Contents\n")
            append("Introduction\n")
            append("Part 1: Two Systems\n")
            append("Part 2: Heuristics and Biases\n")
            append("Conclusions\n\n")

            append("[[VERITAS_PAGE:10]]\n")
            append("Introduction\n")
            append("Every author, I suppose, has in mind a setting in which readers will discuss their work.\n\n")

            append("[[VERITAS_PAGE:35]]\n")
            append("Part 1: Two Systems\n")
            append("To observe your mind in automatic mode, glance at the image below.\n\n")

            append("[[VERITAS_PAGE:80]]\n")
            append("Part 2: Heuristics and Biases\n")
            append("A major puzzle in human judgment is statistical intuition.\n\n")

            append("[[VERITAS_PAGE:120]]\n")
            append("Conclusions\n")
            append("We began this journey by discussing two modes of thinking.\n")
        }

        val textModel = ReaderTextIndex.build(rawText, storedPageCount = 120)
        val chunks = textModel.sentences.map { it.text }
        val outline = buildSmartOutline(chunks, textModel)

        assertTrue("Should extract unnumbered TOC entries", outline.isNotEmpty())
        assertTrue("Should contain Introduction", outline.any { it.title.contains("Introduction") })
        assertTrue("Should contain Part 1", outline.any { it.title.contains("Part 1") })
        assertTrue("Should contain Part 2", outline.any { it.title.contains("Part 2") })
        assertTrue("Should contain Conclusions", outline.any { it.title.contains("Conclusions") })
        org.junit.Assert.assertFalse("Outline should NOT contain self-referential Contents header", outline.any { it.title.trim().equals("Contents", ignoreCase = true) })

        val part1 = outline.firstOrNull { it.title.contains("Part 1") }
        assertEquals(35, part1?.pageNumber)
    }

    @Test
    fun `buildSmartOutline strips markdown hashes and computes hierarchy levels`() {
        val chunks = listOf(
            "# 1. Introduction\nBackground of the research.",
            "The rapid growth in infant feeding demands reliable storage.",
            "## 1.1 Problem Statement\nTraditional cooling methods lack portable temperature controls.",
            "### 1.2.1 General Objectives\nDesign and construct an efficient cooling chamber.",
            "### 1.2.2 Specific Objectives\nEvaluate thermal insulation performance."
        )

        val textModel = ReaderTextIndex.build(chunks.joinToString("\n\n"), storedPageCount = 2)
        val outline = buildSmartOutline(chunks, textModel)

        assertEquals(4, outline.size)

        // Verify # symbols are stripped
        org.junit.Assert.assertFalse("Outline title should not contain #", outline[0].title.contains("#"))
        org.junit.Assert.assertFalse("Outline title should not contain #", outline[1].title.contains("#"))
        org.junit.Assert.assertFalse("Outline title should not contain #", outline[2].title.contains("#"))
        org.junit.Assert.assertFalse("Outline title should not contain #", outline[3].title.contains("#"))

        assertEquals("1. Introduction", outline[0].title)
        assertEquals(0, outline[0].level) // H1 -> level 0

        assertEquals("1.1 Problem Statement", outline[1].title)
        assertEquals(1, outline[1].level) // H2 -> level 1

        assertEquals("1.2.1 General Objectives", outline[2].title)
        assertEquals(2, outline[2].level) // H3 -> level 2

        assertEquals("1.2.2 Specific Objectives", outline[3].title)
        assertEquals(2, outline[3].level) // H3 -> level 2
    }

    @Test
    fun `isSelfReferentialTocHeading correctly filters out contents and table of contents titles`() {
        assertTrue(com.veritas.reader.ui.screens.isSelfReferentialTocHeading("Contents"))
        assertTrue(com.veritas.reader.ui.screens.isSelfReferentialTocHeading("Table of Contents"))
        assertTrue(com.veritas.reader.ui.screens.isSelfReferentialTocHeading("Brief Contents"))
        assertTrue(com.veritas.reader.ui.screens.isSelfReferentialTocHeading("Summary of Contents"))
        assertTrue(com.veritas.reader.ui.screens.isSelfReferentialTocHeading("Index of Chapters"))
        assertTrue(com.veritas.reader.ui.screens.isSelfReferentialTocHeading("TOC"))

        org.junit.Assert.assertFalse(com.veritas.reader.ui.screens.isSelfReferentialTocHeading("Introduction"))
        org.junit.Assert.assertFalse(com.veritas.reader.ui.screens.isSelfReferentialTocHeading("Part 1: Two Systems"))
        org.junit.Assert.assertFalse(com.veritas.reader.ui.screens.isSelfReferentialTocHeading("Chapter 1"))
    }

    @Test
    fun `buildSmartOutline accepts purely numbered chapters in sequence and formats as Chapter N`() {
        val chunks = listOf(
            "# 1\nThis is the content of chapter one.",
            "More story in chapter one.",
            "# 2\nThis is the content of chapter two.",
            "More story in chapter two.",
            "# 3\nThis is the content of chapter three."
        )
        val textModel = ReaderTextIndex.build(chunks.joinToString("\n\n"), storedPageCount = 3)
        val outline = buildSmartOutline(chunks, textModel)

        assertEquals(3, outline.size)
        assertEquals("Chapter 1", outline[0].title)
        assertEquals("Chapter 2", outline[1].title)
        assertEquals("Chapter 3", outline[2].title)
    }

    @Test
    fun `filterAndFormatNumberedOutlineEntries rejects solitary orphan numeral`() {
        val entries = listOf(
            com.veritas.reader.ui.screens.SmartOutlineEntry(0, "FACEBOOK NAME & LINK", "preview", true),
            com.veritas.reader.ui.screens.SmartOutlineEntry(1, "2", "preview", true),
            com.veritas.reader.ui.screens.SmartOutlineEntry(2, "TEAM SPECIFICS", "preview", true)
        )
        val filtered = com.veritas.reader.ui.screens.filterAndFormatNumberedOutlineEntries(entries)

        assertEquals(2, filtered.size)
        assertEquals("FACEBOOK NAME & LINK", filtered[0].title)
        assertEquals("TEAM SPECIFICS", filtered[1].title)
        org.junit.Assert.assertFalse("Solitary orphan 2 should be rejected", filtered.any { it.title == "2" || it.title == "Chapter 2" })
    }

    @Test
    fun `resolveHumanDocumentTitle falls back to clean name when title is UUID or cache`() {
        val uuidTitle = "9b876fc1-c864-4de7-91e8-6e5414f55397"
        val resolved = com.veritas.reader.ui.screens.resolveHumanDocumentTitle(uuidTitle, "PDF")
        assertEquals("Document outline", resolved)

        val customSource = com.veritas.reader.ui.screens.resolveHumanDocumentTitle(uuidTitle, "Sherlock Holmes")
        assertEquals("Sherlock Holmes", customSource)

        val normalTitle = com.veritas.reader.ui.screens.resolveHumanDocumentTitle("Thinking, Fast and Slow", "PDF")
        assertEquals("Thinking, Fast and Slow", normalTitle)
    }

    @Test
    fun `buildSmartOutline resolves numbered TOC chapters to body pages without duplicating TOC page entries`() {
        val rawText = buildString {
            append("[[VERITAS_PAGE:4]]\n")
            append("Contents\n\n")
            append("PART I. TWO SYSTEMS\n\n")
            append("1. The Characters of the Story\n")
            append("2. Attention and Effort\n")
            append("3. The Lazy Controller\n\n")
            append("PART II. HEURISTICS AND BIASES\n")
            append("10. The Law of Small Numbers\n\n")

            append("[[VERITAS_PAGE:19]]\n")
            append("# PART I. TWO SYSTEMS\n")
            append("This section introduces the two modes of thinking.\n\n")

            append("[[VERITAS_PAGE:20]]\n")
            append("# 1. The Characters of the Story\n")
            append("To observe your mind in automatic mode, glance at the image below.\n\n")

            append("[[VERITAS_PAGE:35]]\n")
            append("# 2. Attention and Effort\n")
            append("In the unlikely event of this book being made into a film, System 2 would be a supporting character.\n\n")

            append("[[VERITAS_PAGE:50]]\n")
            append("# 3. The Lazy Controller\n")
            append("I spend several minutes each day in a state of quiet contemplation.\n\n")

            append("[[VERITAS_PAGE:100]]\n")
            append("# PART II. HEURISTICS AND BIASES\n")
            append("A study of the predictive accuracy of algorithms.\n\n")

            append("[[VERITAS_PAGE:105]]\n")
            append("# 10. The Law of Small Numbers\n")
            append("A study of the incidence of kidney cancer in the 3,141 counties of the United States reveals a remarkable pattern.\n")
        }

        val textModel = ReaderTextIndex.build(rawText, storedPageCount = 105)
        val chunks = textModel.sentences.map { it.text }
        val outline = buildSmartOutline(chunks, textModel)

        assertTrue("Should extract outline entries", outline.isNotEmpty())

        // Verify that NO outline entry points to the Table of Contents page (Page 4)
        org.junit.Assert.assertFalse(
            "Smart outline should NOT contain any entries pointing to the TOC page 4",
            outline.any { it.pageNumber == 4 }
        )

        // Verify that entries point to their actual body pages
        val part1 = outline.firstOrNull { it.title.contains("PART I", ignoreCase = true) }
        org.junit.Assert.assertNotNull("Should contain PART I", part1)
        assertEquals(19, part1?.pageNumber)

        val ch1 = outline.firstOrNull { it.title.contains("Characters of the Story", ignoreCase = true) }
        org.junit.Assert.assertNotNull("Should contain Chapter 1", ch1)
        assertEquals(20, ch1?.pageNumber)

        val ch2 = outline.firstOrNull { it.title.contains("Attention and Effort", ignoreCase = true) }
        org.junit.Assert.assertNotNull("Should contain Chapter 2", ch2)
        assertEquals(35, ch2?.pageNumber)

        val ch3 = outline.firstOrNull { it.title.contains("The Lazy Controller", ignoreCase = true) }
        org.junit.Assert.assertNotNull("Should contain Chapter 3", ch3)
        assertEquals(50, ch3?.pageNumber)

        val ch10 = outline.firstOrNull { it.title.contains("Law of Small Numbers", ignoreCase = true) }
        org.junit.Assert.assertNotNull("Should contain Chapter 10", ch10)
        assertEquals(105, ch10?.pageNumber)

        // Verify that entries are not duplicated
        val titles = outline.map { it.title }
        assertEquals("No duplicate titles in outline", titles.distinct().size, titles.size)
    }
}


package com.veritas.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PptxExtractorTest {

    private fun buildPptx(entries: Map<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            (entries + ("ppt/presentation.xml" to "<p:presentation/>")).forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun slideXml(body: String): String =
        """<?xml version="1.0"?><p:sld xmlns:a="a" xmlns:p="p"><p:cSld><p:spTree>$body</p:spTree></p:cSld></p:sld>"""

    private fun titleShape(vararg lines: String): String =
        """<p:sp><p:nvSpPr><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr><p:txBody>""" +
            lines.joinToString("") { "<a:p><a:r><a:t>$it</a:t></a:r></a:p>" } +
            "</p:txBody></p:sp>"

    private fun bodyShape(vararg paragraphs: Pair<Int, String>): String =
        """<p:sp><p:nvSpPr><p:nvPr><p:ph type="body" idx="1"/></p:nvPr></p:nvSpPr><p:txBody>""" +
            paragraphs.joinToString("") { (level, text) ->
                """<a:p><a:pPr lvl="$level"/><a:r><a:t>$text</a:t></a:r></a:p>"""
            } +
            "</p:txBody></p:sp>"

    private fun plainTextBox(text: String): String =
        """<p:sp><p:nvSpPr><p:nvPr/></p:nvSpPr><p:txBody><a:p><a:r><a:t>$text</a:t></a:r></a:p></p:txBody></p:sp>"""

    // ── Structure ────────────────────────────────────────────────────────────

    @Test
    fun `title first then bullets with level prefixes`() {
        val bytes = buildPptx(
            mapOf(
                "ppt/slides/slide1.xml" to slideXml(
                    bodyShape(0 to "First point", 1 to "Sub point") + titleShape("Big Title")
                )
            )
        )
        val deck = PptxExtractor.parseDeck(bytes, includeSpeakerNotes = true)
        val text = PptxExtractor.renderDeckText(deck)

        val lines = text.lines()
        assertEquals(ReaderTextIndex.pageMarker(1), lines[0])
        assertEquals("Big Title", lines[1])
        assertEquals("", lines[2]) // blank line after title
        assertEquals("• First point", lines[3])
        assertEquals("  ◦ Sub point", lines[4])
    }

    @Test
    fun `each slide gets its own page marker and slide numbers survive empty slides`() {
        val bytes = buildPptx(
            mapOf(
                "ppt/slides/slide1.xml" to slideXml(titleShape("One")),
                "ppt/slides/slide2.xml" to slideXml(""), // image-only slide: no text
                "ppt/slides/slide3.xml" to slideXml(titleShape("Three"))
            )
        )
        val deck = PptxExtractor.parseDeck(bytes, includeSpeakerNotes = true)
        assertEquals(3, deck.slideCount)
        val text = PptxExtractor.renderDeckText(deck)
        assertTrue(text.contains(ReaderTextIndex.pageMarker(1)))
        assertFalse(text.contains(ReaderTextIndex.pageMarker(2))) // blank slide skipped
        assertTrue(text.contains(ReaderTextIndex.pageMarker(3))) // numbering stays true

        // The reader model maps sentences to their real slide numbers.
        val model = ReaderTextIndex.build(text, deck.slideCount)
        assertEquals(1, model.sentences.first { it.text == "One" }.pageNumber)
        assertEquals(3, model.sentences.first { it.text == "Three" }.pageNumber)
    }

    @Test
    fun `plain text boxes get no bullet prefix`() {
        val bytes = buildPptx(
            mapOf("ppt/slides/slide1.xml" to slideXml(plainTextBox("A caption line")))
        )
        val text = PptxExtractor.renderDeckText(PptxExtractor.parseDeck(bytes, true))
        assertTrue(text.contains("A caption line"))
        assertFalse(text.contains("• A caption line"))
    }

    @Test
    fun `tables read row by row with pipe-joined cells`() {
        val table = """<p:graphicFrame><a:tbl><a:tr>""" +
            """<a:tc><a:txBody><a:p><a:r><a:t>Region</a:t></a:r></a:p></a:txBody></a:tc>""" +
            """<a:tc><a:txBody><a:p><a:r><a:t>Growth</a:t></a:r></a:p></a:txBody></a:tc>""" +
            """</a:tr></a:tbl></p:graphicFrame>"""
        val bytes = buildPptx(mapOf("ppt/slides/slide1.xml" to slideXml(table)))
        val text = PptxExtractor.renderDeckText(PptxExtractor.parseDeck(bytes, true))
        assertTrue(text.contains("| Region | Growth |"))
    }

    @Test
    fun `xml entities are decoded`() {
        val bytes = buildPptx(
            mapOf("ppt/slides/slide1.xml" to slideXml(titleShape("Q&amp;A &lt;live&gt;")))
        )
        val text = PptxExtractor.renderDeckText(PptxExtractor.parseDeck(bytes, true))
        assertTrue(text.contains("Q&A <live>"))
    }

    // ── Speaker notes ────────────────────────────────────────────────────────

    @Test
    fun `speaker notes included with prefix and excluded when off`() {
        val entries = mapOf(
            "ppt/slides/slide1.xml" to slideXml(titleShape("Topic")),
            "ppt/notesSlides/notesSlide1.xml" to slideXml(
                bodyShape(0 to "Remember to mention the demo.")
            )
        )
        val withNotes = PptxExtractor.renderDeckText(
            PptxExtractor.parseDeck(buildPptx(entries), includeSpeakerNotes = true)
        )
        assertTrue(withNotes.contains("Notes: Remember to mention the demo."))

        val withoutNotes = PptxExtractor.renderDeckText(
            PptxExtractor.parseDeck(buildPptx(entries), includeSpeakerNotes = false)
        )
        assertFalse(withoutNotes.contains("Remember to mention"))
    }

    // ── Auto-punctuate ───────────────────────────────────────────────────────

    @Test
    fun `autoPunctuate appends periods only to unpunctuated lines`() {
        assertEquals("Faster onboarding.", PptxExtractor.autoPunctuateLine("Faster onboarding"))
        assertEquals("Really?", PptxExtractor.autoPunctuateLine("Really?"))
        assertEquals("We deliver:", PptxExtractor.autoPunctuateLine("We deliver:"))
        assertEquals("Up 12%.", PptxExtractor.autoPunctuateLine("Up 12%"))
        assertEquals("", PptxExtractor.autoPunctuateLine(""))
    }

    @Test
    fun `renderDeckText leaves text untouched when autoPunctuate off`() {
        val bytes = buildPptx(
            mapOf("ppt/slides/slide1.xml" to slideXml(bodyShape(0 to "no punctuation here")))
        )
        val deck = PptxExtractor.parseDeck(bytes, true)
        assertTrue(PptxExtractor.renderDeckText(deck, autoPunctuate = false).contains("no punctuation here\n").not() &&
            PptxExtractor.renderDeckText(deck, autoPunctuate = false).contains("• no punctuation here"))
        assertTrue(PptxExtractor.renderDeckText(deck, autoPunctuate = true).contains("• no punctuation here."))
    }

    // ── Media mapping ────────────────────────────────────────────────────────

    @Test
    fun `slide images resolve through rels to media paths`() {
        val bytes = buildPptx(
            mapOf(
                "ppt/slides/slide1.xml" to slideXml(
                    titleShape("Pics") + """<p:pic><a:blip r:embed="rId2"/></p:pic>"""
                ),
                "ppt/slides/_rels/slide1.xml.rels" to
                    """<Relationships><Relationship Id="rId2" Type="image" Target="../media/image7.png"/></Relationships>"""
            )
        )
        val deck = PptxExtractor.parseDeck(bytes, true)
        assertEquals(listOf("ppt/media/image7.png"), deck.slides.first().mediaPaths)
    }

    @Test
    fun `ocr lines land on their slide after content`() {
        val bytes = buildPptx(mapOf("ppt/slides/slide1.xml" to slideXml(titleShape("Shot"))))
        val deck = PptxExtractor.parseDeck(bytes, true)
        val text = PptxExtractor.renderDeckText(deck, ocrLinesBySlide = mapOf(1 to listOf("Text inside a screenshot")))
        assertTrue(text.contains("Text inside a screenshot"))
        assertTrue(text.indexOf("Shot") < text.indexOf("Text inside a screenshot"))
    }
}

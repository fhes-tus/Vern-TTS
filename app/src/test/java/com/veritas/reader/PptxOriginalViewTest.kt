package com.veritas.reader

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PptxOriginalViewTest {
    private val presentation = """<p:presentation><p:sldIdLst><p:sldId r:id="second"/><p:sldId r:id="first"/></p:sldIdLst><p:sldSz cx="1000000" cy="500000"/></p:presentation>"""
    private val shape = """<p:sp><p:spPr><a:xfrm><a:off x="100000" y="50000"/><a:ext cx="800000" cy="200000"/></a:xfrm><a:prstGeom prst="rect"/></p:spPr><p:txBody><a:p><a:r><a:rPr sz="2400" b="1"/><a:t>Positioned title</a:t></a:r></a:p></p:txBody></p:sp>"""
    private fun slide(body: String) = """<p:sld xmlns:p="p" xmlns:a="a" xmlns:r="r"><p:cSld><p:spTree>$body</p:spTree></p:cSld></p:sld>"""

    @Test fun coordinatesAndTypographyUseActualSlideSize() {
        val layout = PptxSlideLayoutParser.parse(slide(shape), "", presentation)!!
        assertEquals(2f, layout.aspectRatio, .001f)
        val element = layout.elements.single()
        assertEquals(.1f, element.x, .001f); assertEquals(.1f, element.y, .001f)
        assertEquals(.8f, element.width, .001f); assertEquals(.4f, element.height, .001f)
        assertEquals("Positioned title", element.text); assertTrue(element.bold)
        assertEquals(24f * 12700 / 1000000, element.fontWidthFraction, .001f)
    }

    @Test fun unsupportedObjectsUseReadableFallbackInsteadOfMisleadingRectangles() {
        assertNull(PptxSlideLayoutParser.parse(slide(shape.replace("prst=\"rect\"", "prst=\"ellipse\"")), "", presentation))
        assertNull(PptxSlideLayoutParser.parse(slide(shape + "<p:graphicFrame/>"), "", presentation))
        assertNull(PptxSlideLayoutParser.parse(slide(shape.replace("<a:xfrm>", "<a:srcRect l=\"200\"/><a:xfrm>")), "", presentation))
    }

    @Test fun inheritedThemeColorsAndUnknownDarkSlideTextKeepReadableFallback() {
        val themed = shape.replace("<a:prstGeom", "<a:solidFill><a:schemeClr val=\"accent1\"/></a:solidFill><a:prstGeom")
        assertNull(PptxSlideLayoutParser.parse(slide(themed), "", presentation))
        val dark = slide(shape).replace("<p:spTree>", "<p:bg><p:bgPr><a:solidFill><a:srgbClr val=\"000000\"/></a:solidFill></p:bgPr></p:bg><p:spTree>")
        assertEquals(0xffffffff, PptxSlideLayoutParser.parse(dark, "", presentation)!!.elements.single().color)
        val explicitWhite = dark.replace("<a:rPr sz=\"2400\" b=\"1\"/>", "<a:rPr sz=\"2400\" b=\"1\"><a:solidFill><a:srgbClr val=\"FFFFFF\"/></a:solidFill></a:rPr>")
        assertEquals(0xffffffff, PptxSlideLayoutParser.parse(explicitWhite, "", presentation)!!.elements.single().color)
        assertEquals(0xff202020, PptxSlideLayoutParser.parse(explicitWhite.replace("val=\"000000\"", "val=\"FFFFFF\""), "", presentation)!!.elements.single().color)
        val imageBackground = slide(shape).replace("<p:spTree>", "<p:bg><a:blip r:embed=\"background\"/></p:bg><p:spTree>")
        assertNull(PptxSlideLayoutParser.parse(imageBackground, "", presentation))
    }

    @Test fun lineFillDoesNotPaintTheShapeAndSourceTextInsetsSurvive() {
        val bordered = shape.replace("<a:prstGeom", "<a:ln w=\"12700\"><a:solidFill><a:srgbClr val=\"FF0000\"/></a:solidFill></a:ln><a:prstGeom")
            .replace("<p:txBody>", "<p:txBody><a:bodyPr lIns=\"0\" rIns=\"0\" tIns=\"0\" bIns=\"0\" anchor=\"ctr\"/>")
        val element = PptxSlideLayoutParser.parse(slide(bordered), "", presentation)!!.elements.single()
        assertNull(element.fill)
        assertEquals(0xffff0000, element.stroke)
        assertEquals(0f, element.insetLeft, 0f)
        assertEquals("ctr", element.verticalAnchor)
    }

    @Test fun sourceRelationshipsControlSlideOrderNotesAndMediaWithAnyAttributeOrder() {
        val file = File.createTempFile("slides", ".pptx")
        val entries = mapOf(
            "ppt/presentation.xml" to presentation,
            "ppt/_rels/presentation.xml.rels" to """<Relationships><Relationship Target="slides/slide7.xml" Id="first"/><Relationship Target="slides/slide3.xml" Id="second"/></Relationships>""",
            "ppt/slides/slide3.xml" to slide(shape + """<p:pic><a:blip r:embed="photo"/></p:pic>"""),
            "ppt/slides/slide7.xml" to slide(shape.replace("Positioned title", "Second slide")),
            "ppt/slides/_rels/slide3.xml.rels" to """<Relationships><Relationship Target="../notesSlides/notesSlide9.xml" Id="notes"/><Relationship Target="../media/image4.png" Id="photo"/></Relationships>""",
            "ppt/notesSlides/notesSlide9.xml" to slide("""<p:sp><p:nvSpPr><p:nvPr><p:ph type="body"/></p:nvPr></p:nvSpPr><p:txBody><a:p><a:r><a:t>Correct speaker note.</a:t></a:r></a:p></p:txBody></p:sp>""")
        )
        try {
            ZipOutputStream(file.outputStream()).use { zip -> entries.forEach { (path, value) -> zip.putNextEntry(ZipEntry(path)); zip.write(value.toByteArray()); zip.closeEntry() } }
            val deck = PptxExtractor.parseDeck(file, true)
            assertEquals(listOf(1, 2), deck.slides.map { it.number })
            assertTrue(deck.slides[0].notesLines.contains("Correct speaker note."))
            assertEquals(listOf("ppt/media/image4.png"), deck.slides[0].mediaPaths)
            assertTrue(deck.slides[1].contentLines.contains("Second slide"))
            assertEquals(deck, PptxExtractor.parseDeck(file.readBytes(), true))
        } finally { file.delete() }
    }
}

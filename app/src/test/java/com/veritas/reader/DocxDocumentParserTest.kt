package com.veritas.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocxDocumentParserTest {

    @Test
    fun parse_extractsHeadingsParagraphsAndTables_fromDocumentXml() {
        val documentXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:p>
                  <w:pPr><w:pStyle w:val="Heading1"/></w:pPr>
                  <w:r><w:t>Project Architecture</w:t></w:r>
                </w:p>
                <w:p>
                  <w:r><w:t>Veritas Reader is built using modern Jetpack Compose and Material 3.</w:t></w:r>
                </w:p>
                <w:tbl>
                  <w:tr>
                    <w:tc><w:p><w:r><w:t>Module</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>Role</w:t></w:r></w:p></w:tc>
                  </w:tr>
                  <w:tr>
                    <w:tc><w:p><w:r><w:t>ActualDocView</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>Native Visual Canvas</w:t></w:r></w:p></w:tc>
                  </w:tr>
                </w:tbl>
              </w:body>
            </w:document>
        """.trimIndent()

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(documentXml.toByteArray())
            zip.closeEntry()
        }

        val doc = DocxDocumentParser.parse(baos.toByteArray(), "Architecture Spec")

        assertEquals("Architecture Spec", doc.title)
        assertTrue(doc.totalPages >= 1)
        val firstPage = doc.pages[0]
        val headings = firstPage.blocks.filterIsInstance<DocxBlock.Heading>()
        val paragraphs = firstPage.blocks.filterIsInstance<DocxBlock.Paragraph>()
        val tables = firstPage.blocks.filterIsInstance<DocxBlock.Table>()

        assertEquals(1, headings.size)
        assertEquals("Project Architecture", headings[0].text)
        assertTrue(paragraphs.any { it.text.contains("Veritas Reader is built") })
        assertEquals(1, tables.size)
        assertEquals(2, tables[0].rows.size)
    }

    @Test
    fun parse_handlesEmptyBytes() {
        val doc = DocxDocumentParser.parse(ByteArray(0), "Empty")
        assertNotNull(doc)
        assertEquals("Empty", doc.title)
    }

    @Test
    fun parse_skipsMediaWhenIncludeImagesIsFalse() {
        val documentXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <w:body>
                <w:p>
                  <w:r><w:t>Image Caption</w:t></w:r>
                  <w:drawing>
                    <w:inline>
                      <a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                        <a:graphicData>
                          <pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
                            <pic:blipFill>
                              <a:blip r:embed="rId1"/>
                            </pic:blipFill>
                          </pic:pic>
                        </a:graphicData>
                      </a:graphic>
                    </w:inline>
                  </w:drawing>
                </w:p>
              </w:body>
            </w:document>
        """.trimIndent()

        val relsXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image1.png"/>
            </Relationships>
        """.trimIndent()

        val fakeImage = ByteArray(2048) { 0x55 }

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(documentXml.toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
            zip.write(relsXml.toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("word/media/image1.png"))
            zip.write(fakeImage)
            zip.closeEntry()
        }

        val docWithoutImages = DocxDocumentParser.parse(baos.toByteArray(), "Doc", includeImages = false)
        val imagesWithout = docWithoutImages.pages.flatMap { it.blocks }.filterIsInstance<DocxBlock.Image>()
        assertTrue(imagesWithout.isEmpty())

        val docWithImages = DocxDocumentParser.parse(baos.toByteArray(), "Doc", includeImages = true)
        val imagesWith = docWithImages.pages.flatMap { it.blocks }.filterIsInstance<DocxBlock.Image>()
        assertEquals(1, imagesWith.size)
        assertEquals(2048, imagesWith[0].imageBytes.size)
    }

    @Test
    fun parse_handlesSpacedHeadingStylesAndTabsInTOC() {
        val documentXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:p>
                  <w:pPr><w:pStyle w:val="Heading 1"/></w:pPr>
                  <w:r><w:t>Chapter One</w:t></w:r>
                </w:p>
                <w:p>
                  <w:pPr><w:pStyle w:val="TOC 1"/></w:pPr>
                  <w:r><w:t>2.1.2 The Immune System</w:t><w:tab/><w:t>11</w:t></w:r>
                </w:p>
              </w:body>
            </w:document>
        """.trimIndent()

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(documentXml.toByteArray())
            zip.closeEntry()
        }

        val doc = DocxDocumentParser.parse(baos.toByteArray(), "Thesis")
        val blocks = doc.pages.flatMap { it.blocks }
        val heading = blocks.filterIsInstance<DocxBlock.Heading>().firstOrNull()
        val paragraph = blocks.filterIsInstance<DocxBlock.Paragraph>().firstOrNull()

        assertNotNull("Should recognize Heading 1 with space", heading)
        assertEquals("Chapter One", heading?.text)
        assertEquals(1, heading?.level)

        assertNotNull("Should format TOC entry with dot leaders", paragraph)
        assertTrue("TOC should separate title from page without fusing", paragraph?.text?.contains("2.1.2 The Immune System ...... 11") == true)
    }

    @Test
    fun parse_splitsRunInSubheadingsFromParagraphBody() {
        val documentXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:p>
                  <w:r>
                    <w:rPr><w:b/></w:rPr>
                    <w:t>1.2.1 General Objectives</w:t>
                  </w:r>
                  <w:r>
                    <w:t> The primary objective of this study is to analyze milk storage.</w:t>
                  </w:r>
                </w:p>
              </w:body>
            </w:document>
        """.trimIndent()

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(documentXml.toByteArray())
            zip.closeEntry()
        }

        val doc = DocxDocumentParser.parse(baos.toByteArray(), "Thesis")
        val blocks = doc.pages.flatMap { it.blocks }
        val heading = blocks.filterIsInstance<DocxBlock.Heading>().firstOrNull()
        val paragraph = blocks.filterIsInstance<DocxBlock.Paragraph>().firstOrNull()

        assertNotNull("Should extract run-in subheading as DocxBlock.Heading", heading)
        assertEquals("1.2.1 General Objectives", heading?.text)
        assertEquals(3, heading?.level) // 1.2.1 has 2 dots -> level 3

        assertNotNull("Should extract body text as DocxBlock.Paragraph", paragraph)
        assertEquals("The primary objective of this study is to analyze milk storage.", paragraph?.text)
    }
}

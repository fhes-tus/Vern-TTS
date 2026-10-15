package com.veritas.reader

import org.junit.Assert.*
import org.junit.Test

class DocxSourceFormattingTest {
    private fun paragraph(xml: String) = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        .newDocumentBuilder().parse(java.io.ByteArrayInputStream(xml.toByteArray())).documentElement

    @Test fun inheritedStylesKeepEmphasisSpacingAndExplicitRunOverrides() {
        val styles = DocxStyleResolver("""<w:styles xmlns:w="w">
            <w:docDefaults><w:rPrDefault><w:rPr><w:sz w:val="22"/></w:rPr></w:rPrDefault></w:docDefaults>
            <w:style w:styleId="Base" w:type="paragraph"><w:rPr><w:b/><w:color w:val="124578"/></w:rPr>
                <w:pPr><w:spacing w:before="240" w:after="120"/></w:pPr></w:style>
            <w:style w:styleId="Derived" w:type="paragraph"><w:basedOn w:val="Base"/>
                <w:pPr><w:jc w:val="center"/></w:pPr></w:style></w:styles>""")
        val result = docxSourceFormat(paragraph("""<w:p xmlns:w="w"><w:pPr><w:pStyle w:val="Derived"/>
            <w:spacing w:after="200"/></w:pPr><w:r><w:t>Bold</w:t></w:r>
            <w:r><w:rPr><w:b w:val="0"/><w:i/></w:rPr><w:t> italic</w:t><w:tab/><w:t>end</w:t></w:r></w:p>"""), styles)
        assertEquals("center", result.alignment)
        assertEquals(12f, result.beforePoints, 0f)
        assertEquals(10f, result.afterPoints, 0f)
        assertTrue(result.runs.first().bold)
        assertFalse(result.runs.last().bold)
        assertTrue(result.runs.last().italic)
        assertEquals(11f, result.runs.first().sizePoints)
        assertEquals(0xff124578, result.runs.first().color)
        assertEquals(" italic\tend", result.runs.last().text)
    }

    @Test fun deletedRunsAndInvalidColorsDoNotLeakIntoOriginalView() {
        val result = docxSourceFormat(paragraph("""<w:p xmlns:w="w"><w:del><w:r><w:t>Deleted</w:t></w:r></w:del>
            <w:r><w:rPr><w:color w:val="auto"/></w:rPr><w:t>Visible</w:t></w:r></w:p>"""))
        assertEquals(listOf("Visible"), result.runs.map { it.text })
        assertNull(result.runs.single().color)
    }
}

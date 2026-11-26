package com.veritas.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class TextImportDecoderTest {
    @Test
    fun declaredModeFallsBackToAutomaticDecodingWhenHtmlHasNoCharset() {
        val html = "<p>Café</p>"
        val options = TextImportOptions(TextImportEncodingCatalog.DECLARED_HTML_ID)
        val result = TextImportDecoder.decode(html.toByteArray(Charset.forName("windows-1252")), options, isHtml = true)
        assertEquals(html, result.text)
        assertTrue(result.diagnostics.single().contains("windows-1252"))
    }

    @Test
    fun autoDetectUsesUtf16LittleEndianBom() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "Hello".toByteArray(Charsets.UTF_16LE)

        val result = TextImportDecoder.decode(bytes, TextImportOptions())

        assertEquals("Hello", result.text)
        assertTrue(result.diagnostics.single().contains("UTF-16LE"))
    }

    @Test
    fun declaredHtmlEncodingUsesDocumentCharset() {
        val bytes = """<meta charset="windows-1252"><p>Café</p>"""
            .toByteArray(Charset.forName("windows-1252"))

        val result = TextImportDecoder.decode(
            bytes,
            TextImportOptions(encodingId = TextImportEncodingCatalog.DECLARED_HTML_ID),
            isHtml = true
        )

        assertEquals("""<meta charset="windows-1252"><p>Café</p>""", result.text)
        assertTrue(result.diagnostics.single().contains("windows-1252"))
    }

    @Test
    fun explicitLegacyEncodingDecodesSelectedCharset() {
        val bytes = "Café".toByteArray(Charset.forName("windows-1252"))

        val result = TextImportDecoder.decode(
            bytes,
            TextImportOptions(encodingId = "windows_1252")
        )

        assertEquals("Café", result.text)
        assertTrue(result.diagnostics.single().contains("windows-1252"))
    }

    @Test
    fun plainTextKeepsUtf8EvenWithALiteralCharsetDeclaration() {
        val plain = """A charset=windows-1252 example, <meta charset="windows-1252">, and café."""

        val auto = TextImportDecoder.decode(plain.toByteArray(), TextImportOptions())
        val declared = TextImportDecoder.decode(plain.toByteArray(),
            TextImportOptions(TextImportEncodingCatalog.DECLARED_HTML_ID))

        assertEquals(plain, auto.text)
        assertEquals(plain, declared.text)
        assertTrue(auto.diagnostics.single().contains("UTF-8"))
        assertTrue(declared.diagnostics.single().contains("only to HTML imports"))
    }

    @Test
    fun htmlAutoDetectionUsesTheContentTypeMetaTagAndIgnoresExamples() {
        val html = """
            <!doctype html><head>
            <!-- <meta charset="UTF-16LE"> -->
            <script>let example = '<!-- <meta charset="UTF-16LE">';</script>
            <meta content="text/html; charset=windows-1251" http-equiv="Content-Type">
            </head><body>Привет</body>
        """.trimIndent()
        val result = TextImportDecoder.decode(html.toByteArray(Charset.forName("windows-1251")),
            TextImportOptions(), isHtml = true)

        assertEquals(html, result.text)
        assertTrue(result.diagnostics.single().contains("declared HTML charset: windows-1251"))
    }

    @Test
    fun htmlTextAndBodyExamplesDoNotActAsCharsetMetadata() {
        val html = """<html><head><title>charset=windows-1252</title></head><body>café<meta charset="windows-1252"></body></html>"""
        val result = TextImportDecoder.decode(html.toByteArray(), TextImportOptions(), isHtml = true)

        assertEquals(html, result.text)
        assertTrue(result.diagnostics.single().contains("UTF-8"))
    }

    @Test
    fun xhtmlAutoDetectionCanUseTheXmlDeclaration() {
        val html = """<?xml version="1.0" encoding="windows-1251"?><html><p>Привет</p></html>"""
        val result = TextImportDecoder.decode(html.toByteArray(Charset.forName("windows-1251")),
            TextImportOptions(), isHtml = true)

        assertEquals(html, result.text)
        assertTrue(result.diagnostics.single().contains("windows-1251"))
    }
}

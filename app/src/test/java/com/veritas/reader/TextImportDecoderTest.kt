package com.veritas.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class TextImportDecoderTest {
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
            TextImportOptions(encodingId = TextImportEncodingCatalog.DECLARED_HTML_ID)
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
}

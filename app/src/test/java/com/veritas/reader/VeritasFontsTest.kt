package com.veritas.reader

import androidx.compose.ui.text.font.FontFamily
import com.veritas.reader.ui.VeritasUiFont
import com.veritas.reader.ui.fontFamily
import com.veritas.reader.ui.veritasTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VeritasFontsTest {

    @Test
    fun testFontIdMapping() {
        assertEquals(VeritasUiFont.SYSTEM, VeritasUiFont.fromId("system"))
        assertEquals(VeritasUiFont.GAZETTE, VeritasUiFont.fromId("gazette"))
        assertEquals(VeritasUiFont.GAZETTE, VeritasUiFont.fromId("bitter"))
        assertEquals(VeritasUiFont.CRISP, VeritasUiFont.fromId("crisp"))
        assertEquals(VeritasUiFont.CRISP, VeritasUiFont.fromId("outfit"))
        assertEquals(VeritasUiFont.TYPEWRITER, VeritasUiFont.fromId("typewriter"))
        assertEquals(VeritasUiFont.TYPEWRITER, VeritasUiFont.fromId("times"))
        assertEquals(VeritasUiFont.TYPEWRITER, VeritasUiFont.fromId("serif"))
        assertEquals(VeritasUiFont.ATKINSON, VeritasUiFont.fromId("atkinson"))
        assertEquals(VeritasUiFont.SYSTEM, VeritasUiFont.fromId("unknown_font_id"))
    }

    @Test
    fun testFontFamilies() {
        // SYSTEM defaults to null to leave Compose on platform/device default
        assertNull(VeritasUiFont.SYSTEM.fontFamily())

        // TYPEWRITER is wired to Serif (Times)
        assertEquals(FontFamily.Serif, VeritasUiFont.TYPEWRITER.fontFamily())

        // GAZETTE, CRISP, ATKINSON have non-null custom font families
        assertNotNull(VeritasUiFont.GAZETTE.fontFamily())
        assertNotNull(VeritasUiFont.CRISP.fontFamily())
        assertNotNull(VeritasUiFont.ATKINSON.fontFamily())
    }

    @Test
    fun testVeritasTypographyRamp() {
        for (font in VeritasUiFont.entries) {
            val typography = veritasTypography(font)
            assertNotNull(typography.bodyLarge)
            assertNotNull(typography.bodyMedium)
            assertNotNull(typography.titleMedium)
            assertNotNull(typography.headlineSmall)
        }
    }
}

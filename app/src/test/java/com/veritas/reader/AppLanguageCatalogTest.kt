package com.veritas.reader

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class AppLanguageCatalogTest {
    @Test fun onlyAnEmptyOverrideRepresentsDeviceLanguage() {
        assertEquals("",AppLanguageCatalog.selectionTag(""))
        assertEquals("en",AppLanguageCatalog.selectionTag("en-GB"))
        assertEquals("fr",AppLanguageCatalog.selectionTag("fr-CA"))
        assertEquals("it-IT",AppLanguageCatalog.selectionTag("it-IT"))
        assertEquals("zh-Hant-TW",AppLanguageCatalog.selectionTag("zh-Hant-TW"))
        assertNotEquals("",AppLanguageCatalog.selectionTag("ha-GH"))
    }
    @Test fun regionalChoicesResolveWithoutChangingDeviceLocale() {
        val before = Locale.getDefault()
        assertEquals("", AppLanguageCatalog.canonicalTag(""))
        assertEquals("fr", AppLanguageCatalog.canonicalTag("fr-CA"))
        assertEquals("es", AppLanguageCatalog.canonicalTag("es-MX"))
        assertEquals("hi", AppLanguageCatalog.canonicalTag("hi-IN"))
        assertEquals("ar", AppLanguageCatalog.canonicalTag("ar-EG"))
        assertEquals("pt", AppLanguageCatalog.canonicalTag("pt-BR"))
        assertEquals("pt", AppLanguageCatalog.canonicalTag("pt-PT"))
        assertEquals("ru", AppLanguageCatalog.canonicalTag("ru-RU"))
        assertEquals("de", AppLanguageCatalog.canonicalTag("de-DE"))
        assertEquals("ko", AppLanguageCatalog.canonicalTag("ko-KR"))
        assertEquals("zh-Hans", AppLanguageCatalog.canonicalTag("zh-CN"))
        assertEquals("zh-Hans", AppLanguageCatalog.canonicalTag("zh-Hans-SG"))
        assertEquals("zh-Hans", AppLanguageCatalog.canonicalTag("zh-Hans-TW"))
        assertEquals("zh-Hans", AppLanguageCatalog.canonicalTag("zh-Hans-HK"))
        assertEquals(before, Locale.getDefault())
    }

    @Test fun unsupportedLanguagesDoNotPretendToHaveATranslation() {
        assertNull(AppLanguageCatalog.canonicalTag("it-IT"))
        assertNull(AppLanguageCatalog.canonicalTag("zh-Hant"))
        assertNull(AppLanguageCatalog.canonicalTag("zh-Hant-CN"))
        assertNull(AppLanguageCatalog.canonicalTag("zh-Latn"))
        assertNull(AppLanguageCatalog.canonicalTag("zh-TW"))
        assertNull(AppLanguageCatalog.canonicalTag("zh-HK"))
        assertNull(AppLanguageCatalog.canonicalTag("und"))
    }
}

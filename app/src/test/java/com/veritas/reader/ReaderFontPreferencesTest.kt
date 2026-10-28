package com.veritas.reader

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReaderFontPreferencesTest {
    @Test fun legacySizeMigratesOnceAndTheTwoSizesThenPersistIndependently() {
        val legacy = ReaderSettings.fromJson(JSONObject().put("fontSizeSp", 22))
        assertEquals(22, legacy.listenFontSizeSp)
        val listenChanged = ReaderSettings.fromJson(legacy.copy(listenFontSizeSp = 26).toJson())
        assertEquals(22, listenChanged.fontSizeSp)
        assertEquals(26, listenChanged.listenFontSizeSp)
        val textChanged = ReaderSettings.fromJson(listenChanged.copy(fontSizeSp = 14).toJson())
        assertEquals(14, textChanged.fontSizeSp)
        assertEquals(26, textChanged.listenFontSizeSp)
    }

    @Test fun invalidListenSizesAreBoundedIndependently() {
        val settings = ReaderSettings.fromJson(JSONObject().put("fontSizeSp", 16).put("listenFontSizeSp", 99))
        assertEquals(16, settings.fontSizeSp)
        assertEquals(28, settings.listenFontSizeSp)
        assertEquals(10, ReaderSettings.fromJson(JSONObject().put("listenFontSizeSp", -1)).listenFontSizeSp)
    }
}

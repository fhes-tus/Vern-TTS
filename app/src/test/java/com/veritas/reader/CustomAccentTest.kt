package com.veritas.reader

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CustomAccentTest {
    @Test fun olderSettingsKeepThemeDefaultsWithoutAnOverride() {
        val settings = ReaderSettings.fromJson(JSONObject("{\"themeId\":\"github_system\",\"themePackId\":\"one_ui\"}"))
        assertEquals("", settings.customAccentColor)
        assertEquals("github_system", settings.themeId)
        assertEquals("one_ui", settings.themePackId)
    }

    @Test fun customAccentSurvivesSettingsAndBackupJsonWithModeAndPackIntact() {
        val source = ReaderSettings(themeId = "dracula_system", themePackId = "liquid_glass", customAccentColor = " 12abef ", amoledMode = true)
        val loaded = ReaderSettings.fromJson(JSONObject(source.toJson().toString()))
        assertEquals(source.copy(customAccentColor = "#12ABEF"), loaded)
        assertEquals("", ReaderSettings.fromJson(loaded.copy(customAccentColor = "").toJson()).customAccentColor)
    }

    @Test fun invalidAndTransparentOverridesFallBackToTheTheme() {
        for (value in listOf("", "null", "#123", "#11223344", "red", "#zz0000", "##123456")) {
            assertEquals(value, "", normalizeCustomAccent(value))
            assertEquals(value, "", ReaderSettings.fromJson(JSONObject().put("customAccentColor", value)).customAccentColor)
            assertEquals(value, "", ReaderSettings(customAccentColor = value).toJson().getString("customAccentColor"))
        }
        assertEquals("#000000", normalizeCustomAccent("#000000"))
        assertEquals("#FFFFFF", normalizeCustomAccent("ffffff"))
    }
}

package com.veritas.reader

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class VernSettingsProfileTest {
    @Test
    fun exportAndImportTransferAppearanceWhileKeepingTheRecipientsOtherPreferences() {
        val sender = ReaderSettings(fontSizeSp = 25, listenFontSizeSp = 23, readingLayout = "scroll",
            showNavLabels = false, dailyGoalMinutes = 75, autoBackupWeekly = true, reduceMotion = false)
        val recipient = ReaderSettings(dailyGoalMinutes = 20, autoBackupWeekly = false, reduceMotion = true)
        val raw = VernSettingsProfile.encode(sender)
        val exported = JSONObject(raw).getJSONObject("preferences")
        assertFalse(exported.has("autoBackupWeekly"))
        assertFalse(exported.has("dailyGoalMinutes"))
        assertFalse(exported.has("reduceMotion"))
        exported.put("autoBackupWeekly", true).put("dailyGoalMinutes", 180).put("reduceMotion", false)

        val modified = JSONObject(raw).put("preferences", exported)
        val applied = VernSettingsProfile.read(modified.toString().byteInputStream()).applyTo(recipient)
        assertEquals(recipient.copy(fontSizeSp = 25, listenFontSizeSp = 23, readingLayout = "scroll",
            showNavLabels = false), applied)
    }

    @Test
    fun partialProfileAppliesOnlyItsKnownFields() {
        val raw = """{"schema":"vern.reader.settings.v1","preferences":{"sectionSpacingDp":18,"futureOption":"new"}}"""
        val recipient = ReaderSettings(fontSizeSp = 22, listenFontSizeSp = 21)
        assertEquals(recipient.copy(sectionSpacingDp = 18),
            VernSettingsProfile.read(raw.byteInputStream()).applyTo(recipient))
    }

    @Test
    fun rejectsBackupsWrongTypesAndOversizedFiles() {
        listOf(
            """{"schema":"veritas.reader.backup.v1","documents":[]}""",
            """{"schema":"vern.reader.settings.v1","preferences":{"showNavLabels":"false"}}""",
            """{"schema":"vern.reader.settings.v1","preferences":{"autoBackupWeekly":false}}""",
            """{"schema":"vern.reader.settings.v1","preferences":{"fontSizeSp":22}}{}""",
            """{"schema":"vern.reader.settings.v1","preferences":{"fontSizeSp":22},"extra":""" +
                "[".repeat(1_000) + "0" + "]".repeat(1_000) + "}",
            " ".repeat(32 * 1024 + 1)
        ).forEach { raw ->
            assertThrows(Exception::class.java) { VernSettingsProfile.read(raw.byteInputStream()) }
        }
    }
}

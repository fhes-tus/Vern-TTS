package com.veritas.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.time.LocalDate

class AutomaticBackupFilesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val location = AutomaticBackupLocation.APP_STORAGE
    private val start = LocalDate.of(2026, 11, 16)

    @Test
    fun failedPublicationPreservesPreviousCopyAndRemovesTemporaryFile() {
        val directory = folder.newFolder()
        val original = """{"text":"é"}"""
        AutomaticBackupFiles.publish(directory, location, start, original)
        val entry = AutomaticBackupFiles.list(directory, location).single()

        assertThrows(IOException::class.java) {
            AutomaticBackupFiles.publish(directory, location, start, """{"text":"new"}""") {
                throw IOException("Write interrupted before publication")
            }
        }

        assertEquals(listOf(entry.name), directory.list()?.toList())
        assertEquals(original, AutomaticBackupFiles.open(directory, entry).bufferedReader().use { it.readText() })
    }

    @Test
    fun successfulSameDayReplacementRejectsAnOutdatedSelection() {
        val directory = folder.newFolder()
        AutomaticBackupFiles.publish(directory, location, start, """{"text":"old"}""")
        val outdated = AutomaticBackupFiles.list(directory, location).single()
        val replacement = """{"text":"a longer replacement"}"""
        AutomaticBackupFiles.publish(directory, location, start, replacement)

        assertThrows(IOException::class.java) { AutomaticBackupFiles.open(directory, outdated).close() }
        val latest = AutomaticBackupFiles.list(directory, location).single()
        assertEquals(replacement, AutomaticBackupFiles.open(directory, latest).bufferedReader().use { it.readText() })
    }

    @Test
    fun retentionIncludesLegacyCopiesAndIgnoresFilesOutsideTheNamingContract() {
        val directory = folder.newFolder()
        val legacy = File(directory, "veritas_auto_backup_20261116.json").apply { writeText("{}") }
        val unrelated = File(directory, "manual_backup.json").apply { writeText("{}") }
        File(directory, "vern_auto_backup_20260230.json").writeText("{}")
        File(directory, "vern_auto_backup_20261129.json").createNewFile()
        assertEquals(legacy.name, AutomaticBackupFiles.list(directory, location).single().name)

        for (offset in 1L..5L) {
            AutomaticBackupFiles.publish(directory, location, start.plusDays(offset), "{}")
        }

        val stored = AutomaticBackupFiles.list(directory, location)
        assertEquals((5L downTo 2L).map(start::plusDays), stored.map { it.date })
        assertFalse(legacy.exists())
        assertEquals("{}", unrelated.readText())
        assertThrows(IllegalArgumentException::class.java) {
            AutomaticBackupFiles.open(directory, stored.first().copy(name = "../manual_backup.json")).close()
        }
    }
}

package com.veritas.reader

import androidx.compose.ui.text.TextRange
import com.veritas.reader.ui.screens.*
import org.junit.Assert.*
import org.junit.Test

class NotesContinuityTest {
    @Test fun linkMetadataHandlesQuotedTitlesAndRelativeSocialImages() {
        val link = NoteLinks.parseMetadata("https://example.org/note", """
            <meta content="God's promise &amp; hope" property="og:title">
            <meta name='twitter:image' content='/images/verse.png'>
        """.trimIndent(), "https://example.org/redirect/page")
        assertEquals("God's promise & hope", link.title)
        assertEquals("https://example.org/images/verse.png", link.image)
    }
    @Test fun filesRoundTripNamesWithBracketsColonsAndParentheses() {
        val file = NoteBlock.File("/data/notes_media/owned.pdf", "Report [final] (2): résumé.pdf", 1125506)
        val content = VeritasNoteEditing.serializeNoteBlocks(listOf(NoteBlock.Text(androidx.compose.ui.text.input.TextFieldValue("Before")), file))
        val parsed = VeritasNoteEditing.parseNoteBlocks(content)
        assertEquals(file, parsed.filterIsInstance<NoteBlock.File>().single())
        assertEquals("Before", parsed.filterIsInstance<NoteBlock.Text>().joinToString("") { it.value.text })
        val legacy = "[file:Report [final] (2).pdf:1125506](/data/notes_media/owned.pdf)"
        assertEquals("Report [final] (2).pdf", VeritasNoteEditing.parseNoteBlocks(legacy).filterIsInstance<NoteBlock.File>().single().fileName)
    }

    @Test fun linkCardsPersistAfterDeletingTheVisibleUrlAndCanBeRemovedSeparately() {
        val link = NoteLink("https://bible.com/bible/111/gal.1.10.NIV", "Galatians 1:10", "https://bible.com/image.jpg")
        val stored = NoteLinks.write("My reflection", listOf(link))
        assertEquals("My reflection", NoteLinks.strip(stored))
        assertEquals(listOf(link), NoteLinks.read(stored))
        val removed = NoteLinks.write("My reflection", emptyList(), listOf(link.url))
        assertTrue(NoteLinks.read(removed).isEmpty())
        assertEquals(listOf(link.url), NoteLinks.dismissed(removed))
        assertFalse(NoteLinks.validUrl("file:///data/notes"))
        assertEquals(listOf(link.url), NoteLinks.urls("(${link.url})."))
    }

    @Test fun historyReopensForOneHourButRejectsExpiredOrExternallyChangedNotes() {
        val current = NoteEditorSnapshot("Edited", false, 0, TextRange.Zero, "Title", 1000)
        val before = current.copy(content = "Original")
        NoteUndoHistory.write("history-test", current, listOf(before), emptyList(), 1000)
        assertEquals(listOf(before), NoteUndoHistory.read("history-test", current, 3_600_999)?.undo)
        assertNull(NoteUndoHistory.read("history-test", current.copy(content = "Changed elsewhere"), 2000))
        assertNull(NoteUndoHistory.read("history-test", current, 3_601_000))
    }

    @Test fun christianAdditionsShareOneGenreFilter() {
        val added = CURATED_CLASSICS.filter { it.id in setOf("classic_pilgrims_progress", "classic_good_morning_holy_spirit", "classic_power_of_imagination") }
        assertEquals(3, added.size)
        assertEquals(setOf("Faith & Spirit"), added.map { it.genre }.toSet())
    }
}

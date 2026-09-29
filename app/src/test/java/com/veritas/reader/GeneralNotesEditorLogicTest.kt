package com.veritas.reader

import androidx.compose.ui.text.input.TextFieldValue
import com.veritas.reader.ui.screens.NoteBlock
import com.veritas.reader.ui.screens.RichTextFormatter
import com.veritas.reader.ui.screens.VeritasNoteEditing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneralNotesEditorLogicTest {

    // -- Checklist Parsing & Serialization -----------------------------------

    @Test
    fun parseChecklistLinesMaintainsCheckedState() {
        val raw = "[ ] Buy groceries\n[x] Read chapter 4\n[ ] Review flashcards"
        val lines = raw.split("\n").filter { it.isNotBlank() }
        val parsed = lines.map { line ->
            val checked = line.startsWith("[x]")
            val text = line.removePrefix("[ ] ").removePrefix("[x] ")
            checked to text
        }

        assertEquals(3, parsed.size)
        assertFalse(parsed[0].first)
        assertEquals("Buy groceries", parsed[0].second)
        assertTrue(parsed[1].first)
        assertEquals("Read chapter 4", parsed[1].second)
        assertFalse(parsed[2].first)
        assertEquals("Review flashcards", parsed[2].second)
    }

    @Test
    fun serializeChecklistProducesCanonicalFormat() {
        val items = listOf(
            false to "Write summary",
            true to "Exercise 30 mins",
            false to "Check notifications"
        )
        val serialized = items.joinToString("\n") { (checked, text) ->
            "${if (checked) "[x]" else "[ ]"} $text"
        }

        assertEquals(
            "[ ] Write summary\n[x] Exercise 30 mins\n[ ] Check notifications",
            serialized
        )
    }

    @Test
    fun togglingChecklistItemPreservesOtherItems() {
        val original = mutableListOf(
            false to "Task A",
            false to "Task B"
        )
        // Toggle Task A
        original[0] = true to original[0].second

        assertTrue(original[0].first)
        assertFalse(original[1].first)

        val out = original.joinToString("\n") { (checked, text) ->
            "${if (checked) "[x]" else "[ ]"} $text"
        }
        assertEquals("[x] Task A\n[ ] Task B", out)
    }

    // -- Rich Text Markup Stripping for Sharing / Export ---------------------

    @Test
    fun stripMarkupRemovesBoldItalicAndCode() {
        val raw = "Here is **bold** text, *italic* word, and `code snippet`."
        val stripped = RichTextFormatter.stripMarkup(raw)
        assertEquals("Here is bold text, italic word, and code snippet.", stripped)
    }

    @Test
    fun stripMarkupRemovesHeadingsAndBlockquotes() {
        val raw = "# Title Heading\n## Subheading\n> Important quote line"
        val stripped = RichTextFormatter.stripMarkup(raw)
        assertEquals("Title Heading\nSubheading\nImportant quote line", stripped)
    }

    @Test
    fun stripMarkupPreservesPlainListsAndPunctuation() {
        val raw = "- Item 1\n- Item 2: with (parentheses) & details!"
        val stripped = RichTextFormatter.stripMarkup(raw)
        assertEquals("- Item 1\n- Item 2: with (parentheses) & details!", stripped)
    }

    // -- Media Block Parsing & Audio Deduplication ---------------------------

    @Test
    fun audioUrlDeduplicationPreservesOrderWithoutDuplicates() {
        val existingAudios = listOf("/path/audio1.3gp", "/path/audio2.3gp")
        val newAudio = "/path/audio3.3gp"
        val merged = (existingAudios + newAudio).distinct()
        assertEquals(listOf("/path/audio1.3gp", "/path/audio2.3gp", "/path/audio3.3gp"), merged)

        // Adding an existing audio should be idempotent
        val duplicateMerged = (merged + "/path/audio1.3gp").distinct()
        assertEquals(3, duplicateMerged.size)
    }

    @Test
    fun blockExtractionHandlesMixedAudioAndText() {
        val raw = "Intro notes\n[audio](/storage/memo1.3gp)\nMiddle notes\n[audio](/storage/memo2.3gp)"
        val blocks = VeritasNoteEditing.parseNoteBlocks(raw)
        assertEquals(5, blocks.size)
        assertTrue(blocks[0] is NoteBlock.Text)
        assertTrue(blocks[1] is NoteBlock.Audio)
        assertEquals("/storage/memo1.3gp", (blocks[1] as NoteBlock.Audio).path)
        assertTrue(blocks[2] is NoteBlock.Text)
        assertTrue(blocks[3] is NoteBlock.Audio)
        assertEquals("/storage/memo2.3gp", (blocks[3] as NoteBlock.Audio).path)
        assertTrue(blocks[4] is NoteBlock.Text)
    }

    @Test
    fun blockReorderingMaintainsIntegrity() {
        val blocks = mutableListOf<NoteBlock>(
            NoteBlock.Text(TextFieldValue("Block A")),
            NoteBlock.Image("/storage/img.jpg"),
            NoteBlock.Text(TextFieldValue("Block B"))
        )

        // Move Image down (index 1 to index 2)
        val item = blocks.removeAt(1)
        blocks.add(2, item)

        assertEquals("Block A", (blocks[0] as NoteBlock.Text).value.text)
        assertEquals("Block B", (blocks[1] as NoteBlock.Text).value.text)
        assertTrue(blocks[2] is NoteBlock.Image)
        assertEquals("/storage/img.jpg", (blocks[2] as NoteBlock.Image).path)
    }
}

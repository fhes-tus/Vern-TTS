package com.veritas.reader

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.veritas.reader.ui.screens.VeritasNoteEditing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteEditingLogicTest {

    // ── toggleLinePrefix: numbered lists ─────────────────────────────────

    @Test
    fun selectAllThenNumberedListNumbersEveryLine() {
        val input = TextFieldValue("alpha\nbeta\ngamma", TextRange(0, 16))
        val out = VeritasNoteEditing.toggleLinePrefix(input, "1. ")
        assertEquals("1. alpha\n2. beta\n3. gamma", out.text)
    }

    @Test
    fun numberedListTogglesOffWhenAllLinesNumbered() {
        val input = TextFieldValue("1. alpha\n2. beta", TextRange(0, 16))
        val out = VeritasNoteEditing.toggleLinePrefix(input, "1. ")
        assertEquals("alpha\nbeta", out.text)
    }

    @Test
    fun numberingReplacesExistingBulletMarkers() {
        val input = TextFieldValue("- alpha\nbeta", TextRange(0, 12))
        val out = VeritasNoteEditing.toggleLinePrefix(input, "1. ")
        assertEquals("1. alpha\n2. beta", out.text)
    }

    @Test
    fun blankLinesInSelectionAreSkippedByNumbering() {
        val input = TextFieldValue("alpha\n\nbeta", TextRange(0, 11))
        val out = VeritasNoteEditing.toggleLinePrefix(input, "1. ")
        assertEquals("1. alpha\n\n2. beta", out.text)
    }

    // ── toggleLinePrefix: bullets and headings ───────────────────────────

    @Test
    fun caretLineGetsBulletWithoutSelection() {
        val input = TextFieldValue("hello", TextRange(3))
        val out = VeritasNoteEditing.toggleLinePrefix(input, "- ")
        assertEquals("- hello", out.text)
    }

    @Test
    fun headingTogglesOnAndOff() {
        val once = VeritasNoteEditing.toggleLinePrefix(TextFieldValue("title", TextRange(2)), "# ")
        assertEquals("# title", once.text)
        val twice = VeritasNoteEditing.toggleLinePrefix(TextFieldValue(once.text, TextRange(4)), "# ")
        assertEquals("title", twice.text)
    }

    @Test
    fun prefixOnlyAffectsLinesTouchedBySelection() {
        // Caret on the middle line only.
        val input = TextFieldValue("one\ntwo\nthree", TextRange(5))
        val out = VeritasNoteEditing.toggleLinePrefix(input, "- ")
        assertEquals("one\n- two\nthree", out.text)
    }

    // ── toggleInlineMarker ────────────────────────────────────────────────

    @Test
    fun inlineMarkerWrapsSelection() {
        val input = TextFieldValue("make bold now", TextRange(5, 9))
        val out = VeritasNoteEditing.toggleInlineMarker(input, "**")
        assertEquals("make **bold** now", out.text)
    }

    @Test
    fun inlineMarkerUnwrapsAlreadyWrappedSelection() {
        val input = TextFieldValue("make **bold** now", TextRange(7, 11))
        val out = VeritasNoteEditing.toggleInlineMarker(input, "**")
        assertEquals("make bold now", out.text)
    }

    @Test
    fun inlineMarkerAtCaretInsertsEmptyPairWithCaretInside() {
        val input = TextFieldValue("ab", TextRange(1))
        val out = VeritasNoteEditing.toggleInlineMarker(input, "*")
        assertEquals("a**b", out.text)
        assertEquals(TextRange(2), out.selection)
    }

    // ── continueListOnNewline ─────────────────────────────────────────────

    private fun enter(oldText: String, newText: String, caret: Int): TextFieldValue =
        VeritasNoteEditing.continueListOnNewline(
            TextFieldValue(oldText, TextRange(oldText.length)),
            TextFieldValue(newText, TextRange(caret))
        )

    @Test
    fun enterContinuesNumberedList() {
        val out = enter("1. one", "1. one\n", 7)
        assertEquals("1. one\n2. ", out.text)
        assertEquals(TextRange(10), out.selection)
    }

    @Test
    fun numberingContinuesPastTwo() {
        val out = enter("1. one\n2. two", "1. one\n2. two\n", 14)
        assertEquals("1. one\n2. two\n3. ", out.text)
    }

    @Test
    fun imeBatchEditStillContinuesList() {
        // Autocorrect commits "word" + newline as one edit replacing "wrd".
        val out = enter("1. wrd", "1. word\n", 8)
        assertEquals("1. word\n2. ", out.text)
    }

    @Test
    fun enterContinuesBulletsAndCheckboxes() {
        assertEquals("- a\n- ", enter("- a", "- a\n", 4).text)
        assertEquals("[ ] a\n[ ] ", enter("[ ] a", "[ ] a\n", 6).text)
    }

    @Test
    fun enterOnEmptyItemEndsList() {
        val out = enter("1. one\n2. ", "1. one\n2. \n", 11)
        assertEquals("1. one\n", out.text)
        assertEquals(TextRange(7), out.selection)
    }

    @Test
    fun indentedListsKeepIndentation() {
        val out = enter("  - a", "  - a\n", 6)
        assertEquals("  - a\n  - ", out.text)
    }

    @Test
    fun plainTypingIsUntouched() {
        val candidate = TextFieldValue("hello!", TextRange(6))
        val out = VeritasNoteEditing.continueListOnNewline(
            TextFieldValue("hello", TextRange(5)), candidate
        )
        assertEquals(candidate.text, out.text)
    }

    @Test
    fun deletionIsUntouched() {
        val candidate = TextFieldValue("1. one\n", TextRange(7))
        val out = VeritasNoteEditing.continueListOnNewline(
            TextFieldValue("1. one\n2. ", TextRange(10)), candidate
        )
        assertEquals(candidate.text, out.text)
    }

    @Test
    fun newlineOnNonListLineIsUntouched() {
        val out = enter("just text", "just text\n", 10)
        assertEquals("just text\n", out.text)
    }

    // ── parseNoteBlocks & serializeNoteBlocks ─────────────────────────────

    @Test
    fun parseNoteBlocksExtractsAttachmentsWithoutExtraWhitespace() {
        val input = "Hello world\n![image](/path/img.jpg)\nNext paragraph"
        val blocks = VeritasNoteEditing.parseNoteBlocks(input)
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is com.veritas.reader.ui.screens.NoteBlock.Text)
        assertEquals("Hello world", (blocks[0] as com.veritas.reader.ui.screens.NoteBlock.Text).value.text)
        assertTrue(blocks[1] is com.veritas.reader.ui.screens.NoteBlock.Image)
        assertEquals("/path/img.jpg", (blocks[1] as com.veritas.reader.ui.screens.NoteBlock.Image).path)
        assertTrue(blocks[2] is com.veritas.reader.ui.screens.NoteBlock.Text)
        assertEquals("Next paragraph", (blocks[2] as com.veritas.reader.ui.screens.NoteBlock.Text).value.text)
    }

    @Test
    fun serializeNoteBlocksRoundtripDoesNotAccumulateNewlines() {
        val original = "First line\n![image](/data/img.png)\n[audio](/data/memo.3gp)\n[video](/data/clip.mp4)\nLast line"
        val parsed1 = VeritasNoteEditing.parseNoteBlocks(original)
        val serialized1 = VeritasNoteEditing.serializeNoteBlocks(parsed1)
        assertEquals(original, serialized1)

        // Multiple cycles must NOT grow whitespace or blank lines
        val parsed2 = VeritasNoteEditing.parseNoteBlocks(serialized1)
        val serialized2 = VeritasNoteEditing.serializeNoteBlocks(parsed2)
        assertEquals(original, serialized2)

        val parsed3 = VeritasNoteEditing.parseNoteBlocks(serialized2)
        val serialized3 = VeritasNoteEditing.serializeNoteBlocks(parsed3)
        assertEquals(original, serialized3)
    }

    @Test fun activeInlineStyleCanBeSwitchedOffWithoutRemovingExistingFormatting() {
        val text = "**Styled**"
        val typingAtEnd = TextFieldValue(text, TextRange(8))
        assertTrue(VeritasNoteEditing.isInlineMarkerActive(typingAtEnd, "**"))
        assertFalse(VeritasNoteEditing.isInlineMarkerActive(typingAtEnd, "*"))
        val off = VeritasNoteEditing.toggleInlineMarker(typingAtEnd, "**")
        assertEquals(text, off.text)
        assertEquals(TextRange(text.length), off.selection)
        assertFalse(VeritasNoteEditing.isInlineMarkerActive(off, "**"))

        val inside = TextFieldValue(text, TextRange(5))
        val split = VeritasNoteEditing.toggleInlineMarker(inside, "**")
        assertEquals("**Sty****led**", split.text)
        assertFalse(VeritasNoteEditing.isInlineMarkerActive(split, "**"))
    }
}

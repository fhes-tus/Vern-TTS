package com.veritas.reader

import com.veritas.reader.ui.ReaderUiState
import com.veritas.reader.ui.ownsTextEdit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextEditorOwnershipTest {
    @Test
    fun editingAcrossSourcePagesKeepsUnchangedSentencesOnTheirOriginalPages() {
        val original = ReaderTextIndex.pageMarker(1) + "\nFirst page ends here." +
            "\n\n" + ReaderTextIndex.pageMarker(2) + "\nThe next page begins here."
        val edited = ReaderTextIndex.replaceSentenceRange(original, 2, 0, 2,
            "A corrected opening. Another sentence belongs here. The next page begins here.")
        val sentences = ReaderTextIndex.build(edited, 2).sentences
        org.junit.Assert.assertEquals(listOf(1, 1, 2), sentences.map { it.pageNumber })
    }

    @Test fun aSaveCannotPublishAfterTheReaderOrEditorChanges() {
        val document = ReaderDocument("book-a", "Reading A", "TXT", "A sentence.", listOf("A sentence."))
        val target = VeritasTextEditTarget.Part(0, "part 1")
        val owner = ReaderUiState(activeDocument = document, showTextEditor = true,
            editorTarget = target, editorDocumentId = document.id)
        assertTrue(owner.ownsTextEdit(document, target))
        assertFalse(owner.copy(showTextEditor = false).ownsTextEdit(document, target))
        assertFalse(owner.copy(isOpeningDocument = true).ownsTextEdit(document, target))
        assertFalse(owner.copy(activeDocument = document.copy(id = "book-b")).ownsTextEdit(document, target))
        assertFalse(owner.copy(editorTarget = target.copy()).ownsTextEdit(document, target))
    }
}

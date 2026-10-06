package com.veritas.reader

import android.text.Spannable
import com.veritas.reader.ui.screens.buildReaderTextSelection
import com.veritas.reader.ui.screens.updateReaderPresentationSpans
import org.junit.Assert.*
import org.junit.Test

class ReaderPresentationUpdateTest {
    private class TestSpannable(private val text: String) : Spannable {
        private data class Span(val start: Int, val end: Int, val flags: Int)
        private val spans = mutableMapOf<Any, Span>()
        override fun setSpan(what: Any?, start: Int, end: Int, flags: Int) { if (what != null) spans[what] = Span(start, end, flags) }
        override fun removeSpan(what: Any?) { spans.remove(what) }
        @Suppress("UNCHECKED_CAST")
        override fun <T : Any?> getSpans(start: Int, end: Int, type: Class<T>?): Array<T> {
            val matching = spans.keys.filter { type == null || type.isInstance(it) }
            val result = java.lang.reflect.Array.newInstance(type ?: Any::class.java, matching.size) as Array<T>
            matching.forEachIndexed { index, span -> result[index] = span as T }
            return result
        }
        override fun getSpanStart(tag: Any?) = spans[tag]?.start ?: -1
        override fun getSpanEnd(tag: Any?) = spans[tag]?.end ?: -1
        override fun getSpanFlags(tag: Any?) = spans[tag]?.flags ?: 0
        override fun nextSpanTransition(start: Int, limit: Int, type: Class<*>?) = limit
        override val length get() = text.length
        override fun get(index: Int) = text[index]
        override fun subSequence(startIndex: Int, endIndex: Int) = text.subSequence(startIndex, endIndex)
        override fun toString() = text
    }

    @Test fun highlightRefreshPreservesSelectionAndEditorSpans() {
        val current = TestSpannable("Selected sentence.")
        val oldHighlight = Any()
        val selectionAnchor = Any()
        val editorWatcher = Any()
        current.setSpan(oldHighlight, 0, 8, 33)
        current.setSpan(selectionAnchor, 2, 2, 34)
        current.setSpan(editorWatcher, 0, current.length, 18)
        val next = TestSpannable(current.toString())
        val newHighlight = Any()
        next.setSpan(newHighlight, 9, next.length, 33)

        val owned = updateReaderPresentationSpans(current, listOf(oldHighlight), next)
        assertEquals(-1, current.getSpanStart(oldHighlight))
        assertEquals(2, current.getSpanStart(selectionAnchor))
        assertEquals(0, current.getSpanStart(editorWatcher))
        assertEquals(9, current.getSpanStart(newHighlight))
        assertEquals(listOf(newHighlight), owned)
    }

    @Test fun presentationRefreshRejectsChangedContentWithoutRemovingExistingSpans() {
        val current = TestSpannable("Old text.")
        val selection = Any()
        current.setSpan(selection, 1, 3, 33)
        assertThrows(IllegalArgumentException::class.java) {
            updateReaderPresentationSpans(current, listOf(selection), TestSpannable("Different text."))
        }
        assertEquals(1, current.getSpanStart(selection))
    }

    @Test fun dismissedSelectionDoesNotBecomeAPrefixSelection() {
        val part = ReaderPart(0, ReaderPageRange(0, 1, 1), 0, 1, "A sentence.", listOf(ReaderPartSentenceRange(0, 0, 11)))
        assertNull(buildReaderTextSelection(part, -1, 6))
        assertNull(buildReaderTextSelection(part, 6, -1))
        assertEquals("sentence", buildReaderTextSelection(part, 10, 2)?.text)
    }

    @Test fun readerModelCarriesImportCompletionStateForEmptyPageMessaging() {
        val metadata = SavedDocument.fromJson(org.json.JSONObject()
            .put("id", "partial-book").put("fileName", "partial-book.txt")
            .put("pageCount", 3).put("partial", true))
        assertTrue(buildReaderDocument(metadata, "First sentence.").partial)
        assertFalse(buildReaderDocument(metadata.copy(partial = false), "First sentence.").partial)
    }
}

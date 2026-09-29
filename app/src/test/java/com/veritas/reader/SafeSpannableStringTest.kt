package com.veritas.reader

import android.text.Spannable
import com.veritas.reader.ui.screens.DelegatingActionModeCallback
import com.veritas.reader.ui.screens.SafeSpannableString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafeSpannableStringTest {

    private class FakeSpannable(private val content: String) : Spannable {
        val spans = mutableMapOf<Any, Pair<Int, Int>>()

        override fun setSpan(what: Any?, start: Int, end: Int, flags: Int) {
            if (start < 0 || end < 0) {
                throw IndexOutOfBoundsException("setSpan ($start ... $end) starts before 0")
            }
            if (start > content.length || end > content.length) {
                throw IndexOutOfBoundsException("setSpan ($start ... $end) ends beyond length")
            }
            if (what != null) {
                spans[what] = Pair(start, end)
            }
        }

        override fun removeSpan(what: Any?) {
            spans.remove(what)
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any?> getSpans(start: Int, end: Int, type: Class<T>?): Array<T> {
            val matched = spans.filter { it.value.first >= start && it.value.second <= end }.keys.toList()
            return matched.toTypedArray() as Array<T>
        }

        override fun getSpanStart(tag: Any?): Int = spans[tag]?.first ?: -1
        override fun getSpanEnd(tag: Any?): Int = spans[tag]?.second ?: -1
        override fun getSpanFlags(tag: Any?): Int = 0
        override fun nextSpanTransition(start: Int, limit: Int, type: Class<*>?): Int = limit
        override val length: Int get() = content.length
        override fun get(index: Int): Char = content[index]
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = content.subSequence(startIndex, endIndex)
        override fun toString(): String = content
    }

    @Test
    fun testSamsungAndroid16NegativeSpanDoesNotCrash() {
        val rawText = "Veritas Reader on Samsung Galaxy"
        val fake = FakeSpannable(rawText)
        val safeSpannable = SafeSpannableString(rawText, delegateOverride = fake)
        val spanTag = "SelectionHandleSpan"

        // 1. Apply normal span first
        safeSpannable.setSpan(spanTag, 0, 7, 0)
        assertEquals(0, safeSpannable.getSpanStart(spanTag))
        assertEquals(7, safeSpannable.getSpanEnd(spanTag))

        // 2. Simulate Samsung One UI Android 16 dismiss animation:
        // Selection.setSelection(spannable, -1, -1) which invokes setSpan(span, -1, -1, ...)
        // The underlying FakeSpannable throws IndexOutOfBoundsException if called with -1, -1!
        // SafeSpannableString must intercept this, remove the span, and not crash!
        safeSpannable.setSpan(spanTag, -1, -1, 0)
        assertEquals(-1, safeSpannable.getSpanStart(spanTag))
    }

    @Test
    fun testPositiveOutOfBoundsIsClampedSafely() {
        val rawText = "Hello World"
        val fake = FakeSpannable(rawText)
        val safeSpannable = SafeSpannableString(rawText, delegateOverride = fake)
        val spanTag = "HighlightSpan"

        // Passing end beyond string length should clamp to text.length, not crash
        safeSpannable.setSpan(spanTag, 0, 999, 0)
        assertEquals(0, safeSpannable.getSpanStart(spanTag))
        assertEquals(rawText.length, safeSpannable.getSpanEnd(spanTag))
    }

    @Test
    fun testDelegatingActionModeCallbackDefaults() {
        val delegator = DelegatingActionModeCallback()
        assertNull(delegator.delegate)
    }
}

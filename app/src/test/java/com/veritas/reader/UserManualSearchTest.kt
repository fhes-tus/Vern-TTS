package com.veritas.reader

import com.veritas.reader.ui.screens.searchManual
import com.veritas.reader.ui.screens.vernManualSections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserManualSearchTest {
    private val sections = vernManualSections()

    @Test
    fun ranksEverydayQuestionsByIntent() {
        val examples = mapOf(
            "Why does audio stop when I lock my phone?" to "Background Playback & Battery",
            "How can I move my books to a new phone?" to "Portable Backup & Restore",
            "my scanned PDF is blank" to "PDF & Import Tools",
            "where can I look up a word without internet?" to "Dictionary & Vocabulary",
            "how do I scroll instead of swiping?" to "Reading Layout & Accessibility",
            "find and replace a word" to "Edit Extracted Text",
            "how do I make the voice slower?" to "Listen & Playback Controls",
            "send a passage to ChatGPT" to "AI Study Studio & Ask AI"
        )
        for ((query, title) in examples) {
            assertEquals(query, title, searchManual(sections, query).firstOrNull()?.chapters?.firstOrNull()?.title)
        }
    }

    @Test
    fun matchesMisspellingsAndExactTitles() {
        assertEquals("Dictionary & Vocabulary", searchManual(sections, "dictoinary").first().chapters.first().title)
        assertEquals("My Library", searchManual(sections, "My Library").first().chapters.first().title)
    }

    @Test
    fun preservesBrowseOrderForBlankQueryAndRejectsUnrelatedTerms() {
        assertEquals(sections, searchManual(sections, "  "))
        assertTrue(searchManual(sections, "unicorn meteorology").isEmpty())
    }
}

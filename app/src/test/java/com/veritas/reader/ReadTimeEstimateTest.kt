package com.veritas.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadTimeEstimateTest {

    @Test
    fun testShortReadTimeEstimate() {
        val doc = SavedDocument(
            id = "test_1",
            title = "Short Story",
            fileName = "short.txt",
            sourceLabel = "TXT",
            createdAt = 0L,
            updatedAt = 0L,
            currentIndex = 0,
            chunkCount = 10,
            charCount = 5000, // ~1000 words -> 5 mins at 200 WPM
            preview = ""
        )
        assertEquals(5, estimatedReadMinutes(doc))
    }

    @Test
    fun testLongReadTimeEstimate() {
        val doc = SavedDocument(
            id = "test_2",
            title = "Epic Novel",
            fileName = "novel.txt",
            sourceLabel = "EPUB",
            createdAt = 0L,
            updatedAt = 0L,
            currentIndex = 0,
            chunkCount = 2000,
            charCount = 500000, // ~100,000 words -> 500 mins -> 8h 20m
            preview = ""
        )
        assertEquals(500, estimatedReadMinutes(doc))
    }

    @Test
    fun testFallbackToChunkCountWhenCharCountIsZero() {
        val doc = SavedDocument(
            id = "test_3",
            title = "Fallback Document",
            fileName = "doc.txt",
            sourceLabel = "PDF",
            createdAt = 0L,
            updatedAt = 0L,
            currentIndex = 0,
            chunkCount = 60, // 60 sentences * 17 words = 1020 words -> ~5 mins
            charCount = 0,
            preview = ""
        )
        assertEquals(5, estimatedReadMinutes(doc))
    }
}

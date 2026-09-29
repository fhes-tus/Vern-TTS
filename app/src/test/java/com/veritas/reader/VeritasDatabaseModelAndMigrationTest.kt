package com.veritas.reader

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VeritasDatabaseModelAndMigrationTest {

    @Test
    fun savedDocumentJsonRoundTripPreservesAllFields() {
        val original = SavedDocument(
            id = "doc-123",
            title = "Test Book Title",
            fileName = "doc-123.txt",
            sourceLabel = "PDF",
            createdAt = 1700000000000L,
            updatedAt = 1700000500000L,
            currentIndex = 42,
            chunkCount = 100,
            charCount = 50000,
            preview = "This is a preview text...",
            favorite = true,
            collection = "Classics",
            originalFileName = "original.pdf",
            originalMimeType = "application/pdf",
            pageCount = 250,
            partial = false,
            language = "en"
        )

        val json = original.toJson()
        val parsed = SavedDocument.fromJson(json)

        assertEquals(original.id, parsed.id)
        assertEquals(original.title, parsed.title)
        assertEquals(original.fileName, parsed.fileName)
        assertEquals(original.sourceLabel, parsed.sourceLabel)
        assertEquals(original.createdAt, parsed.createdAt)
        assertEquals(original.updatedAt, parsed.updatedAt)
        assertEquals(original.currentIndex, parsed.currentIndex)
        assertEquals(original.chunkCount, parsed.chunkCount)
        assertEquals(original.charCount, parsed.charCount)
        assertEquals(original.preview, parsed.preview)
        assertTrue(parsed.favorite)
        assertEquals("Classics", parsed.collection)
        assertEquals("original.pdf", parsed.originalFileName)
        assertEquals("application/pdf", parsed.originalMimeType)
        assertEquals(250, parsed.pageCount)
        assertFalse(parsed.partial)
        assertEquals("en", parsed.language)
    }

    @Test
    fun savedDocumentHandlesMissingLegacyFieldsGracefully() {
        val legacyJson = JSONObject().apply {
            put("id", "legacy-doc-1")
            put("title", "Legacy Doc")
            put("fileName", "legacy.txt")
            put("sourceLabel", "Text")
            put("createdAt", 1600000000000L)
            put("updatedAt", 1600000000000L)
            put("currentIndex", 5)
            put("chunkCount", 20)
            put("charCount", 1000)
            put("preview", "Legacy preview")
        }

        val parsed = SavedDocument.fromJson(legacyJson)
        assertEquals("legacy-doc-1", parsed.id)
        assertFalse(parsed.favorite)
        assertEquals("", parsed.collection)
        assertEquals("", parsed.originalFileName)
        assertEquals(0, parsed.pageCount)
        assertFalse(parsed.partial)
        assertEquals("", parsed.language)
    }

    @Test
    fun readerAnnotationParsesAndPreservesAttributes() {
        val json = JSONObject().apply {
            put("documentId", "doc-99")
            put("chunkIndex", 14)
            put("type", "BOOKMARK")
            put("note", "Crucial chapter insight")
            put("createdAt", 1700000000000L)
            put("updatedAt", 1700001000000L)
            put("highlightColor", "#FF5722")
            put("selectionGroupId", "group-1")
            put("audioPath", "/path/to/voice.m4a")
            put("audioDurationSeconds", 35)
        }

        val parsed = ReaderAnnotation.fromJson(json)
        assertNotNull(parsed)
        assertEquals("doc-99", parsed!!.documentId)
        assertEquals(14, parsed.chunkIndex)
        assertEquals(AnnotationType.BOOKMARK, parsed.type)
        assertEquals("Crucial chapter insight", parsed.note)
        assertEquals("#FF5722", parsed.highlightColor)
        assertEquals("group-1", parsed.selectionGroupId)
        assertEquals("/path/to/voice.m4a", parsed.audioPath)
        assertEquals(35, parsed.audioDurationSeconds)
    }

    @Test
    fun flashcardLegacyMigrationAssignsSetIdAndName() {
        val legacyCards = listOf(
            FlashcardProgress(
                id = "card-1",
                documentId = "doc-alpha",
                front = "Question 1",
                back = "Answer 1",
                setId = "",
                setName = ""
            ),
            FlashcardProgress(
                id = "card-2",
                documentId = "",
                front = "Question 2",
                back = "Answer 2",
                setId = "",
                setName = ""
            ),
            FlashcardProgress(
                id = "card-3",
                documentId = "doc-beta",
                front = "Question 3",
                back = "Answer 3",
                setId = "existing-set",
                setName = "Existing Set"
            )
        )

        val migrated = legacyCards.map { card ->
            if (card.setId.isNotBlank()) card
            else card.copy(
                setId = "legacy-${card.documentId.ifBlank { "pasted" }}",
                setName = "Imported cards"
            )
        }

        assertEquals("legacy-doc-alpha", migrated[0].setId)
        assertEquals("Imported cards", migrated[0].setName)
        assertEquals("legacy-pasted", migrated[1].setId)
        assertEquals("Imported cards", migrated[1].setName)
        assertEquals("existing-set", migrated[2].setId)
        assertEquals("Existing Set", migrated[2].setName)
    }
}

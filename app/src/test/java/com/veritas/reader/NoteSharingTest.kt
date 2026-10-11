package com.veritas.reader

import com.veritas.reader.ui.screens.NoteLink
import com.veritas.reader.ui.screens.NoteLinks
import com.veritas.reader.ui.screens.NoteSharing
import org.junit.Assert.*
import org.junit.Test

class NoteSharingTest {
    @Test fun sharingKeepsTextAndLinksButOmitsEveryAttachmentPathAndInternalTag() {
        val content = "Before\n![image](/data/user/0/app/notes_media/image.jpg)\n" +
            "[audio](/data/user/0/app/notes_media/audio.mp3)\n" +
            "[video](/data/user/0/app/notes_media/video.mp4)\n" +
            "[file:~Report.pdf:20](/data/user/0/app/notes_media/report.pdf)\n**After**"
        val note = GeneralNote("share", "Title", NoteLinks.write(content,
            listOf(NoteLink("https://example.org/article", "Article"))), 1)
        val plain = NoteSharing.plainText(note)
        assertTrue(plain.startsWith("Title\n\n"))
        assertTrue(plain.contains("Before") && plain.contains("After"))
        assertTrue(plain.contains("https://example.org/article"))
        assertFalse(plain.contains("notes_media"))
        assertFalse(plain.contains("link-preview") || plain.contains("[file"))
        assertEquals(4, NoteSharing.blocks(note).count { NoteSharing.path(it) != null })
    }

    @Test fun checklistSharingKeepsChecksAndStripsAttachments() {
        val note = GeneralNote("check", "", "[x] Done\n[ ] Next\n[video](/private/video.mp4)", 1, isChecklist = true)
        assertEquals("☑ Done\n☐ Next", NoteSharing.plainText(note))
    }

    @Test fun attachmentOnlyAndLegacyNotesStillHaveShareableFiles() {
        val note = GeneralNote("legacy", "", "", 1, imageUrl = "/private/photo.jpg", audioUrl = "/private/memo.3gp")
        assertEquals("", NoteSharing.plainText(note))
        assertEquals(listOf("/private/photo.jpg", "/private/memo.3gp"), NoteSharing.blocks(note).mapNotNull(NoteSharing::path))
    }

    @Test fun mixedAttachmentsUseAMimeTypeThatIncludesEveryFile() {
        assertEquals("text/plain", NoteSharing.commonMimeType(emptyList()))
        assertEquals("image/png", NoteSharing.commonMimeType(listOf("image/png", "image/png")))
        assertEquals("image/*", NoteSharing.commonMimeType(listOf("image/png", "image/jpeg")))
        assertEquals("*/*", NoteSharing.commonMimeType(listOf("image/png", "audio/mp3", "application/pdf")))
    }
}

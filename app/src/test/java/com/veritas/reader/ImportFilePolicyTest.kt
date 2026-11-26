package com.veritas.reader

import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class ImportFilePolicyTest {
    @Test fun unsupportedExtensionsCannotBeOverriddenByMimeHints() {
        for (name in listOf("bundle.aab", "app.apk", "archive.zip", "image.svg", "book.mobi", "doc.doc", "doc.rtf")) {
            for (mime in listOf("application/octet-stream", "text/plain", "image/png", "application/pdf")) {
                assertNull("$name / $mime", ImportFilePolicy.detect(name, mime))
            }
        }
    }

    @Test fun extensionlessProviderItemsRequireAKnownMimeType() {
        assertEquals(ImportFormat.IMAGE, ImportFilePolicy.detect("image:123", "image/jpeg"))
        assertEquals(ImportFormat.TEXT, ImportFilePolicy.detect("123", "text/plain; charset=utf-8"))
        assertNull(ImportFilePolicy.detect("123", "application/octet-stream"))
        assertEquals(ImportFormat.EPUB, ImportFilePolicy.detect("BOOK.EPUB", "application/octet-stream"))
        assertEquals(ImportFormat.TEXT, ImportFilePolicy.detect("notes.md", "application/octet-stream"))
    }

    @Test fun chooserDoesNotInviteGenericBinariesOrUnimplementedFormats() {
        assertFalse(ImportFilePolicy.chooserMimeTypes().any { it in setOf("*/*", "application/octet-stream", "application/msword", "application/rtf") })
    }

    @Test fun binariesRenamedAsTextAndInvalidDocumentHeadersAreRejected() {
        val binaryHeaders = listOf(
            byteArrayOf(0x50, 0x4b, 3, 4), "%PDF-1.7".toByteArray(), "MZ executable".toByteArray(),
            byteArrayOf(0x7f, 0x45, 0x4c, 0x46), byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47)
        )
        binaryHeaders.forEach { assertThrows(IllegalArgumentException::class.java) { ImportFilePolicy.validateHeader(ImportFormat.TEXT, it) } }
        assertThrows(IllegalArgumentException::class.java) { ImportFilePolicy.validateHeader(ImportFormat.PDF, "fake pdf".toByteArray()) }
        assertThrows(IllegalArgumentException::class.java) { ImportFilePolicy.validateHeader(ImportFormat.TEXT, byteArrayOf()) }
        ImportFilePolicy.validateHeader(ImportFormat.PDF, "%PDF-1.7".toByteArray())
        ImportFilePolicy.validateHeader(ImportFormat.EPUB, byteArrayOf(0x50, 0x4b, 3, 4))
    }

    @Test fun decodedTextAllowsMultilingualAndUtf16ButRejectsBinaryControls() {
        val text = "Café. Ελληνικά. 日本語.\nAnother readable sentence.\tDone."
        ImportFilePolicy.validateText(text)
        ImportFilePolicy.validateText(TextImportDecoder.decode(text.toByteArray(Charsets.UTF_16LE), TextImportOptions("utf_16le")).text)
        assertThrows(IllegalArgumentException::class.java) { ImportFilePolicy.validateText("binary\u0000\u0001\u0002\u0000data") }
    }

    @Test fun importFingerprintDistinguishesEqualSizedFilesAndMatchesExactCopies() {
        val source = "First document".toByteArray()
        val different = "Other document".toByteArray()
        assertEquals(source.size, different.size)
        val fingerprint = importContentFingerprint(source.size.toLong()) { source.inputStream() }
        assertNotNull(fingerprint)
        assertArrayEquals(fingerprint, importContentFingerprint(source.size.toLong()) { source.copyOf().inputStream() })
        assertFalse(fingerprint!!.contentEquals(importContentFingerprint(different.size.toLong()) { different.inputStream() }!!))
    }

    @Test fun importFingerprintRequiresACompleteStreamOfTheReportedSize() {
        assertNull(importContentFingerprint(3) { ByteArray(2).inputStream() })
        assertNull(importContentFingerprint(3) { ByteArray(4).inputStream() })
        assertNull(importContentFingerprint(3) { throw IOException("Provider unavailable") })
        assertThrows(IllegalStateException::class.java) {
            importContentFingerprint(3, checkActive = { error("Import cancelled") }) { ByteArray(3).inputStream() }
        }
    }
}

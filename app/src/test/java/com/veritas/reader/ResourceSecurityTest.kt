package com.veritas.reader

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ResourceSecurityTest {
    private fun zip(vararg content: Pair<String, Int>): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip -> content.forEach { (name, size) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(ByteArray(size) { 65 }); zip.closeEntry()
        } }
    }.toByteArray()

    @Test fun boundedCopyAcceptsExactLimitAndRejectsOneMoreByte() {
        assertArrayEquals(ByteArray(12), ResourceLimits.read(ByteArrayInputStream(ByteArray(12)), 12))
        assertThrows(IllegalArgumentException::class.java) { ResourceLimits.read(ByteArrayInputStream(ByteArray(13)), 12) }
    }
    @Test fun skippedCompressedEntryCannotBypassItsExpansionLimit() {
        BoundedZipInputStream(ByteArrayInputStream(zip("ignored-image" to 512, "text" to 1)), ArchiveLimits(10, 128, 1024)).use { input ->
            assertEquals("ignored-image", input.nextEntry?.name)
            assertThrows(IllegalArgumentException::class.java) { input.nextEntry }
        }
    }
    @Test fun aggregateBudgetCountsDistinctSmallEntriesAndDirectoryCountIsLimited() {
        BoundedZipInputStream(ByteArrayInputStream(zip("a" to 70, "b" to 70)), ArchiveLimits(10, 100, 100)).use { input ->
            input.nextEntry; assertEquals(70, input.readBytes().size); input.nextEntry
            assertThrows(IllegalArgumentException::class.java) { input.readBytes() }
        }
        BoundedZipInputStream(ByteArrayInputStream(zip("a/" to 0, "b/" to 0)), ArchiveLimits(1, 100, 100)).use { input ->
            input.nextEntry
            assertThrows(IllegalArgumentException::class.java) { input.nextEntry }
        }
    }
    @Test fun secureUrlsRejectOtherSchemesAndEmbeddedCredentials() {
        assertEquals("books.example", SecureDownloads.httpsUrl("https://books.example/book.pdf").host)
        for (value in listOf("http://books.example/a", "file:///private/book", "javascript:alert(1)", "https://user:secret@books.example/a")) {
            assertThrows(IllegalArgumentException::class.java) { SecureDownloads.httpsUrl(value) }
        }
    }
    private class Connection(url: URL, private val code: Int, private val location: String? = null,
                             private val data: ByteArray = byteArrayOf(1, 2)) : HttpURLConnection(url) {
        var disconnected = false
        override fun connect() {}
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode() = code
        override fun getContentLengthLong() = data.size.toLong()
        override fun getInputStream() = ByteArrayInputStream(data)
        override fun getHeaderField(name: String) = if (name == "Location") location else null
    }
    @Test fun redirectRebuildsCookiesForNewOriginAndClosesConnections() {
        val directory = Files.createTempDirectory("vern-safe-download").toFile()
        try {
            val requests = mutableListOf<Connection>()
            SecureDownloads.download("https://source.example/book", java.io.File(directory, "book"), 10,
                headers = { if (it.host == "source.example") mapOf("Cookie" to "source-session=synthetic") else emptyMap() },
                openConnection = { url -> Connection(url, if (requests.isEmpty()) 302 else 200, "https://target.example/book").also { requests.add(it) } })
            assertEquals("source-session=synthetic", requests[0].getRequestProperty("Cookie"))
            assertNull(requests[1].getRequestProperty("Cookie"))
            assertTrue(requests.all { it.disconnected && !it.instanceFollowRedirects })
        } finally { directory.deleteRecursively() }
    }
    @Test fun failedAndOversizedDownloadsRemovePartialFilesAndDowngradesAreBlocked() {
        val directory = Files.createTempDirectory("vern-safe-download").toFile()
        try {
            for (connection in listOf(Connection(URL("https://source.example"), 500),
                Connection(URL("https://source.example"), 302, "http://target.example/book"),
                Connection(URL("https://source.example"), 200, data = ByteArray(20)))) {
                val target = java.io.File(directory, "book")
                target.writeText("previous partial")
                assertThrows(IllegalArgumentException::class.java) { SecureDownloads.download("https://source.example", target, 10, openConnection = { connection }) }
                assertFalse(target.exists())
                assertTrue(connection.disconnected)
            }
        } finally { directory.deleteRecursively() }
    }
}

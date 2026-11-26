package com.veritas.reader

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPOutputStream

class WebArticleExtractorTest {
    @Test fun extractsGzipHtmlWithoutLosingTheArticleBoundaries() {
        val page = """
            <html><head><title>A &amp; B</title></head>
            <body><nav>Navigation links</nav><article>
            <p>First paragraph.</p><p>Second paragraph.</p>
            </article><aside>Unrelated side panel</aside></body></html>
        """.trimIndent().toByteArray()
        val connection = FixtureConnection(
            contentType = "text/html; charset=utf-8",
            encoding = "gzip",
            body = { ByteArrayInputStream(gzip { write(page) }) }
        )

        val article = read(connection)

        assertEquals("A & B", article.title)
        assertEquals("First paragraph.\nSecond paragraph.", article.text)
        assertEquals("https://example.test/article", article.url)
        assertEquals("gzip", connection.getRequestProperty("Accept-Encoding"))
        assertEquals(1, connection.disconnectCount.get())
    }

    @Test fun rejectsAnOversizedDeclaredBodyBeforeOpeningTheStream() {
        val connection = FixtureConnection(length = ImportFilePolicy.byteLimit(ImportFormat.HTML) + 1L)

        val error = assertThrows(IllegalArgumentException::class.java) { read(connection) }

        assertTrue(error.message!!.contains("safety limit"))
        assertFalse(connection.openedBody)
        assertEquals(1, connection.disconnectCount.get())
    }

    @Test fun keepsReadingPlainTextWithAnExplicitCharset() {
        val bytes = "Une journée au café.\nDeuxième paragraphe.".toByteArray(Charsets.ISO_8859_1)
        val connection = FixtureConnection(contentType = "text/plain; charset=ISO-8859-1",
            encoding = "identity", length = bytes.size.toLong(), body = { ByteArrayInputStream(bytes) })

        val article = read(connection)

        assertEquals("Une journée au café.\nDeuxième paragraphe.", article.text)
        assertEquals("example.test", article.title)
        assertEquals(1, connection.disconnectCount.get())
    }

    @Test fun enforcesTheLimitAfterDecompressionWithoutDependingOnContentLength() {
        val block = ByteArray(32 * 1024) { 'a'.code.toByte() }
        val bytes = gzip {
            repeat((ImportFilePolicy.byteLimit(ImportFormat.HTML) / block.size).toInt()) { write(block) }
            write('b'.code)
        }
        val connection = FixtureConnection(encoding = "gzip", length = bytes.size.toLong(),
            body = { ByteArrayInputStream(bytes) })

        val error = assertThrows(IllegalArgumentException::class.java) { read(connection) }

        assertTrue(error.message!!.contains("safety limit"))
        assertTrue(connection.openedBody)
        assertEquals(1, connection.disconnectCount.get())
    }

    @Test fun rejectsIncompleteTransfersAndUnsupportedContent() {
        val partial = FixtureConnection(length = 1000L, body = {
            ByteArrayInputStream("<article><p>Incomplete text.</p></article>".toByteArray())
        })
        val binary = FixtureConnection(contentType = "image/png")

        val incompleteError = assertThrows(IllegalArgumentException::class.java) { read(partial) }
        val binaryError = assertThrows(IllegalArgumentException::class.java) { read(binary) }

        assertTrue(incompleteError.message!!.contains("full article"))
        assertTrue(binaryError.message!!.contains("did not return a readable web page"))
        assertFalse(binary.openedBody)
        assertEquals(1, partial.disconnectCount.get())
        assertEquals(1, binary.disconnectCount.get())
    }

    @Test fun reportsPerReadTimeoutAndHttpRestrictionWithDifferentMessages() {
        val timeout = FixtureConnection(body = { throw SocketTimeoutException("test socket timeout") })
        val forbidden = FixtureConnection(status = HttpURLConnection.HTTP_FORBIDDEN)

        val timeoutError = assertThrows(IllegalArgumentException::class.java) { read(timeout) }
        val forbiddenError = assertThrows(IllegalArgumentException::class.java) { read(forbidden) }

        assertTrue(timeoutError.message!!.contains("timed out"))
        assertTrue(forbiddenError.message!!.contains("HTTP 403"))
        assertFalse(forbidden.openedBody)
    }

    @Test fun wholeDownloadDeadlineDisconnectsABlockedRead() {
        val blocked = BlockingBody()
        val connection = FixtureConnection(body = { blocked }, onDisconnect = blocked::release)

        val error = assertThrows(IllegalArgumentException::class.java) { read(connection, timeoutMillis = 1_000L) }

        assertTrue(blocked.started.isCompleted)
        assertTrue(error.message!!.contains("took too long"))
        assertTrue(connection.disconnectCount.get() in 1..2)
    }

    @Test fun cancellingTheImportDisconnectsABlockedRead() = runBlocking {
        val blocked = BlockingBody()
        val connection = FixtureConnection(body = { blocked }, onDisconnect = blocked::release)
        val transfer = async { WebArticleExtractor.extract("example.test/article", 10_000L) { connection } }

        withTimeout(5_000L) { blocked.started.await() }
        transfer.cancelAndJoin()

        assertTrue(transfer.isCancelled)
        assertTrue(connection.disconnectCount.get() in 1..2)
    }

    private fun read(connection: FixtureConnection, timeoutMillis: Long = 10_000L): WebArticle = runBlocking {
        WebArticleExtractor.extract("example.test/article", timeoutMillis) { connection }
    }

    private fun gzip(writeBody: GZIPOutputStream.() -> Unit): ByteArray =
        ByteArrayOutputStream().apply { GZIPOutputStream(this).use(writeBody) }.toByteArray()

    private class FixtureConnection(
        private val status: Int = HTTP_OK,
        private val contentType: String = "text/html",
        private val encoding: String? = null,
        private val length: Long = -1L,
        private val body: () -> InputStream = { error("Response stream should not be opened") },
        private val onDisconnect: () -> Unit = {}
    ) : HttpURLConnection(URL("https://example.test/article")) {
        val disconnectCount = AtomicInteger()
        var openedBody = false
            private set

        override fun getResponseCode(): Int = status
        override fun getContentType(): String = contentType
        override fun getContentEncoding(): String? = encoding
        override fun getContentLengthLong(): Long = length
        override fun getInputStream(): InputStream {
            openedBody = true
            return body()
        }

        override fun disconnect() {
            disconnectCount.incrementAndGet()
            onDisconnect()
        }

        override fun connect() = Unit
        override fun usingProxy(): Boolean = false
    }

    private class BlockingBody : InputStream() {
        val started = CompletableDeferred<Unit>()
        private val released = CountDownLatch(1)

        override fun read(): Int {
            started.complete(Unit)
            check(released.await(5, TimeUnit.SECONDS)) { "Transfer did not release the response stream" }
            return -1
        }

        fun release() {
            released.countDown()
        }
    }
}

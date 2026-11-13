package com.veritas.reader

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.file.Files

class VoiceArchiveDownloadTest {
    private val url = "https://models.example.invalid/voice.tar.bz2"
    private class Reply(val status: Int,val data: String = "",val length: Long = data.length.toLong(),
        val range: String? = null,val tag: String? = "\"revision-1\"",val timeout: Boolean = false) : HttpURLConnection(URL("https://models.example.invalid/file")) {
        var closed = false
        override fun connect() = Unit
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getContentLengthLong() = length
        override fun getHeaderField(name: String): String? = when(name) { "ETag" -> tag; "Content-Range" -> range; else -> null }
        override fun getInputStream(): ByteArrayInputStream { if(timeout) throw SocketTimeoutException("sensitive URL"); return ByteArrayInputStream(data.toByteArray()) }
    }
    private fun partial() = Files.createTempDirectory("vern-voice-download-test").toFile().let { File(it,"voice.part") }

    @Test fun cancellingMidTransferDeletesPartialBytesAndValidator() = runTest {
        val file = partial()
        var cancelled = false
        val reply = Reply(200, "ABCD")
        try {
            val error = runCatching {
                VoiceArchiveDownload(open = { reply }).download(url, file, 100,
                    checkActive = { if (cancelled) throw kotlinx.coroutines.CancellationException() },
                    progress = { _, _, _ -> cancelled = true })
            }.exceptionOrNull()
            assertTrue(error is kotlinx.coroutines.CancellationException)
            assertTrue(reply.closed)
            assertFalse(file.exists())
            assertFalse(File(file.path + ".validator").exists())
        } finally { file.parentFile.deleteRecursively() }
    }

    @Test fun cancellingDuringRetryDeletesResumableDownload() = runTest {
        val file = partial()
        try {
            val error = runCatching {
                VoiceArchiveDownload(open = { Reply(200, "AB", 4) },
                    waitBeforeRetry = { throw kotlinx.coroutines.CancellationException() }).download(url, file, 100)
            }.exceptionOrNull()
            assertTrue(error is kotlinx.coroutines.CancellationException)
            assertFalse(file.exists())
            assertFalse(File(file.path + ".validator").exists())
        } finally { file.parentFile.deleteRecursively() }
    }

    @Test fun interruptedGetResumesOnlyWithValidatorAndCorrectContentRange() = runTest {
        val file = partial()
        val first = Reply(200,"AB",4)
        val second = Reply(206,"CD",2,"bytes 2-3/4")
        val replies = ArrayDeque(listOf(first,second))
        try {
            VoiceArchiveDownload(open = { replies.removeFirst() },waitBeforeRetry = {}).download(url,file,100)
            assertEquals("ABCD",file.readText())
            assertEquals("bytes=2-",second.getRequestProperty("Range"))
            assertEquals("\"revision-1\"",second.getRequestProperty("If-Range"))
            assertFalse(first.instanceFollowRedirects)
            assertTrue(first.closed && second.closed)
            assertFalse(File(file.path+".validator").exists())
        } finally { file.parentFile.deleteRecursively() }
    }

    @Test fun serverIgnoringRangeStartsFreshWithoutDuplicatingBytes() = runTest {
        val file = partial()
        val replies = ArrayDeque(listOf(Reply(200,"AB",4),Reply(200,"WXYZ")))
        try {
            VoiceArchiveDownload(open = { replies.removeFirst() },waitBeforeRetry = {}).download(url,file,100)
            assertEquals("WXYZ",file.readText())
        } finally { file.parentFile.deleteRecursively() }
    }

    @Test fun noValidatorMeansPartialBytesCannotBeResumed() = runTest {
        val file = partial()
        val second = Reply(200,"ABCD")
        val replies = ArrayDeque(listOf(Reply(200,"AB",4,tag = null),second))
        try {
            VoiceArchiveDownload(open = { replies.removeFirst() },waitBeforeRetry = {}).download(url,file,100)
            assertEquals("ABCD",file.readText()); assertNull(second.getRequestProperty("Range"))
        } finally { file.parentFile.deleteRecursively() }
    }

    @Test fun mismatchedResumeCannotProduceAMixedArchive() = runTest {
        val file = partial()
        val replies = ArrayDeque(listOf(Reply(200,"AB",4),Reply(206,"CD",2,"bytes 1-2/4")))
        try {
            val error = runCatching { VoiceArchiveDownload(open = { replies.removeFirst() },waitBeforeRetry = {}).download(url,file,100) }.exceptionOrNull()
            assertEquals(R.string.voice_download_integrity,(error as VoiceDownloadException).messageId)
            assertFalse(file.exists())
        } finally { file.parentFile.deleteRecursively() }
    }

    @Test fun permanentHttpFailureDoesNotRetryAndTimeoutErrorsAreBoundedAndSafe() = runTest {
        val file = partial()
        var calls = 0
        try {
            val permanent = runCatching { VoiceArchiveDownload(open = { calls++; Reply(404) },waitBeforeRetry = {}).download(url,file,100) }.exceptionOrNull()
            assertEquals(1,calls)
            assertEquals(R.string.voice_download_source,(permanent as VoiceDownloadException).messageId)
            calls = 0
            val timedOut = runCatching { VoiceArchiveDownload(open = { calls++; Reply(200,length = 4,timeout = true) },waitBeforeRetry = {}).download(url,file,100) }.exceptionOrNull()
            assertEquals(3,calls)
            assertEquals(R.string.voice_download_timeout,(timedOut as VoiceDownloadException).messageId)
            assertNull(timedOut.message); assertNull(timedOut.cause)
        } finally { file.parentFile.deleteRecursively() }
    }

    @Test fun changedOrOversizedPayloadCannotPassIntegrityVerification() = runTest {
        val file = partial()
        try {
            val badHash = runCatching { VoiceArchiveDownload(open = { Reply(200,"ABC") }).download(url,file,100,"0".repeat(64)) }.exceptionOrNull()
            assertEquals(R.string.voice_download_integrity,(badHash as VoiceDownloadException).messageId)
            assertFalse(file.exists())
            val oversized = runCatching { VoiceArchiveDownload(open = { Reply(200,"ABC",1000) }).download(url,file,100) }.exceptionOrNull()
            assertEquals(R.string.voice_download_integrity,(oversized as VoiceDownloadException).messageId)
        } finally { file.parentFile.deleteRecursively() }
    }
}

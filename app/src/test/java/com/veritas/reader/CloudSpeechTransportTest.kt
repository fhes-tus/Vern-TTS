package com.veritas.reader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class CloudSpeechTransportTest {
    private class Reply(val status: Int = 200,val bytes: ByteArray = byteArrayOf(73,68,51)) : HttpURLConnection(URL("https://test.invalid/speech")) {
        val sent = ByteArrayOutputStream()
        var closed = false
        var responseRead = false
        var failWrite = false
        var onOutput: () -> Unit = {}
        override fun connect() = Unit
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getOutputStream(): ByteArrayOutputStream {
            if(failWrite) throw IOException("ambiguous write")
            onOutput()
            return sent
        }
        override fun getInputStream(): ByteArrayInputStream { responseRead = true; return ByteArrayInputStream(bytes) }
    }

    @Test fun credentialsStayInProviderHeadersAndSuccessfulBodyIsReturned() = runTest {
        val reply = Reply()
        var submissions = 0
        val transport = CloudSpeechTransport(open = { reply },onSubmission = { _,chars -> submissions += chars })
        assertArrayEquals(byteArrayOf(73,68,51),transport.fetch(CloudVoiceConfig(),"你好","fake-test-key"))
        assertEquals("fake-test-key",reply.getRequestProperty("xi-api-key"))
        assertNull(reply.getRequestProperty("Authorization"))
        assertFalse(reply.instanceFollowRedirects)
        assertEquals("POST",reply.requestMethod)
        assertEquals(2,submissions)
        assertTrue(reply.sent.toString("UTF-8").contains("你好"))
        assertFalse(reply.sent.toString("UTF-8").contains("fake-test-key"))
        assertTrue(reply.closed)
    }

    @Test fun redirectsAndAuthenticationFailuresNeverReadOrExposeResponseBodies() = runTest {
        for(code in listOf(301,302,307,308,401,403)) {
            val reply = Reply(code,"sensitive error echo".toByteArray())
            val transport = CloudSpeechTransport(open = { reply })
            val error = runCatching { transport.fetch(CloudVoiceConfig(),"Hello","fake-test-key") }.exceptionOrNull()
            assertTrue(error is CloudSpeechException)
            assertFalse(reply.responseRead)
            assertFalse(reply.instanceFollowRedirects)
            assertTrue(reply.closed)
            assertNull(error?.message)
        }
    }

    @Test fun ambiguousFailureIsNeverAutomaticallyResubmitted() = runTest {
        var opens = 0
        val reply = Reply().apply { failWrite = true }
        val transport = CloudSpeechTransport(open = { opens++; reply })
        assertTrue(runCatching { transport.fetch(CloudVoiceConfig(retries = 2),"Hello","key") }.exceptionOrNull() is IOException)
        assertEquals(1,opens)
        assertTrue(reply.closed)
    }

    @Test fun rateLimitRetriesAreOptInAndBounded() = runTest {
        var opens = 0
        val transport = CloudSpeechTransport(open = { opens++; if(opens == 1) Reply(429) else Reply() })
        transport.fetch(CloudVoiceConfig(retries = 1),"Hello","key")
        assertEquals(2,opens)
        opens = 0
        val noRetry = CloudSpeechTransport(open = { opens++; Reply(429) })
        assertTrue(runCatching { noRetry.fetch(CloudVoiceConfig(),"Hello","key") }.exceptionOrNull() is CloudSpeechException)
        assertEquals(1,opens)
    }

    @Test fun cancelledTransportCannotSubmitAnotherRequest() = runTest {
        var opened = false
        val transport = CloudSpeechTransport(open = { opened = true; Reply() })
        transport.cancel()
        assertTrue(runCatching { transport.fetch(CloudVoiceConfig(),"Hello","key") }.exceptionOrNull() is CancellationException)
        assertFalse(opened)
    }

    @Test fun stopWhileOpeningConnectionCannotSubmitText() = runTest {
        val reply = Reply()
        var submissions = 0
        val transport = CloudSpeechTransport(open = { reply },onSubmission = { _, _ -> submissions++ })
        reply.onOutput = { transport.cancel() }
        assertTrue(runCatching { transport.fetch(CloudVoiceConfig(),"private passage","key") }.exceptionOrNull() is CancellationException)
        assertEquals(0,reply.sent.size())
        assertEquals(0,submissions)
        assertTrue(reply.closed)
    }
}

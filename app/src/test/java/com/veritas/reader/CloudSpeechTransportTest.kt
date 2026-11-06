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
    private class Reply(val status: Int = 200,val bytes: ByteArray = byteArrayOf(73,68,51,4,0,0,0,0,0,0),
        val mime: String? = null,val advertisedLength: Long = -1) : HttpURLConnection(URL("https://test.invalid/speech")) {
        val sent = ByteArrayOutputStream()
        var closed = false
        var responseRead = false
        var failWrite = false
        var onOutput: () -> Unit = {}
        override fun connect() = Unit
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getContentType() = mime
        override fun getContentLengthLong() = advertisedLength
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
        assertArrayEquals(reply.bytes,transport.fetch(CloudVoiceConfig(),"你好","fake-test-key"))
        assertEquals("fake-test-key",reply.getRequestProperty("xi-api-key"))
        assertNull(reply.getRequestProperty("Authorization"))
        assertFalse(reply.instanceFollowRedirects)
        assertFalse(reply.useCaches)
        assertEquals("no-store",reply.getRequestProperty("Cache-Control"))
        assertEquals("POST",reply.requestMethod)
        assertEquals(2,submissions)
        assertTrue(reply.sent.toString("UTF-8").contains("你好"))
        assertFalse(reply.sent.toString("UTF-8").contains("fake-test-key"))
        assertTrue(reply.closed)
    }

    @Test fun azureCatalogAndSpeechStayOnTheResourceRegionAndEscapePassageMarkup() = runTest {
        val config = CloudVoiceConfig(provider = CloudVoiceProvider.AZURE, region = "westus2")
        val catalog = Reply(bytes = """[{"ShortName":"en-US-JennyNeural","Locale":"en-US","DisplayName":"Jenny"}]""".toByteArray(),
            mime = "application/json")
        val speech = Reply(mime = "audio/mpeg")
        val urls = mutableListOf<String>()
        var submissions = 0
        val transport = CloudSpeechTransport(open = { url -> urls += url.toString(); if (urls.size == 1) catalog else speech },
            onSubmission = { _, _ -> submissions++ })
        assertEquals(listOf(CatalogVoice("en-US-JennyNeural", "en-US", "Jenny")),
            transport.listVoices(config, "azure-test-key"))
        assertEquals(0, submissions)
        assertArrayEquals(speech.bytes, transport.fetch(config, "Fish & <prosody> 🐟\u0000", "azure-test-key"))
        assertEquals(listOf("https://westus2.tts.speech.microsoft.com/cognitiveservices/voices/list",
            "https://westus2.tts.speech.microsoft.com/cognitiveservices/v1"), urls)
        assertEquals("GET", catalog.requestMethod)
        assertEquals(0, catalog.sent.size())
        assertEquals("application/ssml+xml", speech.getRequestProperty("Content-Type"))
        assertEquals("audio-24khz-96kbitrate-mono-mp3", speech.getRequestProperty("X-Microsoft-OutputFormat"))
        for (reply in listOf(catalog, speech)) {
            assertEquals("azure-test-key", reply.getRequestProperty("Ocp-Apim-Subscription-Key"))
            assertNull(reply.getRequestProperty("Authorization"))
            assertFalse(reply.instanceFollowRedirects)
        }
        assertTrue(speech.sent.toString("UTF-8").contains("Fish &amp; &lt;prosody&gt; 🐟</voice>"))
        assertFalse(speech.sent.toString("UTF-8").contains("azure-test-key"))
        assertEquals(1, submissions)
    }

    @Test fun googleCatalogSelectsLanguageAndSynthesisDecodesBase64Mp3() = runTest {
        val config = CloudVoiceConfig(provider = CloudVoiceProvider.GOOGLE_CLOUD, language = "fr-FR", voice = "fr-FR-Neural2-A")
        val mp3 = Reply().bytes
        val encoded = java.util.Base64.getEncoder().encodeToString(mp3)
        val speech = Reply(bytes = """{"audioContent":"$encoded"}""".toByteArray(), mime = "application/json; charset=UTF-8")
        val catalog = Reply(bytes = """{"voices":[{"name":"fr-FR-Neural2-A","languageCodes":["fr-FR","fr-CA"]},
            {"name":"invalid id","languageCodes":["fr-FR"]}]}""".toByteArray(), mime = "application/json")
        val urls = mutableListOf<String>()
        val transport = CloudSpeechTransport(open = { url -> urls += url.toString(); if (urls.size == 1) catalog else speech })
        assertEquals(listOf("fr-CA", "fr-FR"), transport.listVoices(config, "google-test-key").map { it.language })
        assertArrayEquals(mp3, transport.fetch(config, "Bonjour", "google-test-key"))
        assertEquals(listOf("https://texttospeech.googleapis.com/v1/voices",
            "https://texttospeech.googleapis.com/v1/text:synthesize"), urls)
        for (reply in listOf(catalog, speech)) {
            assertEquals("google-test-key", reply.getRequestProperty("X-Goog-Api-Key"))
            assertNull(reply.getRequestProperty("Authorization"))
        }
        val body = org.json.JSONObject(speech.sent.toString("UTF-8"))
        assertEquals("Bonjour", body.getJSONObject("input").getString("text"))
        assertEquals("fr-FR", body.getJSONObject("voice").getString("languageCode"))
        assertEquals("fr-FR-Neural2-A", body.getJSONObject("voice").getString("name"))
        assertEquals("MP3", body.getJSONObject("audioConfig").getString("audioEncoding"))
        assertFalse(body.toString().contains("google-test-key"))
    }

    @Test fun cloudCatalogErrorsNeverReadRedirectOrCredentialErrorBodies() = runTest {
        val config = CloudVoiceConfig(provider = CloudVoiceProvider.GOOGLE_CLOUD)
        for (code in listOf(302, 400, 401, 403)) {
            val reply = Reply(status = code, bytes = "sensitive echo".toByteArray())
            val error = runCatching { CloudSpeechTransport(open = { reply }).listVoices(config, "key") }
                .exceptionOrNull() as CloudSpeechException
            if (code != 302) assertEquals(R.string.cloud_error_key, error.messageId)
            assertFalse(reply.responseRead)
            assertFalse(reply.instanceFollowRedirects)
            assertNull(error.message)
        }
        val malformed = Reply(bytes = """{"error":"echo"}""".toByteArray(), mime = "application/json")
        val error = runCatching { CloudSpeechTransport(open = { malformed }).listVoices(config, "key") }
            .exceptionOrNull() as CloudSpeechException
        assertEquals(R.string.cloud_catalog_error, error.messageId)
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
        val error = runCatching { transport.fetch(CloudVoiceConfig(retries = 2),"Hello","key") }.exceptionOrNull()
        assertEquals(R.string.cloud_error_connection,(error as CloudSpeechException).messageId)
        assertNull(error.message)
        assertNull(error.cause)
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

    @Test fun invalidCredentialsCannotOpenAConnectionOrInjectHeaders() = runTest {
        var opens = 0
        val transport = CloudSpeechTransport(open = { opens++; Reply() })
        for(key in listOf("key\r\nX-Injected: yes","bad key","bad\u0000key","")) {
            val error = runCatching { transport.fetch(CloudVoiceConfig(),"private",key) }.exceptionOrNull()
            assertEquals(R.string.cloud_error_key,(error as CloudSpeechException).messageId)
        }
        assertEquals(0,opens)
    }

    @Test fun successStatusWithProxyJsonHtmlOrOversizedBodyIsRejected() = runTest {
        for(reply in listOf(Reply(bytes = "private echo".toByteArray(),mime = "application/problem+json"),
            Reply(mime = "text/html; charset=utf-8"),Reply(advertisedLength = CloudSpeechTransport.MAX_AUDIO_BYTES.toLong()+1))) {
            val error = runCatching { CloudSpeechTransport(open = { reply }).fetch(CloudVoiceConfig(),"Hello","key") }.exceptionOrNull()
            assertEquals(R.string.cloud_error_audio,(error as CloudSpeechException).messageId)
            assertFalse(reply.responseRead)
            assertTrue(reply.closed)
        }
        for(bytes in listOf("{\"error\":\"private echo\"}".toByteArray(),ByteArray(CloudSpeechTransport.MAX_AUDIO_BYTES+1))) {
            val error = runCatching { CloudSpeechTransport(open = { Reply(bytes = bytes) }).fetch(CloudVoiceConfig(),"Hello","key") }.exceptionOrNull()
            assertEquals(R.string.cloud_error_audio,(error as CloudSpeechException).messageId)
            assertNull(error.message)
        }
    }

    @Test fun minimaxSemanticFailuresAreSafeEvenWithHttp200() = runTest {
        for((code,message) in listOf(1004 to R.string.cloud_error_key,2049 to R.string.cloud_error_key,
            1008 to R.string.cloud_error_quota,2053 to R.string.cloud_error_quota,2069 to R.string.cloud_error_provider)) {
            val reply = Reply(bytes = """{"base_resp":{"status_code":$code,"status_msg":"private-key-echo"}}""".toByteArray(),mime = "application/json")
            val error = runCatching { CloudSpeechTransport(open = { reply }).fetch(CloudVoiceConfig(provider = CloudVoiceProvider.MINIMAX),"Hello","key") }.exceptionOrNull()
            assertEquals(message,(error as CloudSpeechException).messageId)
            assertNull(error.message); assertNull(error.cause)
        }
    }

    @Test fun totalDeadlineDisconnectsAndPreventsLateSubmission() = runTest {
        lateinit var expire: () -> Unit
        var deadlineClosed = false
        var submissions = 0
        val reply = Reply()
        val transport = CloudSpeechTransport(open = { reply },onSubmission = { _, _ -> submissions++ },
            scheduleTimeout = { millis,callback ->
                assertEquals(5000L,millis); expire = callback; AutoCloseable { deadlineClosed = true }
            })
        reply.onOutput = { expire() }
        val error = runCatching { transport.fetch(CloudVoiceConfig(timeoutSeconds = 5),"private","key") }.exceptionOrNull()
        assertEquals(R.string.cloud_error_connection,(error as CloudSpeechException).messageId)
        assertEquals(0,reply.sent.size()); assertEquals(0,submissions)
        assertTrue(reply.closed); assertTrue(deadlineClosed)
    }
}

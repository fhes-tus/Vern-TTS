package com.veritas.reader

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class CloudSecurityTest {
    @Test fun credentialsAreNormalizedWithoutAcceptingHeaderInjection() {
        assertEquals("key-ABC_123",CloudCredentialRules.normalize("  key-ABC_123\n",true))
        assertEquals("",CloudCredentialRules.normalize("",false))
        for(value in listOf("", "bad key", "key\r\nx:foo", "key\u0000hidden", "clé", "x".repeat(8193))) {
            val error = assertThrows(CloudSpeechException::class.java) { CloudCredentialRules.normalize(value,true) }
            assertEquals(R.string.cloud_error_key,error.messageId)
            assertNull(error.message); assertNull(error.cause)
        }
    }

    @Test fun crashDiagnosticsRemoveCredentialHeadersJsonTokensAndPrivateUrls() {
        val input = """
            IOException: HTTP 401 at https://private.example/speech?api_key=opaque-query
            Authorization: Bearer opaque-auth
            xi-api-key: opaque-eleven
            X-API-Key=opaque-header
            {"api_key":"opaque-json","secret":"opaque-secret","x-api-key":"opaque-x-json"}
            bearer opaque-bearer
            sk_test_1234567890
                at com.veritas.reader.CloudSpeechTransport.fetch(CloudSpeechTransport.kt:42)
        """.trimIndent()
        val result = DiagnosticRedactor.redact(input)
        for(secret in listOf("private.example","opaque-query","opaque-auth","opaque-eleven","opaque-header","opaque-json","opaque-secret","opaque-x-json","opaque-bearer","sk_test_1234567890"))
            assertFalse(secret,result.contains(secret))
        assertTrue(result.contains("HTTP 401"))
        assertTrue(result.contains("CloudSpeechTransport.kt:42"))
        assertTrue(result.contains("[redacted"))
        assertEquals(result,DiagnosticRedactor.redact(result))
    }

    @Test fun abandonedAudioIsRemovedOnceWithoutDeletingActiveOrRetainedAudio() {
        val root = Files.createTempDirectory("vern-cloud-cache-test").toFile()
        try {
            val dir = java.io.File(root,"cloud_voice").apply { mkdirs() }
            val abandoned = java.io.File(dir,"temp-abcd-1234.audio").apply { writeText("old passage") }
            val partial = java.io.File(dir,"abcd1234.part").apply { writeText("partial passage") }
            val retained = java.io.File(dir,"abcd1234.audio").apply { writeText("retained passage") }
            val unrelated = java.io.File(dir,"other.txt").apply { writeText("other") }
            assertEquals(dir,CloudAudioCache.directory(root))
            assertFalse(abandoned.exists()); assertFalse(partial.exists())
            assertTrue(retained.exists()); assertTrue(unrelated.exists())
            val current = java.io.File(dir,"temp-abcd-5678.audio").apply { writeText("current passage") }
            CloudAudioCache.directory(root)
            assertTrue(current.exists())
        } finally { root.deleteRecursively() }
    }
}

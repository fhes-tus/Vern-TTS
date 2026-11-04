package com.veritas.reader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** No automatic replay of an ambiguous POST failure. Redirects never receive credentials. */
internal class CloudSpeechTransport(
    private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val onSubmission: (CloudVoiceProvider,Int) -> Unit = { _, _ -> }
) {
    @Volatile private var connection: HttpURLConnection? = null
    @Volatile private var cancelled = false
    fun cancel() { cancelled = true; connection?.disconnect() }
    private fun checkActive() { if(cancelled) throw CancellationException() }

    suspend fun fetch(config: CloudVoiceConfig,text: String,credential: String): ByteArray {
        val request = CloudSpeechProtocol.request(config,text)
        var attempt = 0
        while(true) {
            checkActive(); currentCoroutineContext().ensureActive()
            val conn = open(URL(request.url))
            connection = conn
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = config.timeoutSeconds*1000; conn.readTimeout = config.timeoutSeconds*1000
                conn.requestMethod = "POST"; conn.doOutput = true
                conn.setRequestProperty("Content-Type","application/json")
                conn.setRequestProperty("Accept","audio/*, application/octet-stream, application/json")
                if(credential.isNotBlank()) conn.setRequestProperty(if(config.provider == CloudVoiceProvider.ELEVENLABS) "xi-api-key" else "Authorization",
                    if(config.provider == CloudVoiceProvider.ELEVENLABS) credential else "Bearer $credential")
                request.headers.forEach { (name,value) -> conn.setRequestProperty(name,value) }
                val bytes = request.body.toString().toByteArray(Charsets.UTF_8)
                conn.setFixedLengthStreamingMode(bytes.size)
                checkActive()
                conn.outputStream.use {
                    checkActive(); currentCoroutineContext().ensureActive()
                    onSubmission(config.provider,text.length)
                    it.write(bytes)
                }
                val status = conn.responseCode
                if(status == 429 && attempt++ < config.retries) { delay(attempt*1500L); continue }
                if(status !in 200..299) throw CloudSpeechException(when(status) {
                    401,403 -> R.string.cloud_error_key
                    402,429 -> R.string.cloud_error_quota
                    else -> R.string.cloud_error_provider
                })
                val limit = if(config.provider == CloudVoiceProvider.MINIMAX) MAX_AUDIO_BYTES*2+65536 else MAX_AUDIO_BYTES
                val output = ByteArrayOutputStream()
                conn.inputStream.use { input ->
                    val buffer = ByteArray(16384)
                    while(true) {
                        checkActive(); currentCoroutineContext().ensureActive()
                        val n = input.read(buffer); if(n < 0) break
                        if(output.size()+n > limit) throw CloudSpeechException(R.string.cloud_error_audio)
                        output.write(buffer,0,n)
                    }
                }
                val audio = runCatching { CloudSpeechProtocol.audio(config.provider,output.toByteArray()) }
                    .getOrElse { throw CloudSpeechException(R.string.cloud_error_provider) }
                if(audio.isEmpty() || audio.size > MAX_AUDIO_BYTES) throw CloudSpeechException(R.string.cloud_error_audio)
                checkActive()
                return audio
            } finally { conn.disconnect(); if(connection === conn) connection = null }
        }
    }
    companion object { const val MAX_AUDIO_BYTES = 16*1024*1024 }
}

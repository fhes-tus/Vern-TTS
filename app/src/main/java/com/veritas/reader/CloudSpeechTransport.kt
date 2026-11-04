package com.veritas.reader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** No automatic replay of an ambiguous POST failure. Redirects never receive credentials. */
internal class CloudSpeechTransport(
    private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val onSubmission: (CloudVoiceProvider,Int) -> Unit = { _, _ -> },
    private val scheduleTimeout: (Long, () -> Unit) -> AutoCloseable = CloudSpeechTimeouts::schedule
) {
    @Volatile private var connection: HttpURLConnection? = null
    @Volatile private var cancelled = false
    private val expired = AtomicBoolean(false)
    fun cancel() { cancelled = true; connection?.disconnect() }
    private fun checkActive() {
        if(cancelled) throw CancellationException()
        if(expired.get()) throw CloudSpeechException(R.string.cloud_error_connection)
    }

    suspend fun fetch(config: CloudVoiceConfig,text: String,credential: String): ByteArray {
        val request = CloudSpeechProtocol.request(config,text)
        val token = CloudCredentialRules.normalize(credential,config.provider != CloudVoiceProvider.CUSTOM)
        val deadline = scheduleTimeout(config.timeoutSeconds * 1000L) {
            expired.set(true)
            runCatching { connection?.disconnect() }
        }
        var attempt = 0
        try {
        while(true) {
            checkActive(); currentCoroutineContext().ensureActive()
            val conn = open(URL(request.url))
            connection = conn
            try {
                conn.instanceFollowRedirects = false
                conn.useCaches = false
                conn.connectTimeout = config.timeoutSeconds*1000; conn.readTimeout = config.timeoutSeconds*1000
                conn.requestMethod = "POST"; conn.doOutput = true
                conn.setRequestProperty("Content-Type","application/json")
                conn.setRequestProperty("Cache-Control","no-store")
                conn.setRequestProperty("Accept","audio/*, application/octet-stream, application/json")
                if(token.isNotEmpty()) conn.setRequestProperty(if(config.provider == CloudVoiceProvider.ELEVENLABS) "xi-api-key" else "Authorization",
                    if(config.provider == CloudVoiceProvider.ELEVENLABS) token else "Bearer $token")
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
                val mime = conn.contentType?.substringBefore(';')?.trim()?.lowercase(java.util.Locale.ROOT).orEmpty()
                if(conn.contentLengthLong > limit || mime.startsWith("text/") ||
                    (config.provider != CloudVoiceProvider.MINIMAX && (mime == "application/json" || mime.endsWith("+json"))))
                    throw CloudSpeechException(R.string.cloud_error_audio)
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
                val audio = try { CloudSpeechProtocol.audio(config.provider,output.toByteArray()) }
                    catch(error: CloudSpeechException) { throw error }
                    catch(_: Exception) { throw CloudSpeechException(R.string.cloud_error_provider) }
                if(audio.size > MAX_AUDIO_BYTES || !CloudSpeechProtocol.isAudio(config.provider,audio))
                    throw CloudSpeechException(R.string.cloud_error_audio)
                checkActive()
                return audio
            } finally { conn.disconnect(); if(connection === conn) connection = null }
        }
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(error: CloudSpeechException) { throw error }
        catch(_: Exception) { throw CloudSpeechException(R.string.cloud_error_connection) }
        finally { deadline.close() }
    }
    companion object { const val MAX_AUDIO_BYTES = 16*1024*1024 }
}

private object CloudSpeechTimeouts {
    private val timer = ScheduledThreadPoolExecutor(1) { task ->
        Thread(task,"VernSpeechTimeout").apply { isDaemon = true }
    }.apply { removeOnCancelPolicy = true }
    fun schedule(milliseconds: Long, expire: () -> Unit): AutoCloseable {
        val future = timer.schedule({ expire() },milliseconds,TimeUnit.MILLISECONDS)
        return AutoCloseable { future.cancel(false) }
    }
}

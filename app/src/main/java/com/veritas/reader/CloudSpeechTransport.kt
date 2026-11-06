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
        val jsonAudio = config.provider in setOf(CloudVoiceProvider.MINIMAX, CloudVoiceProvider.GOOGLE_CLOUD)
        val limit = if (jsonAudio) MAX_AUDIO_BYTES * 2 + 65536 else MAX_AUDIO_BYTES
        val data = exchange(config, request, credential, limit, jsonAudio, text.length)
        val audio = try { CloudSpeechProtocol.audio(config.provider,data) }
            catch (error: CloudSpeechException) { throw error }
            catch (_: Exception) {
                throw CloudSpeechException(if (config.provider == CloudVoiceProvider.GOOGLE_CLOUD)
                    R.string.cloud_error_audio else R.string.cloud_error_provider)
            }
        if (audio.size > MAX_AUDIO_BYTES || !CloudSpeechProtocol.isAudio(config.provider,audio))
            throw CloudSpeechException(R.string.cloud_error_audio)
        checkActive()
        return audio
    }

    suspend fun listVoices(config: CloudVoiceConfig, credential: String): List<CatalogVoice> {
        val request = CloudVoiceCatalog.request(config)
        val data = exchange(config, request, credential, 2 * 1024 * 1024, true, 0)
        val voices = runCatching { CloudVoiceCatalog.parse(config.provider, data) }.getOrNull()
        checkActive()
        return voices?.takeIf { it.isNotEmpty() } ?: throw CloudSpeechException(R.string.cloud_catalog_error)
    }

    private suspend fun exchange(config: CloudVoiceConfig, request: CloudSpeechProtocol.Request,
        credential: String, limit: Int, acceptJson: Boolean, submittedChars: Int): ByteArray {
        val token = CloudCredentialRules.normalize(credential,config.provider != CloudVoiceProvider.CUSTOM)
        val bytes = request.body?.toByteArray(Charsets.UTF_8)
        val responseError = if (bytes == null) R.string.cloud_catalog_error else R.string.cloud_error_audio
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
                conn.requestMethod = if (bytes == null) "GET" else "POST"
                conn.setRequestProperty("Cache-Control","no-store")
                conn.setRequestProperty("Accept", if (acceptJson) "application/json" else "audio/*, application/octet-stream")
                if (token.isNotEmpty()) when (config.provider) {
                    CloudVoiceProvider.ELEVENLABS -> conn.setRequestProperty("xi-api-key", token)
                    CloudVoiceProvider.AZURE -> conn.setRequestProperty("Ocp-Apim-Subscription-Key", token)
                    CloudVoiceProvider.GOOGLE_CLOUD -> conn.setRequestProperty("X-Goog-Api-Key", token)
                    else -> conn.setRequestProperty("Authorization", "Bearer $token")
                }
                request.headers.forEach { (name,value) -> conn.setRequestProperty(name,value) }
                checkActive()
                if (bytes != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", request.contentType)
                    conn.setFixedLengthStreamingMode(bytes.size)
                    conn.outputStream.use {
                        checkActive(); currentCoroutineContext().ensureActive()
                        onSubmission(config.provider, submittedChars)
                        it.write(bytes)
                    }
                }
                val status = conn.responseCode
                if(status == 429 && attempt++ < config.retries) { delay(attempt*1500L); continue }
                if(status !in 200..299) throw CloudSpeechException(when(status) {
                    401,403 -> R.string.cloud_error_key
                    400 -> if (bytes == null && config.provider == CloudVoiceProvider.GOOGLE_CLOUD)
                        R.string.cloud_error_key else R.string.cloud_error_provider
                    402,429 -> R.string.cloud_error_quota
                    else -> R.string.cloud_error_provider
                })
                val mime = conn.contentType?.substringBefore(';')?.trim()?.lowercase(java.util.Locale.ROOT).orEmpty()
                if(conn.contentLengthLong > limit || mime.startsWith("text/") ||
                    (!acceptJson && (mime == "application/json" || mime.endsWith("+json"))))
                    throw CloudSpeechException(responseError)
                val output = ByteArrayOutputStream()
                conn.inputStream.use { input ->
                    val buffer = ByteArray(16384)
                    while(true) {
                        checkActive(); currentCoroutineContext().ensureActive()
                        val n = input.read(buffer); if(n < 0) break
                        if(output.size()+n > limit) throw CloudSpeechException(responseError)
                        output.write(buffer,0,n)
                    }
                }
                checkActive()
                return output.toByteArray()
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

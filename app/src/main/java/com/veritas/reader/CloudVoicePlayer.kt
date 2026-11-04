package com.veritas.reader

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.*
import java.io.File

internal class CloudSpeechException(val messageId: Int) : Exception()

/** One bounded passage at a time. Audio is buffered before playback; no word timestamps are invented. */
internal class CloudVoicePlayer(private val context: Context, private val scope: CoroutineScope,
    private val fetchAudio: (suspend (CloudVoiceConfig, String) -> File)? = null) {
    private var job: Job? = null
    private var player: MediaPlayer? = null
    private var downloader: Download? = null
    private var generation = 0
    private var temporary: File? = null
    private var playbackFile: File? = null
    private var volume = 1f

    fun setVolume(value: Float) {
        volume = value.coerceIn(0f,1f)
        player?.let { runCatching { it.setVolume(volume,volume) } }
    }

    fun stop() {
        generation++
        downloader?.cancel(); downloader = null
        job?.cancel(); job = null
        player?.let { runCatching { it.reset() }; it.release() }; player = null
        temporary?.delete(); temporary = null
        playbackFile = null
    }

    fun play(config: CloudVoiceConfig, text: String, rate: Float, pitch: Float,
        onStarted: () -> Unit, onFinished: () -> Unit, onError: (Int) -> Unit,
        credentialOverride: String? = null) {
        stop()
        val token = generation
        val download = Download(context)
        downloader = download
        job = scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    fetchAudio?.invoke(config,text) ?: download.fetch(config,text,
                        credentialOverride ?: ApiCredentialStore.get(context,config.credentialName))
                }
                ensureActive()
                if (token != generation) { if (!config.cacheAudio) file.delete(); return@launch }
                if (!config.cacheAudio) temporary = file
                playbackFile = file
                val media = MediaPlayer()
                player = media
                media.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                media.setOnPreparedListener {
                    if (token != generation) return@setOnPreparedListener
                    runCatching {
                        it.playbackParams = PlaybackParams().setSpeed(rate.coerceIn(0.5f,3f)).setPitch(pitch.coerceIn(0.5f,2f))
                        it.setVolume(volume,volume)
                        it.start()
                        onStarted()
                    }.onFailure { stop(); onError(R.string.cloud_error_audio) }
                }
                media.setOnCompletionListener { if (token == generation) { stop(); onFinished() } }
                media.setOnErrorListener { _, what, _ ->
                    if (token == generation) {
                        if(what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) stop() else rejectAudio()
                        onError(R.string.cloud_error_audio)
                    }
                    true
                }
                media.setDataSource(file.absolutePath)
                media.prepareAsync()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (token == generation) { rejectAudio(); onError((error as? CloudSpeechException)?.messageId ?: R.string.cloud_error_connection) }
            }
        }
    }

    private fun rejectAudio() {
        val rejected = playbackFile
        stop()
        rejected?.delete()
    }

    private class Download(private val context: Context) {
        private val transport = CloudSpeechTransport(onSubmission = { provider,chars -> CloudVoiceStore.recordSubmission(context,provider,chars) })
        @Volatile private var cancelled = false
        private val files = CloudAudioFileOwner()
        fun cancel() {
            cancelled = true; transport.cancel()
            files.cancel()
        }
        private fun checkActive() { if(cancelled) throw CancellationException() }

        suspend fun fetch(config: CloudVoiceConfig, text: String, credential: String): File {
            config.checked()
            val cache = cacheDirectory(context)
            val key = CloudSpeechProtocol.cacheKey(config,text,credential)
            val target = File(cache,"$key.audio")
            if (config.cacheAudio && target.isFile) {
                val cached = if(target.length() in 1..MAX_AUDIO_BYTES.toLong()) runCatching { target.readBytes() }.getOrNull() else null
                if(cached != null && CloudSpeechProtocol.isAudio(config.provider,cached)) {
                    target.setLastModified(System.currentTimeMillis()); return target
                }
                target.delete()
            }
            val network = context.getSystemService(ConnectivityManager::class.java)
            val capabilities = network.getNetworkCapabilities(network.activeNetwork)
            if (config.wifiOnly && capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true)
                throw CloudSpeechException(R.string.cloud_error_wifi)
            if (config.provider != CloudVoiceProvider.CUSTOM && credential.isBlank()) throw CloudSpeechException(R.string.cloud_error_key)
            val audio = transport.fetch(config,text,credential)
            checkActive()
            val result = try { files.publish(cache,key,audio,config.cacheAudio) }
                catch(cancelled: CancellationException) { throw cancelled }
                catch(_: Exception) { throw CloudSpeechException(R.string.cloud_error_storage) }
            trimCache(context,result)
            return result
        }
    }

    companion object {
        private const val MAX_AUDIO_BYTES = 16 * 1024 * 1024
        private fun cacheDirectory(context: Context) = CloudAudioCache.directory(context.cacheDir)
        fun cacheBytes(context: Context): Long = cacheDirectory(context).listFiles().orEmpty().sumOf { it.length() }
        fun clearCache(context: Context) { cacheDirectory(context).listFiles().orEmpty().forEach { it.delete() } }
        @Synchronized private fun trimCache(context: Context, keep: File) {
            val files = cacheDirectory(context).listFiles().orEmpty().sortedBy { it.lastModified() }
            var total = files.sumOf { it.length() }
            for (file in files) if (file != keep && total > 64L*1024*1024) {
                val bytes = file.length()
                if (file.delete()) total -= bytes
            }
        }
    }
}

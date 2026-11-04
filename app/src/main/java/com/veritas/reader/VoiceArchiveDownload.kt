package com.veritas.reader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.security.MessageDigest

internal class VoiceDownloadException(val messageId: Int, val status: Int = 0) : IOException()

/** Public model GETs only. A validator binds every resumed byte to the original representation. */
internal class VoiceArchiveDownload(
    private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val waitBeforeRetry: suspend (Long) -> Unit = { delay(it) }
) {
    suspend fun download(url: String, partial: File, limit: Long, checksum: String? = null,
        checkActive: () -> Unit = {}, progress: (Long, Long, Boolean) -> Unit = { _, _, _ -> }) {
        partial.parentFile?.mkdirs()
        val metadata = File(partial.path+".validator")
        var last: Exception? = null
        repeat(3) { attempt ->
            checkActive()
            try {
                transfer(url,partial,metadata,limit,checkActive) { bytes,total -> progress(bytes,total,false) }
                if(checksum != null) {
                    val digest = MessageDigest.getInstance("SHA-256")
                    partial.inputStream().use { input ->
                        val block = ByteArray(65536)
                        while(true) { checkActive(); val n = input.read(block); if(n < 0) break; digest.update(block,0,n) }
                    }
                    if(digest.digest().joinToString("") { "%02x".format(it) } != checksum.lowercase()) {
                        partial.delete(); metadata.delete(); throw VoiceDownloadException(R.string.voice_download_integrity)
                    }
                }
                metadata.delete()
                return
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(error: Exception) {
                last = error
                val retry = error !is VoiceDownloadException || error.status == 429 || error.status in 500..599
                if(!retry || attempt == 2) throw safe(error)
                progress(partial.length(),-1,true)
                waitBeforeRetry((attempt+1)*1500L)
            }
        }
        throw safe(requireNotNull(last))
    }

    private fun safe(error: Exception): VoiceDownloadException = when(error) {
        is VoiceDownloadException -> error
        is SocketTimeoutException -> VoiceDownloadException(R.string.voice_download_timeout)
        else -> VoiceDownloadException(R.string.voice_download_network)
    }

    private fun transfer(original: String, file: File, metadata: File, limit: Long, checkActive: () -> Unit,
        progress: (Long, Long) -> Unit) {
        var current = SecureDownloads.httpsUrl(original)
        val saved = runCatching { metadata.readLines() }.getOrNull()
        val validator = saved?.takeIf { it.size == 2 && it[0] == cloudDigest(original) }?.get(1)
            ?.takeIf { it.isNotBlank() && it.none(Char::isISOControl) }
        var offset = if(validator != null && file.length() in 1..limit) file.length() else 0L
        if(offset == 0L) { file.delete(); metadata.delete() }
        repeat(6) {
            checkActive()
            val connection = open(current)
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 25_000; connection.readTimeout = 60_000
                connection.setRequestProperty("User-Agent","VernReader")
                connection.setRequestProperty("Accept-Encoding","identity")
                if(offset > 0) {
                    connection.setRequestProperty("Range","bytes=$offset-")
                    connection.setRequestProperty("If-Range",validator)
                }
                val status = connection.responseCode
                if(status in listOf(301,302,303,307,308)) {
                    current = SecureDownloads.httpsUrl(URL(current,connection.getHeaderField("Location")
                        ?: throw VoiceDownloadException(R.string.voice_download_source)).toString())
                } else {
                    if(status != 200 && status != 206) {
                        if(status == 416) { file.delete(); metadata.delete(); throw EOFException() }
                        throw VoiceDownloadException(when(status) {
                            403,429 -> R.string.voice_download_busy
                            404,410 -> R.string.voice_download_source
                            else -> R.string.voice_download_network
                        },status)
                    }
                    val total: Long
                    if(status == 206) {
                        val range = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(connection.getHeaderField("Content-Range").orEmpty())
                        val start = range?.groupValues?.get(1)?.toLongOrNull()
                        val end = range?.groupValues?.get(2)?.toLongOrNull()
                        total = range?.groupValues?.get(3)?.toLongOrNull() ?: -1
                        if(offset == 0L || start != offset || end == null || total <= end || end < offset ||
                            (connection.contentLengthLong >= 0 && connection.contentLengthLong != end-offset+1)) {
                            file.delete(); metadata.delete(); throw VoiceDownloadException(R.string.voice_download_integrity)
                        }
                    } else { offset = 0; total = connection.contentLengthLong }
                    if(total > limit || total == 0L) throw VoiceDownloadException(R.string.voice_download_integrity)
                    val free = file.parentFile?.usableSpace ?: 0L
                    // Leave room for extraction as well as the remaining compressed download.
                    val needed = if(total > 0) (total-offset).coerceAtLeast(0)+total*2+16L*1024*1024 else 32L*1024*1024
                    if(free < needed) throw VoiceDownloadException(R.string.voice_download_storage)
                    val nextValidator = connection.getHeaderField("ETag")?.takeIf { it.startsWith('"') && it.endsWith('"') }
                        ?: connection.getHeaderField("Last-Modified")
                    if(offset > 0 && nextValidator != null && nextValidator != validator) {
                        file.delete(); metadata.delete(); throw VoiceDownloadException(R.string.voice_download_integrity)
                    }
                    try {
                        if(nextValidator != null && nextValidator.none(Char::isISOControl))
                            metadata.writeText(cloudDigest(original)+"\n"+nextValidator)
                        else metadata.delete()
                    } catch(_: IOException) { throw VoiceDownloadException(R.string.voice_download_storage) }
                    var received = offset
                    val output = try { file.outputStreamForAppend(offset > 0) }
                        catch(_: IOException) { throw VoiceDownloadException(R.string.voice_download_storage) }
                    output.use { destination -> connection.inputStream.use { input ->
                        val block = ByteArray(65536)
                        while(true) {
                            checkActive(); val n = input.read(block); if(n < 0) break
                            if(received+n > limit || (total > 0 && received+n > total)) {
                                file.delete(); metadata.delete(); throw VoiceDownloadException(R.string.voice_download_integrity)
                            }
                            try { destination.write(block,0,n) }
                            catch(_: IOException) { throw VoiceDownloadException(R.string.voice_download_storage) }
                            received += n; progress(received,total)
                        }
                    } }
                    if(received == 0L || (total > 0 && received != total)) throw EOFException()
                    return
                }
            } finally { connection.disconnect() }
        }
        throw VoiceDownloadException(R.string.voice_download_source)
    }
    private fun File.outputStreamForAppend(append: Boolean) = java.io.FileOutputStream(this,append)
}

package com.veritas.reader

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.nio.charset.Charset

private const val MAX_CLASSIC_BYTES = 16 * 1024 * 1024
private const val WATERLOO_GUTENBERG_MIRROR = "https://mirror.csclub.uwaterloo.ca/gutenberg"
private val gutenbergTextUrl = Regex("""https://(?:www\.)?gutenberg\.org/cache/epub/([1-9]\d*)/pg\1\.txt""")

internal data class ClassicTextSource(val url: String, val charset: Charset = Charsets.UTF_8)

internal class ClassicDownloadRejected(message: String) : IOException(message)

internal fun classicTextSources(primaryUrl: String): List<ClassicTextSource> {
    val primary = ClassicTextSource(primaryUrl)
    val ebook = gutenbergTextUrl.matchEntire(primaryUrl)?.groupValues?.get(1) ?: return listOf(primary)
    val directory = ebook.dropLast(1).toList().joinToString("/").ifEmpty { "0" }
    // The mirror serves the source edition files. These three entries predate UTF-8 on the mirror.
    val (suffix, charset) = when (ebook) {
        "20203", "39452" -> "-8" to Charsets.ISO_8859_1
        "4507" -> "" to Charsets.US_ASCII
        else -> "-0" to Charsets.UTF_8
    }
    return listOf(primary, ClassicTextSource("$WATERLOO_GUTENBERG_MIRROR/$directory/$ebook/$ebook$suffix.txt", charset))
}

internal suspend fun downloadClassicText(
    primaryUrl: String,
    fetch: suspend (ClassicTextSource) -> String
): String {
    var lastFailure: IOException? = null
    var retryableFailure: IOException? = null
    for (source in classicTextSources(primaryUrl)) {
        currentCoroutineContext().ensureActive()
        try {
            val raw = fetch(source).trimStart { it.isWhitespace() || it == '\uFEFF' }
            if (raw.startsWith("<")) {
                throw ClassicDownloadRejected("The server returned a web page instead of book text.")
            }
            val text = cleanAndUnwrapClassicBookText(raw)
            if (text.isBlank()) throw ClassicDownloadRejected("The download contained no book text.")
            return text
        } catch (error: IOException) {
            lastFailure = error
            if (error !is ClassicDownloadRejected) retryableFailure = error
        }
    }
    currentCoroutineContext().ensureActive()
    throw requireNotNull(retryableFailure ?: lastFailure)
}

internal suspend fun fetchClassicText(source: ClassicTextSource, progress: suspend (Int) -> Unit = {}): String {
    val connection = SecureDownloads.httpsUrl(source.url).openConnection() as HttpURLConnection
    connection.connectTimeout = 15_000
    connection.readTimeout = 15_000
    try {
        val status = connection.responseCode
        if (status != HttpURLConnection.HTTP_OK) {
            val message = "The book server returned HTTP $status."
            if (status in 400..499 && status != 408 && status != 429) throw ClassicDownloadRejected(message)
            throw IOException(message)
        }
        if (connection.contentType?.substringBefore(';')?.trim().equals("text/html", ignoreCase = true)) {
            throw ClassicDownloadRejected("The server returned a web page instead of book text.")
        }
        val length = connection.contentLengthLong
        if (length > MAX_CLASSIC_BYTES) throw ClassicDownloadRejected("This download exceeds the book size limit.")
        val output = ByteArrayOutputStream()
        connection.inputStream.use { input ->
            val buffer = ByteArray(16 * 1024)
            var reportedBucket = -1
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                if (output.size() + read > MAX_CLASSIC_BYTES) {
                    throw ClassicDownloadRejected("This download exceeds the book size limit.")
                }
                output.write(buffer, 0, read)
                if (length > 0) {
                    val percent = (output.size() * 100L / length).toInt().coerceIn(0, 100)
                    if (percent / 5 != reportedBucket) {
                        progress(percent)
                        reportedBucket = percent / 5
                    }
                }
            }
        }
        if (length >= 0 && output.size().toLong() != length) throw IOException("This download was incomplete.")
        return output.toString(source.charset.name())
    } finally {
        connection.disconnect()
    }
}

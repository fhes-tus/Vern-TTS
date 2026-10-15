package com.veritas.reader

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

internal object SecureDownloads {
    data class Result(val disposition: String?, val contentType: String?, val finalUrl: URL)
    fun httpsUrl(value: String): URL {
        val uri = URI(value)
        require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.rawUserInfo == null) {
            "Downloads require a valid HTTPS address."
        }
        return uri.toURL()
    }

    /** Rebuild origin-sensitive headers after every redirect, never delegate redirects. */
    fun download(url: String, target: File, limit: Long, headers: (URL) -> Map<String, String> = { emptyMap() },
                 checkActive: () -> Unit = {}, onProgress: (Long, Long) -> Unit = { _, _ -> },
                 openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }): Result {
        var current = httpsUrl(url)
        var complete = false
        try {
            repeat(6) {
                checkActive()
                val connection = openConnection(current).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 20_000
                    readTimeout = 40_000
                    headers(current).forEach { (name, value) -> setRequestProperty(name, value) }
                }
                try {
                    val status = connection.responseCode
                    if (status in listOf(301, 302, 303, 307, 308)) {
                        val location = requireNotNull(connection.getHeaderField("Location")) { "Download redirect has no destination." }
                        current = httpsUrl(URL(current, location).toString())
                    } else {
                        require(status == 200) { "Download failed (HTTP $status)." }
                        val expected = connection.contentLengthLong
                        val available = target.parentFile?.usableSpace ?: 0L
                        val effectiveLimit = minOf(limit, available - 16L * 1024 * 1024)
                        require(effectiveLimit > 0 && expected <= effectiveLimit) { "Download is too large or there is insufficient free storage." }
                        var read = 0L
                        connection.inputStream.use { input -> target.outputStream().use { output ->
                            ResourceLimits.copy(input, object : java.io.OutputStream() {
                                override fun write(value: Int) { output.write(value); read++; onProgress(read, expected) }
                                override fun write(bytes: ByteArray, offset: Int, length: Int) {
                                    output.write(bytes, offset, length); read += length; onProgress(read, expected)
                                }
                            }, effectiveLimit, checkActive)
                        } }
                        require(read > 0 && (expected < 0 || read == expected)) { "Download is empty or incomplete." }
                        complete = true
                        return Result(connection.getHeaderField("Content-Disposition"), connection.contentType, current)
                    }
                } finally { connection.disconnect() }
            }
            error("Download redirected too many times.")
        } finally { if (!complete) target.delete() }
    }
}

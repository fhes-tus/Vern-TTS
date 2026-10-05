package com.veritas.reader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Locale

data class WebArticle(
    val title: String,
    val text: String,
    val url: String
)

object WebArticleExtractor {
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36 VernReader/2.0"

    suspend fun extract(urlText: String): WebArticle = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeUrl(urlText)
        val connection = try {
            (URL(normalizedUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 20000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            }
        } catch (e: UnknownHostException) {
            throw IllegalArgumentException("Unable to reach the web server. Please check your internet connection.", e)
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to open connection to article URL: ${e.localizedMessage ?: "Unknown error"}", e)
        }

        val (rawBytes, headerCharset) = try {
            when (val responseCode = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val contentType = connection.contentType.orEmpty()
                    val charset = extractCharsetFromHeader(contentType)
                    val bytes = readBytesSafely(connection.inputStream)
                    bytes to charset
                }
                HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN -> {
                    throw IllegalArgumentException("This article is paywalled, protected, or restricted by the website (HTTP $responseCode).")
                }
                HttpURLConnection.HTTP_NOT_FOUND -> {
                    throw IllegalArgumentException("Article was not found at this URL (HTTP 404).")
                }
                in 500..599 -> {
                    throw IllegalArgumentException("The website server encountered an error (HTTP $responseCode). Please try again later.")
                }
                else -> {
                    throw IllegalArgumentException("Unable to read web article (HTTP $responseCode).")
                }
            }
        } catch (e: SocketTimeoutException) {
            throw IllegalArgumentException("Connection timed out while loading article. Please try again.", e)
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to download web article: ${e.localizedMessage ?: "Network error"}", e)
        } finally {
            connection.disconnect()
        }

        val html = decodeHtml(rawBytes, headerCharset)
        val title = extractTitle(html).ifBlank { URL(normalizedUrl).host.removePrefix("www.") }
        val articleHtml = extractArticleHtml(html)
        val text = htmlToText(articleHtml).ifBlank { htmlToText(html) }
        if (text.isBlank()) throw IllegalArgumentException("No readable article text was found at this link.")
        WebArticle(title = title, text = text, url = normalizedUrl)
    }

    fun looksLikeUrl(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            Regex("^[A-Za-z0-9.-]+\\.[A-Za-z]{2,}([/?#].*)?$").matches(trimmed)
    }

    private fun normalizeUrl(text: String): String {
        val trimmed = text.trim()
        require(looksLikeUrl(trimmed)) { "Enter a valid web article link." }
        return if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    private fun readBytesSafely(input: InputStream): ByteArray {
        val buffer = ByteArray(8192)
        val output = ByteArrayOutputStream()
        input.use { stream ->
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
            }
        }
        return output.toByteArray()
    }

    private fun extractCharsetFromHeader(contentType: String): Charset? {
        val match = Regex("(?i)charset=([A-Za-z0-9_\\-]+)").find(contentType)
        val name = match?.groupValues?.getOrNull(1)?.trim('\'', '"', ' ') ?: return null
        return runCatching { Charset.forName(name) }.getOrNull()
    }

    private fun decodeHtml(rawBytes: ByteArray, headerCharset: Charset?): String {
        if (headerCharset != null) {
            return String(rawBytes, headerCharset)
        }
        // Tentative decode with UTF-8 to look for meta charset
        val tentative = String(rawBytes, StandardCharsets.UTF_8)
        val metaMatch = Regex("(?is)<meta[^>]+charset=[\"']?([A-Za-z0-9_\\-]+)[\"']?").find(tentative)
            ?: Regex("(?is)<meta[^>]+content=[\"'][^\"']*charset=([A-Za-z0-9_\\-]+)[\"']?").find(tentative)
        val metaCharsetName = metaMatch?.groupValues?.getOrNull(1)?.trim('\'', '"', ' ')
        if (!metaCharsetName.isNullOrBlank()) {
            val detected = runCatching { Charset.forName(metaCharsetName) }.getOrNull()
            if (detected != null && detected != StandardCharsets.UTF_8) {
                return String(rawBytes, detected)
            }
        }
        return tentative
    }

    private fun extractTitle(html: String): String {
        return Regex("(?is)<title[^>]*>(.*?)</title>")
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::decodeEntities)
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            .orEmpty()
    }

    private fun extractArticleHtml(html: String): String {
        val article = Regex("(?is)<article[^>]*>(.*?)</article>").find(html)?.value
        if (!article.isNullOrBlank()) return article
        val main = Regex("(?is)<main[^>]*>(.*?)</main>").find(html)?.value
        if (!main.isNullOrBlank()) return main
        val contentRole = Regex("(?is)<div[^>]+role=[\"']main[\"'][^>]*>(.*?)</div>").find(html)?.value
        return contentRole ?: html
    }

    private fun htmlToText(html: String): String {
        return html
            .replace(Regex("(?is)<!--.*?-->"), " ")
            .replace(Regex("(?is)<script[^>]*>.*?</script>"), " ")
            .replace(Regex("(?is)<style[^>]*>.*?</style>"), " ")
            .replace(Regex("(?is)<noscript[^>]*>.*?</noscript>"), " ")
            .replace(Regex("(?is)<nav[^>]*>.*?</nav>"), " ")
            .replace(Regex("(?is)<header[^>]*>.*?</header>"), " ")
            .replace(Regex("(?is)<footer[^>]*>.*?</footer>"), " ")
            .replace(Regex("(?is)<aside[^>]*>.*?</aside>"), " ")
            .replace(Regex("(?is)<form[^>]*>.*?</form>"), " ")
            .replace(Regex("(?is)<svg[^>]*>.*?</svg>"), " ")
            .replace(Regex("(?is)<iframe[^>]*>.*?</iframe>"), " ")
            .replace(Regex("(?is)<figure[^>]*>.*?</figure>"), " ")
            .replace(Regex("(?is)<figcaption[^>]*>.*?</figcaption>"), " ")
            .replace(Regex("(?is)<button[^>]*>.*?</button>"), " ")
            .replace(Regex("(?is)<dialog[^>]*>.*?</dialog>"), " ")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</(p|div|section|article|blockquote|li|h[1-6])>"), "\n\n")
            .replace(Regex("<[^>]+>"), " ")
            .let(::decodeEntities)
            .lines()
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter(::isReadableArticleLine)
            .joinToString("\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun isReadableArticleLine(line: String): Boolean {
        if (line.length <= 1) return false
        val lower = line.lowercase(Locale.getDefault())
        if (lower.matches(Regex("^(cookie|privacy|advertisement|subscribe|sign in|sign up|log in|menu|navigation|contents|table of contents).*$"))) {
            return false
        }
        if (line.contains("{{") || line.contains("}}") || line.contains("\\n") || line.contains("\\\"")) return false
        if (Regex("\"(wt|function|params|i)\"\\s*:").containsMatchIn(line)) return false
        if (Regex("[{}\\[\\]<>]{3,}").containsMatchIn(line)) return false
        val letterCount = line.count { it.isLetter() }
        val symbolCount = line.count { !it.isLetterOrDigit() && !it.isWhitespace() }
        if (letterCount < 8 && line.length > 24) return false
        if (symbolCount > letterCount && line.length > 32) return false
        return true
    }

    private fun decodeEntities(text: String): String {
        return text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }
}

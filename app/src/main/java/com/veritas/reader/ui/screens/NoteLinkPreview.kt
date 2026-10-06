package com.veritas.reader.ui.screens

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Base64

/** Stored separately from visible text, so deleting a pasted URL keeps its preview. */
internal data class NoteLink(val url: String, val title: String, val image: String = "")

internal object NoteLinks {
    fun readLimited(input: java.io.InputStream, limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (output.size() < limit) {
            val count = input.read(buffer, 0, minOf(buffer.size, limit - output.size()))
            if (count < 0) break
            if (count > 0) output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
    private val tag = Regex("""\[link-preview:([A-Za-z0-9_=-]+):([A-Za-z0-9_=-]+):([A-Za-z0-9_=-]*)\]""")
    private val dismissedTag = Regex("""\[link-dismissed:([A-Za-z0-9_=-]+)\]""")
    private fun encode(value: String) = Base64.getUrlEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
    private fun decode(value: String) = String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
    fun read(content: String): List<NoteLink> = tag.findAll(content).mapNotNull { match ->
        runCatching { NoteLink(decode(match.groupValues[1]), decode(match.groupValues[2]), decode(match.groupValues[3])) }
            .getOrNull()?.takeIf { validUrl(it.url) }
    }.distinctBy { it.url }.toList()
    fun dismissed(content: String) = dismissedTag.findAll(content).mapNotNull { runCatching { decode(it.groupValues[1]) }.getOrNull() }.toList()
    fun strip(content: String) = dismissedTag.replace(tag.replace(content, ""), "").trimEnd()
    fun write(content: String, links: List<NoteLink>, dismissed: List<String> = emptyList()): String {
        val tags = links.map { "[link-preview:${encode(it.url)}:${encode(it.title)}:${encode(it.image)}]" } + dismissed.map { "[link-dismissed:${encode(it)}]" }
        return if (tags.isEmpty()) content else content.trimEnd() + "\n" + tags.joinToString("\n")
    }
    fun validUrl(value: String) = runCatching {
        val uri = URI(value)
        uri.scheme?.lowercase() in listOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)
    fun urls(content: String): List<String> = Regex("""https?://[^\s<>\[\]]+""").findAll(content)
        .map { it.value.trimEnd('.', ',', ';', '!', '?', ')', '"', '\'') }.filter(::validUrl).distinct().take(6).toList()
    fun host(url: String) = runCatching { URI(url).host.removePrefix("www.") }.getOrDefault("Link")

    /** Bounded fetch on IO; a site without social metadata still gets a usable domain card. */
    fun fetch(url: String): NoteLink = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 VernReader/2.5")
            require(connection.responseCode in 200..299)
            require(connection.contentType.orEmpty().contains("text/html", true))
            val html = connection.inputStream.use { String(readLimited(it, 256 * 1024), Charsets.UTF_8) }
            parseMetadata(url, html, connection.url.toString())
        } finally { connection.disconnect() }
    }.getOrDefault(NoteLink(url, host(url)))

    internal fun parseMetadata(url: String, html: String, baseUrl: String = url): NoteLink {
            fun clean(value: String) = value.replace(Regex("<[^>]*>"), "").replace("&amp;", "&")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").trim()
            fun meta(key: String): String = Regex("<meta\\s+[^>]*>", RegexOption.IGNORE_CASE).findAll(html).firstNotNullOfOrNull { m ->
                val attrs = Regex("([\\w:-]+)\\s*=\\s*([\"'])(.*?)\\2", RegexOption.DOT_MATCHES_ALL).findAll(m.value)
                    .associate { it.groupValues[1].lowercase() to it.groupValues[3] }
                if (attrs["property"].equals(key, true) || attrs["name"].equals(key, true)) attrs["content"] else null
            }.orEmpty()
            val title = clean(meta("og:title").ifBlank { meta("twitter:title") }.ifBlank { Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(html)?.groupValues?.get(1).orEmpty() }).take(180).ifBlank { host(url) }
            val image = meta("og:image").ifBlank { meta("og:image:secure_url") }.ifBlank { meta("twitter:image") }
                .takeIf { it.isNotBlank() }?.let { runCatching { URL(URL(baseUrl), clean(it)).toString() }.getOrNull() }
                ?.takeIf(::validUrl).orEmpty()
            return NoteLink(url, title, image)
    }
}

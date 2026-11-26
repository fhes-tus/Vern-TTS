package com.veritas.reader.ui.screens

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.util.Base64

/** Stored separately from visible text, so deleting a pasted URL keeps its preview. */
internal data class NoteLink(val url: String, val title: String, val image: String = "")

/** Reading the cache never starts a request. Concurrent loads for a URL share a single result. */
internal class NotePreviewCache<T : Any>(private val capacity: Int) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val saved = LinkedHashMap<String, T>(capacity, 0.75f, true)
    private val active = mutableMapOf<String, Deferred<T?>>()

    fun cached(url: String): T? = synchronized(lock) { saved[url] }

    suspend fun load(url: String, fetch: suspend () -> T?): T? {
        val request = synchronized(lock) {
            saved[url]?.let { return it }
            active[url]?.takeUnless { it.isCompleted } ?: scope.async(start = CoroutineStart.LAZY) {
                fetch()?.also { value ->
                    synchronized(lock) {
                        saved[url] = value
                        if (saved.size > capacity) saved.remove(saved.keys.first())
                    }
                }
            }.also { request ->
                active[url] = request
                request.invokeOnCompletion {
                    synchronized(lock) { active.remove(url, request) }
                }
            }
        }
        return request.await()
    }
}

internal object NoteLinks {
    private val metadata = NotePreviewCache<NoteLink>(40)

    fun readLimited(input: java.io.InputStream, limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (output.size() < limit) {
            val count = input.read(buffer, 0, minOf(buffer.size, limit - output.size()))
            if (count <= 0) break
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
    private val tag = Regex("""\[link-preview:([A-Za-z0-9_=-]+):([A-Za-z0-9_=-]+):([A-Za-z0-9_=-]*)\]""")
    private val dismissedTag = Regex("""\[link-dismissed:([A-Za-z0-9_=-]+)\]""")
    private fun encode(value: String) = Base64.getUrlEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
    private fun decode(value: String) = String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
    fun read(content: String): List<NoteLink> = consolidate(tag.findAll(content).mapNotNull { match ->
        runCatching { NoteLink(decode(match.groupValues[1]), decode(match.groupValues[2]), decode(match.groupValues[3])) }
            .getOrNull()?.takeIf { validUrl(it.url) }
    }.toList(), urls(content))
    fun dismissed(content: String) = dismissedTag.findAll(content).mapNotNull { runCatching { decode(it.groupValues[1]) }.getOrNull() }.toList()
    fun strip(content: String) = dismissedTag.replace(tag.replace(content, ""), "").trimEnd()
    fun write(content: String, links: List<NoteLink>, dismissed: List<String> = emptyList()): String {
        val tags = mergeDuplicates(links).values.map { "[link-preview:${encode(it.url)}:${encode(it.title)}:${encode(it.image)}]" } +
            dismissed.distinctBy(::identity).map { "[link-dismissed:${encode(it)}]" }
        return if (tags.isEmpty()) content else content.trimEnd() + "\n" + tags.joinToString("\n")
    }
    fun validUrl(value: String) = runCatching {
        val uri = URI(value)
        uri.scheme?.lowercase() in listOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)
    fun urls(content: String): List<String> = Regex("""https?://[^\s<>\[\]]+""").findAll(content)
        .map { it.value.trimEnd('.', ',', ';', '!', '?', ')', '"', '\'') }.filter(::validUrl).distinct().take(6).toList()
    fun host(url: String) = runCatching { URI(url).host.removePrefix("www.") }.getOrDefault("Link")

    fun identity(url: String): String = runCatching {
        val uri = URI(url)
        val scheme = uri.scheme.lowercase()
        val port = uri.port.takeUnless { it == -1 || (scheme == "https" && it == 443) || (scheme == "http" && it == 80) }
        buildString {
            append(scheme).append("://").append(uri.host.lowercase())
            port?.let { append(':').append(it) }
            append(uri.rawPath.orEmpty().ifBlank { "/" })
            uri.rawQuery?.let { append('?').append(it) }
            uri.rawFragment?.let { append('#').append(it) }
        }
    }.getOrDefault(url)

    private fun extendsUrl(shorter: String, longer: String): Boolean =
        shorter != longer && longer.startsWith(shorter)

    private fun mergeDuplicates(links: List<NoteLink>): Map<String, NoteLink> {
        val byUrl = linkedMapOf<String, NoteLink>()
        for (link in links) {
            val key = identity(link.url)
            val previous = byUrl[key]
            byUrl[key] = if (previous == null) link else withMetadata(previous, link.copy(url = previous.url))
        }
        return byUrl
    }

    private fun consolidate(links: List<NoteLink>, visibleUrls: List<String>): List<NoteLink> {
        val byUrl = mergeDuplicates(links)
        val visible = visibleUrls.mapTo(mutableSetOf(), ::identity)
        val richer = byUrl.filterValues { it.image.isNotBlank() || (it.title.isNotBlank() && it.title != host(it.url)) }.keys
        return byUrl.filterKeys { key ->
            // Older editors saved otherwise empty cards for each unfinished URL prefix.
            key in visible || key in richer || richer.none { extendsUrl(key, it) }
        }.values.toList()
    }

    fun syncDetectedLinks(
        links: List<NoteLink>, previousUrls: List<String>, urls: List<String>, dismissed: List<String>
    ): List<NoteLink> {
        val present = urls.mapTo(mutableSetOf(), ::identity)
        val previous = previousUrls.mapTo(mutableSetOf(), ::identity)
        val existing = links.filterNot { link ->
            val key = identity(link.url)
            key in previous && key !in present && present.any { extendsUrl(key, it) || extendsUrl(key.removeSuffix("/"), it) }
        }
        val excluded = (existing.map { it.url } + dismissed).mapTo(mutableSetOf(), ::identity)
        val added = urls.filter { excluded.add(identity(it)) }.map { cachedMetadata(it) ?: NoteLink(it, host(it)) }
        return mergeDuplicates(existing + added).values.toList()
    }

    fun cachedMetadata(url: String): NoteLink? = metadata.cached(url)

    fun withMetadata(link: NoteLink, loaded: NoteLink?): NoteLink {
        if (loaded == null || loaded.url != link.url) return link
        return link.copy(
            title = loaded.title.takeIf { it.isNotBlank() && it != host(link.url) } ?: link.title,
            image = loaded.image.ifBlank { link.image }
        )
    }

    suspend fun loadMetadata(url: String, automatic: Boolean = false): NoteLink? = metadata.load(url) {
        requestPreview(url, automatic = automatic) { connection ->
            if (!connection.contentType.orEmpty().contains("text/html", true)) return@requestPreview null
            val html = connection.inputStream.use { String(readLimited(it, 256 * 1024), Charsets.UTF_8) }
            parseMetadata(url, html, connection.url.toString())
                .takeUnless { it.title == host(url) && it.image.isBlank() }
        }
    }

    suspend fun <T> requestPreview(
        url: String, referrer: String? = null, automatic: Boolean = false, read: (HttpURLConnection) -> T?
    ): T? {
        if (!validUrl(url)) return null
        return try {
            withTimeoutOrNull(12_000L) {
                var target = url
                repeat(5) {
                    if (!validUrl(target) || (automatic && !runInterruptible { isPublicHttps(target) })) {
                        return@withTimeoutOrNull null
                    }
                    val (value, redirect) = coroutineScope {
                        val connection = URI(target).toURL().openConnection() as HttpURLConnection
                        val result = async {
                            try {
                                runInterruptible {
                                    connection.instanceFollowRedirects = !automatic
                                    connection.connectTimeout = 5_000
                                    connection.readTimeout = 5_000
                                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 VernReader/2.5")
                                    if (referrer != null && validUrl(referrer)) {
                                        val source = URI(referrer)
                                        connection.setRequestProperty("Referer", "${source.scheme}://${source.authority}/")
                                    }
                                    when (connection.responseCode) {
                                        in 200..299 -> read(connection) to null
                                        301, 302, 303, 307, 308 -> null to connection.getHeaderField("Location")
                                        else -> null to null
                                    }
                                }
                            } finally {
                                runCatching { connection.disconnect() }
                            }
                        }
                        try {
                            result.await()
                        } finally {
                            if (!result.isCompleted) runCatching { connection.disconnect() }
                        }
                    }
                    if (redirect.isNullOrBlank()) return@withTimeoutOrNull value
                    target = URI(target).resolve(redirect).toString()
                }
                null
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private fun isPublicHttps(url: String): Boolean {
        val address = URI(url)
        if (!address.scheme.equals("https", ignoreCase = true)) return false
        return InetAddress.getAllByName(address.host).all { ip ->
            !ip.isAnyLocalAddress && !ip.isLoopbackAddress && !ip.isSiteLocalAddress &&
                !ip.isLinkLocalAddress && !ip.isMulticastAddress &&
                (ip.address.size != 16 || (ip.address[0].toInt() and 0xfe) != 0xfc)
        }
    }

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

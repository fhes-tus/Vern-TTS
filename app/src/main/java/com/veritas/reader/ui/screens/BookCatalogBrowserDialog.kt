package com.veritas.reader.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Universal In-App Browser for Free Book Repositories (Project Gutenberg, Standard Ebooks,
 * Open Library, ManyBooks, Ocean of PDF, etc.) with automatic download interception
 * and enterprise-grade privacy and security shielding.
 */
private data class BookDownloadRequest(val url: String, val userAgent: String?, val disposition: String?, val mimeType: String?)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BookCatalogBrowserDialog(
    initialUrl: String = "https://oceanofpdf.com/",
    siteName: String = "Free Books",
    initialQuery: String = "",
    onImportDownloadedFile: (File, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val resolvedInitialUrl = remember(initialUrl, initialQuery) {
        if (initialQuery.isNotBlank()) {
            val encoded = Uri.encode(initialQuery.trim())
            when {
                initialUrl.contains("gutenberg.org", ignoreCase = true) ->
                    "https://www.gutenberg.org/ebooks/search/?query=$encoded"
                initialUrl.contains("standardebooks.org", ignoreCase = true) ->
                    "https://standardebooks.org/ebooks?query=$encoded"
                initialUrl.contains("openlibrary.org", ignoreCase = true) ->
                    "https://openlibrary.org/search?q=$encoded"
                initialUrl.contains("manybooks.net", ignoreCase = true) ->
                    "https://manybooks.net/search-books?search=$encoded"
                initialUrl.contains("oceanofpdf.com", ignoreCase = true) ->
                    "https://oceanofpdf.com/?s=$encoded"
                else -> {
                    if (initialUrl.contains("?")) "$initialUrl&q=$encoded" else "$initialUrl?q=$encoded"
                }
            }
        } else {
            initialUrl.ifBlank { "https://oceanofpdf.com/" }
        }
    }

    var currentUrl by remember { mutableStateOf(resolvedInitialUrl) }
    var lastKnownBookTitle by remember { mutableStateOf("") }
    var pageTitle by remember { mutableStateOf(siteName) }
    var pageProgress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(true) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadStatus by remember { mutableStateOf("") }
    var pendingDownload by remember { mutableStateOf<BookDownloadRequest?>(null) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    fun sanitizeBookTitle(raw: String): String {
        return raw
            .replace(Regex("""(?i)[/_(\[]?OceanofPDF(\.com)?[/_\])]?"""), "")
            .replace(Regex("""(?i)\bOceanofPDF(\.com)?\b"""), "")
            .replace(Regex("""(?i)\bProject Gutenberg\b"""), "")
            .replace(Regex("""(?i)\bStandard Ebooks\b"""), "")
            .replace(Regex("""(?i)\bOpen Library\b"""), "")
            .replace(Regex("""(?i)\bManyBooks\b"""), "")
            .replace(Regex("""(?i)\[(PDF|EPUB|TXT)\]"""), "")
            .replace(Regex("""(?i)\((PDF|EPUB|TXT)\)"""), "")
            .replace(Regex("""(?i)\bDownload\b"""), "")
            .replace(Regex("""(?i)\bFetching Resource(\.\.\.)?\b"""), "")
            .replace('_', ' ')
            .trim(' ', '-', '•', '_', ':', '/', '\\', '(', ')', '[', ']')
    }

    fun downloadAndImport(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {
        if (isDownloading) return
        isDownloading = true
        downloadStatus = "Downloading book…"
        val titleAtRequest = lastKnownBookTitle.ifBlank { sanitizeBookTitle(pageTitle).ifBlank { "Imported Book" } }
        scope.launch(Dispatchers.IO) {
            var downloaded: File? = null
            try {
                val directory = File(context.cacheDir, "book_downloads").apply { mkdirs() }
                val target = File.createTempFile("book_", ".download", directory)
                downloaded = target
                val result = com.veritas.reader.SecureDownloads.download(url, target,
                    com.veritas.reader.ResourceLimits.BOOK_DOWNLOAD_BYTES,
                    headers = { destination -> buildMap {
                        put("User-Agent", userAgent ?: "VernReader")
                        put("Referer", "${destination.protocol}://${destination.host}/")
                        CookieManager.getInstance().getCookie(destination.toString())?.takeIf { it.isNotBlank() }?.let { put("Cookie", it) }
                    } }, checkActive = { coroutineContext.ensureActive() })
                val responseName = result.disposition ?: contentDisposition
                val filename = responseName?.let { Regex("""filename(?:\*)?=["']?([^";]+)""", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
                    ?.trim('\'')?.substringAfter("''")?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
                val returnedType = result.contentType ?: mimeType.orEmpty()
                val extension = when {
                    result.finalUrl.path.endsWith(".epub", true) || filename?.endsWith(".epub", true) == true || returnedType.contains("epub", true) -> ".epub"
                    result.finalUrl.path.endsWith(".txt", true) || filename?.endsWith(".txt", true) == true || returnedType.contains("text/plain", true) -> ".txt"
                    else -> ".pdf"
                }
                if (extension != ".pdf") require(target.length() <= com.veritas.reader.ResourceLimits.DOCUMENT_INPUT_BYTES) { "Book text exceeds the 64 MB safety limit." }
                if (extension == ".pdf") {
                    val header = target.inputStream().use { input -> ByteArray(1024).let { bytes ->
                        val count = input.read(bytes); String(bytes, 0, count.coerceAtLeast(0), Charsets.ISO_8859_1)
                    } }
                    require(header.contains("%PDF-")) { "The website did not return a PDF book." }
                } else if (extension == ".epub") {
                    java.util.zip.ZipFile(target).use { archive ->
                        require(archive.getEntry("META-INF/container.xml") != null) { "The website did not return an EPUB book." }
                    }
                }
                val title = filename?.substringBeforeLast('.')?.let(::sanitizeBookTitle)?.takeIf { it.isNotBlank() } ?: titleAtRequest
                val importFile = File(directory, target.nameWithoutExtension + extension)
                check(target.renameTo(importFile)) { "Could not prepare the downloaded book." }
                downloaded = importFile
                withContext(Dispatchers.Main) {
                    onImportDownloadedFile(importFile, title)
                    // Transfer ownership before dismissal cancels this scope.
                    downloaded = null
                    onDismiss()
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, error.message ?: "Could not download this book.", android.widget.Toast.LENGTH_LONG).show()
                }
            } finally {
                downloaded?.delete()
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) {
                    isDownloading = false
                    downloadStatus = ""
                }
            }
        }
    }


    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BackHandler {
            if (webViewInstance?.canGoBack() == true) {
                webViewInstance?.goBack()
            } else {
                onDismiss()
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Top Navigation Bar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 4.dp
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(onClick = {
                                if (webViewInstance?.canGoBack() == true) {
                                    webViewInstance?.goBack()
                                } else {
                                    onDismiss()
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }

                            IconButton(
                                onClick = { webViewInstance?.goForward() },
                                enabled = webViewInstance?.canGoForward() == true
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                            }

                            IconButton(onClick = { webViewInstance?.reload() }) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Reload")
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Security,
                                        contentDescription = "Encrypted & Sandboxed",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = pageTitle.ifBlank { siteName },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = currentUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, contentDescription = "Close")
                            }
                        }

                        if (isLoading && pageProgress in 0.01f..0.99f) {
                            LinearProgressIndicator(
                                progress = { pageProgress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Downloading Banner
                if (isDownloading) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = downloadStatus,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // In-App WebView with Enterprise Security Shielding
                Box(modifier = Modifier.weight(1f)) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewInstance = this
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    // Security & Privacy Shielding: Prevent local file access and content leaks
                                    allowFileAccess = false
                                    allowContentAccess = false
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    safeBrowsingEnabled = true
                                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                    userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                                }

                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, false)

                                setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                                    if (!isDownloading && pendingDownload == null && runCatching { com.veritas.reader.SecureDownloads.httpsUrl(url) }.isSuccess)
                                        pendingDownload = BookDownloadRequest(url, userAgent, contentDisposition, mimetype)
                                })

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        pageProgress = newProgress / 100f
                                        isLoading = newProgress < 100
                                    }

                                    override fun onReceivedTitle(view: WebView?, title: String?) {
                                        if (!title.isNullOrBlank()) {
                                            val clean = sanitizeBookTitle(title)
                                            if (clean.isNotBlank() &&
                                                !clean.contains("Fetching Resource", ignoreCase = true) &&
                                                !clean.contains("Ocean of PDF", ignoreCase = true) &&
                                                !clean.contains("Project Gutenberg", ignoreCase = true)
                                            ) {
                                                lastKnownBookTitle = clean
                                            }
                                            pageTitle = clean.ifBlank { siteName }
                                        }
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        currentUrl = url ?: ""
                                        isLoading = true
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        currentUrl = url ?: ""
                                        isLoading = false
                                    }

                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val reqUri = request?.url ?: return false
                                        val scheme = reqUri.scheme?.lowercase(java.util.Locale.ROOT)
                                        // Block non-web schemes for complete device security
                                        if (scheme != "https") {
                                            return true
                                        }
                                        if (!request.isForMainFrame) return false
                                        val url = reqUri.toString()
                                        if (url.endsWith(".pdf", ignoreCase = true) ||
                                            url.endsWith(".epub", ignoreCase = true) ||
                                            url.endsWith(".txt", ignoreCase = true) ||
                                            url.contains("/download/", ignoreCase = true) ||
                                            url.contains("download_file", ignoreCase = true) ||
                                            url.contains("/files/", ignoreCase = true) && (url.contains(".epub") || url.contains(".pdf"))
                                        ) {
                                            if (!isDownloading && pendingDownload == null) pendingDownload = BookDownloadRequest(url, settings.userAgentString, null, null)
                                            return true
                                        }
                                        return false
                                    }
                                }

                                if (runCatching { com.veritas.reader.SecureDownloads.httpsUrl(resolvedInitialUrl) }.isSuccess) loadUrl(resolvedInitialUrl)
                            }
                        },
                        onRelease = { view ->
                            view.stopLoading()
                            view.setDownloadListener(null)
                            view.webViewClient = WebViewClient()
                            view.webChromeClient = null
                            view.removeAllViews()
                            view.destroy()
                            if (webViewInstance === view) webViewInstance = null
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
    pendingDownload?.let { download ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDownload = null },
            title = { Text("Download and add this book?") },
            text = { Text("From ${Uri.parse(download.url).host}. Only supported book files will be imported.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = {
                pendingDownload = null
                downloadAndImport(download.url, download.userAgent, download.disposition, download.mimeType)
            }) { Text("Download") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { pendingDownload = null }) { Text("Cancel") } }
        )
    }

}

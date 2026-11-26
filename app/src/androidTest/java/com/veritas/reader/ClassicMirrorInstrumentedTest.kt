package com.veritas.reader

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

class ClassicMirrorInstrumentedTest {
    @Test fun sameEditionDownloadsFromTheMirrorWhenThePrimaryIsUnavailable() = runBlocking(Dispatchers.IO) {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Run explicitly on a device with internet access",
            arguments.getString("runLiveClassicMirror") == "true")
        val book = requireNotNull(resolveClassicDownload("classic_pilgrims_progress:modern"))
        val previousProxy = ProxySelector.getDefault()
        val loopbackPort = arguments.getString("classicMirrorProxyPort")?.let { it.toInt() }
        if (loopbackPort != null) {
            require(loopbackPort in 1024..65535)
            val mirrorHost = URI(classicTextSources(book.downloadUrl).last().url).host
            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", loopbackPort))
            ProxySelector.setDefault(object : ProxySelector() {
                override fun select(uri: URI): List<Proxy> =
                    if (uri.host == mirrorHost) listOf(proxy) else previousProxy?.select(uri) ?: listOf(Proxy.NO_PROXY)

                override fun connectFailed(uri: URI, address: SocketAddress, error: IOException) {
                    previousProxy?.connectFailed(uri, address, error)
                }
            })
        }
        val text = try {
            downloadClassicText(book.downloadUrl) { source ->
                if (source.url == book.downloadUrl) throw IOException("Simulated HTTP 503")
                fetchClassicText(source)
            }
        } finally {
            if (loopbackPort != null) ProxySelector.setDefault(previousProxy)
        }
        assertTrue(text.length > 50_000)
        assertTrue(text.contains("Pilgrim", ignoreCase = true))
        assertFalse(text.contains('\uFFFD'))
    }
}

package com.veritas.reader

import com.veritas.reader.ui.screens.NoteLink
import com.veritas.reader.ui.screens.NoteLinks
import com.veritas.reader.ui.screens.NotePreviewCache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class NotePreviewCacheTest {
    @Test
    fun viewingTheCacheDoesNotFetchAndConcurrentLoadsShareOneResult() = runBlocking {
        val cache = NotePreviewCache<String>(2)
        val calls = AtomicInteger()
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<String>()

        assertNull(cache.cached("page"))
        assertEquals(0, calls.get())

        val first = async {
            cache.load("page") {
                calls.incrementAndGet()
                started.complete(Unit)
                finish.await()
            }
        }
        started.await()
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            cache.load("page") { error("The page is already loading") }
        }
        assertNull(cache.cached("page"))
        finish.complete("Website title")
        assertEquals("Website title", first.await())
        assertEquals("Website title", second.await())
        assertEquals("Website title", cache.cached("page"))
        assertEquals(1, calls.get())
    }

    @Test
    fun failedLoadsCanBeRetriedAndSuccessfulValuesAreBounded() = runBlocking {
        val cache = NotePreviewCache<String>(2)
        assertNull(cache.load("a") { null })
        assertEquals("A", cache.load("a") { "A" })
        cache.load("b") { "B" }
        assertEquals("A", cache.cached("a"))

        cache.load("c") { "C" }

        assertNull(cache.cached("b"))
        assertEquals("A", cache.cached("a"))
        assertEquals("C", cache.cached("c"))
    }

    @Test
    fun newlyLoadedTitleReplacesTheStoredTitleAndKeepsTheSavedImage() {
        val saved = NoteLink("https://example.test/a", "Old title", "https://example.test/a.png")
        val fresh = NoteLink(saved.url, "Website title")
        assertEquals(saved.copy(title = "Website title"), NoteLinks.withMetadata(saved, fresh))
        assertEquals(saved, NoteLinks.withMetadata(saved, fresh.copy(title = "example.test")))
        assertEquals(saved, NoteLinks.withMetadata(saved, fresh.copy(url = "https://example.test/b")))
        assertEquals(saved, NoteLinks.withMetadata(saved, null))
    }
}

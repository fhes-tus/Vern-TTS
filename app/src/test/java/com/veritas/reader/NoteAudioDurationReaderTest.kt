package com.veritas.reader

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class NoteAudioDurationReaderTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun concurrentRequestsShareCachedMetadataAndNeverReadOnTheCallerThread() = runBlocking {
        val file = temporary.newFile().apply { writeText("1000") }
        val caller = Thread.currentThread()
        val reads = AtomicInteger()
        val reader = NoteAudioDurationReader(readMetadata = {
            assertNotSame(caller, Thread.currentThread())
            reads.incrementAndGet()
            1000L
        })
        val results = List(12) { async { reader.read(file.path) } }.awaitAll()
        assertEquals(List(12) { 1000L }, results)
        assertEquals(1, reads.get())
    }

    @Test fun replacementAndExplicitRevisionInvalidateTheDuration() = runBlocking {
        val file = temporary.newFile().apply { writeText("1000") }
        val reader = NoteAudioDurationReader(readMetadata = { it.readText().toLong() })
        assertEquals(1000L, reader.read(file.path))
        val modified = file.lastModified()
        file.writeText("2000")
        assertTrue(file.setLastModified(modified + 2000))
        assertEquals(2000L, reader.read(file.path))
        val sameStamp = file.lastModified()
        file.writeText("3000")
        assertTrue(file.setLastModified(sameStamp))
        assertEquals(3000L, reader.read(file.path, revision = 1))
        file.delete()
        assertNull(reader.read(file.path, revision = 1))
    }

    @Test fun cacheEvictsOldEntriesInsteadOfGrowingWithTheLibrary() = runBlocking {
        val files = List(3) { temporary.newFile().apply { writeText("audio") } }
        val reads = AtomicInteger()
        val reader = NoteAudioDurationReader(readMetadata = { reads.incrementAndGet(); 1000L }, cacheLimit = 2)
        files.forEach { assertEquals(1000L, reader.read(it.path)) }
        assertEquals(1000L, reader.read(files.first().path))
        assertEquals(4, reads.get())
    }

    @Test fun failuresAreNotCachedAndMissingFilesAreNotOpened() = runBlocking {
        val file = temporary.newFile().apply { writeText("audio") }
        val reads = AtomicInteger()
        val reader = NoteAudioDurationReader(readMetadata = {
            if (reads.incrementAndGet() == 1) throw IOException("temporarily unreadable")
            2000L
        })
        assertNull(reader.read(""))
        assertNull(reader.read(file.path + ".missing"))
        assertEquals(0, reads.get())
        assertNull(reader.read(file.path))
        assertEquals(2000L, reader.read(file.path))
    }

    @Test fun cancelledBlockingReadCannotPublishOrPopulateTheCache() = runBlocking {
        val file = temporary.newFile().apply { writeText("audio") }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val reads = AtomicInteger()
        val reader = NoteAudioDurationReader(readMetadata = {
            if (reads.incrementAndGet() == 1) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
            1000L
        })
        val first = async { reader.read(file.path) }
        try {
            // Yield to the child before waiting on the test thread.
            kotlinx.coroutines.yield()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            first.cancel()
        } finally { release.countDown() }
        first.join()
        assertTrue(first.isCancelled)
        assertEquals(1000L, reader.read(file.path))
        assertEquals(2, reads.get())
    }

    @Test fun fileChangedDuringReadIsNotPublishedOrCached() = runBlocking {
        val file = temporary.newFile().apply { writeText("old") }
        val reads = AtomicInteger()
        val reader = NoteAudioDurationReader(readMetadata = {
            if (reads.incrementAndGet() == 1) file.writeText("replacement with a different size")
            2000L
        })
        assertNull(reader.read(file.path))
        assertEquals(2000L, reader.read(file.path))
        assertEquals(2, reads.get())
    }

    @Test fun differentRecordingsStillHaveOnlyOneNativeReadInFlight() = runBlocking {
        val files = List(8) { temporary.newFile().apply { writeText("audio") } }
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val reader = NoteAudioDurationReader(readMetadata = {
            val count = active.incrementAndGet()
            peak.updateAndGet { previous -> maxOf(previous, count) }
            try { Thread.sleep(5); 1000L } finally { active.decrementAndGet() }
        })
        assertEquals(List(8) { 1000L }, files.map { async { reader.read(it.path) } }.awaitAll())
        assertEquals(1, peak.get())
    }
}

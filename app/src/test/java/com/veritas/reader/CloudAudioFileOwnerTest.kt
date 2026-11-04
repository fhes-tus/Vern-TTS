package com.veritas.reader

import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

class CloudAudioFileOwnerTest {
    @get:Rule val folder = TemporaryFolder()
    private val key = "a".repeat(64)

    @Test fun stoppingAfterDownloadRemovesUncachedAudio() {
        val directory = folder.newFolder()
        val owner = CloudAudioFileOwner()
        val file = owner.publish(directory,key,byteArrayOf(1,2,3),false)
        assertTrue(file.isFile)
        owner.cancel()
        assertFalse(file.exists())
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test fun deliberateCacheSurvivesStopping() {
        val directory = folder.newFolder()
        val owner = CloudAudioFileOwner()
        val file = owner.publish(directory,key,byteArrayOf(1,2,3),true)
        owner.cancel()
        assertArrayEquals(byteArrayOf(1,2,3),file.readBytes())
        assertEquals("$key.audio",file.name)
    }

    @Test fun cancelledOwnerNeverPublishesLateAudio() {
        val directory = folder.newFolder()
        val owner = CloudAudioFileOwner()
        owner.cancel()
        assertThrows(CancellationException::class.java) { owner.publish(directory,key,byteArrayOf(1),false) }
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test fun simultaneousPublicationAndStopLeaveNoTemporaryAudio() {
        repeat(50) {
            val directory = folder.newFolder()
            val owner = CloudAudioFileOwner()
            val start = CountDownLatch(1)
            val failure = AtomicReference<Throwable?>()
            val publish = Thread {
                start.await()
                try { owner.publish(directory,key,ByteArray(8192),false) }
                catch (_: CancellationException) { }
                catch (error: Throwable) { failure.set(error) }
            }
            val cancel = Thread { start.await(); owner.cancel() }
            publish.start(); cancel.start(); start.countDown()
            publish.join(5000); cancel.join(5000)
            assertFalse(publish.isAlive || cancel.isAlive)
            assertNull(failure.get())
            assertTrue(directory.listFiles()!!.isEmpty())
        }
    }
}

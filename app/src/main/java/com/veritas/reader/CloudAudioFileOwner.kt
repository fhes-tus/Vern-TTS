package com.veritas.reader

import kotlinx.coroutines.CancellationException
import java.io.File
import java.util.UUID

/** Serializes cancellation with publication, including cancellation during an IO-to-main handoff. */
internal class CloudAudioFileOwner {
    private val lock = Any()
    private var cancelled = false
    private var temporary: File? = null

    fun cancel() = synchronized(lock) {
        cancelled = true
        temporary?.delete()
        temporary = null
    }

    fun publish(directory: File, key: String, audio: ByteArray, retain: Boolean): File {
        require(key.matches(Regex("[a-f0-9]{64}")))
        check(directory.isDirectory || directory.mkdirs())
        val result = File(directory,if(retain) "$key.audio" else "temp-${UUID.randomUUID()}.audio")
        val part = File(directory,"${UUID.randomUUID()}.part")
        try {
            part.writeBytes(audio)
            synchronized(lock) {
                if(cancelled) throw CancellationException()
                check(part.renameTo(result))
                if(!retain) temporary = result
            }
            return result
        } finally { part.delete() }
    }
}

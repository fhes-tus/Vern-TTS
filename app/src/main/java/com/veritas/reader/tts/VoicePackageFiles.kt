package com.veritas.reader.tts

import java.io.File

/** Same-directory publication for shared models with a recoverable previous installation. */
internal object VoicePackageFiles {
    private val publicationLock = Any()

    fun previous(destination: File): File = File(destination.parentFile, ".${destination.name}.previous")

    fun installedDirectory(destination: File, complete: (File) -> Boolean): File = synchronized(publicationLock) {
        val previous = previous(destination)
        if (!previous.exists() || complete(destination)) return@synchronized destination
        if (!destination.exists() && previous.renameTo(destination)) return@synchronized destination
        if (complete(previous)) previous else destination
    }

    fun publish(
        staging: File,
        destination: File,
        complete: (File) -> Boolean,
        move: (File, File) -> Boolean = File::renameTo
    ): Boolean = synchronized(publicationLock) {
        require(staging.parentFile == destination.parentFile && staging != destination)
        if (!complete(staging)) return@synchronized false
        val previous = previous(destination)
        if (previous.exists() && complete(destination) && !previous.deleteRecursively()) return@synchronized false
        if (destination.exists()) {
            // An earlier interrupted replacement can leave a damaged target beside its old copy.
            if (previous.exists()) {
                if (!destination.deleteRecursively()) return@synchronized false
            } else if (!move(destination, previous)) return@synchronized false
        }
        try {
            if (!move(staging, destination)) return@synchronized false
            if (!complete(destination)) {
                destination.deleteRecursively()
                return@synchronized false
            }
            previous.deleteRecursively()
            true
        } finally {
            if (!destination.exists() && previous.exists()) move(previous, destination)
        }
    }

    fun delete(destination: File): Boolean = synchronized(publicationLock) {
        // Explicit deletion removes the recovery copy before deleting the live package.
        previous(destination).deleteRecursively() && destination.deleteRecursively()
    }
}

package com.veritas.reader

import java.io.File

/** First access in each process removes uncached fragments left by a killed previous process. */
internal object CloudAudioCache {
    private val initialized = mutableSetOf<String>()
    @Synchronized fun directory(cacheRoot: File): File {
        val directory = File(cacheRoot,"cloud_voice").apply { mkdirs() }
        if(initialized.add(directory.absolutePath)) directory.listFiles().orEmpty().filter {
            it.isFile && (it.name.matches(Regex("temp-[a-f0-9-]+\\.audio")) || it.name.matches(Regex("[a-f0-9-]+\\.part")))
        }.forEach { it.delete() }
        return directory
    }
}

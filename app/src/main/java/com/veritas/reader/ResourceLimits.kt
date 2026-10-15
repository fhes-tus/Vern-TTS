package com.veritas.reader

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

internal object ResourceLimits {
    const val DOCUMENT_INPUT_BYTES = 64L * 1024 * 1024
    const val BOOK_DOWNLOAD_BYTES = 256L * 1024 * 1024
    const val MODEL_DOWNLOAD_BYTES = 512L * 1024 * 1024
    const val MODEL_EXPANDED_BYTES = 1024L * 1024 * 1024
    fun copy(input: InputStream, output: OutputStream, limit: Long, checkActive: () -> Unit = {}): Long {
        val buffer = ByteArray(32 * 1024)
        var total = 0L
        while (true) {
            checkActive()
            val count = input.read(buffer)
            if (count < 0) return total
            require(count.toLong() <= limit - total) { "File exceeds the ${limit / (1024 * 1024)} MB safety limit." }
            output.write(buffer, 0, count)
            total += count
        }
    }
    fun read(input: InputStream, limit: Long = DOCUMENT_INPUT_BYTES): ByteArray =
        ByteArrayOutputStream().also { copy(input, it, limit) }.toByteArray()
}

internal data class ArchiveLimits(
    val entries: Int = 20_000,
    val entryBytes: Long = 16L * 1024 * 1024,
    val expandedBytes: Long = 128L * 1024 * 1024
)

/** Counts all expanded bytes, including entries skipped by getNextEntry/closeEntry. */
internal class BoundedZipInputStream(input: InputStream, private val limits: ArchiveLimits = ArchiveLimits()) : ZipInputStream(input) {
    private var entryCount = 0
    private var entryRead = 0L
    private var totalRead = 0L
    override fun getNextEntry(): ZipEntry? {
        val entry = super.getNextEntry() ?: return null
        require(++entryCount <= limits.entries) { "Document archive has too many entries." }
        entryRead = 0
        require(entry.size <= limits.entryBytes) { "Document archive resource is too large." }
        return entry
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val count = super.read(buffer, offset, length)
        if (count > 0) {
            require(count.toLong() <= limits.entryBytes - entryRead) { "Document archive resource is too large." }
            require(count.toLong() <= limits.expandedBytes - totalRead) { "Document archive expands beyond its safety limit." }
            entryRead += count
            totalRead += count
        }
        return count
    }
}

package com.veritas.reader

import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/** Entry indexes retain names, not the complete ZIP or all embedded media. */
internal class OriginalArchiveEntries(private val file: File) {
    val names: Set<String> = ZipFile(file).use { zip ->
        val names = linkedSetOf<String>()
        var count = 0
        zip.entries().asSequence().forEach {
            require(++count <= 20_000) { "The document archive has too many entries." }
            if (!it.isDirectory) require(names.add(it.name)) { "Document archive contains duplicate resources." }
        }
        names
    }
    private val images = object : LinkedHashMap<String, ByteArray>(8, 0.75f, true) {}
    private var cachedBytes = 0
    private val admittedText = mutableSetOf<String>()
    private var admittedTextBytes = 0L
    fun textMap(predicate: (String) -> Boolean): Map<String, String> = lazyMap(names.filter(predicate).toSet()) { name ->
        val bytes = read(name, 16 * 1024 * 1024)
        synchronized(admittedText) {
            if (name !in admittedText) {
                require(bytes.size <= 128L * 1024 * 1024 - admittedTextBytes) { "Document text expands beyond its safety limit." }
                admittedText.add(name)
                admittedTextBytes += bytes.size
            }
        }
        String(bytes, Charsets.UTF_8)
    }
    fun imageMap(predicate: (String) -> Boolean): Map<String, ByteArray> = lazyMap(names.filter(predicate).toSet()) { name ->
        synchronized(images) {
            images[name] ?: read(name, 16 * 1024 * 1024).also { bytes ->
                if (bytes.size <= 4 * 1024 * 1024) {
                    while (cachedBytes + bytes.size > 4 * 1024 * 1024 && images.isNotEmpty()) {
                        val first = images.entries.iterator(); val entry = first.next()
                        cachedBytes -= entry.value.size; first.remove()
                    }
                    images[name] = bytes; cachedBytes += bytes.size
                }
            }
        }
    }
    private fun read(name: String, limit: Int): ByteArray = ZipFile(file).use { zip ->
        val entry = requireNotNull(zip.getEntry(name)) { "Missing archive entry." }
        require(entry.size <= limit) { "A document resource is too large to display." }
        zip.getInputStream(entry).use { readBounded(it, limit) }
    }
    companion object {
        fun readBounded(input: InputStream, limit: Int): ByteArray {
            val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer); if (count < 0) break
                require(output.size().toLong() + count <= limit) { "A document resource is too large to display." }
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }
        private fun <T> lazyMap(names: Set<String>, read: (String) -> T): Map<String, T> = object : AbstractMap<String, T>() {
            override val entries: Set<Map.Entry<String, T>> = object : AbstractSet<Map.Entry<String, T>>() {
                override val size = names.size
                override fun iterator(): Iterator<Map.Entry<String, T>> = names.map { name ->
                    object : Map.Entry<String, T> {
                        override val key = name
                        override val value: T get() = read(name)
                    }
                }.iterator()
            }
            override fun get(key: String): T? = if (key in names) read(key) else null
            override fun containsKey(key: String) = key in names
        }
    }
}

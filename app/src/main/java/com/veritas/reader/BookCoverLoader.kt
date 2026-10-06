package com.veritas.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File

/** Small shared cache; decode bounds first and keep all file work off the UI thread. */
object BookCoverLoader {
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    private fun decode(open: () -> java.io.InputStream): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 400 || bounds.outHeight / sample > 600) sample *= 2
        return open().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    }

    fun asset(context: Context, catalogId: String): Bitmap? {
        if (catalogId.isBlank()) return null
        val key = "asset:$catalogId"
        cache.get(key)?.let { return it }
        return runCatching { decode { context.assets.open("covers/${catalogId.substringBefore(':')}.jpg") } }.getOrNull()
            ?.also { cache.put(key, it) }
    }

    fun document(context: Context, documentId: String, catalogId: String = ""): Bitmap? {
        val file = CoverExtractor.coverFile(context, documentId)
        if (file != null && file.isFile) {
            val key = "${file.absolutePath}:${file.lastModified()}:${file.length()}"
            cache.get(key)?.let { return it }
            runCatching { decode { file.inputStream() } }.getOrNull()?.let { cache.put(key, it); return it }
        }
        return asset(context, catalogId)
    }
}

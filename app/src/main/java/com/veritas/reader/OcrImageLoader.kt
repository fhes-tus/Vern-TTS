package com.veritas.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.File
import kotlin.math.min
import kotlin.math.sqrt

/** Decode orientation-aware images to at most 4 MP, without allocating the original pixels. */
internal object OcrImageLoader {
    fun load(context: Context, uri: Uri, maxDimension: Int = 2560, maxPixels: Int = 4_000_000,
        checkActive: () -> Unit = {}): Bitmap {
        val staging = File.createTempFile("ocr_decode_", ".image", context.cacheDir)
        try {
            val input = context.contentResolver.openInputStream(uri) ?: error("The selected photo is unavailable.")
            input.use { source -> staging.outputStream().use { ResourceLimits.copy(source, it, 32L * 1024 * 1024, checkActive) } }
            checkActive()
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(staging)) { decoder, info, _ ->
                val width = info.size.width
                val height = info.size.height
                require(width > 0 && height > 0) { "This photo could not be read." }
                val scale = min(1.0, min(maxDimension.toDouble() / maxOf(width, height), sqrt(maxPixels.toDouble() / (width.toDouble() * height))))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSize((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1))
                decoder.setOnPartialImageListener { false }
            }
        } finally { staging.delete() }
    }
}

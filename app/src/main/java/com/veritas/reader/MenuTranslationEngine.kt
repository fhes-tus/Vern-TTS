package com.veritas.reader

/** Base/optional-feature boundary. No ML Kit translation classes may enter the base APK. */
interface MenuTranslationEngine {
    fun client(target: String): MenuTranslator
    suspend fun isDownloaded(target: String): Boolean
}

interface MenuTranslator : java.io.Closeable {
    suspend fun prepare(allowMobile: Boolean)
    suspend fun translate(source: String): String
}

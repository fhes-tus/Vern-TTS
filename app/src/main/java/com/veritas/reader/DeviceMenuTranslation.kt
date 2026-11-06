package com.veritas.reader

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal object DeviceMenuTranslation {
    fun target(device: Locale, override: String): String? {
        val tag = device.toLanguageTag()
        if (override.isNotEmpty() || AppLanguageCatalog.canonicalTag(tag) != null) return null
        // The SDK advertises one Chinese model and doesn't distinguish Traditional Chinese.
        if (device.language == "zh") return null
        return TranslateLanguage.fromLanguageTag(tag.substringBefore('-'))
    }

    fun client(target: String): Translator = Translation.getClient(TranslatorOptions.Builder()
        .setSourceLanguage(TranslateLanguage.ENGLISH).setTargetLanguage(target).build())

    suspend fun isDownloaded(target: String): Boolean = RemoteModelManager.getInstance()
        .isModelDownloaded(TranslateRemoteModel.Builder(target).build()).awaitResult()

    suspend fun prepare(client: Translator, allowMobile: Boolean) {
        val conditions = DownloadConditions.Builder()
        if (!allowMobile) conditions.requireWifi()
        client.downloadModelIfNeeded(conditions.build()).awaitResult()
    }

    suspend fun translate(client: Translator, source: String): String = client.translate(source).awaitResult()

    data class Transfer(val running: Boolean, val percent: Int?)

    /** ML Kit owns the download. This is optional progress from its app-owned DownloadManager row. */
    fun transfer(context: Context, target: String): Transfer? = runCatching {
        val manager = context.getSystemService(DownloadManager::class.java)
        val active = DownloadManager.STATUS_PENDING or DownloadManager.STATUS_RUNNING or DownloadManager.STATUS_PAUSED
        manager.query(DownloadManager.Query().setFilterByStatus(active))?.use { cursor ->
            while (cursor.moveToNext()) {
                val url = Uri.parse(cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_URI)))
                if (!isModelUrl(url, target)) continue
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val current = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val percent = if (total > 0 && current >= 0) (current.coerceAtMost(total) * 100.0 / total).toInt() else null
                return@use Transfer(status == DownloadManager.STATUS_RUNNING, percent)
            }
            null
        }
    }.getOrNull()

    private fun isModelUrl(url: Uri, target: String): Boolean {
        val host = url.host
        return url.scheme == "https" && (host == "dl.google.com" || host?.endsWith(".gvt1.com") == true) &&
            url.path?.startsWith("/edgedl/translate/offline/") == true &&
            url.lastPathSegment in setOf("${target}_en.zip", "en_${target}.zip")
    }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { result ->
            if (!continuation.isActive) return@addOnCompleteListener
            when {
                result.isCanceled -> continuation.cancel()
                result.isSuccessful -> continuation.resume(result.result)
                else -> continuation.resumeWithException(result.exception ?: IllegalStateException("Translation task failed"))
            }
        }
    }
}

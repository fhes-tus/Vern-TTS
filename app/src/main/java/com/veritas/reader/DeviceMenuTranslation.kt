package com.veritas.reader

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.android.play.core.splitcompat.SplitCompat
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallException
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal object DeviceMenuTranslation {
    const val MODULE = "menu_translation"
    // ML Kit 17.0.3's supported tags; usable before loading the optional SDK.
    private val supported = ("af ar be bg bn ca cs cy da de el en eo es et fa fi fr ga gl gu he hi hr ht hu " +
        "id is it ja ka kn ko lt lv mk mr ms mt nl no pl pt ro ru sk sl sq sv sw ta te th tl tr uk ur vi zh").split(' ').toSet()

    fun target(device: Locale, override: String): String? {
        val tag = device.toLanguageTag()
        if (override.isNotEmpty() || AppLanguageCatalog.canonicalTag(tag) != null) return null
        // The SDK advertises one Chinese model and doesn't distinguish Traditional Chinese.
        if (device.language == "zh") return null
        return device.language.takeIf { it in supported }
    }

    private fun engine(): MenuTranslationEngine = Class.forName(
        "com.veritas.reader.menutranslation.MlKitMenuTranslationEngine"
    ).getDeclaredConstructor().newInstance() as MenuTranslationEngine

    fun client(target: String): MenuTranslator = engine().client(target)
    suspend fun isDownloaded(target: String): Boolean = engine().isDownloaded(target)
    suspend fun prepare(client: MenuTranslator, allowMobile: Boolean) = client.prepare(allowMobile)
    suspend fun translate(client: MenuTranslator, source: String): String = client.translate(source)

    fun installed(context: Context): Boolean = MODULE in SplitInstallManagerFactory.create(context).installedModules

    /** Called exclusively by the enabled automatic-translation session. */
    suspend fun install(context: Context, progress: (SplitInstallSessionState) -> Unit) {
        if (installed(context)) { SplitCompat.install(context); return }
        val manager = SplitInstallManagerFactory.create(context)
        val terminal = setOf(SplitInstallSessionStatus.FAILED, SplitInstallSessionStatus.CANCELED, SplitInstallSessionStatus.INSTALLED)
        val existing = manager.sessionStates.awaitResult().firstOrNull {
            MODULE in it.moduleNames() && it.status() !in terminal
        }
        val request = if (existing == null) manager.startInstall(SplitInstallRequest.newBuilder().addModule(MODULE).build()) else null
        var sessionId = existing?.sessionId()
        try {
            if (sessionId == null) sessionId = requireNotNull(request).awaitResult()
            while (true) {
                val state = manager.getSessionState(sessionId).awaitResult()
                progress(state)
                when (state.status()) {
                    SplitInstallSessionStatus.INSTALLED -> { SplitCompat.install(context); return }
                    SplitInstallSessionStatus.CANCELED -> throw IllegalStateException("Translation installation cancelled")
                    SplitInstallSessionStatus.FAILED -> throw SplitInstallException(state.errorCode())
                }
                delay(500)
            }
        } catch (cancelled: CancellationException) {
            if (sessionId != null) manager.cancelInstall(sessionId)
            else request?.addOnSuccessListener { manager.cancelInstall(it) }
            throw cancelled
        }
    }

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

package com.veritas.reader.menutranslation

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.TranslatorOptions
import com.veritas.reader.MenuTranslationEngine
import com.veritas.reader.MenuTranslator
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Loaded only after Play has installed menu_translation. Kept by the base R8 rules. */
class MlKitMenuTranslationEngine : MenuTranslationEngine {
    override fun client(target: String): MenuTranslator {
        val translator = Translation.getClient(TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH).setTargetLanguage(target).build())
        return object : MenuTranslator {
            override suspend fun prepare(allowMobile: Boolean) {
                val conditions = DownloadConditions.Builder()
                if (!allowMobile) conditions.requireWifi()
                translator.downloadModelIfNeeded(conditions.build()).awaitResult()
            }
            override suspend fun translate(source: String): String = translator.translate(source).awaitResult()
            override fun close() = translator.close()
        }
    }

    override suspend fun isDownloaded(target: String): Boolean = RemoteModelManager.getInstance()
        .isModelDownloaded(TranslateRemoteModel.Builder(target).build()).awaitResult()

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { result ->
            if (continuation.isActive) when {
                result.isCanceled -> continuation.cancel()
                result.isSuccessful -> continuation.resume(result.result)
                else -> continuation.resumeWithException(result.exception ?: IllegalStateException("Translation task failed"))
            }
        }
    }
}

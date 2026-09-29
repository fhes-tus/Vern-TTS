package com.veritas.reader

import com.google.mlkit.nl.languageid.LanguageIdentification
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object LanguageDetector {
    suspend fun detectLanguage(text: String): String {
        if (text.isBlank()) return "en"
        return runCatching {
            withTimeoutOrNull(1000L) {
                suspendCancellableCoroutine { continuation ->
                    runCatching {
                        val sample = text.take(2000)
                        val identifier = LanguageIdentification.getClient()
                        identifier.identifyLanguage(sample)
                            .addOnSuccessListener { languageCode ->
                                val result = if (languageCode == "und") "en" else languageCode
                                if (continuation.isActive) continuation.resume(result)
                            }
                            .addOnFailureListener {
                                if (continuation.isActive) continuation.resume("en")
                            }
                    }.onFailure {
                        if (continuation.isActive) continuation.resume("en")
                    }
                }
            }
        }.getOrNull() ?: "en"
    }
}

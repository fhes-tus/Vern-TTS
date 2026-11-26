package com.veritas.reader

import java.net.URI

internal const val AI_STUDY_EXCERPT_LIMIT = 25_000
internal const val AI_STUDY_LONG_EXCERPT_LIMIT = 30_000

/** Captured once per request, including the credential and the destination it belongs to. */
internal class AiStudyProfile private constructor(
    val provider: AiProvider,
    val model: String,
    val endpoint: String,
    val apiKey: String
) {
    companion object {
        fun create(provider: AiProvider, model: String, endpoint: String, apiKey: String): AiStudyProfile {
            val requestedModel = model.trim().ifBlank { provider.defaultModel }
            val compatibleModel = when (provider) {
                AiProvider.GEMINI -> when (requestedModel.removePrefix("models/")) {
                    "gemini-1.5-pro", "gemini-2.0-flash", "gemini-2.0-flash-001" -> provider.defaultModel
                    else -> requestedModel
                }
                AiProvider.OPENAI -> if (requestedModel == "gpt-4.5-preview") provider.defaultModel else requestedModel
                AiProvider.ANTHROPIC -> when (requestedModel) {
                    "claude-3-7-sonnet-latest", "claude-3-7-sonnet-20250219" -> "claude-sonnet-5-5"
                    else -> requestedModel
                }
                else -> requestedModel
            }
            val cleanModel = if (provider == AiProvider.GEMINI) {
                compatibleModel.removePrefix("models/").also {
                    require(it.isNotEmpty() && it.all { char -> char.isLetterOrDigit() || char in "._-" }) { "Enter a valid Gemini model ID." }
                }
            } else compatibleModel
            val cleanKey = apiKey.trim()
            require(cleanKey.length <= 8192 && cleanKey.all { it in '!'..'~' }) { "Enter a valid API key." }
            val destination = if (provider == AiProvider.CUSTOM) endpoint.trim() else provider.defaultEndpoint
            if (provider == AiProvider.CUSTOM && (destination.isNotEmpty() || cleanKey.isNotEmpty())) {
                require(isSecureEndpoint(destination)) { "Enter a complete HTTPS endpoint without embedded credentials." }
            }
            if (provider == AiProvider.CUSTOM && cleanKey.isNotEmpty()) {
                require(cleanModel.isNotEmpty()) { "Enter the custom endpoint's model ID." }
            }
            return AiStudyProfile(provider, cleanModel, destination, cleanKey)
        }

        private fun isSecureEndpoint(value: String): Boolean = runCatching {
            val uri = URI(value)
            uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() &&
                uri.userInfo == null && uri.fragment == null
        }.getOrDefault(false)
    }
}

internal fun studyRequestText(scopeText: String?, wholeDocumentText: () -> String): String {
    val text = scopeText ?: wholeDocumentText()
    require(text.isNotBlank()) { "Choose a passage that contains readable text." }
    return text
}

internal fun explicitStudyPageRange(first: String, last: String, pageCount: Int): IntRange? {
    val start = first.toIntOrNull() ?: return null
    val end = last.toIntOrNull() ?: return null
    return (start..end).takeIf { start in 1..pageCount && end in start..pageCount }
}

internal fun boundedStudySource(text: String, limit: Int): String {
    val excerpt = text.take(limit)
    require(excerpt.isNotBlank()) { "Choose a passage that contains readable text." }
    return if (excerpt.last().isHighSurrogate()) excerpt.dropLast(1) else excerpt
}

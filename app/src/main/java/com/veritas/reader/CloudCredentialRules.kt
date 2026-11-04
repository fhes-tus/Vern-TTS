package com.veritas.reader

/** Provider keys are HTTP header tokens. Reject controls and embedded whitespace before storage or IO. */
internal object CloudCredentialRules {
    fun normalize(value: String, required: Boolean): String {
        val token = value.trim()
        if((required && token.isEmpty()) || token.length > 8192 || token.any { it !in '!'..'~' })
            throw CloudSpeechException(R.string.cloud_error_key)
        return token
    }
}

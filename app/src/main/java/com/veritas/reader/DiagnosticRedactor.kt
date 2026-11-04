package com.veritas.reader

/** Defense in depth for user-shared crash diagnostics; never inspect or load stored credentials. */
internal object DiagnosticRedactor {
    private val quotedSecrets = Regex("(?i)([\"'](?:api[_-]?key|xi-api-key|x-api-key|authorization|credential|access_token|secret)[\"']\\s*:\\s*)[\"'][^\"'\\r\\n]*[\"']")
    private val headerSecrets = Regex("(?im)((?:authorization|xi-api-key|x-api-key|api[_-]?key|access_token|credential|secret)\\s*[:=]\\s*)[^\\r\\n,;}]+")
    private val bearer = Regex("(?i)\\bBearer\\s+[^\\s\"',;}]+")
    private val providerKeys = Regex("\\bsk[-_][A-Za-z0-9_-]{8,}")
    private val urls = Regex("https?://[^\\s<>\"']+",RegexOption.IGNORE_CASE)

    fun redact(value: String): String = value
        .replace(quotedSecrets) { it.groupValues[1]+"\"[redacted]\"" }
        .replace(headerSecrets) { it.groupValues[1]+"[redacted]" }
        .replace(bearer,"Bearer [redacted]")
        .replace(providerKeys,"[redacted key]")
        .replace(urls,"[redacted URL]")
}

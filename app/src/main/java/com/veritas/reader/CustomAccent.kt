package com.veritas.reader

/** Store opaque RGB only, so backups and older settings have a predictable fallback. */
internal fun normalizeCustomAccent(value: String): String {
    val hex = value.trim().removePrefix("#")
    return if (hex.matches(Regex("[0-9a-fA-F]{6}"))) "#${hex.uppercase(java.util.Locale.ROOT)}" else ""
}

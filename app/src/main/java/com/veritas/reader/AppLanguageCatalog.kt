package com.veritas.reader

import java.util.Locale

/** Interface languages only: never use this preference to select a document or TTS language. */
object AppLanguageCatalog {
    data class Choice(val tag: String, val nativeName: String)

    val choices = listOf(
        Choice("en", "English"),
        Choice("fr", "Français"),
        Choice("es", "Español"),
        Choice("hi", "हिन्दी"),
        Choice("zh-Hans", "简体中文"),
        Choice("ar", "العربية")
    )

    /** An unsupported explicit override is still an override, not the device-default choice. */
    fun selectionTag(rawTag: String): String = canonicalTag(rawTag) ?: Locale.forLanguageTag(rawTag).toLanguageTag()

    /** Region variants share translations; Traditional Chinese is not advertised as Simplified. */
    fun canonicalTag(tag: String): String? {
        if (tag.isBlank()) return ""
        val locale = Locale.forLanguageTag(tag)
        return when (locale.language) {
            "en", "fr", "es", "hi", "ar" -> locale.language
            "zh" -> when (locale.script) {
                "Hans" -> "zh-Hans"
                "Hant" -> null
                "" -> if (locale.country in setOf("TW", "HK", "MO")) null else "zh-Hans"
                else -> null
            }
            else -> null
        }
    }
}

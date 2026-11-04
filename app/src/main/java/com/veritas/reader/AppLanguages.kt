package com.veritas.reader

import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object AppLanguages {
    fun selectedTag(): String = AppCompatDelegate.getApplicationLocales().get(0)
        ?.toLanguageTag()?.let(AppLanguageCatalog::canonicalTag).orEmpty()

    /** Android/AndroidX owns persistence and synchronizes with Android 13+ app-language settings. */
    fun select(tag: String) {
        require(tag.isEmpty() || AppLanguageCatalog.choices.any { it.tag == tag })
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    /** Locale.getDefault() changes with the interface; narration must follow the phone instead. */
    fun systemSpeechLocale(): Locale = runCatching {
        Resources.getSystem().configuration.locales.get(0)
    }.getOrNull() ?: Locale.ENGLISH
}

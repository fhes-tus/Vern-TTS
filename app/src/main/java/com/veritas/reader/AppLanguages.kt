package com.veritas.reader

import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object AppLanguages {
    private val revision = MutableStateFlow(0)
    internal val changes = revision.asStateFlow()

    fun selectedTag(context: Context): String {
        val raw = if(Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.get(0)?.toLanguageTag().orEmpty()
        } else AppCompatDelegate.getApplicationLocales().get(0)?.toLanguageTag().orEmpty()
        return AppLanguageCatalog.selectionTag(raw)
    }

    /** Android/AndroidX owns persistence and synchronizes with Android 13+ app-language settings. */
    fun select(context: Context, tag: String) {
        require(tag.isEmpty() || AppLanguageCatalog.choices.any { it.tag == tag })
        if(Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales = android.os.LocaleList.forLanguageTags(tag)
        } else AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        revision.update { it + 1 }
    }

    fun deviceLocales(context: Context): LocaleListCompat = if(Build.VERSION.SDK_INT >= 33) {
        LocaleListCompat.wrap(context.getSystemService(LocaleManager::class.java).systemLocales)
    } else LocaleListCompat.wrap(Resources.getSystem().configuration.locales)

    /** Locale.getDefault() changes with the interface; narration must follow the phone instead. */
    fun systemSpeechLocale(): Locale = runCatching {
        Resources.getSystem().configuration.locales.get(0)
    }.getOrNull() ?: Locale.ENGLISH
}

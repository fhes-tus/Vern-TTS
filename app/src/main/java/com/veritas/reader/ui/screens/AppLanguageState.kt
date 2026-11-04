package com.veritas.reader.ui.screens

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.veritas.reader.AppLanguageCatalog
import com.veritas.reader.AppLanguages
import com.veritas.reader.R
import java.util.Locale

/** Also refresh on resume: clearing an English override on an English phone changes no resources. */
@Composable
internal fun rememberAppLanguageSelection(): String {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val configuration = LocalConfiguration.current
    val changed by AppLanguages.changes.collectAsState()
    var resumed by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _,event -> if(event == Lifecycle.Event.ON_RESUME) resumed++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return remember(context,configuration,changed,resumed) { AppLanguages.selectedTag(context) }
}

@Composable
internal fun deviceLanguageChoiceLabel(): String {
    val context = LocalContext.current
    val displayLocale = LocalConfiguration.current.locales[0]
    val device = AppLanguages.deviceLocales(context).get(0) ?: Locale.ENGLISH
    return stringResource(R.string.language_follow_device_named,device.getDisplayName(displayLocale))
}

@Composable
internal fun appLanguageSummary(selected: String): String = if(selected.isEmpty()) deviceLanguageChoiceLabel() else
    AppLanguageCatalog.choices.firstOrNull { it.tag == selected }?.nativeName ?: Locale.forLanguageTag(selected).displayName

package com.veritas.reader

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.ColorScheme

/** Resolve persisted appearance once for native reader chrome, dialogs and paper. */
internal fun veritasReaderColorScheme(settings: ReaderSettings, context: Context): ColorScheme {
    val systemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val themeId = VeritasThemeCatalog.resolveConcreteThemeId(settings.themeId, systemDark)
    return veritasPackColorScheme(
        base = veritasColorScheme(themeId, context),
        packId = settings.themePackId,
        themeId = themeId,
        amoledMode = settings.amoledMode
    )
}

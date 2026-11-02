package com.veritas.reader

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Packs keep their surfaces and shapes; custom colour supplies the shared accent roles. */
internal fun veritasAppearanceColorScheme(
    base: ColorScheme,
    packId: String,
    themeId: String,
    amoledMode: Boolean = false,
    customAccentColor: String = ""
): ColorScheme {
    val packed = veritasPackColorScheme(base, packId, themeId, amoledMode)
    val hex = normalizeCustomAccent(customAccentColor)
    if (hex.isEmpty() || themeId.contains("high_contrast")) return packed
    val seed = Color(0xFF000000L or hex.drop(1).toLong(16))
    val dark = packed.background.luminance() < .3f
    // Preserve the picker colour. Contrast correction belongs to the text drawn
    // on the accent, rather than silently replacing the user's chosen fill.
    val tinted = veritasReadableInk(packed.copy(
        primary = seed,
        secondary = seed,
        tertiary = seed,
        primaryContainer = blendColors(packed.surface, seed, if (dark) .24f else .18f),
        secondaryContainer = blendColors(packed.surface, seed, if (dark) .18f else .12f),
        tertiaryContainer = blendColors(packed.surface, seed, if (dark) .14f else .09f),
        surfaceTint = seed,
        inversePrimary = seed
    ))
    return if (dark) veritasDarkSurfaceScheme(tinted, amoledMode || themeId == "amoled") else tinted
}

package com.veritas.reader

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance

@Immutable
internal data class VeritasHeroColors(
    val container: Color,
    val content: Color,
    val secondaryContent: Color,
    val actionContainer: Color,
    val actionContent: Color,
    val progress: Color,
    val vibrant: Boolean = false
) {
    val brush get() = SolidColor(container)
}

/** One theme-colored reading surface, shared by Home and its appearance preview. */
internal fun veritasHeroColors(scheme: ColorScheme, vibrant: Boolean): VeritasHeroColors {
    if (!vibrant) {
        val tint = if (scheme.background.luminance() < .3f) .12f else .10f
        val container = blendColors(scheme.surface, scheme.primary, tint)
        return VeritasHeroColors(container, readableDocumentInk(scheme.onSurface, container),
            readableDocumentInk(scheme.onSurfaceVariant, container), scheme.primary, scheme.onPrimary, scheme.primary)
    }

    val primary = scheme.primary
    val low = minOf(primary.red, primary.green, primary.blue)
    val spread = maxOf(primary.red, primary.green, primary.blue) - low
    // Recover some chroma from dark-mode pastels without rotating the theme's hue.
    // Neutral palettes stay neutral; the card never acquires an unrelated accent.
    val hue = if (spread < .04f) primary else Color(
        (primary.red - low) / spread, (primary.green - low) / spread, (primary.blue - low) / spread)
    val seed = blendColors(primary, hue, .38f)
    val targetLuminance = if (scheme.background == Color.Black) .035f else .065f
    val darker = seed.luminance() > targetLuminance
    val destination = if (darker) Color.Black else Color.White
    var from = 0f
    var to = 1f
    repeat(18) {
        val amount = (from + to) / 2f
        val candidate = blendColors(seed, destination, amount)
        if ((candidate.luminance() > targetLuminance) == darker) from = amount else to = amount
    }
    val container = blendColors(seed, destination, to)
    val content = Color(0xFFF9FAFB)
    return VeritasHeroColors(container, content, blendColors(container, content, .84f),
        content, container, content, vibrant = true)
}

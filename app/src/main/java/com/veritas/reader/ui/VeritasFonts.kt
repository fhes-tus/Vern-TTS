package com.veritas.reader.ui

import android.content.Context
import android.graphics.Typeface
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.veritas.reader.R
import java.util.concurrent.ConcurrentHashMap

/**
 * The typefaces Veritas can be set in.
 *
 * Distinct font personalities:
 * - SYSTEM: Whatever your phone ships with
 * - GAZETTE: Editorial newsprint slab with bold ink-traps (Bitter)
 * - CRISP: Clean geometric sans with open curves (Outfit)
 * - TYPEWRITER: Fixed-width monospace with authentic draft character (JetBrains Mono)
 * - ATKINSON: Braille Institute high-contrast legibility (Atkinson Hyperlegible)
 */
enum class VeritasUiFont(
    val id: String,
    val label: String,
    val note: String
) {
    SYSTEM("system", "System default", "Whatever your phone ships with"),
    GAZETTE("gazette", "Gazette", "Editorial newsprint slab with bold ink-traps"),
    CRISP("crisp", "Crisp", "Clean geometric sans with open curves"),
    TYPEWRITER("typewriter", "Typewriter", "Fixed-width monospace with authentic draft character"),
    ATKINSON("atkinson", "Atkinson Hyperlegible", "Braille Institute; disambiguates similar letters");

    companion object {
        fun fromId(id: String?): VeritasUiFont = when (id) {
            "gazette", "bitter", "literata", "lora" -> GAZETTE
            "crisp", "outfit" -> CRISP
            "typewriter", "jetbrains_mono" -> TYPEWRITER
            "atkinson" -> ATKINSON
            "system" -> SYSTEM
            else -> entries.firstOrNull { it.id == id } ?: SYSTEM
        }
    }
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variableFamily(resId: Int): FontFamily = FontFamily(
    Font(resId, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(resId, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(resId, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(resId, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(resId, FontWeight.Black, variationSettings = FontVariation.Settings(FontVariation.weight(800)))
)

/** The [FontFamily] for a choice, or null to leave Compose on the platform default. */
fun VeritasUiFont.fontFamily(): FontFamily? = when (this) {
    VeritasUiFont.SYSTEM -> null
    VeritasUiFont.GAZETTE -> variableFamily(R.font.bitter_variable)
    VeritasUiFont.CRISP -> variableFamily(R.font.outfit)
    VeritasUiFont.TYPEWRITER -> FontFamily(Font(R.font.jetbrains_mono, FontWeight.Normal))
    VeritasUiFont.ATKINSON -> FontFamily(
        Font(R.font.atkinson_regular, FontWeight.Normal),
        Font(R.font.atkinson_bold, FontWeight.Bold)
    )
}

private val typefaceCache = ConcurrentHashMap<Int, Typeface>()

/** Android [Typeface] for native View rendering (e.g. TextView in ReaderPageItemView). */
fun VeritasUiFont.asTypeface(context: Context): Typeface {
    if (this == VeritasUiFont.SYSTEM) return Typeface.DEFAULT
    val resId = when (this) {
        VeritasUiFont.SYSTEM -> return Typeface.DEFAULT
        VeritasUiFont.GAZETTE -> R.font.bitter_variable
        VeritasUiFont.CRISP -> R.font.outfit
        VeritasUiFont.TYPEWRITER -> R.font.jetbrains_mono
        VeritasUiFont.ATKINSON -> R.font.atkinson_regular
    }
    return typefaceCache.getOrPut(resId) {
        try {
            ResourcesCompat.getFont(context, resId) ?: Typeface.DEFAULT
        } catch (_: Exception) {
            Typeface.DEFAULT
        }
    }
}

/**
 * The Veritas type ramp.
 *
 * Not Material 3 scaled by a constant. Two things shape it:
 *
 * 1. **The top of M3's ramp is far too generous for a phone reading app.** Nothing
 *    here is a marketing page; `displayLarge` at 57sp never had a job. The display
 *    and headline steps take the deepest cuts, titles a moderate one.
 *
 * 2. **The bottom cannot move.** `bodySmall`, `labelMedium` and `labelSmall` are
 *    the app's most-used roles (111, 45 and 35 call sites) and already sit at
 *    11-12sp. A user running system `font_scale` below 1.0 renders those smaller
 *    still — 11sp becomes 9.9sp at 0.9 — so they hold exactly where they are.
 *    Shrinking the whole ramp proportionally would have pushed captions past
 *    legibility to win space that the top of the ramp was wasting.
 *
 * The result compresses ~21% at the top and 0% at the bottom, which also opens up
 * M3's flat spots: stock has `titleSmall`, `bodyMedium` and `labelLarge` all at
 * 14sp and `bodySmall`/`labelMedium` both at 12sp, so a title and its caption were
 * separated only by weight. Here the title roles sit clear of the body roles.
 *
 * Line heights move with their sizes, each keeping its original ratio — shrinking
 * text while holding M3's line heights would have left the app looking airy and
 * loose rather than denser.
 *
 * `titleMedium` 15 / `bodyMedium` 13 were settled first, against the settings hub
 * on-device; the rest of the ramp is built to sit consistently around them.
 */
private data class Step(val size: Int, val lineHeight: Int)

private val RAMP = mapOf(
    // role            M3 size/line     Veritas
    "displayLarge" to Step(45, 51),   // was 57/64
    "displayMedium" to Step(36, 42),  // was 45/52
    "displaySmall" to Step(30, 37),   // was 36/44
    "headlineLarge" to Step(26, 33),  // was 32/40
    "headlineMedium" to Step(23, 30), // was 28/36
    "headlineSmall" to Step(20, 27),  // was 24/32  — the "Settings" header
    "titleLarge" to Step(19, 24),     // was 22/28  — screen titles
    "titleMedium" to Step(15, 22),    // was 16/24  — list-row titles
    "titleSmall" to Step(13, 19),     // was 14/20
    "bodyLarge" to Step(15, 22),      // was 16/24
    "bodyMedium" to Step(13, 19),     // was 14/20  — row subtitles
    "bodySmall" to Step(12, 16),      // unchanged  — floor
    "labelLarge" to Step(13, 19),     // was 14/20
    "labelMedium" to Step(12, 16),    // unchanged  — floor
    "labelSmall" to Step(11, 16)      // unchanged  — floor
)

private fun TextStyle.step(role: String, family: FontFamily?): TextStyle {
    val s = RAMP.getValue(role)
    return copy(
        fontFamily = family ?: fontFamily,
        fontSize = s.size.sp,
        lineHeight = s.lineHeight.sp
    )
}

fun veritasTypography(font: VeritasUiFont): Typography {
    val base = Typography()
    val family = font.fontFamily()
    return Typography(
        displayLarge = base.displayLarge.step("displayLarge", family),
        displayMedium = base.displayMedium.step("displayMedium", family),
        displaySmall = base.displaySmall.step("displaySmall", family),
        headlineLarge = base.headlineLarge.step("headlineLarge", family),
        headlineMedium = base.headlineMedium.step("headlineMedium", family),
        headlineSmall = base.headlineSmall.step("headlineSmall", family),
        titleLarge = base.titleLarge.step("titleLarge", family),
        titleMedium = base.titleMedium.step("titleMedium", family),
        titleSmall = base.titleSmall.step("titleSmall", family),
        bodyLarge = base.bodyLarge.step("bodyLarge", family),
        bodyMedium = base.bodyMedium.step("bodyMedium", family),
        bodySmall = base.bodySmall.step("bodySmall", family),
        labelLarge = base.labelLarge.step("labelLarge", family),
        labelMedium = base.labelMedium.step("labelMedium", family),
        labelSmall = base.labelSmall.step("labelSmall", family)
    )
}

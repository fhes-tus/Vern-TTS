package com.veritas.reader.ui

import android.content.Context
import android.graphics.Typeface
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
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
    SYSTEM("system", "System default", "Times serif reader; device system UI"),
    GAZETTE("gazette", "Gazette", "Editorial newsprint slab with bold ink-traps"),
    CRISP("crisp", "Crisp", "Clean geometric sans with open curves"),
    TYPEWRITER("typewriter", "Typewriter", "Classic Word document & publication serif (Times)"),
    ATKINSON("atkinson", "Atkinson Hyperlegible", "Braille Institute; disambiguates similar letters");

    companion object {
        fun fromId(id: String?): VeritasUiFont = when (id) {
            "gazette", "bitter", "literata", "lora" -> GAZETTE
            "crisp", "outfit" -> CRISP
            "typewriter", "jetbrains_mono", "times", "serif" -> TYPEWRITER
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
    VeritasUiFont.TYPEWRITER -> FontFamily.Serif
    VeritasUiFont.ATKINSON -> FontFamily(
        Font(R.font.atkinson_regular, FontWeight.Normal),
        Font(R.font.atkinson_bold, FontWeight.Bold)
    )
}

private data class TypefaceCacheKey(val fontId: String, val weight: Int, val isItalic: Boolean)
private val typefaceCache = ConcurrentHashMap<TypefaceCacheKey, Typeface>()

/** Android [Typeface] for native View rendering (e.g. TextView in ReaderPageItemView). */
fun VeritasUiFont.asTypeface(
    context: Context,
    weight: Int = 450,
    isBold: Boolean = false,
    isItalic: Boolean = false
): Typeface {
    val targetWeight = if (isBold) 700 else weight
    val key = TypefaceCacheKey(this.id, targetWeight, isItalic)
    return typefaceCache.getOrPut(key) {
        when (this) {
            // SYSTEM uses Times (Classic Serif) for the extracted text reader as requested
            VeritasUiFont.SYSTEM -> Typeface.create(Typeface.SERIF, targetWeight, isItalic)
            // TYPEWRITER is wired to Times (Classic Serif) as requested
            VeritasUiFont.TYPEWRITER -> Typeface.create(Typeface.SERIF, targetWeight, isItalic)
            VeritasUiFont.GAZETTE -> {
                val base = try {
                    ResourcesCompat.getFont(context, R.font.bitter_variable) ?: Typeface.SERIF
                } catch (_: Exception) {
                    Typeface.SERIF
                }
                Typeface.create(base, targetWeight, isItalic)
            }
            VeritasUiFont.CRISP -> {
                val base = try {
                    ResourcesCompat.getFont(context, R.font.outfit) ?: Typeface.SANS_SERIF
                } catch (_: Exception) {
                    Typeface.SANS_SERIF
                }
                Typeface.create(base, targetWeight, isItalic)
            }
            VeritasUiFont.ATKINSON -> {
                val res = if (isBold || targetWeight >= 600) R.font.atkinson_bold else R.font.atkinson_regular
                val base = try {
                    ResourcesCompat.getFont(context, res) ?: Typeface.SANS_SERIF
                } catch (_: Exception) {
                    Typeface.SANS_SERIF
                }
                Typeface.create(base, targetWeight, isItalic)
            }
        }
    }
}

/** Shared app-chrome hierarchy. Reader text keeps its independent font settings. */
private data class Step(val size: Int, val lineHeight: Int, val weight: FontWeight)

private val RAMP = mapOf(
    "displayLarge" to Step(36, 44, FontWeight.SemiBold),
    "displayMedium" to Step(32, 40, FontWeight.SemiBold),
    "displaySmall" to Step(28, 36, FontWeight.SemiBold),
    "headlineLarge" to Step(26, 34, FontWeight.SemiBold),
    "headlineMedium" to Step(24, 32, FontWeight.SemiBold),
    "headlineSmall" to Step(22, 29, FontWeight.SemiBold),
    "titleLarge" to Step(22, 28, FontWeight.SemiBold),
    "titleMedium" to Step(17, 24, FontWeight.Medium),
    "titleSmall" to Step(15, 21, FontWeight.Medium),
    "bodyLarge" to Step(16, 24, FontWeight.Normal),
    "bodyMedium" to Step(14, 21, FontWeight.Normal),
    "bodySmall" to Step(12, 18, FontWeight.Normal),
    "labelLarge" to Step(14, 20, FontWeight.Medium),
    "labelMedium" to Step(12, 16, FontWeight.Medium),
    "labelSmall" to Step(11, 16, FontWeight.Medium)
)

private fun TextStyle.step(role: String, family: FontFamily?): TextStyle {
    val step = RAMP.getValue(role)
    return copy(
        fontFamily = family ?: fontFamily,
        fontSize = step.size.sp,
        lineHeight = step.lineHeight.sp,
        fontWeight = step.weight,
        letterSpacing = if (role.startsWith("display") || role.startsWith("headline")) (-0.25).sp else 0.sp
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

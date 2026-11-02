package com.veritas.reader

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Ephemeral picker coordinates. Only the chosen RGB colour is persisted. */
internal data class AccentHsv(val hue: Float, val saturation: Float, val value: Float) {
    fun bounded(): AccentHsv = AccentHsv(
        if (hue.isFinite()) hue.coerceIn(0f, 360f) else 0f,
        if (saturation.isFinite()) saturation.coerceIn(0f, 1f) else 0f,
        if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
    )

    fun atFieldPosition(x: Float, y: Float): AccentHsv = copy(saturation = x, value = 1f - y).bounded()

    fun toArgb(): Int {
        val hsv = bounded()
        val sector = (hsv.hue % 360f) / 60f
        val chroma = hsv.value * hsv.saturation
        val cross = chroma * (1f - abs(sector % 2f - 1f))
        val (red, green, blue) = when (sector.toInt()) {
            0 -> Triple(chroma, cross, 0f)
            1 -> Triple(cross, chroma, 0f)
            2 -> Triple(0f, chroma, cross)
            3 -> Triple(0f, cross, chroma)
            4 -> Triple(cross, 0f, chroma)
            else -> Triple(chroma, 0f, cross)
        }
        val offset = hsv.value - chroma
        fun channel(component: Float): Int = ((component + offset) * 255f).roundToInt().coerceIn(0, 255)
        return 0xFF000000L.toInt() or (channel(red) shl 16) or (channel(green) shl 8) or channel(blue)
    }

    fun toHex(): String = String.format(Locale.ROOT, "#%06X", toArgb() and 0xFFFFFF)

    fun colourName(): String {
        val hsv = bounded()
        if (hsv.value < .12f) return "Black"
        if (hsv.saturation < .12f) return when {
            hsv.value > .9f -> "White"
            hsv.value < .45f -> "Dark grey"
            else -> "Grey"
        }
        val name = when {
            hsv.hue < 15f || hsv.hue >= 345f -> "Red"
            hsv.hue < 45f -> "Orange"
            hsv.hue < 75f -> "Yellow"
            hsv.hue < 165f -> "Green"
            hsv.hue < 195f -> "Cyan"
            hsv.hue < 255f -> "Blue"
            hsv.hue < 285f -> "Purple"
            else -> "Pink"
        }
        return when {
            hsv.value < .45f -> "Dark ${name.lowercase(Locale.ROOT)}"
            hsv.saturation < .35f && hsv.value > .75f -> "Light ${name.lowercase(Locale.ROOT)}"
            else -> name
        }
    }

    companion object {
        /** Achromatic colours retain the hue so subsequent field drags stay predictable. */
        fun fromHex(text: String, previousHue: Float = 0f): AccentHsv? {
            val normalized = normalizeCustomAccent(text)
            if (normalized.isEmpty()) return null
            val rgb = normalized.drop(1).toInt(16)
            val red = ((rgb shr 16) and 255) / 255f
            val green = ((rgb shr 8) and 255) / 255f
            val blue = (rgb and 255) / 255f
            val maximum = maxOf(red, green, blue)
            val delta = maximum - minOf(red, green, blue)
            val hue = if (delta == 0f) previousHue else {
                val degrees = 60f * when (maximum) {
                    red -> ((green - blue) / delta) % 6f
                    green -> (blue - red) / delta + 2f
                    else -> (red - green) / delta + 4f
                }
                if (degrees < 0f) degrees + 360f else degrees
            }
            return AccentHsv(hue, if (maximum == 0f) 0f else delta / maximum, maximum).bounded()
        }
    }
}

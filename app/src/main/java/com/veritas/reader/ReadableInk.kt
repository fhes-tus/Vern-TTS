package com.veritas.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** The higher-contrast ink for a user-selected opaque paper colour (WCAG luminance). */
internal fun readableInk(background: Color): Color =
    if (background.luminance() > .179f) Color(0xFF111111) else Color(0xFFF9FAFB)

/** Source and accent ink must be checked against the paper actually behind it. */
internal fun readableDocumentInk(preferred: Color, background: Color): Color {
    val light = maxOf(preferred.luminance(), background.luminance())
    val dark = minOf(preferred.luminance(), background.luminance())
    return if ((light + .05f) / (dark + .05f) >= 4.5f) preferred else readableInk(background)
}

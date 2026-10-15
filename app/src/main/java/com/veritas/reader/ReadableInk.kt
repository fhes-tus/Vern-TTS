package com.veritas.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** The higher-contrast ink for a user-selected opaque paper colour (WCAG luminance). */
internal fun readableInk(background: Color): Color =
    if (background.luminance() > .179f) Color(0xFF111111) else Color(0xFFF9FAFB)

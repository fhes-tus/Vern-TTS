package com.veritas.reader.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.text.NumberFormat

/** Formatting for display only. Document text, names and stored identifiers remain intact. */
@Composable
internal fun localizedUiValue(value: Any?): String {
    val locale = LocalConfiguration.current.locales[0]
    return when (value) {
        is Byte, is Short, is Int, is Long -> NumberFormat.getIntegerInstance(locale).apply {
            isGroupingUsed = false
        }.format(value)
        is Float, is Double -> NumberFormat.getNumberInstance(locale).apply {
            isGroupingUsed = false
            maximumFractionDigits = 3
        }.format(value)
        else -> value.toString()
    }
}

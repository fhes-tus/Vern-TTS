package com.veritas.reader

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.glance.LocalContext
import java.text.NumberFormat

@Composable
internal fun widgetString(@StringRes id: Int, vararg values: Any): String =
    LocalContext.current.getString(id, *values)

@Composable
internal fun widgetValue(value: Any?): String {
    val locale = LocalContext.current.resources.configuration.locales[0]
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

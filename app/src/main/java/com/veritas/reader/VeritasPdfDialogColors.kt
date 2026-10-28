package com.veritas.reader

import android.content.res.ColorStateList
import android.os.Build
import android.widget.EditText
import androidx.core.graphics.ColorUtils

/** Native fields must use the same resolved accent as their dialog and buttons. */
internal fun VeritasPdfViewerActivity.stylePdfInput(input: EditText) {
    input.setTextColor(colorTextPrimary)
    input.setHintTextColor(colorTextSecondary)
    input.highlightColor = ColorUtils.setAlphaComponent(colorPrimary, 64)
    input.backgroundTintList = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
        intArrayOf(colorPrimary, colorOutline)
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        input.textCursorDrawable?.mutate()?.setTint(colorPrimary)
        input.textSelectHandle?.mutate()?.setTint(colorPrimary)
        input.textSelectHandleLeft?.mutate()?.setTint(colorPrimary)
        input.textSelectHandleRight?.mutate()?.setTint(colorPrimary)
    }
}

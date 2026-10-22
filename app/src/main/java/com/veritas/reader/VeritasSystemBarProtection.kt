package com.veritas.reader

import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.insets.ColorProtection
import androidx.core.view.insets.ProtectionLayout

private const val SYSTEM_BAR_PROTECTION_TAG = "vern_system_bar_protection"

/** App-owned backgrounds behind transparent system bars, including landscape nav bars. */
internal fun updateVeritasSystemBarProtection(window: Window, statusColor: Int, navigationColor: Int) {
    val content = window.decorView.findViewById<FrameLayout>(android.R.id.content) ?: return
    val protection = content.findViewWithTag<ProtectionLayout>(SYSTEM_BAR_PROTECTION_TAG)
        ?: ProtectionLayout(content.context).also {
            it.tag = SYSTEM_BAR_PROTECTION_TAG
            it.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            content.addView(it, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            ViewCompat.requestApplyInsets(it)
        }
    protection.setProtections(listOf(
        ColorProtection(WindowInsetsCompat.Side.TOP, statusColor),
        ColorProtection(WindowInsetsCompat.Side.BOTTOM, navigationColor),
        ColorProtection(WindowInsetsCompat.Side.LEFT, navigationColor),
        ColorProtection(WindowInsetsCompat.Side.RIGHT, navigationColor)
    ))
    // The app supplies an opaque background; an additional platform scrim would tint it.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
}

package com.veritas.reader

import android.app.Activity
import android.graphics.drawable.Animatable
import android.os.Build
import android.provider.Settings
import android.widget.ImageView
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator

/** Animate the very first Android splash icon instead of replaying a second logo screen. */
internal fun Activity.configureVernSystemSplash(settings: ReaderSettings) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    splashScreen.setOnExitAnimationListener { view ->
        val reduce = settings.reduceMotion || settings.splashAnimation == "none" ||
            Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        if (reduce || isFinishing || isDestroyed) {
            ((view.iconView as? ImageView)?.drawable as? Animatable)?.stop()
            view.remove()
        } else {
            val icon = view.iconView
            if (settings.splashAnimation == "fade" || settings.splashAnimation == "rise") {
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = VernStartupMotion.duration(settings.splashAnimation).toLong()
                    interpolator = AccelerateDecelerateInterpolator()
                    addUpdateListener {
                        val p = it.animatedValue as Float
                        val pose = VernStartupMotion.exitPose(settings.splashAnimation, p)
                        if (settings.splashAnimation == "fade") view.alpha = pose.alpha
                        else {
                            icon?.alpha = pose.alpha
                            icon?.translationY = pose.liftDp * resources.displayMetrics.density
                            icon?.scaleX = pose.scale
                            icon?.scaleY = pose.scale
                            view.alpha = (1f - (p - .35f).coerceAtLeast(0f) / .65f).coerceIn(0f, 1f)
                        }
                        if (p >= 1f) view.remove()
                    }
                    start()
                }
                return@setOnExitAnimationListener
            }
            val started = view.iconAnimationStart?.toEpochMilli() ?: System.currentTimeMillis()
            val duration = view.iconAnimationDuration?.toMillis()?.takeIf { it > 0L } ?: 950L
            val remaining = (duration - (System.currentTimeMillis() - started)).coerceIn(0L, 950L)
            view.postDelayed({
                if (isFinishing || isDestroyed) view.remove()
                else view.animate().alpha(0f).setDuration(220).withEndAction { view.remove() }.start()
            }, remaining)
        }
    }
}

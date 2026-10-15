package com.veritas.reader

/** Shared timing and exit poses for the real system splash and the visible settings preview. */
internal object VernStartupMotion {
    data class Pose(val alpha: Float = 1f, val scale: Float = 1f, val liftDp: Float = 0f)
    fun duration(style: String): Int = when (style) {
        "bloom" -> 950
        "rise" -> 800
        "fade" -> 680
        else -> 0
    }
    fun exitPose(style: String, progress: Float): Pose {
        val p = progress.coerceIn(0f, 1f)
        return when (style) {
            "fade" -> Pose(alpha = 1f - p)
            "rise" -> Pose(alpha = 1f - p, scale = 1f - .25f * p, liftDp = -86f * p)
            else -> Pose()
        }
    }
    fun bloomPose(progress: Float): Pose {
        val p = progress.coerceIn(0f, 1f)
        return Pose(alpha = .4f + .6f * (p / .65f).coerceIn(0f, 1f), scale = .72f + .28f * p)
    }
}

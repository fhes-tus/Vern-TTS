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
        val bloom = if (p < .45f) p / .45f else (1f - p) / .55f
        return Pose(alpha = 1f - .06f * bloom, scale = 1f + .08f * bloom)
    }
}

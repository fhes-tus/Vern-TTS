package com.veritas.reader

/** Finite delivery values at persistence and synthesis boundaries. Existing control ranges stay distinct. */
internal object SpeechDelivery {
    private fun bounded(value: Float, min: Float, max: Float, fallback: Float): Float =
        (if (value.isFinite()) value else if (fallback.isFinite()) fallback else 1f).coerceIn(min, max)

    fun userRate(value: Float, fallback: Float = 1f): Float = bounded(value, .5f, 2f, fallback)
    fun userPitch(value: Float, fallback: Float = 1f): Float = bounded(value, .7f, 1.4f, fallback)
    fun rate(value: Float): Float = bounded(value, .5f, 2f, 1f)
    fun pitch(value: Float): Float = bounded(value, .6f, 1.5f, 1f)
    fun strength(value: Float): Float = bounded(value, 0f, 1f, .5f)

    // Match the ranges already accepted by saveNarrationSettings, including legacy fallback roles.
    fun legacyRate(value: Float, fallback: Float = 1f): Float = bounded(value, .75f, 1.25f, fallback)
    fun legacyPitch(value: Float, fallback: Float = 1f): Float = bounded(value, .8f, 1.25f, fallback)

    // Android-only tutorial audition has historically exposed a wider range.
    fun tutorialRate(value: Float): Float = bounded(value, .5f, 2.5f, 1f)
    fun tutorialPitch(value: Float): Float = bounded(value, .5f, 2f, 1f)

    fun applyResolved(rate: Float, pitch: Float, setRate: (Float) -> Boolean, setPitch: (Float) -> Boolean): Boolean =
        setRate(this.rate(rate)) && setPitch(this.pitch(pitch))
}

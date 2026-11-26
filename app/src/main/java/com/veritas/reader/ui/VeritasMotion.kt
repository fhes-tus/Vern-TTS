package com.veritas.reader.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// Motion
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The app-wide motion scheme, installed once by `VeritasTheme` and read through
 * [VeritasMotion].
 *
 * Material splits motion into two families and so do we:
 *  - **spatial** — anything that moves, resizes, or changes shape. Springs,
 *    lightly underdamped so they settle crisply.
 *  - **effects** — anything that only recolours or fades. Critically damped
 *    (ratio 1f) on purpose: a colour that overshoots reads as a rendering bug.
 *
 * The numbers are lifted from Material's *standard* scheme, not *expressive*.
 * Expressive springs (damping 0.6-0.8) visibly bounce; charming on first launch
 * and tiring by page 200 of a reading session, so Veritas stays calm. Standard
 * is also what the bundled Material components already use internally, so our
 * own animations and theirs agree by construction.
 *
 * When "Reduce motion" is on, spatial specs collapse to [snap] while effects
 * specs are left untouched — the setting is about movement, not about colour,
 * and a cross-fade is the accessible substitute for a slide.
 *
 * Note: material3 1.4.0 keeps its own `MotionScheme` and the corresponding
 * `MaterialTheme(motionScheme = ...)` overload `internal`, so this cannot be
 * handed to Material to drive its components' internals. It governs Veritas
 * call sites only. If material3 is ever bumped to a version that makes those
 * public, this class maps onto it one-for-one.
 */
@Immutable
class VeritasMotionScheme internal constructor(val reduceMotion: Boolean) {

    fun <T> defaultSpatial(): FiniteAnimationSpec<T> = spatial(DefaultSpatial)

    fun <T> fastSpatial(): FiniteAnimationSpec<T> = spatial(FastSpatial)

    fun <T> slowSpatial(): FiniteAnimationSpec<T> = spatial(SlowSpatial)

    fun <T> defaultEffects(): FiniteAnimationSpec<T> = cast(DefaultEffects)

    fun <T> fastEffects(): FiniteAnimationSpec<T> = cast(FastEffects)

    fun <T> slowEffects(): FiniteAnimationSpec<T> = cast(SlowEffects)

    private fun <T> spatial(spec: SpringSpec<Any>): FiniteAnimationSpec<T> =
        if (reduceMotion) cast(Snap) else cast(spec)

    @Suppress("UNCHECKED_CAST")
    private fun <T> cast(spec: FiniteAnimationSpec<Any>): FiniteAnimationSpec<T> =
        spec as FiniteAnimationSpec<T>

    private companion object {
        // Specs are allocated once and cast per call, the same trick the
        // Material implementations use — these run on every animation start.
        val DefaultSpatial: SpringSpec<Any> = spring(dampingRatio = 0.9f, stiffness = 700f)
        val FastSpatial: SpringSpec<Any> = spring(dampingRatio = 0.9f, stiffness = 1400f)
        val SlowSpatial: SpringSpec<Any> = spring(dampingRatio = 0.9f, stiffness = 300f)
        val DefaultEffects: SpringSpec<Any> = spring(dampingRatio = 1f, stiffness = 1600f)
        val FastEffects: SpringSpec<Any> = spring(dampingRatio = 1f, stiffness = 3800f)
        val SlowEffects: SpringSpec<Any> = spring(dampingRatio = 1f, stiffness = 800f)
        val Snap: SnapSpec<Any> = snap()
    }
}

internal val LocalVeritasMotion = staticCompositionLocalOf {
    VeritasMotionScheme(reduceMotion = false)
}

/**
 * Motion tokens for animation call sites:
 *
 *     val offset by animateDpAsState(target, animationSpec = VeritasMotion.spatial())
 *     val tint by animateColorAsState(target, animationSpec = VeritasMotion.effects())
 *
 * Pick **spatial** if the thing moves or resizes, **effects** if it only
 * changes colour or opacity. Pick *fast* for small elements (chips, icons),
 * *default* for cards and sheets, *slow* for full-screen changes. Going through
 * these instead of a literal `tween(300)` is what makes "Reduce motion" work.
 */
object VeritasMotion {

    val scheme: VeritasMotionScheme
        @Composable @ReadOnlyComposable get() = LocalVeritasMotion.current

    @Composable
    @ReadOnlyComposable
    fun <T> spatial(): FiniteAnimationSpec<T> = LocalVeritasMotion.current.defaultSpatial()

    @Composable
    @ReadOnlyComposable
    fun <T> spatialFast(): FiniteAnimationSpec<T> = LocalVeritasMotion.current.fastSpatial()

    @Composable
    @ReadOnlyComposable
    fun <T> spatialSlow(): FiniteAnimationSpec<T> = LocalVeritasMotion.current.slowSpatial()

    @Composable
    @ReadOnlyComposable
    fun <T> effects(): FiniteAnimationSpec<T> = LocalVeritasMotion.current.defaultEffects()

    @Composable
    @ReadOnlyComposable
    fun <T> effectsFast(): FiniteAnimationSpec<T> = LocalVeritasMotion.current.fastEffects()

    @Composable
    @ReadOnlyComposable
    fun <T> effectsSlow(): FiniteAnimationSpec<T> = LocalVeritasMotion.current.slowEffects()
}

private const val PlaybackWaveTurnMillis = 1800
// The three waves use phase multipliers of 1, 1.25 and 0.85. After twenty
// turns, all three are back at their starting phase.
private val PlaybackWaveCycle = (40 * Math.PI).toFloat()

@Composable
internal fun rememberPlaybackWavePhase(running: Boolean): State<Float> {
    val phase = remember { Animatable(0f) }
    LaunchedEffect(running) {
        if (running) {
            val start = phase.value % PlaybackWaveCycle
            phase.snapTo(start)
            phase.animateTo(
                targetValue = start + PlaybackWaveCycle,
                animationSpec = infiniteRepeatable(tween(PlaybackWaveTurnMillis * 20, easing = LinearEasing))
            )
        }
    }
    return phase.asState()
}

@Composable
internal fun LazyItemScope.animateVeritasItem(modifier: Modifier = Modifier): Modifier {
    val motion = VeritasMotion.scheme
    return modifier.animateItem(
        fadeInSpec = motion.fastEffects(),
        placementSpec = if (motion.reduceMotion) null else motion.defaultSpatial(),
        fadeOutSpec = motion.fastEffects()
    )
}

/**
 * Dips the element slightly while it is held down, then springs back.
 *
 * This generalises an idiom the app already had in two hand-rolled copies
 * (`AudioModeButton`, `BottomNavItem`), so new surfaces do not each invent
 * their own scale and spring. Pass the *same* [interactionSource] the
 * clickable uses, or the press will never be observed:
 *
 *     val interactionSource = remember { MutableInteractionSource() }
 *     Modifier
 *         .pressScale(interactionSource)
 *         .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
 *
 * Under "Reduce motion" this returns the receiver untouched — a press dip is
 * pure decoration, so the honest response to that setting is not to snap it but
 * to drop it. The scale is applied through the deferred `graphicsLayer` lambda,
 * which keeps the animation on the draw phase and out of recomposition; that
 * matters when this lands on rows inside a `LazyColumn`.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.97f
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    return pressScale(pressed, pressedScale)
}

/**
 * The [pressScale] overload for surfaces driven by `detectTapGestures` rather
 * than a clickable, which have no `InteractionSource` to observe. Track the
 * press yourself and hand the flag over:
 *
 *     var pressed by remember { mutableStateOf(false) }
 *     detectTapGestures(
 *         onPress = { pressed = true; tryAwaitRelease(); pressed = false },
 *         onTap = { … }
 *     )
 */
@Composable
fun Modifier.pressScale(
    pressed: Boolean,
    pressedScale: Float = 0.97f
): Modifier {
    if (LocalVeritasMotion.current.reduceMotion) return this
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = VeritasMotion.spatialFast(),
        label = "pressScale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Haptics
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Haptics named by *intent* rather than by effect.
 *
 * The call site says what happened ("a toggle flipped") and this class owns the
 * question of which vibration represents that. Before it existed, every site in
 * the app passed [HapticFeedbackType.LongPress] — a deliberately heavy thump —
 * including plain transport taps and Next buttons.
 *
 * There is deliberately **no tap()**. A plain tap is answered by the ripple;
 * Android's own guidance is that wrong feedback is worse than none, and a phone
 * that buzzes on all 558 of this app's onClick handlers feels cheap. Reach for
 * these only when something *changed state* or *crossed a threshold*.
 *
 * Some constants below map to platform effects added after API 28 (this app's
 * `minSdk`). On older devices the platform substitutes a fallback or does
 * nothing — degraded, never a crash.
 */
@Immutable
class VeritasHaptics internal constructor(private val haptics: HapticFeedback) {

    /** An action completed: note saved, import finished, playback jumped. */
    fun success() = haptics.performHapticFeedback(HapticFeedbackType.Confirm)

    /** An action was refused or failed. Heavier than [success] on purpose. */
    fun failure() = haptics.performHapticFeedback(HapticFeedbackType.Reject)

    /** A two-state control flipped: switch, checkbox, play/pause, expand. */
    fun toggle(on: Boolean) = haptics.performHapticFeedback(
        if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff
    )

    /** One item picked out of a set, or a step through discrete segments. */
    fun select() = haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)

    /** Repeated ticks during a continuous drag — slider steps, scrubbing. */
    fun tick() = haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)

    /** A long press was recognised. Only for actual long presses. */
    fun longPress() = haptics.performHapticFeedback(HapticFeedbackType.LongPress)

    /** A context menu opened. */
    fun contextMenu() = haptics.performHapticFeedback(HapticFeedbackType.ContextClick)

    /** A drag crossed the point where releasing would commit it. */
    fun threshold() = haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
}

@Composable
fun rememberVeritasHaptics(): VeritasHaptics {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) { VeritasHaptics(haptics) }
}

/**
 * How many ticks a *continuous* slider gets across its full travel. Stepped
 * sliders tick on their own steps instead. 24 is dense enough to feel like a
 * textured track and sparse enough not to become a buzz.
 */
private const val ContinuousSliderNotches = 24

/**
 * Wraps a slider's `onValueChange` so the handle ticks as it passes each notch.
 *
 * A slider is the one control where continuous haptics are right: the value is
 * changing under the finger and there is no other confirmation that it moved.
 * Firing on *every* callback would be a buzz-storm, so the value is quantised
 * and a tick fires only when the notch changes.
 *
 *     Slider(value = v, onValueChange = rememberSliderHaptics(v, range, steps, onChange), …)
 */
@Composable
fun rememberSliderHaptics(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChange: (Float) -> Unit
): (Float) -> Unit {
    val haptics = rememberVeritasHaptics()
    val notches = if (steps > 0) steps + 1 else ContinuousSliderNotches
    val start = valueRange.start
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val lastNotch = remember { mutableIntStateOf((((value - start) / span) * notches).roundToInt()) }
    val latest by rememberUpdatedState(onValueChange)
    return remember(haptics, notches, start, span) {
        { newValue: Float ->
            val notch = (((newValue - start) / span) * notches).roundToInt()
            if (notch != lastNotch.intValue) {
                lastNotch.intValue = notch
                haptics.tick()
            }
            latest(newValue)
        }
    }
}

/**
 * The integer counterpart of [rememberSliderHaptics], for scrubbers that are
 * already discrete — a page slider, a chapter picker. Ticks whenever the value
 * lands on a different step.
 */
@Composable
fun rememberStepHaptics(
    value: Int,
    onValueChange: (Int) -> Unit
): (Int) -> Unit {
    val haptics = rememberVeritasHaptics()
    val lastValue = remember { mutableIntStateOf(value) }
    val latest by rememberUpdatedState(onValueChange)
    return remember(haptics) {
        { newValue: Int ->
            if (newValue != lastValue.intValue) {
                lastValue.intValue = newValue
                haptics.tick()
            }
            latest(newValue)
        }
    }
}

/**
 * A [Switch] that reports its own flip.
 *
 * Material's Switch performs no haptic feedback of its own (verified against
 * material3 1.4.0), and this app has 17 of them across settings and dialogs.
 * Rather than repeat the haptic at each call site, swap `Switch(` for
 * `VeritasSwitch(` — rendering is identical, only the flip is now felt.
 */
@Composable
fun VeritasSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptics = rememberVeritasHaptics()
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange?.let { callback ->
            { newValue: Boolean ->
                haptics.toggle(newValue)
                callback(newValue)
            }
        },
        modifier = modifier,
        enabled = enabled
    )
}

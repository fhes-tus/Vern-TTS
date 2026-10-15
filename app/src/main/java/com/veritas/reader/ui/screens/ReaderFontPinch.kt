package com.veritas.reader.ui.screens

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.roundToInt

/** Own two-finger gestures before native text or the pager can consume them. */
@Composable
internal fun Modifier.readerFontPinch(fontSizeSp: Int, onFontSizeChange: (Int) -> Unit,
    onPinchChanged: (Boolean) -> Unit = {}): Modifier {
    val latestSize by rememberUpdatedState(fontSizeSp)
    val latestChange by rememberUpdatedState(onFontSizeChange)
    val latestPinch by rememberUpdatedState(onPinchChanged)
    val haptics = LocalHapticFeedback.current
    // Font updates must not cancel the pointer coroutine halfway through a pinch.
    return pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var pinching = false
            var scale = 1f
            var startingSize = latestSize
            var requestedSize = startingSize
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } >= 2) {
                        if (!pinching) {
                            startingSize = latestSize
                            requestedSize = startingSize
                            pinching = true
                            latestPinch(true)
                        }
                        val zoom = event.calculateZoom()
                        if (zoom.isFinite() && zoom > 0f) scale = (scale * zoom).coerceIn(.25f, 4f)
                        val next = (startingSize * scale).roundToInt().coerceIn(10, 28)
                        if (next != requestedSize) {
                            requestedSize = next
                            latestChange(next)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                    // Keep ownership when one finger lifts first, so the remaining
                    // finger cannot accidentally become a page swipe or a text tap.
                    if (pinching) event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            } finally {
                if (pinching) latestPinch(false)
            }
        }
    }
}

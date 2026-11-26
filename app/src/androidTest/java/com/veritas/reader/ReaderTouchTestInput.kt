package com.veritas.reader

import android.app.UiAutomation
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent

/** Use the same virtual touchscreen as the test's gestures to cancel a stranded contact. */
internal fun UiAutomation.cancelReaderTestTouch() {
    val now = SystemClock.uptimeMillis()
    val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
    event.source = InputDevice.SOURCE_TOUCHSCREEN
    try {
        injectInputEvent(event, true)
    } finally {
        event.recycle()
    }
}

internal fun UiAutomation.injectReaderTestTouch(event: MotionEvent, waitForDispatch: Boolean): Boolean {
    if (injectInputEvent(event, waitForDispatch)) return true
    if (event.actionMasked != MotionEvent.ACTION_DOWN) return false
    cancelReaderTestTouch()
    return injectInputEvent(event, waitForDispatch)
}

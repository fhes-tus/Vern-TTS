package com.veritas.reader

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

internal fun PlaybackService.requestAudioFocus(): Boolean {
    // Reuse the same focus client. A new listener on every Play competes with
    // our previous request and delivers a duck/loss callback back to ourselves.
    audioFocusRequest?.let { return audioManager.requestAudioFocus(it) == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED }
    lateinit var req: android.media.AudioFocusRequest
    val focusChangeListener = android.media.AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (audioFocusRequest !== req) return@OnAudioFocusChangeListener
        when (focusChange) {
            android.media.AudioManager.AUDIOFOCUS_LOSS -> {
                pausedDueToTransientFocusLoss = false
                pauseSpeech("Paused - audio focus lost.")
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (PlaybackStateStore.isPlaying) {
                    pausedDueToTransientFocusLoss = true
                    pauseSpeechTransiently("Paused - interrupted.")
                }
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (PlaybackStateStore.isPlaying) {
                    pausedDueToTransientFocusLoss = true
                    pauseSpeechTransiently("Paused - another app needs audio.")
                }
            }
            android.media.AudioManager.AUDIOFOCUS_GAIN -> {
                if (pausedDueToTransientFocusLoss) {
                    pausedDueToTransientFocusLoss = false
                    handlePlay(null)
                }
            }
        }
    }
    req = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(
            android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener(focusChangeListener, mainHandler)
        .build()
    audioFocusRequest = req
    return audioManager.requestAudioFocus(req) == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
}

internal fun PlaybackService.abandonAudioFocus() {
    val previous = audioFocusRequest
    audioFocusRequest = null
    previous?.let { audioManager.abandonAudioFocusRequest(it) }
}

internal fun PlaybackService.createShakeEventListener(): SensorEventListener = object : SensorEventListener {
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        lastAcceleration = currentAcceleration
        currentAcceleration = kotlin.math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        val delta = currentAcceleration - lastAcceleration
        shakeAcceleration = shakeAcceleration * 0.9f + delta
        if (shakeAcceleration > 11.5f) {
            val now = System.currentTimeMillis()
            if (now - lastShakeTimestamp > 2000L) {
                lastShakeTimestamp = now
                onShakeDetected()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

internal fun PlaybackService.registerShakeListenerIfNeeded() {
    if (isShakeListenerRegistered) return
    val settings = runCatching { repository.loadReaderSettings() }.getOrNull()
    if (settings?.shakeToExtendSleepTimer != false) {
        if (sensorManager == null) {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        }
        accelerometer?.let { sensor ->
            sensorManager?.registerListener(shakeEventListener, sensor, SensorManager.SENSOR_DELAY_UI)
            isShakeListenerRegistered = true
        }
    }
}

internal fun PlaybackService.unregisterShakeListener() {
    if (isShakeListenerRegistered) {
        sensorManager?.unregisterListener(shakeEventListener)
        isShakeListenerRegistered = false
    }
}

internal fun PlaybackService.onShakeDetected() {
    val snapshot = PlaybackStateStore.activeSleepTimerSnapshot()
    val settings = runCatching { repository.loadReaderSettings() }.getOrNull()
    if (settings?.shakeToExtendSleepTimer == false) return

    // If sleep timer is active and near end (<= 3 min remaining):
    if (snapshot != null && snapshot.remainingMillis() <= 3 * 60 * 1000L) {
        val extendMillis = 10 * 60 * 1000L
        val action = snapshot.action
        val newSnapshot = VeritasSleepTimerRequest(
            durationMillis = extendMillis,
            action = action,
            stopAtEndOfSection = false
        )
        PlaybackStateStore.setSleepTimer(newSnapshot)
        repository.saveSleepTimerState(
            durationMillis = extendMillis,
            endsAtMillis = System.currentTimeMillis() + extendMillis,
            actionName = action.name,
            stopAtEndOfSection = false
        )
        scheduleSleepTimerFromStore()
        vibrateShake()
        PlaybackStateStore.statusMessage = "Sleep timer extended by 10 min ⏳"
        updateMediaSessionState()
        refreshForegroundNotification()
    }
}

internal fun PlaybackService.vibrateShake() {
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(120L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(VibrationEffect.createOneShot(120L, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}

internal fun PlaybackService.createSleepTimerTicker(): Runnable = object : Runnable {
    override fun run() {
        val snapshot = PlaybackStateStore.activeSleepTimerSnapshot()
        if (snapshot == null) {
            sleepFadeVolume = 1.0f
            return
        }
        if (snapshot.stopAtEndOfSection) {
            return
        }
        val remaining = snapshot.remainingMillis()
        if (remaining <= 0) {
            sleepFadeVolume = 1.0f
            fireSleepTimer()
        } else {
            if (remaining <= 60_000L) {
                sleepFadeVolume = (remaining.toFloat() / 60_000f).coerceIn(0.05f, 1.0f)
            } else {
                sleepFadeVolume = 1.0f
            }
            mainHandler.postDelayed(this, 1000L)
        }
    }
}

internal fun PlaybackService.scheduleSleepTimerFromStore() {
    cancelSleepTimerCallback()
    val snapshot = PlaybackStateStore.activeSleepTimerSnapshot() ?: return
    registerShakeListenerIfNeeded()
    if (snapshot.stopAtEndOfSection) {
        return
    }
    mainHandler.post(sleepTimerTicker)
}

internal fun PlaybackService.cancelSleepTimerCallback() {
    mainHandler.removeCallbacks(sleepTimerTicker)
    sleepTimerRunnable?.let(mainHandler::removeCallbacks)
    sleepTimerRunnable = null
    sleepFadeVolume = 1.0f
    unregisterShakeListener()
}

internal fun PlaybackService.cancelSleepTimer(message: String) {
    cancelSleepTimerCallback()
    PlaybackStateStore.clearSleepTimer()
    repository.clearSleepTimerState()
    PlaybackStateStore.statusMessage = message
    updateMediaSessionState()
    refreshForegroundNotification()
}

internal fun PlaybackService.fireSleepTimer() {
    val snapshot = PlaybackStateStore.activeSleepTimerSnapshot()
    val action = snapshot?.action ?: VeritasSleepTimerAction.PAUSE
    cancelSleepTimerCallback()
    repository.clearSleepTimerState()
    PlaybackStateStore.clearSleepTimer()
    sleepFadeVolume = 1.0f
    when (action) {
        VeritasSleepTimerAction.PAUSE -> pauseSpeech("Sleep timer paused playback.")
        VeritasSleepTimerAction.STOP  -> stopSpeechAndService("Sleep timer stopped playback.")
    }
}

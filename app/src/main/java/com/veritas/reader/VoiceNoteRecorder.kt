package com.veritas.reader

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

enum class VoiceRecordingState {
    IDLE,
    RECORDING,
    PLAYING
}

object VoiceNoteRecorder {
    private const val TAG = "VoiceNoteRecorder"

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var activeRecordingFile: File? = null
    private var recordingStartTime: Long = 0L

    private val _recordingState = MutableStateFlow(VoiceRecordingState.IDLE)
    val recordingState: StateFlow<VoiceRecordingState> = _recordingState.asStateFlow()

    private val _activeAudioPath = MutableStateFlow<String?>(null)
    val activeAudioPath: StateFlow<String?> = _activeAudioPath.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    private val _playbackPositionMs = MutableStateFlow(0)
    val playbackPositionMs: StateFlow<Int> = _playbackPositionMs.asStateFlow()

    private val _playbackDurationMs = MutableStateFlow(0)
    val playbackDurationMs: StateFlow<Int> = _playbackDurationMs.asStateFlow()

    private val _recordingDurationSeconds = MutableStateFlow(0)
    val recordingDurationSeconds: StateFlow<Int> = _recordingDurationSeconds.asStateFlow()

    private val _recordingAmplitude = MutableStateFlow(0)
    val recordingAmplitude: StateFlow<Int> = _recordingAmplitude.asStateFlow()

    private var progressJob: Job? = null
    private var recordingTickerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun startRecording(context: Context, documentId: String, sentenceIndex: Int): File? {
        stopAll()
        try {
            val dir = File(context.filesDir, "voice_notes").apply { if (!exists()) mkdirs() }
            val file = File(dir, "note_${documentId.replace(Regex("[^a-zA-Z0-9_-]"), "_")}_${sentenceIndex}_${System.currentTimeMillis()}.m4a")
            
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            activeRecordingFile = file
            recordingStartTime = SystemClock.elapsedRealtime()
            _activeAudioPath.value = file.absolutePath
            _recordingDurationSeconds.value = 0
            _recordingAmplitude.value = 0
            _recordingState.value = VoiceRecordingState.RECORDING

            recordingTickerJob?.cancel()
            recordingTickerJob = scope.launch {
                while (isActive && _recordingState.value == VoiceRecordingState.RECORDING) {
                    val elapsedSec = ((SystemClock.elapsedRealtime() - recordingStartTime) / 1000).toInt()
                    _recordingDurationSeconds.value = elapsedSec
                    val amp = runCatching { mediaRecorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                    _recordingAmplitude.value = amp
                    delay(100)
                }
            }

            Log.d(TAG, "Started voice recording to: ${file.absolutePath}")
            return file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start voice recording", e)
            stopAll()
            return null
        }
    }

    fun stopRecording(): Pair<String, Int>? {
        if (_recordingState.value != VoiceRecordingState.RECORDING) return null
        recordingTickerJob?.cancel()
        recordingTickerJob = null
        val durationMs = SystemClock.elapsedRealtime() - recordingStartTime
        val durationSeconds = (durationMs / 1000).toInt().coerceAtLeast(1)
        val file = activeRecordingFile

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaRecorder", e)
        } finally {
            mediaRecorder = null
            _recordingState.value = VoiceRecordingState.IDLE
            _recordingDurationSeconds.value = 0
            _recordingAmplitude.value = 0
        }

        return if (file != null && file.exists() && file.length() > 0) {
            Pair(file.absolutePath, durationSeconds)
        } else {
            null
        }
    }

    fun playAudio(path: String, onFinished: () -> Unit = {}) {
        stopAll()
        val file = File(path)
        if (!file.exists()) {
            Log.w(TAG, "Audio file does not exist: $path")
            return
        }

        try {
            val player = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                setOnCompletionListener {
                    stopAll()
                    onFinished()
                }
                start()
            }
            mediaPlayer = player
            _activeAudioPath.value = path
            _recordingState.value = VoiceRecordingState.PLAYING
            _playbackDurationMs.value = player.duration.coerceAtLeast(0)
            _playbackPositionMs.value = 0
            _playbackProgress.value = 0f

            progressJob?.cancel()
            progressJob = scope.launch {
                while (isActive && _recordingState.value == VoiceRecordingState.PLAYING) {
                    val duration = player.duration
                    val current = player.currentPosition
                    if (duration > 0) {
                        _playbackProgress.value = (current.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                        _playbackPositionMs.value = current
                        _playbackDurationMs.value = duration
                    }
                    delay(50)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio note", e)
            stopAll()
        }
    }

    fun seekTo(progress: Float) {
        try {
            val player = mediaPlayer ?: return
            val duration = player.duration
            if (duration > 0) {
                val clamped = progress.coerceIn(0f, 1f)
                val targetMs = (clamped * duration).toInt()
                player.seekTo(targetMs)
                _playbackProgress.value = clamped
                _playbackPositionMs.value = targetMs
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking audio note", e)
        }
    }

    fun seekToMs(positionMs: Int) {
        try {
            val player = mediaPlayer ?: return
            val duration = player.duration
            if (duration > 0) {
                val clamped = positionMs.coerceIn(0, duration)
                player.seekTo(clamped)
                _playbackProgress.value = (clamped.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                _playbackPositionMs.value = clamped
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking audio note to ms", e)
        }
    }

    fun stopPlayback() {
        if (_recordingState.value == VoiceRecordingState.PLAYING) {
            stopAll()
        }
    }

    fun stopAll() {
        progressJob?.cancel()
        progressJob = null
        recordingTickerJob?.cancel()
        recordingTickerJob = null
        _playbackProgress.value = 0f
        _playbackPositionMs.value = 0
        _playbackDurationMs.value = 0
        _recordingDurationSeconds.value = 0
        _recordingAmplitude.value = 0

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {}
        mediaRecorder = null

        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null

        _recordingState.value = VoiceRecordingState.IDLE
    }

    fun deleteAudioFile(path: String) {
        if (_activeAudioPath.value == path) {
            stopAll()
        }
        runCatching {
            val file = File(path)
            if (file.exists()) file.delete()
        }
    }
}

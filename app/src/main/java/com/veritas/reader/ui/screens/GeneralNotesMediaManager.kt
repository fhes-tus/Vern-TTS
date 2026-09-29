package com.veritas.reader.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

internal class GeneralNotesMediaManager(
    val isRecording: Boolean,
    val recordingDurationSec: Int,
    val recordingAmplitudes: List<Int>,
    val isPlaying: Boolean,
    val activePlayingAudioPath: String?,
    val playProgress: Float,
    val currentPosition: String,
    val onToggleRecording: () -> Unit,
    val onCancelRecording: () -> Unit,
    val onTogglePlayAudio: (String) -> Unit,
    val onSeekAudio: (Float, String) -> Unit,
    val onPickImage: () -> Unit,
    val onPickVideo: () -> Unit
)

@Composable
internal fun rememberGeneralNotesMediaManager(
    onInsertAttachment: (NoteBlock) -> Unit,
    audioUrls: List<String>,
    onAudioUrlsChanged: (List<String>) -> Unit,
    onImageUrlChanged: (String) -> Unit,
    focusedBlockIndex: Int,
    blocksCount: Int
): GeneralNotesMediaManager {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isRecording by remember { mutableStateOf(false) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordFilePath by remember { mutableStateOf<String?>(null) }
    var recordingDurationSec by remember { mutableIntStateOf(0) }
    var recordingAmplitudes by remember { mutableStateOf(listOf<Int>()) }
    var recordingInsertTargetIndex by remember { mutableIntStateOf(-1) }
    var recordingTickerJob by remember { mutableStateOf<Job?>(null) }

    fun stopRecording() {
        try {
            recordingTickerJob?.cancel()
            recordingTickerJob = null
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            val newPath = recordFilePath
            if (!newPath.isNullOrBlank() && File(newPath).exists()) {
                onInsertAttachment(NoteBlock.Audio(newPath))
                onAudioUrlsChanged((audioUrls + newPath).distinct())
                Toast.makeText(context, "Voice memo inserted in note", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to stop recording: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        } finally {
            recordFilePath = null
            recordingDurationSec = 0
            recordingAmplitudes = emptyList()
            recordingInsertTargetIndex = -1
        }
    }

    fun cancelRecording() {
        try {
            recordingTickerJob?.cancel()
            recordingTickerJob = null
            mediaRecorder?.apply {
                runCatching { stop() }
                release()
            }
            mediaRecorder = null
            isRecording = false
            recordFilePath?.let { path ->
                val f = File(path)
                if (f.exists()) f.delete()
            }
            Toast.makeText(context, "Recording discarded", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            recordFilePath = null
            recordingDurationSec = 0
            recordingAmplitudes = emptyList()
            recordingInsertTargetIndex = -1
        }
    }

    fun startRecording(insertAtIndex: Int = -1) {
        try {
            val mediaDir = File(context.filesDir, "notes_media")
            if (!mediaDir.exists()) mediaDir.mkdirs()
            val fileName = "rec_${System.currentTimeMillis()}.3gp"
            val file = File(mediaDir, fileName)
            val path = file.absolutePath
            recordFilePath = path
            recordingInsertTargetIndex = if (insertAtIndex >= 0) insertAtIndex else focusedBlockIndex

            val recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(path)
                prepare()
                start()
            }
            mediaRecorder = recorder
            isRecording = true
            recordingDurationSec = 0
            recordingAmplitudes = emptyList()

            recordingTickerJob?.cancel()
            val startTime = SystemClock.elapsedRealtime()
            recordingTickerJob = coroutineScope.launch {
                while (isRecording && mediaRecorder != null) {
                    val elapsed = ((SystemClock.elapsedRealtime() - startTime) / 1000).toInt()
                    recordingDurationSec = elapsed
                    val amp = runCatching { mediaRecorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                    recordingAmplitudes = (recordingAmplitudes + amp).takeLast(60)
                    delay(100)
                }
            }

            Toast.makeText(context, "Recording started...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to start recording: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startRecording(recordingInsertTargetIndex)
        } else {
            Toast.makeText(context, "Microphone permission is required to record audio notes", Toast.LENGTH_SHORT).show()
        }
    }

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val mediaDir = File(context.filesDir, "notes_media")
                    if (!mediaDir.exists()) mediaDir.mkdirs()
                    val fileName = "img_${System.currentTimeMillis()}.jpg"
                    val file = File(mediaDir, fileName)
                    val outputStream = FileOutputStream(file)
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()
                    onImageUrlChanged(file.absolutePath)
                    onInsertAttachment(NoteBlock.Image(file.absolutePath))
                    Toast.makeText(context, "Image inserted in note", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Failed to attach image: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val mediaDir = File(context.filesDir, "notes_media")
                    if (!mediaDir.exists()) mediaDir.mkdirs()
                    val fileName = "vid_${System.currentTimeMillis()}.mp4"
                    val file = File(mediaDir, fileName)
                    val outputStream = FileOutputStream(file)
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()
                    onInsertAttachment(NoteBlock.Video(file.absolutePath))
                    Toast.makeText(context, "Video attached to note", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Failed to attach video: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var activePlayingAudioPath by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var playProgress by remember { mutableStateOf(0f) }
    var currentPosition by remember { mutableStateOf("0:00") }

    fun seekAudio(fraction: Float, path: String) {
        try {
            val clamped = fraction.coerceIn(0f, 1f)
            if (activePlayingAudioPath == path && mediaPlayer != null) {
                val dur = mediaPlayer?.duration ?: 0
                if (dur > 0) {
                    val targetMs = (clamped * dur).toInt()
                    mediaPlayer?.seekTo(targetMs)
                    playProgress = clamped
                    val curSecs = targetMs / 1000
                    currentPosition = String.format(Locale.US, "%d:%02d", curSecs / 60, curSecs % 60)
                }
            } else {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null
                val player = MediaPlayer().apply {
                    setDataSource(path)
                    prepare()
                    val dur = duration.coerceAtLeast(1)
                    val targetMs = (clamped * dur).toInt()
                    seekTo(targetMs)
                    setOnCompletionListener {
                        isPlaying = false
                        playProgress = 0f
                        currentPosition = "0:00"
                        activePlayingAudioPath = null
                    }
                }
                mediaPlayer = player
                activePlayingAudioPath = path
                playProgress = clamped
                val curSecs = player.currentPosition / 1000
                currentPosition = String.format(Locale.US, "%d:%02d", curSecs / 60, curSecs % 60)
                player.start()
                isPlaying = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun togglePlayPause(path: String) {
        try {
            if (activePlayingAudioPath == path && isPlaying) {
                mediaPlayer?.pause()
                isPlaying = false
            } else if (activePlayingAudioPath == path && mediaPlayer != null) {
                mediaPlayer?.start()
                isPlaying = true
            } else {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null
                playProgress = 0f
                currentPosition = "0:00"

                val player = MediaPlayer().apply {
                    setDataSource(path)
                    prepare()
                    setOnCompletionListener {
                        isPlaying = false
                        playProgress = 0f
                        currentPosition = "0:00"
                        activePlayingAudioPath = null
                    }
                }
                mediaPlayer = player
                activePlayingAudioPath = path
                player.start()
                isPlaying = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Playback error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(isPlaying, activePlayingAudioPath) {
        if (isPlaying && activePlayingAudioPath != null) {
            while (isPlaying && mediaPlayer != null) {
                try {
                    val current = mediaPlayer?.currentPosition ?: 0
                    val duration = mediaPlayer?.duration ?: 1
                    playProgress = current.toFloat() / duration.coerceAtLeast(1)

                    val curSecs = current / 1000
                    val durSecs = duration / 1000
                    currentPosition = String.format(Locale.US, "%d:%02d", curSecs / 60, curSecs % 60)
                } catch (e: Exception) {
                    // ignore
                }
                delay(200)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaRecorder?.release()
            mediaRecorder = null
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    return remember(
        isRecording, recordingDurationSec, recordingAmplitudes,
        isPlaying, activePlayingAudioPath, playProgress, currentPosition
    ) {
        GeneralNotesMediaManager(
            isRecording = isRecording,
            recordingDurationSec = recordingDurationSec,
            recordingAmplitudes = recordingAmplitudes,
            isPlaying = isPlaying,
            activePlayingAudioPath = activePlayingAudioPath,
            playProgress = playProgress,
            currentPosition = currentPosition,
            onToggleRecording = {
                if (isRecording) {
                    stopRecording()
                } else {
                    val targetIdx = (focusedBlockIndex + 1).coerceIn(0, blocksCount)
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        startRecording(targetIdx)
                    } else {
                        recordingInsertTargetIndex = targetIdx
                        recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            },
            onCancelRecording = { cancelRecording() },
            onTogglePlayAudio = { togglePlayPause(it) },
            onSeekAudio = { frac, path -> seekAudio(frac, path) },
            onPickImage = { imageLauncher.launch("image/*") },
            onPickVideo = { videoLauncher.launch("video/*") }
        )
    }
}

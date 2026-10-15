package com.veritas.reader

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.veritas.reader.ui.screens.NoteBlock
import com.veritas.reader.ui.screens.NoteSharing
import com.veritas.reader.ui.screens.rememberGeneralNotesMediaManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun QuizQuestionMedia(question: QuizQuestion, ownerId: String, onChange: ((List<ManagedAttachment>) -> Unit)?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    val latestQuestion by rememberUpdatedState(question)
    val latestChange by rememberUpdatedState(onChange)
    fun insert(block: NoteBlock) {
        val path = NoteSharing.path(block) ?: return
        val kind = if (block is NoteBlock.Audio) AttachmentKind.AUDIO else AttachmentKind.IMAGE
        val record = managedAttachment(context, path, kind, ownerId)
        latestChange?.invoke(latestQuestion.attachments + record)
    }
    val media = rememberGeneralNotesMediaManager(::insert,
        question.attachments.filter { it.kind == AttachmentKind.AUDIO }.mapNotNull { it.resolve(context)?.path }, 0, 0)
    val importAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importing = true; error = null
            try {
                val (file, name, _) = NoteAttachmentStore.copyDocument(context, uri)
                val record = managedAttachment(context, file.path, AttachmentKind.AUDIO, ownerId, displayName = name)
                latestChange?.invoke(latestQuestion.attachments + record)
            } catch (failure: Exception) { if (failure is kotlinx.coroutines.CancellationException) throw failure; error = failure.message }
            finally { importing = false }
        }
    }
    val lifecycle = LocalLifecycleOwner.current
    val currentMedia by rememberUpdatedState(media)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) {
            if (currentMedia.isRecording) currentMedia.onCancelRecording()
            currentMedia.activePlayingAudioPath?.takeIf { currentMedia.isPlaying }?.let(currentMedia.onTogglePlayAudio)
        } }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        question.attachments.forEach { record ->
            key(record.id) {
                val file = record.resolve(context)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (record.kind == AttachmentKind.IMAGE) "Question image" else "Question recording", style = MaterialTheme.typography.labelLarge)
                        if (file == null) Text("Attachment unavailable. Re-add it or restore the portable ZIP backup.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else if (record.kind == AttachmentKind.IMAGE) {
                            val bitmap by produceState<android.graphics.Bitmap?>(null, file.path) {
                                value = withContext(Dispatchers.IO) {
                                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                    BitmapFactory.decodeFile(file.path, bounds)
                                    var scale = 1
                                    while (maxOf(bounds.outWidth, bounds.outHeight) / scale > 1600) scale *= 2
                                    BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = scale })
                                }
                            }
                            bitmap?.let { Image(it.asImageBitmap(), record.displayName.ifBlank { "Image for this quiz question" }, Modifier.fillMaxWidth().heightIn(max = 240.dp), contentScale = ContentScale.Fit) }
                                ?: Text("Image could not be displayed.")
                        } else {
                            val playing = media.isPlaying && media.activePlayingAudioPath == file.path
                            OutlinedButton(onClick = { media.onTogglePlayAudio(file.path) }) { Text(if (playing) "Stop question recording" else "Play question recording") }
                        }
                        if (onChange != null) TextButton(onClick = {
                            file?.path?.takeIf { media.activePlayingAudioPath == it && media.isPlaying }?.let(media.onTogglePlayAudio)
                            onChange(question.attachments.filterNot { it.id == record.id })
                        }) { Text("Remove attachment") }
                    }
                }
            }
        }
        if (onChange != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = media.onPickImage, enabled = !media.isImporting && !importing && !media.isRecording) { Text("Add image") }
                OutlinedButton(onClick = { importAudio.launch(arrayOf("audio/*")) }, enabled = !importing && !media.isRecording) { Text("Add recording") }
                OutlinedButton(onClick = media.onToggleRecording, enabled = !importing) { Text(if (media.isRecording) "Stop & save recording (${media.recordingDurationSec}s)" else "Record audio") }
                if (media.isRecording) TextButton(onClick = media.onCancelRecording) { Text("Discard recording") }
            }
        }
        if (importing || media.isImporting) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

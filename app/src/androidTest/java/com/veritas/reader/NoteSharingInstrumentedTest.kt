package com.veritas.reader

import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaMetadataRetriever
import android.text.SpannableString
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.ReaderViewModel
import com.veritas.reader.ui.importSharedNote
import com.veritas.reader.ui.screens.NoteSharing
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class NoteSharingInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun legacyRecordingIsExtractedAsPlayableAmrWithoutChangingOriginalBytes() {
        val source = File(context.cacheDir, "legacy-${UUID.randomUUID()}.3gp")
        var exported: File? = null
        val codec = MediaCodec.createEncoderByType("audio/3gpp")
        val muxer = MediaMuxer(source.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP)
        var muxerStarted = false
        try {
            val format = MediaFormat.createAudioFormat("audio/3gpp", 8000, 1).apply { setInteger(MediaFormat.KEY_BIT_RATE, 12200) }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val samples = ByteArray(16000) // One second of synthetic silence, without microphone access.
            var offset = 0
            var inputEnded = false
            var outputEnded = false
            var track = -1
            val info = MediaCodec.BufferInfo()
            val expected = java.io.ByteArrayOutputStream().apply { write("#!AMR\n".toByteArray()) }
            val deadline = SystemClock.elapsedRealtime() + 20_000
            while (!outputEnded) {
                check(SystemClock.elapsedRealtime() < deadline) { "AMR fixture encoding timed out" }
                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(10000)
                    if (inputIndex >= 0) {
                        val buffer = codec.getInputBuffer(inputIndex)!!
                        buffer.clear()
                        val count = minOf(buffer.remaining(), samples.size - offset)
                        buffer.put(samples, offset, count)
                        codec.queueInputBuffer(inputIndex, 0, count, offset * 1_000_000L / 16000,
                            if (count == 0) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                        offset += count
                        inputEnded = count == 0
                    }
                }
                val outputIndex = codec.dequeueOutputBuffer(info, 10000)
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(codec.outputFormat); muxer.start(); muxerStarted = true
                } else if (outputIndex >= 0) {
                    val buffer = codec.getOutputBuffer(outputIndex)!!
                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        buffer.position(info.offset); buffer.limit(info.offset + info.size)
                        val bytes = ByteArray(info.size); buffer.get(bytes); expected.write(bytes)
                        buffer.position(info.offset)
                        muxer.writeSampleData(track, buffer, info)
                    }
                    outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(outputIndex, false)
                }
            }
            muxer.stop(); muxerStarted = false
            val original = source.readBytes()
            val (file, mime) = prepareNoteAudioShare(source, File(context.cacheDir, "note_shares"))!!
            exported = file
            assertEquals("audio/amr", mime)
            assertArrayEquals(original, source.readBytes())
            assertArrayEquals(expected.toByteArray(), file.readBytes())
            val metadata = MediaMetadataRetriever()
            try {
                metadata.setDataSource(file.absolutePath)
                assertTrue(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() >= 900)
            } finally { metadata.release() }
        } finally {
            runCatching { codec.stop() }; codec.release()
            if (muxerStarted) runCatching { muxer.stop() }
            muxer.release(); source.delete(); exported?.delete()
        }
    }

    @Test fun textSharingIsIndependentOfAudioAndSingleAttachmentHasNoCaption() {
        val file = File(context.filesDir, "notes_media/share-${UUID.randomUUID()}.m4a").apply {
            parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2, 3))
        }
        try {
            val note = GeneralNote("compatibility", "Title", "Body", 1, audioUrl = file.absolutePath)
            val text = NoteSharing.textIntent(note)!!
            assertEquals("text/plain", text.type)
            assertFalse(text.hasExtra(Intent.EXTRA_STREAM))
            assertNull(text.clipData)
            assertEquals("Title\n\nBody", text.getStringExtra(Intent.EXTRA_TEXT))
            val audio = NoteSharing.attachmentIntent(context, com.veritas.reader.ui.screens.NoteBlock.Audio(file.absolutePath))
            assertEquals("audio/mp4", audio.type)
            assertEquals(Intent.ACTION_SEND, audio.action)
            assertFalse(audio.hasExtra(Intent.EXTRA_TEXT))
            assertTrue(audio.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            context.contentResolver.openInputStream(audio.clipData!!.getItemAt(0).uri)!!.use {
                assertArrayEquals(file.readBytes(), it.readBytes())
            }
        } finally { file.delete() }
    }

    @Test fun completeNoteArchiveRestoresTextAndOwnedRecording() {
        check(context.packageName.endsWith(".checks"))
        val repository = DocumentRepository(context)
        val original = repository.loadGeneralNotes()
        val media = File(context.filesDir, "notes_media/archive-${UUID.randomUUID()}.m4a").apply {
            parentFile!!.mkdirs(); writeBytes(byteArrayOf(4, 5, 6, 7))
        }
        val id = UUID.randomUUID().toString()
        var restoredPath: String? = null
        var archive: Uri? = null
        try {
            val record = managedAttachment(context, media.path, AttachmentKind.AUDIO, "note:$id").copy(id = "stable-note-$id")
            val note = GeneralNote(id, "Complete note", "Body\n[audio](${media.absolutePath})", 1, audioUrl = media.absolutePath, attachments = listOf(record))
            val share = NoteSharing.archiveIntent(context, note)
            archive = share.clipData!!.getItemAt(0).uri
            context.contentResolver.openInputStream(archive!!)!!.use { repository.restoreFullBackupZip(it, false) }
            val restored = repository.loadGeneralNotes().single { it.id == id }
            restoredPath = restored.allAudioUrls.single()
            assertTrue(restored.content.contains("Body"))
            assertNotEquals(media.absolutePath, restoredPath)
            assertArrayEquals(media.readBytes(), File(restoredPath!!).readBytes())
            assertTrue(restored.content.contains(restoredPath!!))
            assertEquals(record.id, restored.attachments.single().id)
            assertEquals(File(restoredPath!!).canonicalFile, restored.attachments.single().resolve(context))
        } finally {
            repository.saveGeneralNotes(original)
            media.delete(); restoredPath?.let { File(it).delete() }
            archive?.let { context.contentResolver.delete(it, null, null) }
        }
    }

    private fun await(job: Job) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (!job.isCompleted) {
            check(SystemClock.elapsedRealtime() < deadline) { "Shared note did not finish saving" }
            SystemClock.sleep(50)
        }
    }

    @Test fun intentReadsRichTextAllStreamsAndClipDataWithoutDuplicates() {
        val first = Uri.parse("content://sender/first")
        val second = Uri.parse("content://sender/second")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            component = ComponentName(context.packageName, "com.veritas.reader.ShareToNotesActivity")
            putExtra(Intent.EXTRA_TEXT, SpannableString("Shared caption"))
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(first, second))
            clipData = ClipData.newRawUri("Photos", first).apply { addItem(ClipData.Item(second)) }
        }
        val share = IncomingShare.from(intent)
        assertEquals("Shared caption", share.text)
        assertEquals(listOf(first, second), share.uris)
        assertTrue(share.toNotes)
        val clipOnly = IncomingShare.from(Intent(Intent.ACTION_SEND).apply { clipData = intent.clipData })
        assertEquals(listOf(first, second), clipOnly.uris)
    }

    @Test fun unchangedSharedTextIsPersistedBeforeTheEditorOpens() {
        check(context.packageName.endsWith(".checks"))
        val repository = DocumentRepository(context)
        val original = repository.loadGeneralNotes()
        val text = "Shared unchanged ${UUID.randomUUID()}\nhttps://example.org/article"
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            lateinit var vm: ReaderViewModel
            lateinit var job: Job
            scenario.onActivity {
                vm = ViewModelProvider(it)[ReaderViewModel::class.java]
                job = vm.importSharedNote(IncomingShare(text = text, toNotes = true))
            }
            await(job)
            val id = vm.uiState.value.generalNoteEditorTarget!!.id
            assertTrue(vm.uiState.value.showGeneralNotesEditor)
            scenario.close()
            assertEquals(text, DocumentRepository(context).loadGeneralNotes().single { it.id == id }.content)
        } finally { scenario.close(); repository.saveGeneralNotes(original) }
    }

    @Test fun mixedIncomingAttachmentsSurviveSenderRemovalAndShareWithReadGrants() {
        check(context.packageName.endsWith(".checks"))
        val repository = DocumentRepository(context)
        val original = repository.loadGeneralNotes()
        val inputs = listOf("png", "mp3", "mp4", "pdf").map { extension ->
            File(context.cacheDir, "shared-${UUID.randomUUID()}.$extension").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        }
        val uris = inputs.map(Uri::fromFile)
        var owned = emptyList<String>()
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            lateinit var vm: ReaderViewModel
            lateinit var job: Job
            scenario.onActivity {
                vm = ViewModelProvider(it)[ReaderViewModel::class.java]
                job = vm.importSharedNote(IncomingShare(uris = uris, mimeType = "*/*", toNotes = true))
            }
            await(job)
            val note = repository.loadGeneralNotes().single { it.id == vm.uiState.value.generalNoteEditorTarget!!.id }
            owned = NoteSharing.blocks(note).mapNotNull(NoteSharing::path)
            inputs.forEach { it.delete() }
            assertEquals(4, owned.size)
            assertEquals(1, note.allImageUrls.size)
            assertEquals(1, note.allAudioUrls.size)
            assertEquals(1, note.allVideoUrls.size)
            owned.forEach { assertArrayEquals(byteArrayOf(1, 2, 3, 4), File(it).readBytes()) }
            val outgoing = NoteSharing.intent(context, note)!!
            assertEquals(Intent.ACTION_SEND_MULTIPLE, outgoing.action)
            assertEquals("*/*", outgoing.type)
            assertEquals(4, outgoing.clipData!!.itemCount)
            assertTrue(outgoing.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            repeat(outgoing.clipData!!.itemCount) { index ->
                context.contentResolver.openInputStream(outgoing.clipData!!.getItemAt(index).uri)!!.use {
                    assertArrayEquals(byteArrayOf(1, 2, 3, 4), it.readBytes())
                }
            }
        } finally {
            scenario.close(); repository.saveGeneralNotes(original)
            inputs.forEach { it.delete() }; owned.forEach { File(it).delete() }
        }
    }
}

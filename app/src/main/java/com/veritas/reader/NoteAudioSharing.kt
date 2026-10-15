package com.veritas.reader

import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteBuffer

/** Older in-app recordings contain AMR in 3GP. Extract real AMR frames; never rename bytes. */
internal fun prepareNoteAudioShare(source: File, directory: File): Pair<File, String>? {
    if (!source.extension.equals("3gp", ignoreCase = true)) return null
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(source.absolutePath)
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) in listOf("audio/3gpp", "audio/amr-wb")
        } ?: return null
        val wideband = extractor.getTrackFormat(track).getString(MediaFormat.KEY_MIME) == "audio/amr-wb"
        extractor.selectTrack(track)
        directory.mkdirs()
        val target = File(directory, "recording_${java.util.UUID.randomUUID()}.amr")
        try {
            target.outputStream().buffered().use { output ->
                output.write((if (wideband) "#!AMR-WB\n" else "#!AMR\n").toByteArray(Charsets.US_ASCII))
                val buffer = ByteBuffer.allocate(64 * 1024)
                var samples = 0
                while (true) {
                    buffer.clear()
                    val count = extractor.readSampleData(buffer, 0)
                    if (count < 0) break
                    require(count in 1..buffer.capacity()) { "Invalid recording frame" }
                    val bytes = ByteArray(count)
                    buffer.position(0); buffer.get(bytes)
                    output.write(bytes)
                    samples++
                    if (!extractor.advance()) break
                }
                require(samples > 0) { "The recording has no audio" }
            }
            return target to "audio/amr"
        } catch (failure: Exception) { target.delete(); throw failure }
    } finally { extractor.release() }
}

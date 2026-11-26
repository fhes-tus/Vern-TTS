package com.veritas.reader

import android.media.MediaExtractor
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.sin

class AacAudioExportInstrumentedTest {
    @Test
    fun streamingWriterProducesReadableMonoAac() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("export-check-", ".m4a", context.cacheDir)
        try {
            AacM4aWriter(file, EXPORT_SAMPLE_RATE).use { writer ->
                repeat(10) { block ->
                    writer.append(ShortArray(EXPORT_SAMPLE_RATE / 10) { index ->
                        val time = (block * (EXPORT_SAMPLE_RATE / 10) + index).toDouble() / EXPORT_SAMPLE_RATE
                        (sin(time * 440 * 2 * Math.PI) * 5_000).toInt().toShort()
                    })
                }
                writer.finish {}
            }
            val reader = MediaExtractor()
            try {
                reader.setDataSource(file.absolutePath)
                assertEquals(1, reader.trackCount)
                val format = reader.getTrackFormat(0)
                assertEquals("audio/mp4a-latm", format.getString(android.media.MediaFormat.KEY_MIME))
                assertEquals(1, format.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT))
                reader.selectTrack(0)
                assertTrue(reader.readSampleData(ByteBuffer.allocate(4096), 0) > 0)
            } finally {
                reader.release()
            }
        } finally {
            file.delete()
        }
    }
}

package com.veritas.reader

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.screens.VeritasCoverPlaceholder
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class GeneratedBookCoverInstrumentedTest {
    @get:Rule val rule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun jacketsAreBoundedStableAndNeverReplaceRealArt() {
        check(context.packageName.endsWith(".checks"))
        val repository = DocumentRepository(context)
        val document = repository.createDocument("A reading without artwork", "A readable passage.", "TXT")
        val real = Bitmap.createBitmap(80, 120, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        try {
            val generated = BookCoverLoader.forDocument(context, document)
            assertEquals(400, generated.width)
            assertEquals(600, generated.height)
            assertTrue(generated.allocationByteCount <= 400 * 600 * 4)
            assertSame(generated, BookCoverLoader.forDocument(context, document))
            val widget = requireNotNull(widgetCover(context, document))
            assertTrue(widget.width <= 256 && widget.height <= 256)
            assertEquals(generated.getPixel(4, 4), widget.getPixel(4, 4))
            if (widget !== generated) widget.recycle()
            val intMin = GeneratedBookCover.palette("polygenelubricants")
            assertTrue(ColorUtils.calculateContrast(intMin.ink, intMin.paper) >= 4.5)
            (0..40).forEach { number ->
                val colors = GeneratedBookCover.palette("cover-$number")
                assertTrue(ColorUtils.calculateContrast(colors.ink, colors.paper) >= 4.5)
            }
            assertNotNull(CoverExtractor.saveCoverBitmap(context, document.id, real))
            val loadedReal = BookCoverLoader.forDocument(context, document)
            val pixel = loadedReal.getPixel(40, 60)
            assertTrue("Existing WebP artwork keeps priority", Color.red(pixel) > 245 && Color.green(pixel) < 8 && Color.blue(pixel) < 8)
        } finally { real.recycle(); repository.deleteDocument(document.id) }
    }

    @Test fun realCoverComponentShowsShortLongAndFileTitlesTogether() {
        val titles = listOf("Pride and Prejudice", "Meditations", "Research_notes_2026.docx",
            "The Art of Paying Attention to the World Around Us", "Study workbook", "An evening of reading")
        rule.setContent { MaterialTheme {
            Column(Modifier.fillMaxWidth().testTag("generated-cover-gallery").padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("Covers for your reading", style = MaterialTheme.typography.titleLarge)
                titles.chunked(3).forEachIndexed { row, group ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        group.forEachIndexed { index, title ->
                            VeritasCoverPlaceholder("preview-${row * 3 + index}", title, if (index == 2) "DOCX" else "TXT",
                                Modifier.weight(1f).aspectRatio(2f / 3f))
                        }
                    }
                }
            }
        } }
        rule.waitForIdle()
        android.os.SystemClock.sleep(600)
        val folder = File(context.getExternalFilesDir(null), "CoverRefresh").apply { mkdirs() }
        val screenshot = rule.onNodeWithTag("generated-cover-gallery").captureToImage().asAndroidBitmap()
        try { File(folder, "generated-jackets.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { screenshot.recycle() }
    }
}

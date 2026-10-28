package com.veritas.reader

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.screens.AudioModeScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class QuoteSharingInstrumentedTest {
    @get:Rule val rule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun listenLongPressSelectsFiveWithoutSeekingAndSharesThePreviewedColorAndCredit() {
        val sentences = (1..6).map { "Sentence $it is part of the selected passage." }
        var seeks = 0
        var pauses = 0
        var playing by mutableStateOf(true)
        var shared by mutableStateOf<File?>(null)
        rule.setContent { MaterialTheme {
            AudioModeScreen("A book by Jane Author", 0, sentences.size, sentences.first(), playing,
                onPlayPause = { playing = !playing; pauses++ }, onNext = {}, onPrevious = {}, onDismiss = {},
                documentChunks = sentences, onSentenceClick = { seeks++ }, onShareQuoteReady = { shared = it })
        } }
        try {
            rule.onNodeWithText("Read", useUnmergedTree = true).performClick()
            rule.onNodeWithTag("listen-sentences").performScrollToIndex(0)
            rule.onNodeWithTag("listen-sentence-0").performTouchInput { longClick() }
            rule.onNodeWithText("1 of 5 selected").assertIsDisplayed()
            for (index in 1..4) {
                rule.onNodeWithTag("listen-sentences").performScrollToIndex(index)
                rule.onNodeWithTag("listen-sentence-$index").performClick()
            }
            rule.onNodeWithText("5 of 5 selected").assertIsDisplayed()
            rule.onNodeWithTag("listen-sentences").performScrollToIndex(5)
            rule.onNodeWithTag("listen-sentence-5").performClick().assertIsNotSelected()
            rule.onNodeWithText("5 of 5 selected").assertIsDisplayed()
            rule.onNodeWithText("Preview").performClick()
            rule.waitUntil(15_000) { rule.onAllNodesWithTag("quote-image-preview").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("quote-source").performTextReplacement("A character in the book")
            rule.onNodeWithTag("quote-color-teal").performScrollTo().performClick()
            rule.waitUntil(15_000) {
                rule.onAllNodesWithContentDescription("Source: A character in the book", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithText("Share image").assertIsEnabled().performClick()
            rule.waitUntil(15_000) { shared != null }
            val image = requireNotNull(BitmapFactory.decodeFile(shared!!.path))
            try {
                assertEquals(1080, image.width)
                assertTrue(image.height in 1350..1920)
                assertEquals(quotePalettes.first { it.id == "teal" }.background, image.getPixel(4, 4))
            } finally { image.recycle() }
            val intent = quoteShareIntent(context, shared!!)
            assertEquals("image/png", intent.type)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals("content", intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)?.scheme)
            val screenshots = File(context.getExternalFilesDir(null), "ReaderOrganization").apply { mkdirs() }
            shared!!.copyTo(File(screenshots, "shared-quote.png"), overwrite = true)
            rule.onNodeWithContentDescription("Back to sentence selection").performClick()
            rule.onNodeWithText("5 of 5 selected").assertIsDisplayed()
            rule.runOnIdle { assertEquals(0, seeks); assertEquals(1, pauses) }
        } finally { shared?.delete() }
    }

    @Test fun cardIncludesCoverAndRealLocalAuthorMetadata() {
        val repository = DocumentRepository(context)
        val original = File.createTempFile("quote_metadata_", ".docx", context.cacheDir)
        var saved: SavedDocument? = null
        val cover = Bitmap.createBitmap(132, 190, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        try {
            ZipOutputStream(original.outputStream()).use {
                it.putNextEntry(ZipEntry("docProps/core.xml"))
                it.write("""<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:creator>Local Author</dc:creator></cp:coreProperties>""".toByteArray())
                it.closeEntry()
            }
            val metadata = repository.createDocument("Quote metadata ${UUID.randomUUID()}", "A readable passage.", "DOCX",
                Uri.fromFile(original), original.name, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            saved = metadata
            val details = loadQuoteBookDetails(context, metadata.id, metadata.title, null)
            assertEquals("Local Author", details.author)
            val image = QuoteCardRenderer.render(context, QuoteCardSpec("A passage worth remembering.", "Book title", "A character", quotePalettes.first(), cover))
            try {
                assertEquals(Color.RED, image.getPixel(110, image.height - 250))
                assertTrue(image.allocationByteCount <= 1080 * 1920 * 4)
            } finally { image.recycle() }
        } finally { cover.recycle(); saved?.let { repository.deleteDocument(it.id) }; original.delete() }
    }
}

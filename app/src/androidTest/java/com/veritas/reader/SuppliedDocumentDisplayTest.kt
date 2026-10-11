package com.veritas.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/** Optional private source fixture: the supplied deck is never bundled or committed. */
class SuppliedDocumentDisplayTest {
    @get:Rule val rule = createComposeRule()


    @Test fun suppliedBreastMilkReportKeepsCoverTableAndIllustrationAccessible() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks"))
        val file = File(context.getExternalFilesDir(null), "original-fixtures/breastmilk.docx")
        assumeTrue("Private source fixture has not been supplied", file.isFile)
        val doc = DocxDocumentParser.parse(file, "Breast milk unit report", preserveSourceFormatting = true)
        val blocks = doc.pages.flatMap { it.blocks }
        assertTrue(blocks.size > 500)
        assertTrue(blocks.any { it is DocxBlock.Table })
        assertTrue(blocks.any { it is DocxBlock.Image })
        assertTrue(docxBlockPlainText(blocks.first()).startsWith("KWAME NKRUMAH"))
        var page by mutableIntStateOf(0)
        rule.setContent { MaterialTheme {
            DocxDocumentCanvas(doc.title, doc.pages[page], doc.totalPages, onNextPage = { page++ }, onPrevPage = { page-- },
                onToggleBars = {}, paperToneMode = PaperToneMode.NATURAL_WHITE, modifier = Modifier.fillMaxSize().testTag("report-docx"))
        } }
        listOf(0, doc.pages.indexOfFirst { it.blocks.any { block -> block is DocxBlock.Table } },
            doc.pages.indexOfFirst { it.blocks.any { block -> block is DocxBlock.Image } }).distinct().forEach { index ->
            rule.runOnIdle { page = index }
            rule.waitForIdle()
            rule.onNodeWithContentDescription("Next Page").assertIsDisplayed()
            val output = File(context.getExternalFilesDir(null), "remaining-fixes/report-docx-page-${index + 1}.png").apply { parentFile!!.mkdirs() }
            output.outputStream().use { rule.onNodeWithTag("report-docx").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun suppliedLabReportPreservesTitleAndNestedContent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks"))
        val file = File(context.getExternalFilesDir(null), "original-fixtures/lab2.docx")
        assumeTrue("Private source fixture has not been supplied", file.isFile)
        val doc = DocxDocumentParser.parse(file, "LAB 2", preserveSourceFormatting = true)
        val blocks = doc.pages.flatMap { it.blocks }
        val title = blocks.filterIsInstance<DocxBlock.Paragraph>().first()
        assertEquals("LAB 2 - ASSIGNMENT (REPORT)", title.text)
        assertEquals("center", title.sourceFormat!!.alignment)
        assertTrue(title.sourceFormat!!.runs.any { it.bold && it.sizePoints == 12f })
        assertTrue(blocks.filterIsInstance<DocxBlock.Bullet>().any { it.level == 1 && it.text.contains("DDR2") })
        var page by mutableIntStateOf(0)
        rule.setContent { MaterialTheme {
            DocxDocumentCanvas(doc.title, doc.pages[page], doc.totalPages, onNextPage = { page++ }, onPrevPage = { page-- },
                onToggleBars = {}, paperToneMode = PaperToneMode.NATURAL_WHITE, modifier = Modifier.fillMaxSize().testTag("lab-docx"))
        } }
        rule.onNodeWithText(title.text).assertIsDisplayed()
        rule.onNodeWithText("Overview").assertIsDisplayed()
        val output = File(context.getExternalFilesDir(null), "remaining-fixes/lab2-docx-app.png").apply { parentFile!!.mkdirs() }
        output.outputStream().use { rule.onNodeWithTag("lab-docx").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        blocks.filterIsInstance<DocxBlock.Bullet>().firstOrNull { it.text.contains("DDR2") }?.let { target ->
            val targetPage = doc.pages.indexOfFirst { target in it.blocks }
            rule.runOnIdle { page = targetPage }
            rule.onNodeWithText(target.text, substring = true).performScrollTo().assertIsDisplayed()
        }
    }

    @Test fun suppliedIsoDeckKeepsSimpleSourceSlidesCompactAndSelectable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks"))
        val file = File(context.getExternalFilesDir(null), "original-fixtures/iso.pptx")
        assumeTrue("Private source fixture has not been supplied", file.isFile)
        val deck = PptxExtractor.parseDeck(file, true)
        assertEquals(67, deck.slideCount)
        var number by mutableIntStateOf(12)
        var selected = ""
        val bitmaps = mutableMapOf<String, Bitmap>()
        ZipFile(file).use { zip ->
            listOf(1,2,3,5,8,12,14,21,29,40,53,67).flatMap { deck.slides[it - 1].mediaPaths }.distinct().forEach { path ->
                zip.getEntry(path)?.let { entry -> zip.getInputStream(entry).use { BitmapFactory.decodeStream(it) } }?.let { bitmaps[path] = it }
            }
        }
        try {
            rule.setContent { MaterialTheme {
                val slide = deck.slides[number - 1]
                PresentationSlideCanvas(slide, deck.slideCount, slide.mediaPaths.mapNotNull(bitmaps::get), bitmaps,
                    showNotes = false, onToggleNotes = {}, onNextSlide = { number++ }, onPrevSlide = { number-- },
                    onToggleBars = {}, onSelectText = { selected = it }, modifier = Modifier.fillMaxSize().testTag("source-slide"))
            } }
            listOf(1,2,3,5,8,12,14,21,29,40,53,67).forEach { page ->
                rule.runOnIdle { number = page }
                rule.waitForIdle()
                rule.onNodeWithContentDescription("Next slide").assertIsDisplayed()
                if (deck.slides[page - 1].layout != null) {
                    rule.onAllNodes(hasText("", substring = true), useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
                        val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                        node.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
                        results.filter { result -> deck.slides[page - 1].layout!!.elements.any { it.text.isNotBlank() && it.text == result.layoutInput.text.text } }.forEach { assertFalse("Source text clips on slide $page: ${it.layoutInput.text}", it.hasVisualOverflow) }
                    }
                }
                val output = File(context.getExternalFilesDir(null), "remaining-fixes/source-slide-$page-app.png").apply { parentFile!!.mkdirs() }
                output.outputStream().use { rule.onNodeWithTag("source-slide").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            rule.runOnIdle { number = 14 }
            assertNotNull("Heel Stick uses supported source-positioned text and images", deck.slides[13].layout)
            rule.onNodeWithText("The Heel Stick", substring = true).performClick()
            assertTrue(selected.contains("Heel"))
        } finally { bitmaps.values.forEach { it.recycle() } }
    }
}

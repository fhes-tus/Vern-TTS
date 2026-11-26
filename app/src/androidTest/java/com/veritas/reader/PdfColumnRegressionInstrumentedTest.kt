package com.veritas.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfColumnRegressionInstrumentedTest {
    private fun normalized(text: String) = text.filter { it.isLetterOrDigit() }.lowercase()

    @Test fun boxedQuoteFollowsTheWholeParagraphThatWrapsBesideIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PDFBoxResourceLoader.init(context)
        PDDocument().use { document ->
            val page = PDPage()
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                fun line(text: String, y: Float, x: Float = 55f) {
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 12f)
                    stream.newLineAtOffset(x, y)
                    stream.showText(text)
                    stream.endText()
                }
                line("The heavy rain had soaked the fields for three days before the crew arrived.", 722f)
                stream.addRect(332f, 604f, 212f, 94f)
                stream.stroke()
                line("When the crew opened the gate,", 686f)
                line("Wait for the water", 686f, 364f)
                line("the floodwater started running", 668f)
                line("to slow down.", 668f, 378f)
                line("down the channel and into", 650f)
                line("Keep the pumps ready.", 650f, 364f)
                line("the storage pond before it reached", 632f)
                line("the village road.", 614f)
                line("The team later inspected the fields to see how far the water had travelled.", 584f)
            }
            val direct = PdfPageTextExtractor.extractPage(document, 1)
            val extracted = PdfTextCleaner.cleanPages(listOf(direct)).text
            val expectedBody = "When the crew opened the gate, the floodwater started running down the channel and into the storage pond before it reached the village road."
            val expectedQuote = "Wait for the water to slow down. Keep the pumps ready."
            assertTrue(extracted, expectedBody in extracted)
            assertTrue(extracted, expectedQuote in extracted)
            assertTrue(extracted, extracted.indexOf(expectedBody) < extracted.indexOf(expectedQuote))
            assertTrue(extracted, extracted.indexOf(expectedQuote) < extracted.indexOf("The team later"))
            assertFalse(extracted, PdfTextCleaner.isPipeTableLine(direct))
        }
    }

    @Test fun narrowGutterUsesColumnOrderAndPreservesDrawnUnderlines() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PDFBoxResourceLoader.init(context)
        PDDocument().use { document ->
            val page = PDPage()
            document.addPage(page)
            val font = PDType1Font.COURIER
            val leftFirst = "Left item 01 continues in this column."
            val leftStart = 36f
            val rightStart = leftStart + font.getStringWidth(leftFirst) / 1000 * 11f + 7.7f
            PDPageContentStream(document, page).use { stream ->
                fun text(value: String, x: Float, y: Float) {
                    stream.beginText()
                    stream.setFont(font, 11f)
                    stream.newLineAtOffset(x, y)
                    stream.showText(value)
                    stream.endText()
                }
                val heading = "A SPANNING TITLE"
                val headingX = (page.mediaBox.width - font.getStringWidth(heading) / 1000 * 11f) / 2f
                text(heading, headingX, 742f)
                stream.moveTo(headingX, 741f)
                stream.lineTo(headingX + font.getStringWidth(heading) / 1000 * 11f, 741f)
                stream.stroke()
                for (row in 1..19) {
                    val number = row.toString().padStart(2, '0')
                    val y = 710f - row * 14
                    text("Left item $number continues in this column.", leftStart, y)
                    text("Right item $number continues in its column.", rightStart, y)
                }
            }
            val extracted = PdfPageTextExtractor.extractPage(document, 1)
            assertTrue(extracted.startsWith("## <u>A SPANNING TITLE</u>"))
            val leftEnd = extracted.indexOf("Left item 19")
            val rightBegin = extracted.indexOf("Right item 01")
            assertTrue("Narrow gutter with shared baselines must keep column order", leftEnd in 0 until rightBegin)
        }
    }

    @Test fun suppliedCoursePageKeepsReadingOrderAndStructure() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = java.io.File(context.cacheDir, "vern-course-source.pdf")
        assumeTrue("The optional course PDF is staged locally for this run", file.isFile)
        PDFBoxResourceLoader.init(context)
        PDDocument.load(file).use { document ->
            val extracted = runBlocking {
                DocumentExtractor.extractPdfChunk(
                    context, android.net.Uri.fromFile(file), document, document.numberOfPages, file.name,
                    PdfImportOptions(startPage = 7, endPage = 7, preferOcrWhenLowText = false)
                ).text
            }
            val part = ReaderTextIndex.build(extracted, document.numberOfPages).parts.first {
                7 in it.pageRange.startPage..it.pageRange.endPage
            }
            val plain = ReadingTextCleanup.forDisplay(part.text)
            val order = listOf("LOYALTY as it pertains", "LOYALTY IS SPELT F.A.T", "Availability:",
                "BENEFITS OF LOYALTY TO THE LOYALIST", "build on.", "BENEFITS A THE LOYAL STEWARD",
                "DISLOYALTY DEFINED", "EXAMPLES OF DISLOYAL BEHAVIOUR", "our instructors.").map(plain::indexOf)
            assertTrue("Both columns should read from top to bottom", order.first() >= 0 &&
                order.zipWithNext().all { (a, b) -> a < b })
            assertFalse("Course prose should not become a table", part.text.lineSequence().any(PdfTextCleaner::isPipeTableLine))
            assertTrue("Source heading underline should reach the reader text", part.text.contains("<u>LOYALTY IS SPELT F.A.T</u>"))
            assertTrue("Bullets and paragraphs should survive into the reader text",
                Regex("""\n[ \t]*•\s+Faithfulness""").containsMatchIn(plain) && "\n\n" in plain)
            assertTrue("Physical wrapped lines must not each become a paragraph",
                Regex("\\n\\n").findAll(extracted).count() in 5..42)
            val display = android.text.SpannableString(part.text)
            com.veritas.reader.ui.screens.applyMarkdownFormatting(display, part.text, context)
            assertTrue(display.getSpans(0, display.length, android.text.style.UnderlineSpan::class.java).isNotEmpty())
        }
    }

    @Test fun suppliedCourseTablesReachTheReaderAfterCleanup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = java.io.File(context.cacheDir, "vern-course-source.pdf")
        assumeTrue("The optional course PDF is staged locally for this run", file.isFile)
        PDFBoxResourceLoader.init(context)
        PDDocument.load(file).use { document ->
            val imported = runBlocking {
                DocumentExtractor.extractPdfChunk(
                    context, android.net.Uri.fromFile(file), document, document.numberOfPages, file.name,
                    PdfImportOptions(preferOcrWhenLowText = false)
                )
            }
            val model = ReaderTextIndex.build(imported.text, document.numberOfPages)
            for (page in listOf(5, 11, 13, 17)) {
                val rows = model.sentences.count { it.pageNumber == page && PdfTextCleaner.isPipeTableLine(it.text.trim()) }
                assertTrue("Course PDF page $page should keep multiple table rows", rows >= 3)
            }
        }
    }

    @Test fun wideSingleColumnProseStaysInOneLane() {
        PDFBoxResourceLoader.init(InstrumentationRegistry.getInstrumentation().targetContext)
        PDDocument().use { document ->
            val page = PDPage()
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                for (row in 1..21) {
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 12f)
                    stream.newLineAtOffset(36f, 735f - row * 17f)
                    stream.showText("Line $row runs straight through the middle of the page without a column gutter.")
                    stream.endText()
                }
            }
            val probe = PdfLayoutProbeStripper.extract(document, 1)
            assertNull(PdfPageTextExtractor.detectColumns(probe, page))
            val extracted = PdfPageTextExtractor.extractPage(document, 1)
            val rows = extracted.lineSequence().filter { it.isNotBlank() }.toList()
            assertEquals(21, rows.size)
            assertTrue(rows[0].startsWith("Line 1 "))
            assertTrue(rows[20].startsWith("Line 21 "))
        }
    }

    @Test fun borderlessTableSurvivesOnPageWithTwoColumnProse() {
        PDFBoxResourceLoader.init(InstrumentationRegistry.getInstrumentation().targetContext)
        PDDocument().use { document ->
            val page = PDPage()
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                fun text(value: String, x: Float, y: Float) {
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 11f)
                    stream.newLineAtOffset(x, y)
                    stream.showText(value)
                    stream.endText()
                }
                listOf(746f, 731f, 703f, 688f, 660f).forEachIndexed { row, y ->
                    text(if (row == 0) "Item" else "Item $row", 40f, y)
                    text(if (row == 0) "Status" else "Ready $row", 220f, y)
                    text(if (row == 0) "Location" else "Shelf $row", 410f, y)
                }
                text("READING NOTES", 40f, 625f)
                for (row in 1..20) {
                    val y = 608f - row * 15
                    text("Left item $row continues in this column with notes.", 40f, y)
                    text("Right item $row continues in its column with notes.", 332f, y)
                }
            }
            val probe = PdfLayoutProbeStripper.extract(document, 1)
            assertNotNull("The two prose columns must also be detected", PdfPageTextExtractor.detectColumns(probe, page))
            val extracted = PdfPageTextExtractor.extractPage(document, 1)
            assertTrue("A separate borderless table must survive column detection",
                extracted.contains("| Item") && extracted.contains("| Status") && extracted.contains("| Location"))
            assertTrue(extracted.lineSequence().count { PdfTextCleaner.isPipeTableLine(it.trim()) } >= 3)
        }
    }

    @Test fun drawnRulesKeepWrappedAndEmptyCellsWithTextBeforeAndAfter() {
        PDFBoxResourceLoader.init(InstrumentationRegistry.getInstrumentation().targetContext)
        PDDocument().use { document ->
            val page = PDPage()
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                fun text(value: String, x: Float, y: Float) {
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 11f)
                    stream.newLineAtOffset(x, y)
                    stream.showText(value)
                    stream.endText()
                }
                val xs = listOf(50f, 215f, 405f)
                val ys = listOf(680f, 650f, 606f, 580f)
                for (x in xs) {
                    stream.moveTo(x, ys.first())
                    stream.lineTo(x, ys.last())
                }
                for (y in ys) {
                    stream.moveTo(xs.first(), y)
                    stream.lineTo(xs.last(), y)
                }
                stream.stroke()
                text("Prose before the grid.", 50f, 715f)
                text("Topic", 58f, 662f)
                text("Details", 222f, 662f)
                text("First item", 58f, 635f)
                text("A long entry", 222f, 635f)
                text("wrapped onto a second line.", 222f, 621f)
                text("Second item", 58f, 593f)
                text("Prose after the grid.", 50f, 540f)
            }
            val probe = PdfLayoutProbeStripper.extract(document, 1)
            val tables = PdfRuledTableExtractor.extract(page, probe)
            assertEquals(listOf(
                listOf("Topic", "Details"),
                listOf("First item", "A long entry\nwrapped onto a second line."),
                listOf("Second item", "")
            ), tables.single().rows)

            val text = PdfPageTextExtractor.extractPage(document, 1)
            assertTrue(text.startsWith("Prose before the grid."))
            assertTrue(text.contains("| First item | A long entry wrapped onto a second line. |"))
            assertTrue(text.contains("| Second item |  |"))
            assertTrue(text.endsWith("Prose after the grid."))
        }
    }

    @Test fun sherlockChapterHeadingsAndInterruptedColumnsKeepReadingOrder() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        PDFBoxResourceLoader.init(instrumentation.targetContext)
        instrumentation.context.assets.open("sherlock-column-regression.pdf").use { input ->
            PDDocument.load(input).use { document ->
                (1..3).forEach { page ->
                    val probe = PdfLayoutProbeStripper.extract(document, page)
                    val layout = PdfPageTextExtractor.detectColumns(probe, document.getPage(page - 1))
                    android.util.Log.i("PdfColumnRegression", "PAGE $page LAYOUT $layout")
                    android.util.Log.i("PdfColumnRegression", "PAGE $page TEXT ${normalized(PdfPageTextExtractor.extractPage(document, page))}")
                }
                // Original PDF pages 13, 101, and 111, supplied by the user.
                val first = normalized(PdfPageTextExtractor.extractPage(document, 1))
                assertTrue("Chapter title must remain intact: ${first.take(160)}", first.contains("chapteri"))
                assertTrue("Subtitle must remain intact", first.contains("mrsherlockholmes"))
                assertTrue(first.indexOf("mrsherlockholmes") < first.indexOf("theyear1878"))
                assertTrue("Read entire left column before right", first.indexOf("empireareirresistibly") >= 0 && first.indexOf("empireareirresistibly") < first.indexOf("drainedthere"))
                val second = normalized(PdfPageTextExtractor.extractPage(document, 2))
                assertTrue(second.contains("chapterix"))
                assertTrue(second.contains("abreakinthechain"))
                assertTrue(second.indexOf("mustremainonguard") >= 0 && second.indexOf("mustremainonguard") < second.indexOf("withallmyomissions"))
                val third = normalized(PdfPageTextExtractor.extractPage(document, 3))
                val rightTop = third.indexOf("metalorjewelry")
                val chapter = third.indexOf("chapterxii")
                assertTrue(third.indexOf("notoneshredorcrumbof") in 0 until rightTop)
                assertTrue(third.indexOf("gainedone") in rightTop until chapter)
                assertTrue(third.indexOf("averypatientman") > chapter)
                assertTrue(third.indexOf("astationupontheway") > third.indexOf("reportthemselvesat"))
            }
        }
    }
}

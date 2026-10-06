package com.veritas.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfColumnRegressionInstrumentedTest {
    private fun normalized(text: String) = text.filter { it.isLetterOrDigit() }.lowercase()
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

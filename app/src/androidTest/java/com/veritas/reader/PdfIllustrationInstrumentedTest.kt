package com.veritas.reader

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.junit.Assert.*
import org.junit.Test

class PdfIllustrationInstrumentedTest {
    @Test fun drawnImageUsesItsFollowingSentenceAsInlineAnchor() {
        PDFBoxResourceLoader.init(InstrumentationRegistry.getInstrumentation().targetContext)
        PDDocument().use { document ->
            val page = PDPage().also(document::addPage)
            val bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.BLUE)
            val image = LosslessFactory.createFromImage(document, bitmap)
            PDPageContentStream(document, page).use { stream ->
                fun text(value: String, y: Float) {
                    stream.beginText(); stream.setFont(PDType1Font.HELVETICA, 12f)
                    stream.newLineAtOffset(60f, y); stream.showText(value); stream.endText()
                }
                text("Before the illustration is a separate sentence.", 700f)
                stream.drawImage(image, 60f, 500f, 120f, 120f)
                text("Following the illustration is another sentence.", 470f)
            }
            val illustrations = PdfIllustrationExtractor.extract(document, 1)
            try {
                assertEquals(1, illustrations.size)
                assertEquals("Following the illustration is another sentence.", illustrations.single().followingText?.trim())
                val part = ReaderTextIndex.build(PdfPageTextExtractor.extractPage(document, 1), 1).parts.single()
                val anchor = InlineIllustrationPlanner.anchors(part, illustrations.map { it.followingText }).single()
                assertTrue(anchor.start > 0)
                assertTrue(part.text.substring(anchor.start).startsWith("Following the illustration"))
                assertEquals(anchor.start, anchor.endExclusive)
                page.rotation = 90
                val rotated = PdfIllustrationExtractor.extract(document, 1)
                try {
                    assertEquals("Rotation must retain the image for bottom-of-page placement", 1, rotated.size)
                    assertNull(rotated.single().followingText)
                } finally { rotated.forEach { it.bitmap.recycle() } }
            } finally { illustrations.forEach { it.bitmap.recycle() }; bitmap.recycle() }
        }
    }
}

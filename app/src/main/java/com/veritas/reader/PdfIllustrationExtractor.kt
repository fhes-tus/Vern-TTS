package com.veritas.reader

import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import com.tom_roush.pdfbox.contentstream.PDFGraphicsStreamEngine
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImage
import com.tom_roush.pdfbox.text.PDFTextStripperByArea

internal data class PdfIllustration(val bitmap: Bitmap, val followingText: String?)

/** Use drawn image positions (including Form XObjects), not resource enumeration order. */
internal class PdfIllustrationExtractor(private val sourcePage: PDPage, columnLayout: () -> PdfColumnLayout?) : PDFGraphicsStreamEngine(sourcePage) {
    private val columns by lazy(columnLayout)
    val illustrations = mutableListOf<PdfIllustration>()
    private var point = PointF()
    override fun drawImage(image: PDImage) {
        if (illustrations.size >= 3 || image.width < 80 || image.height < 80 ||
            image.width.toLong() * image.height > 4_000_000L) return
        // Rotation/crop geometry needs its own transform; retain a bottom illustration
        // rather than inventing an inline anchor for these pages.
        val box = sourcePage.cropBox
        val matrix = graphicsState.currentTransformationMatrix
        val corners = listOf(matrix.transformPoint(0f, 0f), matrix.transformPoint(0f, 1f),
            matrix.transformPoint(1f, 0f), matrix.transformPoint(1f, 1f))
        val left = corners.minOf { it.x } - box.lowerLeftX
        val right = corners.maxOf { it.x } - box.lowerLeftX
        val bottom = box.upperRightY - corners.minOf { it.y }
        val top = box.upperRightY - corners.maxOf { it.y }
        if (right - left < 30f || bottom - top < 30f) return
        val bitmap = image.image ?: return
        val ratio = minOf(1f, 720f / bitmap.width, 720f / bitmap.height)
        val scaled = if (ratio < 1f) Bitmap.createScaledBitmap(bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1), (bitmap.height * ratio).toInt().coerceAtLeast(1), true).also { bitmap.recycle() } else bitmap
        val following = if (sourcePage.rotation == 0 && bottom < box.height - 12f) {
            val columns = this.columns
            val center = (left + right) / 2f
            val regionLeft = if (columns != null && center >= columns.splitX) columns.splitX else 0f
            val regionRight = if (columns != null && center < columns.splitX) columns.splitX else box.width
            val stripper = PDFTextStripperByArea().apply {
                sortByPosition = true
                addRegion("after", RectF(regionLeft, bottom + 1f, regionRight, box.height))
            }
            stripper.extractRegions(sourcePage)
            stripper.getTextForRegion("after").lineSequence().map { it.trim() }.firstOrNull { it.length >= 12 }
        } else null
        illustrations.add(PdfIllustration(scaled, following))
    }
    override fun appendRectangle(p0: PointF, p1: PointF, p2: PointF, p3: PointF) { point = p0 }
    override fun clip(windingRule: Path.FillType) {}
    override fun moveTo(x: Float, y: Float) { point = PointF(x, y) }
    override fun lineTo(x: Float, y: Float) { point = PointF(x, y) }
    override fun curveTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) { point = PointF(x3, y3) }
    override fun getCurrentPoint(): PointF = point
    override fun closePath() {}
    override fun endPath() {}
    override fun strokePath() {}
    override fun fillPath(windingRule: Path.FillType) {}
    override fun fillAndStrokePath(windingRule: Path.FillType) {}
    override fun shadingFill(shadingName: COSName) {}

    companion object {
        fun extract(document: PDDocument, pageNumber: Int): List<PdfIllustration> {
            val page = document.getPage(pageNumber - 1)
            val box = page.cropBox
            return PdfIllustrationExtractor(page) {
                if (page.rotation == 0) {
                    val probe = PdfLayoutProbeStripper.extract(document, pageNumber)
                    PdfColumnDetector.detect(probe.segments, box.width, box.height)
                } else null
            }.apply { processPage(page) }.illustrations
        }
    }
}

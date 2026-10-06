package com.veritas.reader

/** Find a repeated gutter, allowing headings and unequal/short columns. */
internal object PdfColumnDetector {
    fun detect(segments: List<PositionedPdfSegment>, pageWidth: Float, pageHeight: Float): PdfColumnLayout? {
        if (pageWidth <= 1f || pageHeight <= 1f) return null
        val body = segments.filter {
            it.glyphCount >= 3 && it.width >= pageWidth * 0.10f &&
                it.y in 0f..pageHeight && it.minX >= 0f && it.maxX <= pageWidth + 1f
        }
        if (body.size < 6) return null
        val edges = body.flatMap { listOf(it.minX, it.maxX) }.distinct().sorted()
        val candidates = edges.zipWithNext().map { (a, b) -> (a + b) / 2f }
            .filter { it in (pageWidth * 0.20f)..(pageWidth * 0.80f) }
        var best: PdfColumnLayout? = null
        var bestScore = Float.NEGATIVE_INFINITY
        for (split in candidates) {
            val left = body.filter { it.maxX <= split - 3f }
            val right = body.filter { it.minX >= split + 3f }
            if (left.size < 3 || right.size < 3) continue
            val crossing = body.size - left.size - right.size
            if (crossing > maxOf(2, body.size / 5)) continue
            val gutterLeft = left.maxOf { it.maxX }
            val gutterRight = right.minOf { it.minX }
            if (gutterRight - gutterLeft < maxOf(8f, pageWidth * 0.012f)) continue
            val leftTop = left.minOf { it.y }
            val rightTop = right.minOf { it.y }
            val leftBottom = left.maxOf { it.y }
            val rightBottom = right.maxOf { it.y }
            val lineHeight = (left + right).map { it.height }.sorted().let { it[it.size / 2] }.coerceAtLeast(4f)
            if (leftBottom - leftTop < lineHeight * 2 || rightBottom - rightTop < lineHeight * 2) continue
            val overlap = minOf(leftBottom, rightBottom) - maxOf(leftTop, rightTop)
            if (overlap < lineHeight) continue // reject unrelated sidebars/indented blocks
            val pairedRows = left.count { l -> right.any { kotlin.math.abs(l.y - it.y) <= lineHeight } }
            val score = minOf(left.size, right.size) * 4f + pairedRows * 2f +
                (left.size + right.size) - crossing * 8f
            if (score > bestScore) {
                bestScore = score
                val top = (minOf(leftTop, rightTop) - lineHeight).coerceAtLeast(0f)
                best = PdfColumnLayout(
                    (gutterLeft + gutterRight) / 2f, top,
                    (maxOf(leftBottom, rightBottom) + lineHeight * 2).coerceIn(top, pageHeight),
                    pageWidth, pageHeight
                )
            }
        }
        return best
    }
}

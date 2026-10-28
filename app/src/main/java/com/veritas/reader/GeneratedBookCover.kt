package com.veritas.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import java.util.Locale

internal data class GeneratedCoverPalette(val paper: Int, val ink: Int, val field: Int, val fold: Int)

/** Flat publisher-style jackets: actual title first, a folded-page composition beneath it. */
internal object GeneratedBookCover {
    private val palettes = listOf(
        GeneratedCoverPalette(0xFFE6C958.toInt(), 0xFF242C2A.toInt(), 0xFF2B5550.toInt(), 0xFFF4E6AF.toInt()),
        GeneratedCoverPalette(0xFF264B55.toInt(), 0xFFF7EEE0.toInt(), 0xFFE5AA70.toInt(), 0xFF89B5B9.toInt()),
        GeneratedCoverPalette(0xFFE9E3D7.toInt(), 0xFF2B3935.toInt(), 0xFF567E65.toInt(), 0xFFACC4AF.toInt()),
        GeneratedCoverPalette(0xFFA74537.toInt(), 0xFFFFF2D6.toInt(), 0xFFEFB76D.toInt(), 0xFF6D302E.toInt()),
        GeneratedCoverPalette(0xFF303C70.toInt(), 0xFFF4EAD5.toInt(), 0xFFB9C3D9.toInt(), 0xFFDC9B7B.toInt()),
        GeneratedCoverPalette(0xFFE6B9B2.toInt(), 0xFF422D35.toInt(), 0xFF78516A.toInt(), 0xFFF6DBBD.toInt()),
        GeneratedCoverPalette(0xFF2B4536.toInt(), 0xFFF2E7C8.toInt(), 0xFFA7B784.toInt(), 0xFFE4B96D.toInt()),
        GeneratedCoverPalette(0xFFDDD9EB.toInt(), 0xFF373449.toInt(), 0xFF7C779E.toInt(), 0xFFB0BED3.toInt())
    )

    fun palette(identity: String): GeneratedCoverPalette = palettes[identity.hashCode().ushr(1) % palettes.size]

    fun render(context: Context, identity: String, title: String, sourceLabel: String, author: String = ""): Bitmap {
        val colors = palette(identity.ifBlank { title })
        val bitmap = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(colors.paper)
            val sans = Typeface.create(ResourcesCompat.getFont(context, R.font.outfit) ?: Typeface.DEFAULT, 650, false)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            // Two planes echo a page turning, without fake leather, spine or a generic monogram.
            val artTop = 382f
            paint.color = colors.field
            when (identity.hashCode().ushr(1) % 3) {
                0 -> {
                    canvas.drawRect(36f, artTop, 240f, 526f, paint)
                    canvas.drawArc(RectF(144f, artTop, 364f, 670f), 180f, 180f, true, paint)
                    paint.color = colors.fold
                    canvas.drawRect(240f, artTop, 364f, 526f, paint)
                    paint.color = colors.paper
                    canvas.drawArc(RectF(180f, artTop, 300f, 670f), 180f, 180f, true, paint)
                }
                1 -> {
                    canvas.drawOval(RectF(36f, artTop, 324f, 526f), paint)
                    paint.color = colors.fold
                    canvas.drawRect(200f, artTop, 364f, 526f, paint)
                    paint.color = colors.paper
                    canvas.drawRect(200f, artTop, 208f, 526f, paint)
                }
                else -> {
                    canvas.drawRect(36f, artTop, 364f, 526f, paint)
                    paint.color = colors.fold
                    canvas.drawPath(android.graphics.Path().apply {
                        moveTo(36f, 526f); lineTo(364f, artTop); lineTo(364f, 526f); close()
                    }, paint)
                    paint.color = colors.paper
                    canvas.drawRect(192f, artTop, 200f, 526f, paint)
                }
            }
            fun layout(text: String, ink: TextPaint, lines: Int): StaticLayout =
                StaticLayout.Builder.obtain(text, 0, text.length, ink, 328)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
                    .setLineSpacing(2f, 1.02f).setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).build()
            val cleanTitle = title.trim().replace(Regex("(?i)\\.(pdf|epub|docx|pptx|ppt|txt|md|html?|csv|json|jpe?g|png|webp)$"), "")
                .replace('_', ' ').replace(Regex("\\s+"), " ").ifBlank { "Untitled document" }.take(280)
            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.ink; typeface = sans }
            var size = 52f
            var titleLayout: StaticLayout
            do {
                titlePaint.textSize = size
                titleLayout = layout(cleanTitle, titlePaint, 6)
                if (titleLayout.height <= 246 || size <= 30f) break
                size -= 2f
            } while (true)
            canvas.save()
            canvas.translate(36f, 38f)
            titleLayout.draw(canvas)
            if (author.isNotBlank()) {
                canvas.translate(0f, titleLayout.height + 20f)
                titlePaint.textSize = 23f
                titlePaint.typeface = Typeface.create(sans, 450, false)
                layout(author.trim().take(120), titlePaint, 2).draw(canvas)
            }
            canvas.restore()
            titlePaint.textSize = 17f
            titlePaint.typeface = Typeface.create(sans, 550, false)
            canvas.drawText(sourceLabel.trim().uppercase(Locale.ROOT).take(18), 36f, 566f, titlePaint)
            titlePaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Vern", 364f, 566f, titlePaint)
            return bitmap
        } catch (error: Throwable) { bitmap.recycle(); throw error }
    }
}

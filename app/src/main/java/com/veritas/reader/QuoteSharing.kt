package com.veritas.reader

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import androidx.core.content.ContextCompat
import com.veritas.reader.ui.screens.CURATED_CLASSICS
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import java.io.StringReader
import java.util.zip.ZipFile
import org.xmlpull.v1.XmlPullParser

internal object QuoteSelection {
    const val MAX_SENTENCES = 5
    const val MAX_CHARACTERS = 2400

    fun toggle(chunks: List<String>, selection: List<Int>, index: Int): List<Int> {
        require(index in chunks.indices) { "Choose a readable sentence." }
        val current = selection.distinct().filter { it in chunks.indices }.sorted()
        if (index in current) return current - index
        require(current.size < MAX_SENTENCES) { "You can share up to five sentences at a time." }
        val next = (current + index).sorted()
        text(chunks, next)
        return next
    }

    fun text(chunks: List<String>, selection: List<Int>): String {
        require(selection.size in 1..MAX_SENTENCES && selection.distinct().size == selection.size)
        val sentences = selection.sorted().map { index ->
            require(index in chunks.indices && chunks[index].length <= MAX_CHARACTERS * 2) { "Choose shorter sentences for this quote card." }
            ReaderTextIndex.stripInternalMarkers(chunks[index]).trim()
        }
        require(sentences.all { it.isNotBlank() }) { "Choose a readable sentence." }
        require(sentences.sumOf { it.length } + (sentences.size - 1) * 2 <= MAX_CHARACTERS) {
            "This quote is too long for one card. Choose shorter sentences."
        }
        return sentences.joinToString("\n\n")
    }
}

internal data class QuotePalette(val id: String, val name: String, val background: Int, val ink: Int, val accent: Int)
internal val quotePalettes = listOf(
    QuotePalette("cream", "Cream", 0xFFF6F0E6.toInt(), 0xFF292925.toInt(), 0xFF9E4B38.toInt()),
    QuotePalette("ink", "Ink", 0xFF142034.toInt(), 0xFFF7F0E7.toInt(), 0xFF9FC4EF.toInt()),
    QuotePalette("sage", "Sage", 0xFFDCE8DD.toInt(), 0xFF20352B.toInt(), 0xFF4F765D.toInt()),
    QuotePalette("rose", "Rose", 0xFFF3E0E5.toInt(), 0xFF4C2633.toInt(), 0xFFA5536A.toInt()),
    QuotePalette("teal", "Teal", 0xFF123D3B.toInt(), 0xFFECF5E8.toInt(), 0xFFABD4C1.toInt()),
    QuotePalette("charcoal", "Charcoal", 0xFF1F1F24.toInt(), 0xFFF8F5EA.toInt(), 0xFFDEBC74.toInt()),
    QuotePalette("lavender", "Lavender", 0xFFE7DEF4.toInt(), 0xFF382747.toInt(), 0xFF79549B.toInt()),
    QuotePalette("sky", "Sky", 0xFFDCEAF7.toInt(), 0xFF213A53.toInt(), 0xFF497CA8.toInt()),
    QuotePalette("sand", "Sand", 0xFFEBDDCA.toInt(), 0xFF403226.toInt(), 0xFF956E42.toInt()),
    QuotePalette("terracotta", "Terracotta", 0xFF914B38.toInt(), 0xFFFFF0DC.toInt(), 0xFFF3C295.toInt()),
    QuotePalette("plum", "Plum", 0xFF40283F.toInt(), 0xFFF9EAF4.toInt(), 0xFFD7AFD0.toInt()),
    QuotePalette("forest", "Forest", 0xFF263D2D.toInt(), 0xFFF0EED8.toInt(), 0xFFBDD09A.toInt()),
    QuotePalette("burgundy", "Burgundy", 0xFF532537.toInt(), 0xFFFFEADF.toInt(), 0xFFE7B199.toInt()),
    QuotePalette("snow", "Snow", 0xFFFAFAF8.toInt(), 0xFF25282C.toInt(), 0xFF626E7D.toInt())
)

internal data class QuoteBookDetails(val title: String, val author: String, val cover: Bitmap?)
internal data class QuoteCardSpec(val text: String, val title: String, val credit: String, val palette: QuotePalette, val cover: Bitmap?)

/** Resolve only local metadata; never invent an author or fetch book data from the network. */
internal fun loadQuoteBookDetails(context: Context, documentId: String?, title: String, coverFile: File?): QuoteBookDetails {
    val repository = DocumentRepository(context)
    val saved = documentId?.let(repository::findDocument)
    val named = getBookAndAuthor(title)
    val catalogAuthor = saved?.catalogId?.let { id -> CURATED_CLASSICS.firstOrNull { it.id == id || id.startsWith("${it.id}:") }?.author }
    val author = catalogAuthor?.takeIf { it.isNotBlank() } ?: saved?.let { readQuoteAuthor(context, repository.originalFile(it), it.sourceLabel) }
        ?.takeIf { it.isNotBlank() } ?: named.second
    val cover = documentId?.let { BookCoverLoader.document(context, it, saved?.catalogId.orEmpty()) }
        ?: coverFile?.takeIf { it.isFile }?.let { runCatching { OcrImageLoader.load(context, Uri.fromFile(it), 400, 240_000) }.getOrNull() }
        ?: BookCoverLoader.generated(context, documentId ?: title, named.first, saved?.sourceLabel.orEmpty(), author)
    return QuoteBookDetails(named.first, author.trim().take(140), cover)
}

private fun readQuoteAuthor(context: Context, original: File?, sourceLabel: String): String = runCatching {
    if (original == null || !original.isFile) return ""
    when (sourceLabel.uppercase()) {
        "PDF" -> {
            // Metadata is optional. Do not reopen a very large source merely to prefill a credit.
            if (original.length() > 16L * 1024 * 1024) return ""
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
            PDDocument.load(original, MemoryUsageSetting.setupMixed(8L * 1024 * 1024).apply { setTempDir(context.cacheDir) })
                .use { it.documentInformation.author.orEmpty() }
        }
        "DOCX", "EPUB" -> ZipFile(original).use { zip ->
            val entry = if (sourceLabel.equals("DOCX", true)) zip.getEntry("docProps/core.xml")
                else zip.entries().asSequence().take(20_000).firstOrNull { it.name.endsWith(".opf", true) }
            if (entry == null) return ""
            val xml = zip.getInputStream(entry).use { ResourceLimits.read(it, 256 * 1024L) }.toString(Charsets.UTF_8)
            val parser = android.util.Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                setInput(StringReader(xml))
            }
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "creator") return parser.nextText()
                parser.next()
            }
            ""
        }
        else -> ""
    }
}.getOrDefault("")

internal object QuoteCardRenderer {
    fun render(context: Context, spec: QuoteCardSpec): Bitmap {
        require(spec.text.isNotBlank() && spec.text.length <= QuoteSelection.MAX_CHARACTERS)
        val width = 1080
        val quotePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = spec.palette.ink
            typeface = Typeface.create(runCatching { ResourcesCompat.getFont(context, R.font.bitter_variable) }.getOrNull() ?: Typeface.SERIF, 500, false)
        }
        val sans = runCatching { ResourcesCompat.getFont(context, R.font.outfit) }.getOrNull() ?: Typeface.DEFAULT
        fun layout(text: String, paint: TextPaint, maxWidth: Int, maxLines: Int = Int.MAX_VALUE,
            alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth).setAlignment(alignment)
                .setLineSpacing(8f, 1.12f).setIncludePad(false).setMaxLines(maxLines)
                .apply { if (maxLines != Int.MAX_VALUE) setEllipsize(TextUtils.TruncateAt.END) }.build()
        var size = 64f
        var quoteLayout: StaticLayout
        do {
            quotePaint.textSize = size
            quoteLayout = layout(spec.text, quotePaint, 888)
            if (quoteLayout.height <= 900 || size <= 28f) break
            size -= 2f
        } while (true)
        val creditPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = spec.palette.ink; textSize = 32f; typeface = Typeface.create(sans, 500, false)
        }
        val creditLayout = spec.credit.trim().take(140).takeIf { it.isNotBlank() }?.let {
            layout("— $it", creditPaint, 888, 2, Layout.Alignment.ALIGN_OPPOSITE)
        }
        val creditHeight = creditLayout?.let { it.height + 28 } ?: 0
        val height = maxOf(1350, quoteLayout.height + creditHeight + 580)
        require(height <= 1920) { "This quote is too long for one image. Choose fewer sentences." }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(spec.palette.background)
            val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = spec.palette.accent; textSize = 180f; typeface = Typeface.SERIF }
            canvas.drawText("“", 82f, 160f, mark)
            val footerTop = height - 280f
            val quoteTop = 170f + ((footerTop - 80f - 170f - quoteLayout.height - creditHeight) / 2f).coerceAtLeast(0f)
            canvas.save()
            canvas.translate(96f, quoteTop)
            quoteLayout.draw(canvas)
            canvas.restore()
            if (creditLayout != null) {
                canvas.save()
                canvas.translate(96f, quoteTop + quoteLayout.height + 28f)
                creditLayout.draw(canvas)
                canvas.restore()
            }
            canvas.drawRect(96f, footerTop - 32f, 984f, footerTop - 29f, mark.apply { alpha = 100 })
            val coverRect = RectF(96f, footerTop, 228f, footerTop + 190f)
            val cover = spec.cover ?: BookCoverLoader.generated(context, spec.title, spec.title, "")
            canvas.drawBitmap(cover, null, coverRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = spec.palette.ink; textSize = 36f
                typeface = Typeface.create(sans, 600, false)
            }
            canvas.save()
            canvas.translate(268f, footerTop + 12f)
            layout(spec.title.take(240), titlePaint, 716, 3).draw(canvas)
            canvas.restore()
            titlePaint.textSize = 32f
            val brandWidth = titlePaint.measureText("Vern")
            val brandLeft = 984f - brandWidth
            ContextCompat.getDrawable(context, R.drawable.vern_v_cream)?.mutate()?.apply {
                setTint(spec.palette.ink)
                setBounds((brandLeft - 58f).toInt(), height - 72, (brandLeft - 12f).toInt(), height - 37)
                draw(canvas)
            }
            canvas.drawText("Vern", brandLeft, height - 42f, titlePaint)
            return bitmap
        } catch (error: Throwable) { bitmap.recycle(); throw error }
    }

    fun save(context: Context, bitmap: Bitmap): File {
        val directory = File(context.cacheDir, "shared_exports").apply { check(isDirectory || mkdirs()) }
        directory.listFiles()?.filter { it.name.startsWith("quote_") && it.lastModified() < System.currentTimeMillis() - 24 * 3600_000L }
            ?.forEach { it.delete() }
        val file = File.createTempFile("quote_", ".png", directory)
        try {
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            return file
        } catch (error: Throwable) { file.delete(); throw error }
    }
}

internal fun quoteShareIntent(context: Context, file: File): Intent {
    val uri = SafeFileSharing.uri(context, "${context.packageName}.fileprovider", file)
    return Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, "Quote image", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

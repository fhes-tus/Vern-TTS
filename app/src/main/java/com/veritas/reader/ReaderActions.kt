package com.veritas.reader

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun sendPlaybackIntent(
    context: Context,
    action: String,
    documentId: String? = null,
    startIndex: Int = PlaybackStateStore.currentIndex,
    rate: Float = PlaybackStateStore.rate,
    pitch: Float = PlaybackStateStore.pitch
) {
    val intent = Intent(context, PlaybackService::class.java).setAction(action)
    documentId?.let { intent.putExtra(PlaybackActions.EXTRA_DOCUMENT_ID, it) }
    intent.putExtra(PlaybackActions.EXTRA_START_INDEX, startIndex)
    intent.putExtra(PlaybackActions.EXTRA_RATE, rate)
    intent.putExtra(PlaybackActions.EXTRA_PITCH, pitch)
    if (action == PlaybackActions.ACTION_PLAY) {
        ContextCompat.startForegroundService(context, intent)
    } else {
        context.startService(intent)
    }
}

fun sendSelectionSpeechIntent(context: Context, selection: String) {
    val cleanSelection = selection.replace(Regex("\\s+"), " ").trim()
    if (cleanSelection.isBlank()) return
    val intent = Intent(context, PlaybackService::class.java)
        .setAction(PlaybackActions.ACTION_SPEAK_SELECTION)
        .putExtra(PlaybackActions.EXTRA_SELECTION_TEXT, cleanSelection)
    ContextCompat.startForegroundService(context, intent)
}

fun isPackageInstalled(context: Context, packageName: String): Boolean {
    if (packageName.isBlank()) return true
    return runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess
}

fun installedPackageForOption(context: Context, option: AiAssistantOption): String? {
    option.packageNames.firstOrNull { isPackageInstalled(context, it) }?.let { return it }
    if (option.labelKeywords.isEmpty()) return null
    return queryTextShareTargets(context)
        .firstOrNull { target ->
            val label = target.label.lowercase(Locale.getDefault())
            val packageName = target.packageName.lowercase(Locale.getDefault())
            option.labelKeywords.any { keyword -> keyword in label || keyword in packageName }
        }
        ?.packageName
}

fun installedAiOptions(context: Context): List<Pair<AiAssistantOption, String>> {
    return aiAssistantOptions
        .filter { it.packageName.isNotBlank() }
        .mapNotNull { option -> installedPackageForOption(context, option)?.let { option to it } }
}

private data class ShareTarget(val packageName: String, val label: String)

private fun queryTextShareTargets(context: Context): List<ShareTarget> {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
    }
    val pm = context.packageManager
    val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(intent, 0)
    }
    return resolved.mapNotNull { info ->
        val activityInfo = info.activityInfo ?: return@mapNotNull null
        ShareTarget(
            packageName = activityInfo.packageName.orEmpty(),
            label = info.loadLabel(pm).toString()
        )
    }
}

fun openPlayStoreForPackage(context: Context, packageName: String) {
    if (packageName.isBlank()) return
    val market = Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
    val web = Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$packageName".toUri())
    try {
        context.startActivity(market)
    } catch (e: ActivityNotFoundException) {
        android.util.Log.w("ReaderActions", "Play Store app not available for $packageName: ${e.message}")
        try {
            context.startActivity(web)
        } catch (webError: ActivityNotFoundException) {
            android.util.Log.w("ReaderActions", "Could not open Play Store web fallback for $packageName: ${webError.message}")
        }
    }
}

fun copyTextToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val safeText = if (text.length > 200_000) {
        text.take(200_000) + "\n\n[Text truncated for clipboard safety]"
    } else {
        text
    }
    clipboard.setPrimaryClip(ClipData.newPlainText(label, safeText))
    val msg = if (text.length > 200_000) "Copied (truncated to fit clipboard limit)." else "Copied to clipboard."
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}

fun sharePlainText(context: Context, title: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Share from Vern"))
    } catch (e: ActivityNotFoundException) {
        android.util.Log.w("ReaderActions", "No share apps found: ${e.message}")
        copyTextToClipboard(context, title, text)
    }
}

fun openGoogleSearch(context: Context, text: String) {
    val cleanText = text.replace(Regex("\\s+"), " ").trim()
    if (cleanText.isBlank()) return
    val query = Uri.encode(cleanText.take(280))
    val intent = Intent(Intent.ACTION_VIEW, "https://www.google.com/search?q=$query".toUri())
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        android.util.Log.w("ReaderActions", "Could not open web lookup: ${e.message}")
        copyTextToClipboard(context, "Vern lookup", cleanText)
    }
}

fun openGoogleTranslate(context: Context, text: String) {
    val cleanText = text.replace(Regex("\\s+"), " ").trim()
    if (cleanText.isBlank()) return
    val appIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        setPackage("com.google.android.apps.translate")
        putExtra(Intent.EXTRA_TEXT, cleanText)
    }
    if (isPackageInstalled(context, "com.google.android.apps.translate")) {
        try {
            context.startActivity(appIntent)
            return
        } catch (e: ActivityNotFoundException) {
            android.util.Log.w("ReaderActions", "Google Translate app not found despite package check: ${e.message}")
        }
    }
    val web = Intent(
        Intent.ACTION_VIEW,
        "https://translate.google.com/?sl=auto&text=${Uri.encode(cleanText.take(3_500))}&op=translate".toUri()
    )
    try {
        context.startActivity(web)
    } catch (e: ActivityNotFoundException) {
        android.util.Log.w("ReaderActions", "Could not open Google Translate web: ${e.message}")
    }
}

fun askAiWithSelection(context: Context, settings: AskAiSettings, selection: String) {
    val prompt = settings.promptTemplate
        .replace("{selection}", selection)
        .replace("{text}", selection)
        .ifBlank { selection }
    sendPromptToAi(context, settings, "Ask ${settings.assistantLabel}", prompt)
}

fun sendPromptToAi(context: Context, settings: AskAiSettings, subject: String, prompt: String) {
    val selectedPackage = when {
        settings.packageName.isNotBlank() && isPackageInstalled(context, settings.packageName) -> settings.packageName
        settings.assistantId.isNotBlank() -> aiAssistantOptions
            .firstOrNull { it.id == settings.assistantId }
            ?.let { installedPackageForOption(context, it) }
            .orEmpty()
        else -> installedAiOptions(context).firstOrNull()?.second.orEmpty()
    }
    if (selectedPackage.isBlank()) {
        android.util.Log.w("ReaderActions", "No installed AI assistant found")
        copyTextToClipboard(context, "Vern AI prompt", prompt)
        Toast.makeText(context, "No installed AI assistant found. Prompt copied.", Toast.LENGTH_LONG).show()
        return
    }
    if (!isPackageInstalled(context, selectedPackage)) {
        android.util.Log.w("ReaderActions", "Selected AI package $selectedPackage not installed, opening Play Store")
        openPlayStoreForPackage(context, selectedPackage)
        return
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, prompt)
        setPackage(selectedPackage)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        android.util.Log.w("ReaderActions", "Could not launch AI app $selectedPackage: ${e.message}")
        copyTextToClipboard(context, "Vern AI prompt", prompt)
    }
}

fun buildDocumentNotesExport(
    document: ReaderDocument,
    annotations: List<ReaderAnnotation>,
    documentNote: String
): String {
    val notes = annotations
        .filter { it.type == AnnotationType.NOTE && it.note.isNotBlank() }
        .sortedBy { it.chunkIndex }
    // Highlights and bookmarks belong in the export too — the whole point of
    // marking passages while studying is getting them back out afterwards.
    val highlights = annotations
        .filter { it.type == AnnotationType.HIGHLIGHT || it.type == AnnotationType.BOOKMARK }
        .sortedBy { it.chunkIndex }
    val cleanDocumentNote = documentNote.trim()
    val exportedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
    return buildString {
        appendLine("Vern Notes")
        appendLine("Document: ${document.title}")
        appendLine("Source: ${document.sourceLabel}")
        appendLine("Exported: $exportedAt")
        appendLine()
        if (cleanDocumentNote.isNotBlank()) {
            appendLine("Document note")
            appendLine(cleanDocumentNote)
            appendLine()
            appendLine("---")
            appendLine()
        }
        if (highlights.isNotEmpty()) {
            appendLine("Highlights & bookmarks")
            appendLine()
            highlights.forEachIndexed { position, mark ->
                val passage = document.chunks.getOrNull(mark.chunkIndex)
                    .orEmpty()
                    .replace(Regex("\\s+"), " ")
                    .trim()
                val label = if (mark.type == AnnotationType.BOOKMARK) "Bookmark" else "Highlight"
                appendLine("${position + 1}. $label — sentence ${mark.chunkIndex + 1}")
                if (passage.isNotBlank()) appendLine("“$passage”")
                if (mark.note.isNotBlank()) appendLine("Note: ${mark.note.trim()}")
                appendLine()
            }
            appendLine("---")
            appendLine()
        }
        if (notes.isEmpty()) {
            if (cleanDocumentNote.isBlank() && highlights.isEmpty()) {
                appendLine("No notes have been added to this document yet.")
            }
        } else {
            appendLine("Sentence notes")
            appendLine()
            notes.forEachIndexed { position, note ->
                val sentenceNumber = note.chunkIndex + 1
                val excerpt = document.chunks.getOrNull(note.chunkIndex)
                    .orEmpty()
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(360)
                appendLine("${position + 1}. Sentence $sentenceNumber")
                appendLine(note.note.trim())
                if (excerpt.isNotBlank()) {
                    appendLine()
                    appendLine("Excerpt: $excerpt${if (excerpt.length >= 360) "..." else ""}")
                }
                appendLine()
                appendLine("---")
                appendLine()
            }
        }
    }.trim()
}

fun shareBookmarkAsImage(
    context: Context,
    bookTitle: String,
    authorName: String,
    highlightedText: String,
    highlightColorHex: String
) {
    val width = 1080
    val height = 1350
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    
    val paint = Paint()
    val bgGrad = LinearGradient(
        0f, 0f, 0f, height.toFloat(),
        intArrayOf(0xFFF9FBFC.toInt(), 0xFFE9EDF5.toInt()),
        null,
        Shader.TileMode.CLAMP
    )
    paint.shader = bgGrad
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    paint.shader = null
    
    val textPaint = TextPaint().apply {
        color = 0xFF1E293B.toInt()
        textSize = 42f
        isAntiAlias = true
        typeface = Typeface.create("serif", Typeface.NORMAL)
    }
    
    val textPaddingLeft = 110f
    val textWidth = width - textPaddingLeft - 100f
    
    var currentTextSize = 42f
    var staticLayout: StaticLayout
    var textHeight: Float
    do {
        textPaint.textSize = currentTextSize
        staticLayout = StaticLayout.Builder.obtain(
            highlightedText,
            0,
            highlightedText.length,
            textPaint,
            textWidth.toInt()
        )
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(10f, 1.2f)
            .build()
        textHeight = staticLayout.height.toFloat()
        
        if (textHeight <= 700f || currentTextSize <= 26f) {
            break
        }
        currentTextSize -= 2f
    } while (currentTextSize >= 26f)

    if (textHeight > 700f) {
        val maxLines = (700f / (currentTextSize * 1.2f + 10f)).toInt()
        staticLayout = StaticLayout.Builder.obtain(
            highlightedText,
            0,
            highlightedText.length,
            textPaint,
            textWidth.toInt()
        )
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(10f, 1.2f)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        textHeight = staticLayout.height.toFloat()
    }

    val startY = if (textHeight < 700f) 300f + (700f - textHeight) / 2f else 300f

    val quoteColor = runCatching { android.graphics.Color.parseColor(highlightColorHex) }.getOrDefault(0xFFFFE082.toInt())
    val quoteScale = currentTextSize / 42f
    val quotePaint = Paint().apply {
        color = quoteColor
        alpha = 70
        textSize = 250f * quoteScale
        typeface = Typeface.create("serif", Typeface.BOLD)
        isAntiAlias = true
    }
    canvas.drawText("“", 80f, startY - 20f * quoteScale, quotePaint)
    
    val accentPaint = Paint().apply {
        color = quoteColor
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    canvas.drawRoundRect(60f, startY, 75f, startY + textHeight, 8f, 8f, accentPaint)
        
    canvas.save()
    canvas.translate(textPaddingLeft, startY)
    staticLayout.draw(canvas)
    canvas.restore()
    
    val linePaint = Paint().apply {
        color = 0xFFCBD5E1.toInt()
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    canvas.drawLine(100f, 1050f, width - 100f, 1050f, linePaint)
    
    val bookTitlePaint = TextPaint().apply {
        color = 0xFF0F172A.toInt()
        textSize = 36f
        isAntiAlias = true
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    
    val authorPaint = TextPaint().apply {
        color = 0xFF475569.toInt()
        textSize = 32f
        isAntiAlias = true
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
    }
    
    val brandingPaint = TextPaint().apply {
        color = 0xFF94A3B8.toInt()
        textSize = 28f
        isAntiAlias = true
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    
    val bookTitleClean = if (bookTitle.length > 50) bookTitle.take(47) + "..." else bookTitle
    val hasAuthor = authorName.isNotBlank() && authorName != "Vern" && authorName != "Veritas Reader"
    if (hasAuthor) {
        canvas.drawText(bookTitleClean, 100f, 1120f, bookTitlePaint)
        canvas.drawText("by $authorName", 100f, 1170f, authorPaint)
    } else {
        canvas.drawText(bookTitleClean, 100f, 1150f, bookTitlePaint)
    }
    canvas.drawText("Shared via Vern", width - 100f, 1220f, brandingPaint)
    
    val dotPaint = Paint().apply {
        color = quoteColor
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    if (hasAuthor) {
        canvas.drawCircle(80f, 1108f, 10f, dotPaint)
    } else {
        canvas.drawCircle(80f, 1138f, 10f, dotPaint)
    }
    
    val shareDir = File(context.cacheDir, "shares").apply { mkdirs() }
    val shareFile = File(shareDir, "Vern_Highlight_${System.currentTimeMillis()}.png")
    try {
        FileOutputStream(shareFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shareFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Highlight Card"))
    } catch (e: Exception) {
        android.util.Log.e("ReaderActions", "Error saving highlight image: ${e.message}", e)
        sharePlainText(context, bookTitle, "\"$highlightedText\" — $bookTitle by $authorName")
    }
}

fun shareVocabularyAsWords(context: Context, bookTitle: String, entry: VocabularyEntry) {
    val text = buildString {
        append(entry.word)
        if (!entry.pronunciation.isNullOrBlank()) append(" (${entry.pronunciation})")
        append("\n")
        append(entry.explanation)
        if (!entry.contextSentence.isNullOrBlank()) {
            append("\n\nContext:\n\"${entry.contextSentence}\"")
        }
        append("\n\nFrom: $bookTitle")
    }
    sharePlainText(context, "Vocabulary: ${entry.word}", text)
}

fun shareBookmarkAsWords(context: Context, bookTitle: String, text: String, note: String? = null) {
    val shareText = buildString {
        append("“$text”\n\n— $bookTitle")
        if (!note.isNullOrBlank()) {
            append("\n\nNote: $note")
        }
    }
    sharePlainText(context, "Bookmark from $bookTitle", shareText)
}

fun shareNoteAsWords(context: Context, bookTitle: String, sentenceText: String?, noteText: String) {
    val shareText = buildString {
        if (!sentenceText.isNullOrBlank()) {
            append("“$sentenceText”\n\n")
        }
        append("Note:\n$noteText\n\n— $bookTitle")
    }
    sharePlainText(context, "Note from $bookTitle", shareText)
}

fun shareVocabularyAsImage(
    context: Context,
    bookTitle: String,
    authorName: String,
    entry: VocabularyEntry
) {
    val width = 1080
    val height = 1350
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val bgPaint = Paint().apply {
        shader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            intArrayOf(0xFFF8FAFC.toInt(), 0xFFE2E8F0.toInt()),
            null,
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

    val wordPaint = TextPaint().apply {
        color = 0xFF0F172A.toInt()
        textSize = 56f
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        isAntiAlias = true
    }

    val pronPaint = TextPaint().apply {
        color = 0xFF6366F1.toInt()
        textSize = 34f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
        isAntiAlias = true
    }

    val tagPaint = TextPaint().apply {
        color = 0xFF64748B.toInt()
        textSize = 26f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        isAntiAlias = true
    }

    val defPaint = TextPaint().apply {
        color = 0xFF334155.toInt()
        textSize = 36f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        isAntiAlias = true
    }

    val quotePaint = TextPaint().apply {
        color = 0xFF1E293B.toInt()
        textSize = 32f
        typeface = Typeface.create("serif", Typeface.ITALIC)
        isAntiAlias = true
    }

    var y = 140f
    canvas.drawText("VERN VOCABULARY", 100f, y, tagPaint)
    y += 70f
    canvas.drawText(entry.word, 100f, y, wordPaint)
    if (!entry.pronunciation.isNullOrBlank()) {
        val wordW = wordPaint.measureText(entry.word)
        canvas.drawText(entry.pronunciation, 100f + wordW + 24f, y - 4f, pronPaint)
    }
    y += 50f

    val linePaint = Paint().apply {
        color = 0xFFCBD5E1.toInt()
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    canvas.drawLine(100f, y, width - 100f, y, linePaint)
    y += 50f

    // Explanation StaticLayout
    val contentWidth = width - 200
    val defLayout = StaticLayout.Builder.obtain(entry.explanation, 0, entry.explanation.length, defPaint, contentWidth)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(10f, 1.25f)
        .build()

    canvas.save()
    canvas.translate(100f, y)
    defLayout.draw(canvas)
    canvas.restore()
    y += defLayout.height + 60f

    // Context quote card
    if (!entry.contextSentence.isNullOrBlank()) {
        val contextLayout = StaticLayout.Builder.obtain("“${entry.contextSentence}”", 0, entry.contextSentence.length + 2, quotePaint, contentWidth - 40)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(8f, 1.2f)
            .setMaxLines(7)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

        val cardH = contextLayout.height + 60f
        val boxPaint = Paint().apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(100f, y, width - 100f, y + cardH, 16f, 16f, boxPaint)

        val accentBar = Paint().apply {
            color = 0xFF6366F1.toInt()
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(100f, y, 112f, y + cardH, 4f, 4f, accentBar)

        canvas.save()
        canvas.translate(130f, y + 30f)
        contextLayout.draw(canvas)
        canvas.restore()

        y += cardH + 40f
    }

    // Footer
    canvas.drawLine(100f, 1120f, width - 100f, 1120f, linePaint)

    val bookTitlePaint = TextPaint().apply {
        color = 0xFF0F172A.toInt()
        textSize = 34f
        isAntiAlias = true
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    val authorPaint = TextPaint().apply {
        color = 0xFF475569.toInt()
        textSize = 30f
        isAntiAlias = true
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
    }

    val brandingPaint = TextPaint().apply {
        color = 0xFF94A3B8.toInt()
        textSize = 26f
        isAntiAlias = true
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }

    val bookTitleClean = if (bookTitle.length > 50) bookTitle.take(47) + "..." else bookTitle
    val hasAuthor = authorName.isNotBlank() && authorName != "Vern" && authorName != "Veritas Reader"
    if (hasAuthor) {
        canvas.drawText(bookTitleClean, 100f, 1180f, bookTitlePaint)
        canvas.drawText("by $authorName", 100f, 1225f, authorPaint)
    } else {
        canvas.drawText(bookTitleClean, 100f, 1200f, bookTitlePaint)
    }
    canvas.drawText("Shared via Vern", width - 100f, 1270f, brandingPaint)

    val shareDir = File(context.cacheDir, "shares").apply { mkdirs() }
    val shareFile = File(shareDir, "Vern_Vocab_${System.currentTimeMillis()}.png")
    try {
        FileOutputStream(shareFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shareFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Vocabulary Card"))
    } catch (e: Exception) {
        shareVocabularyAsWords(context, bookTitle, entry)
    }
}

fun shareNoteText(context: Context, noteText: String) {
    val pm = context.packageManager
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, noteText)
    }
    
    val resolveInfos = pm.queryIntentActivities(sendIntent, 0)
    val targetIntents = mutableListOf<Intent>()
    val noteKeywords = listOf("note", "keep", "memo", "journal", "evernote", "onenote", "notion")
    
    for (info in resolveInfos) {
        val packageName = info.activityInfo.packageName
        val label = info.loadLabel(pm).toString()
        if (noteKeywords.any { packageName.contains(it, ignoreCase = true) || label.contains(it, ignoreCase = true) }) {
            val intent = Intent(sendIntent).apply {
                setPackage(packageName)
                setClassName(packageName, info.activityInfo.name)
            }
            targetIntents.add(intent)
        }
    }
    
    val chooserIntent = Intent.createChooser(sendIntent, "Share note via...").apply {
        if (targetIntents.isNotEmpty()) {
            putExtra(Intent.EXTRA_INITIAL_INTENTS, targetIntents.toTypedArray())
        }
    }
    context.startActivity(chooserIntent)
}

fun shareToAi(
    context: Context,
    document: ReaderDocument,
    scope: ShareScope,
    selection: ReaderTextSelection?,
    customPageRange: IntRange?,
    settings: AskAiSettings? = null,
    noPrompt: Boolean = false
) {
    val model = ReaderTextModelCache.get(document.id, document.rawText, document.pageCount)
    
    val selectedSentences = when (scope) {
        ShareScope.SELECTED_TEXT -> {
            if (selection == null) return
            model.sentences.filter { it.index in selection.sentenceIndexes }
        }
        ShareScope.CURRENT_SENTENCE -> {
            val currentIndex = PlaybackStateStore.currentIndex
            if (currentIndex in model.sentences.indices) {
                listOf(model.sentences[currentIndex])
            } else {
                emptyList()
            }
        }
        ShareScope.CURRENT_SECTION -> {
            val currentIndex = PlaybackStateStore.currentIndex
            val part = model.partForSentence(currentIndex) ?: return
            model.sentences.subList(part.sentenceStartIndex, part.sentenceEndIndexExclusive)
        }
        ShareScope.CUSTOM_PAGE_RANGE -> {
            if (customPageRange == null) return
            model.sentences.filter { it.pageNumber in customPageRange }
        }
        ShareScope.ENTIRE_DOCUMENT -> {
            model.sentences
        }
    }
    
    if (selectedSentences.isEmpty()) {
        Toast.makeText(context, "No content to share", Toast.LENGTH_SHORT).show()
        return
    }
    
    val markdownText = buildMarkdownForSentences(selectedSentences)
    
    val minPage = selectedSentences.minOfOrNull { it.pageNumber } ?: 1
    val maxPage = selectedSentences.maxOfOrNull { it.pageNumber } ?: 1
    val bookTitle = document.title.trim()
    val sanitizedTitle = bookTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    
    val selectedPackage = settings?.let { s ->
        when {
            s.packageName.isNotBlank() && isPackageInstalled(context, s.packageName) -> s.packageName
            s.assistantId.isNotBlank() -> aiAssistantOptions
                .firstOrNull { it.id == s.assistantId }
                ?.let { installedPackageForOption(context, it) }
                .orEmpty()
            else -> installedAiOptions(context).firstOrNull()?.second.orEmpty()
        }
    } ?: installedAiOptions(context).firstOrNull()?.second.orEmpty()

    val fileNameMd = when (scope) {
        ShareScope.SELECTED_TEXT -> "Vern - $sanitizedTitle - Selection.md"
        ShareScope.CURRENT_SENTENCE -> "Vern - $sanitizedTitle - Sentence ${PlaybackStateStore.currentIndex + 1}.md"
        ShareScope.CURRENT_SECTION -> {
            if (minPage == maxPage) "Vern - $sanitizedTitle - Page $minPage.md"
            else "Vern - $sanitizedTitle - Pages $minPage-$maxPage.md"
        }
        ShareScope.CUSTOM_PAGE_RANGE -> {
            if (minPage == maxPage) "Vern - $sanitizedTitle - Page $minPage.md"
            else "Vern - $sanitizedTitle - Pages $minPage-$maxPage.md"
        }
        ShareScope.ENTIRE_DOCUMENT -> "Vern - $sanitizedTitle - Entire Document.md"
    }
    
    val prompt = "Attached is the document text from '$bookTitle'. Please analyze the contents."
    val textBody = if (noPrompt) markdownText else "$prompt\n\n$markdownText"
    
    if (!noPrompt) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Vern Prompt", prompt))
        Toast.makeText(context, "Prompt copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    val shareDir = File(context.cacheDir, "shares").apply { mkdirs() }
    val shareFileMd = File(shareDir, fileNameMd)
    try {
        FileOutputStream(shareFileMd).use { out ->
            out.write(markdownText.toByteArray(Charsets.UTF_8))
        }
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shareFileMd)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("Vern Share", uri)
            val truncatedText = if (textBody.length <= 60000) {
                textBody
            } else {
                textBody.take(60000) + "\n\n... [Content truncated due to size limits. Full text is attached as a file] ..."
            }
            putExtra(Intent.EXTRA_TEXT, truncatedText)
            if (selectedPackage.isNotBlank()) {
                setPackage(selectedPackage)
            }
        }
        if (selectedPackage.isBlank()) {
            context.startActivity(Intent.createChooser(shareIntent, "Share Document to AI"))
        } else {
            try {
                context.startActivity(shareIntent)
            } catch (e: ActivityNotFoundException) {
                try {
                    val fileNameTxt = fileNameMd.substringBeforeLast(".md") + ".txt"
                    val shareFileTxt = File(shareDir, fileNameTxt)
                    if (!shareFileTxt.exists()) {
                        FileOutputStream(shareFileTxt).use { out ->
                            out.write(markdownText.toByteArray(Charsets.UTF_8))
                        }
                    }
                    val uriTxt = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shareFileTxt)
                    val txtIntent = Intent(shareIntent).apply {
                        putExtra(Intent.EXTRA_STREAM, uriTxt)
                        clipData = ClipData.newRawUri("Vern Share", uriTxt)
                    }
                    context.startActivity(txtIntent)
                } catch (e2: Exception) {
                    val chooserIntent = Intent.createChooser(shareIntent.apply { setPackage(null) }, "Share Document to AI")
                    context.startActivity(chooserIntent)
                }
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("ReaderActions", "Error sharing markdown to AI: ${e.message}", e)
        Toast.makeText(context, "Error sharing file: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

internal fun buildMarkdownForSentences(sentences: List<ReaderSentence>): String {
    if (sentences.isEmpty()) return ""
    val output = StringBuilder()
    val ordered = sentences.groupBy { it.pageNumber.coerceAtLeast(1) }.toSortedMap()
    ordered.forEach { (_, pageSentences) ->
        if (output.isNotBlank()) output.append("\n\n")
        val pageText = StringBuilder()
        pageSentences.forEachIndexed { index, sentence ->
            if (pageText.isNotBlank()) {
                val rawSeparator = sentence.separatorBefore.replace('\r', '\n')
                val separator = when {
                    index > 0 && pageSentences.getOrNull(index - 1)?.pageNumber != sentence.pageNumber -> "\n\n"
                    rawSeparator.count { it == '\n' } >= 2 -> "\n\n"
                    rawSeparator.contains('\n') -> "\n"
                    else -> " "
                }
                pageText.append(separator)
            }
            pageText.append(sentence.text)
        }
        output.append(pageText.toString().trim())
    }
    return output.toString().trim()
}

package com.veritas.reader

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object TranslationLauncher {
    enum class Mode(val label: String) {
        DOCUMENT("Translate full document"),
        CURRENT_SECTION("Translate current section"),
        BILINGUAL_DOCUMENT("Bilingual full document"),
        BILINGUAL_SECTION("Bilingual current section")
    }

    fun launch(
        context: Context,
        title: String,
        chunks: List<String>,
        currentIndex: Int,
        targetLanguage: String,
        mode: Mode
    ) {
        launchTarget(context, null, title, chunks, currentIndex, targetLanguage, mode)
    }

    fun launchTarget(
        context: Context,
        targetPackage: String?,
        title: String,
        chunks: List<String>,
        currentIndex: Int,
        targetLanguage: String,
        mode: Mode
    ) {
        val safeTarget = targetLanguage.trim().ifBlank { "English" }
        val selectedText = when (mode) {
            Mode.CURRENT_SECTION, Mode.BILINGUAL_SECTION -> chunks.getOrNull(currentIndex).orEmpty()
            Mode.DOCUMENT, Mode.BILINGUAL_DOCUMENT -> chunks.joinToString("\n\n")
        }.trim()

        val prompt = when (mode) {
            Mode.DOCUMENT -> "Translate the document below into $safeTarget. Keep the meaning clear and preserve paragraph breaks.\n\nTitle: $title\n\n$selectedText"
            Mode.CURRENT_SECTION -> "Translate the passage below into $safeTarget. Keep the meaning clear.\n\nTitle: $title\n\n$selectedText"
            Mode.BILINGUAL_DOCUMENT -> "Create a bilingual version of the document below. For each paragraph, show the original first, then the $safeTarget translation immediately after it.\n\nTitle: $title\n\n$selectedText"
            Mode.BILINGUAL_SECTION -> "Create a bilingual version of the passage below. Show the original first, then the $safeTarget translation immediately after it.\n\nTitle: $title\n\n$selectedText"
        }

        val isOversized = prompt.length > MAX_SHARE_CHARS

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Vern translation: $title")
            if (!targetPackage.isNullOrBlank()) {
                setPackage(targetPackage)
            }

            if (isOversized) {
                try {
                    val cacheDir = File(context.cacheDir, "translations").apply { mkdirs() }
                    val file = File(cacheDir, "Translation_${System.currentTimeMillis()}.txt")
                    file.writeText(prompt, Charsets.UTF_8)
                    val uri = com.veritas.reader.SafeFileSharing.uri(context, "${context.packageName}.fileprovider", file)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri("Vern Translation", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    putExtra(Intent.EXTRA_TEXT, prompt.take(MAX_SHARE_CHARS) + "\n\n[Vern note: Text truncated in body. Full text attached as file.]")
                } catch (e: Exception) {
                    android.util.Log.w("TranslationLauncher", "Could not create translation file: ${e.message}", e)
                    putExtra(Intent.EXTRA_TEXT, prompt.take(MAX_SHARE_CHARS) + "\n\n[Vern note: Document shortened because share targets reject very long text.]")
                }
            } else {
                putExtra(Intent.EXTRA_TEXT, prompt)
            }
        }
        try {
            if (targetPackage != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                context.startActivity(Intent.createChooser(intent, "Send to Translate or AI app"))
            }
        } catch (e: ActivityNotFoundException) {
            android.util.Log.w("TranslationLauncher", "Direct target failed, falling back to chooser: ${e.message}")
            try {
                val chooserIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Vern translation: $title")
                    putExtra(Intent.EXTRA_TEXT, prompt.take(MAX_SHARE_CHARS))
                }
                context.startActivity(Intent.createChooser(chooserIntent, "Send to Translate or AI app"))
            } catch (fallbackEx: Exception) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Vern translation prompt", prompt))
                Toast.makeText(context, "Translation prompt copied to clipboard.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private const val MAX_SHARE_CHARS = 24000
}

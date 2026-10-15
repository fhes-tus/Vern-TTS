package com.veritas.reader

import android.content.Intent
import android.net.Uri
import android.os.Build

/** Keep all streams and the sender's MIME hint until the chosen import has finished. */
internal data class IncomingShare(
    val text: String = "",
    val uris: List<Uri> = emptyList(),
    val mimeType: String? = null,
    val toNotes: Boolean = false
) {
    val hasContent: Boolean get() = text.isNotBlank() || uris.isNotEmpty()

    companion object {
        fun from(intent: Intent): IncomingShare {
            val sending = intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE
            val streams = mutableListOf<Uri>()
            when (intent.action) {
                Intent.ACTION_VIEW -> intent.data?.let(streams::add)
                Intent.ACTION_SEND -> {
                    val uri = if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    }
                    uri?.let(streams::add)
                }
                Intent.ACTION_SEND_MULTIPLE -> {
                    val items = if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                    }
                    streams.addAll(items.orEmpty())
                }
            }
            if (sending) intent.clipData?.let { clip ->
                repeat(clip.itemCount) { clip.getItemAt(it).uri?.let(streams::add) }
            }
            val text = if (sending) {
                intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty().ifBlank {
                    intent.clipData?.let { clip ->
                        (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).text?.toString() }.joinToString("\n")
                    }.orEmpty()
                }
            } else ""
            return IncomingShare(text, streams.distinct(), intent.type,
                intent.component?.className?.endsWith("ShareToNotesActivity") == true)
        }
    }
}

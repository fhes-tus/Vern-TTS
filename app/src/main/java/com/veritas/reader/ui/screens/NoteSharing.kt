package com.veritas.reader.ui.screens

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import android.app.AlertDialog
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.veritas.reader.GeneralNote
import com.veritas.reader.withManagedAttachments
import com.veritas.reader.mediaSha256
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.*

internal object NoteSharing {
    fun blocks(note: GeneralNote): List<NoteBlock> {
        val blocks = VeritasNoteEditing.parseNoteBlocks(NoteLinks.strip(note.content)).toMutableList()
        val paths = blocks.mapNotNull(::path).toMutableSet()
        note.allImageUrls.filter { paths.add(it) }.forEach { blocks.add(NoteBlock.Image(it)) }
        note.allAudioUrls.filter { paths.add(it) }.forEach { blocks.add(NoteBlock.Audio(it)) }
        return blocks
    }

    fun path(block: NoteBlock): String? = when (block) {
        is NoteBlock.Text -> null
        is NoteBlock.Image -> block.path
        is NoteBlock.Audio -> block.path
        is NoteBlock.Video -> block.path
        is NoteBlock.File -> block.path
    }

    /** Attachment tags and preview metadata belong to storage, never the shared text. */
    fun plainText(note: GeneralNote): String {
        val body = blocks(note).filterIsInstance<NoteBlock.Text>().joinToString("\n") {
            RichTextFormatter.stripMarkup(it.value.text)
        }.let { raw ->
            if (!note.isChecklist) raw else raw.lineSequence().joinToString("\n") { line ->
                when {
                    line.startsWith("[x]", true) -> "☑ " + line.substring(3).trim()
                    line.startsWith("[ ]") -> "☐ " + line.substring(3).trim()
                    else -> line
                }
            }
        }
        val links = NoteLinks.read(note.content).map { it.url }.filterNot { body.contains(it) }
        return (listOf(note.title, body) + links).filter { it.isNotBlank() }.joinToString("\n\n").trim()
    }

    fun intent(context: Context, note: GeneralNote): Intent? {
        val text = plainText(note)
        val streams = blocks(note).mapNotNull { block ->
            val path = path(block) ?: return@mapNotNull null
            runCatching {
                val file = if (path.startsWith("file://")) File(Uri.parse(path).path.orEmpty()) else File(path)
                // Publish only owned files. Never forward a sender's temporary grant or a remote URL.
                require(file.isFile && file.length() > 0)
                val uri = com.veritas.reader.SafeFileSharing.uri(context, "${context.packageName}.fileprovider", file)
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
                    ?.takeUnless { it == "application/octet-stream" }
                    ?: when (block) {
                        is NoteBlock.Image -> "image/*"
                        is NoteBlock.Audio -> "audio/*"
                        is NoteBlock.Video -> "video/*"
                        else -> "application/octet-stream"
                    }
                uri to mime
            }.getOrNull()
        }.distinctBy { it.first }
        if (text.isBlank() && streams.isEmpty()) return null
        return Intent(if (streams.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND).apply {
            type = commonMimeType(streams.map { it.second })
            putExtra(Intent.EXTRA_SUBJECT, note.title.ifBlank { "Vern note" })
            if (text.isNotBlank()) putExtra(Intent.EXTRA_TEXT, text)
            if (streams.size == 1) putExtra(Intent.EXTRA_STREAM, streams.single().first)
            if (streams.size > 1) putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(streams.map { it.first }))
            if (streams.isNotEmpty()) {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(context.contentResolver, "Note attachments", streams.first().first).apply {
                    streams.drop(1).forEach { addItem(ClipData.Item(it.first)) }
                }
            }
        }
    }

    fun commonMimeType(types: List<String>): String {
        if (types.isEmpty()) return "text/plain"
        if (types.distinct().size == 1) return types.first()
        val families = types.map { it.substringBefore('/') }.distinct()
        return if (families.size == 1) "${families.single()}/*" else "*/*"
    }

    fun share(context: Context, note: GeneralNote) {
        val attachments = blocks(note).filter { path(it) != null }
        if (attachments.isEmpty()) { launchShare(context, textIntent(note), "Share note"); return }
        val choices = mutableListOf<Pair<String, () -> Unit>>()
        if (plainText(note).isNotBlank()) {
            choices.add("Share note text" to { launchShare(context, textIntent(note), "Share note text") })
            choices.add("Share note as PDF" to { prepareAndShare(context) { pdfIntent(context, note) } })
        }
        attachments.forEachIndexed { index, block ->
            val name = if (block is NoteBlock.Audio) "Share recording ${attachments.take(index + 1).count { it is NoteBlock.Audio }}"
                else "Share ${File(path(block).orEmpty()).name}"
            choices.add(name to { prepareAndShare(context) { attachmentIntent(context, block) } })
        }
        choices.add("Export complete note (ZIP)" to { prepareAndShare(context) { archiveIntent(context, note) } })
        AlertDialog.Builder(context).setTitle("Share note")
            .setItems(choices.map { it.first }.toTypedArray()) { _, which -> choices[which].second() }
            .setNegativeButton("Cancel", null).show()
    }

    internal fun textIntent(note: GeneralNote): Intent? = plainText(note).takeIf { it.isNotBlank() }?.let { text ->
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, note.title.ifBlank { "Vern note" })
            putExtra(Intent.EXTRA_TEXT, text)
        }
    }

    private fun fileIntent(context: Context, file: File, mime: String): Intent {
        val uri = com.veritas.reader.SafeFileSharing.uri(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
        }
    }

    internal fun attachmentIntent(context: Context, block: NoteBlock): Intent {
        val sourcePath = requireNotNull(path(block))
        val source = if (sourcePath.startsWith("file://")) File(Uri.parse(sourcePath).path.orEmpty()) else File(sourcePath)
        require(source.isFile && source.length() > 0) { "This attachment is no longer available" }
        if (block is NoteBlock.Audio) {
            com.veritas.reader.prepareNoteAudioShare(source, File(context.cacheDir, "note_shares"))?.let { (file, mime) ->
                return fileIntent(context, file, mime)
            }
        }
        val mime = if (block is NoteBlock.Audio && source.extension.equals("m4a", true)) "audio/mp4"
            else MimeTypeMap.getSingleton().getMimeTypeFromExtension(source.extension.lowercase(java.util.Locale.ROOT))
                ?: "application/octet-stream"
        return fileIntent(context, source, mime)
    }

    internal fun pdfIntent(context: Context, note: GeneralNote): Intent {
        val text = plainText(note)
        require(text.isNotBlank()) { "There is no note text to export" }
        val target = File(File(context.cacheDir, "note_shares").apply { mkdirs() }, "note_${UUID.randomUUID()}.pdf")
        val paint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = 14f; color = android.graphics.Color.BLACK }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, 523)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(4f, 1f).build()
        val pdf = PdfDocument()
        try {
            var firstLine = 0
            var pageIndex = 1
            while (firstLine < layout.lineCount) {
                val top = layout.getLineTop(firstLine)
                var lastLine = firstLine + 1
                while (lastLine < layout.lineCount && layout.getLineBottom(lastLine) - top <= 750) lastLine++
                val bottom = layout.getLineTop(lastLine)
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, pageIndex++).create())
                page.canvas.save()
                page.canvas.translate(36f, 36f - top)
                page.canvas.clipRect(0f, top.toFloat(), 523f, bottom.toFloat())
                layout.draw(page.canvas)
                page.canvas.restore()
                pdf.finishPage(page)
                firstLine = lastLine
            }
            target.outputStream().use(pdf::writeTo)
        } catch (failure: Exception) { target.delete(); throw failure }
        finally { pdf.close() }
        return fileIntent(context, target, "application/pdf")
    }

    /** Reuses the app's importable backup schema, with just this note and its owned media. */
    internal fun archiveIntent(context: Context, original: GeneralNote): Intent {
        val note = original.withManagedAttachments(context)
        val directory = File(context.filesDir, "notes_media").canonicalFile
        val media = blocks(note).mapNotNull(::path).distinct().map { path ->
            val file = File(path).canonicalFile
            require(file.isFile && file.toPath().startsWith(directory.toPath())) { "A note attachment is unavailable" }
            path to file
        }
        var content = note.content
        var image = note.imageUrl
        val audio = note.allAudioUrls.toMutableList()
        val manifest = JSONArray()
        media.forEachIndexed { index, (path, file) ->
            val name = "${index}_${file.name}"
            val reference = "attachments/$name"
            content = content.replace(path, reference)
            if (image == path) image = reference
            audio.indices.forEach { if (audio[it] == path) audio[it] = reference }
            manifest.put(JSONObject().put("version", 1).put("path", reference).put("fileName", name)
                .put("id", note.attachments.firstOrNull { it.relativePath.substringAfterLast('/') == file.name }?.id)
                .put("size", file.length()).put("sha256", mediaSha256(file)))
        }
        val saved = note.copy(id = note.id.ifBlank { UUID.randomUUID().toString() }, content = content,
            imageUrl = image, audioUrl = audio.firstOrNull(), audioUrls = audio)
        val root = JSONObject().put("schema", "veritas.reader.backup.v1").put("createdAt", System.currentTimeMillis())
            .put("documents", JSONArray()).put("generalNotes", JSONArray().put(saved.toJson())).put("noteMedia", manifest)
        val target = File(File(context.cacheDir, "note_shares").apply { mkdirs() }, "note_${UUID.randomUUID()}.zip")
        try {
            ZipOutputStream(target.outputStream().buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("backup.json")); zip.write(root.toString().toByteArray(Charsets.UTF_8)); zip.closeEntry()
                zip.putNextEntry(ZipEntry("note.txt")); zip.write(plainText(note).toByteArray(Charsets.UTF_8)); zip.closeEntry()
                media.forEachIndexed { index, (_, file) ->
                    zip.putNextEntry(ZipEntry("note_media/${index}_${file.name}"))
                    file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                }
            }
        } catch (failure: Exception) { target.delete(); throw failure }
        return fileIntent(context, target, "application/zip")
    }

    private fun prepareAndShare(context: Context, prepare: () -> Intent) {
        Toast.makeText(context, "Preparing to share…", Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.Main).launch {
            try { launchShare(context, withContext(Dispatchers.IO) { prepare() }, "Share from Vern") }
            catch (failure: Exception) { Toast.makeText(context, failure.message ?: "Could not prepare sharing", Toast.LENGTH_LONG).show() }
        }
    }

    private fun launchShare(context: Context, intent: Intent?, title: String) {
        if (intent == null) {
            Toast.makeText(context, "Nothing available to share", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching { context.startActivity(Intent.createChooser(intent, title)) }.onFailure {
            Toast.makeText(context, "Could not open sharing", Toast.LENGTH_SHORT).show()
        }
    }
}

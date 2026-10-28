package com.veritas.reader

import java.util.Locale

internal enum class ImportFormat(val sourceLabel: String) {
    PDF("PDF"), DOCX("DOCX"), PPTX("PPTX"), PPT("PPT"), EPUB("EPUB"),
    TEXT("TXT"), HTML("TXT"), IMAGE("OCR")
}

/** Only formats with an actual extractor belong in the chooser or parser dispatch. */
internal object ImportFilePolicy {
    const val UNSUPPORTED_MESSAGE = "Unsupported file. Choose a PDF, EPUB, DOCX, PPT/PPTX, text, HTML or photo."
    private val extensions = buildMap {
        put("pdf", ImportFormat.PDF)
        put("docx", ImportFormat.DOCX)
        put("pptx", ImportFormat.PPTX)
        put("ppt", ImportFormat.PPT)
        put("epub", ImportFormat.EPUB)
        listOf("html", "htm", "xhtml").forEach { put(it, ImportFormat.HTML) }
        listOf("txt", "text", "md", "markdown", "csv", "tsv", "log", "json", "yml", "yaml",
            "rst", "srt", "vtt", "tex", "latex", "ini", "conf", "properties", "asciidoc", "adoc")
            .forEach { put(it, ImportFormat.TEXT) }
        listOf("png", "jpg", "jpeg", "webp", "bmp", "heic", "heif", "avif", "gif")
            .forEach { put(it, ImportFormat.IMAGE) }
    }
    private val mimeFormats = buildMap {
        put("application/pdf", ImportFormat.PDF)
        put("application/epub+zip", ImportFormat.EPUB)
        put("application/vnd.openxmlformats-officedocument.wordprocessingml.document", ImportFormat.DOCX)
        put("application/vnd.openxmlformats-officedocument.presentationml.presentation", ImportFormat.PPTX)
        put("application/vnd.ms-powerpoint", ImportFormat.PPT)
        listOf("text/html", "application/xhtml+xml").forEach { put(it, ImportFormat.HTML) }
        listOf("text/plain", "text/markdown", "text/x-markdown", "text/csv", "text/tab-separated-values",
            "text/vtt", "application/json", "text/json", "application/yaml", "text/yaml",
            "application/x-yaml", "application/x-tex", "application/x-latex")
            .forEach { put(it, ImportFormat.TEXT) }
        listOf("image/jpeg", "image/png", "image/webp", "image/bmp", "image/x-ms-bmp",
            "image/heic", "image/heif", "image/avif", "image/gif").forEach { put(it, ImportFormat.IMAGE) }
    }

    fun detect(name: String, mimeType: String): ImportFormat? {
        val leaf = name.substringAfterLast('/').lowercase(Locale.ROOT)
        val extension = leaf.substringAfterLast('.', "")
        // A sender's MIME hint must not turn app.aab or archive.zip into a text/photo import.
        if (extension.isNotEmpty()) return extensions[extension]
        return mimeFormats[mimeType.substringBefore(';').trim().lowercase(Locale.ROOT)]
    }

    fun chooserMimeTypes(): Array<String> = mimeFormats.keys.toTypedArray()

    fun byteLimit(format: ImportFormat): Long = when (format) {
        ImportFormat.TEXT, ImportFormat.HTML -> 16L * 1024 * 1024
        ImportFormat.IMAGE -> 32L * 1024 * 1024
        else -> ResourceLimits.DOCUMENT_INPUT_BYTES
    }

    fun validateHeader(format: ImportFormat, bytes: ByteArray) {
        require(bytes.isNotEmpty()) { "This file is empty." }
        fun starts(vararg prefix: Int) = bytes.size >= prefix.size &&
            prefix.indices.all { (bytes[it].toInt() and 255) == prefix[it] }
        val zip = starts(0x50, 0x4b, 0x03, 0x04)
        val ole = starts(0xd0, 0xcf, 0x11, 0xe0, 0xa1, 0xb1, 0x1a, 0xe1)
        when (format) {
            ImportFormat.PDF -> require(bytes.take(1024).toByteArray().toString(Charsets.ISO_8859_1).contains("%PDF-")) {
                "This file is not a readable PDF."
            }
            ImportFormat.DOCX, ImportFormat.PPTX, ImportFormat.EPUB -> require(zip) {
                "The file contents do not match the selected document format."
            }
            ImportFormat.PPT -> require(ole) { "This file is not a readable PowerPoint document." }
            ImportFormat.TEXT, ImportFormat.HTML -> {
                require(!zip && !ole && !starts(0x25, 0x50, 0x44, 0x46) && !starts(0x4d, 0x5a) &&
                    !starts(0x7f, 0x45, 0x4c, 0x46) && !starts(0x64, 0x65, 0x78, 0x0a) &&
                    !starts(0x89, 0x50, 0x4e, 0x47) && !starts(0xff, 0xd8, 0xff) &&
                    !starts(0x47, 0x49, 0x46, 0x38) && !starts(0x52, 0x49, 0x46, 0x46)) {
                    "This file contains binary data, not readable text."
                }
            }
            ImportFormat.IMAGE -> Unit // The bounded platform image decoder validates the image.
        }
    }

    fun validateText(text: String) {
        val controls = text.count { it == '\u0000' || (it.code < 32 && it !in "\n\r\t\u000c") }
        require(controls <= text.length / 100) { "This file contains binary data, not readable text." }
    }
}

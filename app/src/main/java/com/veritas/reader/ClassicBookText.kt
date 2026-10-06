package com.veritas.reader

private val GUTENBERG_START_REGEX = Regex("""\*\*\*\s*START OF (THE|THIS) PROJECT GUTENBERG[^\n]*\*\*\*""", RegexOption.IGNORE_CASE)
private val GUTENBERG_END_REGEX = Regex("""\*\*\*\s*END OF (THE|THIS) PROJECT GUTENBERG[^\n]*\*\*\*""", RegexOption.IGNORE_CASE)
private val DOUBLE_NEWLINE_SPLIT_REGEX = Regex("""\n\s*\n+""")
private val LIST_NUMBERED_REGEX = Regex("""^\d+[.)]""")
private val CLASSIC_HEADING_REGEX = Regex("""^(CHAPTER|Chapter|PROLOGUE|Prologue|EPILOGUE|Epilogue|INTRODUCTION|Introduction|PREFACE|Preface|PART|Part|BOOK|Book|ACT|Act|SCENE|Scene)\b.*""", RegexOption.IGNORE_CASE)

fun cleanAndUnwrapClassicBookText(rawText: String): String {
    val normalized = rawText.replace("\r\n", "\n").replace('\r', '\n')
    var body = normalized
    val startMarker = GUTENBERG_START_REGEX.find(body)
    if (startMarker != null) {
        body = body.substring(startMarker.range.last + 1).trimStart()
    }
    val endMarker = GUTENBERG_END_REGEX.find(body)
    if (endMarker != null) {
        body = body.substring(0, endMarker.range.first).trimEnd()
    }

    val paragraphs = body.split(DOUBLE_NEWLINE_SPLIT_REGEX)
    val result = StringBuilder()

    paragraphs.forEach { paragraph ->
        val trimmed = paragraph.trim()
        if (trimmed.isBlank()) return@forEach

        val lines = trimmed.split('\n').map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return@forEach

        if (result.isNotEmpty()) {
            result.append("\n\n")
        }

        val isList = lines.all { it.startsWith("-") || it.startsWith("*") || it.startsWith("•") || LIST_NUMBERED_REGEX.containsMatchIn(it) }
        val isShortLinesPoetry = lines.size >= 3 && lines.all { it.length < 45 }
        val isExplicitHeading = lines.size == 1 && (
            lines[0].startsWith("#") ||
            CLASSIC_HEADING_REGEX.matches(lines[0]) ||
            (lines[0].length in 3..60 && lines[0].filter { it.isLetter() }.all { it.isUpperCase() })
        )

        if (isList || isShortLinesPoetry || isExplicitHeading) {
            result.append(lines.joinToString("\n"))
        } else {
            val unwrappedPara = StringBuilder()
            lines.forEach { line ->
                if (unwrappedPara.isEmpty()) {
                    unwrappedPara.append(line)
                } else {
                    if (unwrappedPara.endsWith("-") && line.firstOrNull()?.isLowerCase() == true) {
                        unwrappedPara.deleteCharAt(unwrappedPara.length - 1)
                        unwrappedPara.append(line)
                    } else {
                        unwrappedPara.append(' ').append(line)
                    }
                }
            }
            result.append(unwrappedPara.toString())
        }
    }

    return result.toString()
}


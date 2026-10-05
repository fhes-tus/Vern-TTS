package com.veritas.reader

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal fun previewText(text: String): String {
    return extractSynopsisHeuristic(text, maxChars = 360)
}

internal fun extractSynopsisHeuristic(rawText: String, maxChars: Int = 360): String {
    if (rawText.isBlank()) return ""
    val cleaned = ReaderTextIndex.stripInternalMarkers(rawText)
    // Sample up to first 40,000 characters to process front matter without excessive memory
    val sample = if (cleaned.length > 40000) cleaned.substring(0, 40000) else cleaned

    // If a Gutenberg start marker exists, skip past it to the actual text body
    val gutenbergMarker = "*** START OF"
    val gutenbergIdx = sample.indexOf(gutenbergMarker, ignoreCase = true)
    val searchBody = if (gutenbergIdx >= 0) {
        val afterMarker = sample.substring(gutenbergIdx)
        val endLineIdx = afterMarker.indexOf('\n')
        if (endLineIdx >= 0) afterMarker.substring(endLineIdx + 1) else afterMarker
    } else {
        sample
    }

    // Normalize newlines and split into candidate paragraphs
    val rawParagraphs = searchBody.split(Regex("(?:\r?\n){2,}"))
        .map { it.replace(Regex("\\s+"), " ").trim() }
        .filter { it.isNotBlank() }

    val skipKeywords = listOf(
        "project gutenberg",
        "all rights reserved",
        "copyright",
        "isbn",
        "library of congress",
        "table of contents",
        "printed in",
        "published by",
        "first edition",
        "public domain",
        "redistributing",
        "electronic work",
        "foundation",
        "license",
        "cover design",
        "cover art",
        "typeset",
        "typesetting",
        "cataloging-in-publication",
        "cataloguing in publication",
        "reproduced in any form",
        "permissions department",
        "without written permission",
        "start of the project gutenberg",
        "end of the project gutenberg"
    )

    fun isHeaderOrTOC(p: String): Boolean {
        val lower = p.lowercase()
        // Strong boilerplate trigger
        if (skipKeywords.any { lower.contains(it) }) return true

        // Chapter / heading markers
        if (lower.startsWith("chapter ") || lower.startsWith("book ") || lower.startsWith("part ") ||
            lower.startsWith("act ") || lower.startsWith("scene ") || lower.startsWith("canto ")) {
            if (p.length < 80) return true
        }
        if (lower == "contents" || lower == "table of contents" || lower == "preface" ||
            lower == "prologue" || lower == "epilogue" || lower == "introduction" ||
            lower == "dedication" || lower == "acknowledgments" || lower == "acknowledgements" ||
            lower == "author's note" || lower == "foreword") {
            return true
        }

        // Dotted leader / TOC format
        if (p.count { it == '.' } > 6 && p.contains(Regex("\\.{2,}"))) return true

        // Dominated by uppercase (title / author / header lines)
        val letters = p.filter { it.isLetter() }
        if (letters.isNotEmpty()) {
            val upperCount = letters.count { it.isUpperCase() }
            if (upperCount.toFloat() / letters.length > 0.50f && p.length < 160) return true
        }

        // Dominated by digits
        val digits = p.filter { it.isDigit() }
        return digits.length > 8 && digits.toFloat() / p.length > 0.25f
    }

    // 1. First pass: find substantive prose paragraph (>= 12 words, complete sentences)
    for (p in rawParagraphs) {
        if (isHeaderOrTOC(p)) continue
        val words = p.split(Regex("\\s+"))
        if (words.size >= 12 && p.length >= 50 && (p.contains('.') || p.contains(',') || p.contains(';') || p.contains('—') || p.contains('-'))) {
            return formatSynopsis(p, maxChars)
        }
    }

    // 2. Second pass: relaxed prose criteria
    for (p in rawParagraphs) {
        if (skipKeywords.any { p.lowercase().contains(it) }) continue
        val words = p.split(Regex("\\s+"))
        if (words.size >= 8 && p.length >= 35) {
            return formatSynopsis(p, maxChars)
        }
    }

    // 3. Fallback: clean non-blank snippet
    val flat = searchBody.replace(Regex("\\s+"), " ").trim()
    return formatSynopsis(flat, maxChars)
}

private fun formatSynopsis(text: String, maxChars: Int): String {
    val clean = text.replace(Regex("\\s+"), " ").trim()
    if (clean.length <= maxChars) return clean

    val minCut = (maxChars * 0.55).toInt()
    val candidateSub = clean.substring(0, maxChars)
    val lastSentenceEnd = candidateSub.lastIndexOfAny(charArrayOf('.', '!', '?'))
    if (lastSentenceEnd >= minCut) {
        return candidateSub.substring(0, lastSentenceEnd + 1).trim()
    }

    val lastSpace = candidateSub.lastIndexOf(' ')
    return if (lastSpace >= minCut) {
        candidateSub.substring(0, lastSpace).trim() + "…"
    } else {
        candidateSub.trim() + "…"
    }
}

data class SavedDocument(
    val id: String,
    val title: String,
    val fileName: String,
    val sourceLabel: String,
    val createdAt: Long,
    val updatedAt: Long,
    val currentIndex: Int,
    val chunkCount: Int,
    val charCount: Int,
    val preview: String,
    val favorite: Boolean = false,
    val collection: String = "",
    val originalFileName: String = "",
    val originalMimeType: String = "",
    val pageCount: Int = 0,
    val partial: Boolean = false,
    val language: String = "",
    val catalogId: String = "",
    val sentenceIndexVersion: Int = 2
) {
    val sentenceCount: Int
        get() = chunkCount

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("fileName", fileName)
        .put("sourceLabel", sourceLabel)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)
        .put("currentIndex", currentIndex)
        .put("chunkCount", chunkCount)
        .put("charCount", charCount)
        .put("preview", preview)
        .put("favorite", favorite)
        .put("collection", collection)
        .put("originalFileName", originalFileName)
        .put("originalMimeType", originalMimeType)
        .put("pageCount", pageCount)
        .put("partial", partial)
        .put("language", language)
        .put("catalogId", catalogId)
        .put("sentenceIndexVersion", sentenceIndexVersion)

    companion object {
        fun fromJson(obj: JSONObject): SavedDocument = SavedDocument(
            id = obj.optString("id"),
            title = obj.optString("title", "Untitled reading"),
            fileName = obj.optString("fileName"),
            sourceLabel = obj.optString("sourceLabel", "Text"),
            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
            currentIndex = obj.optInt("currentIndex", 0),
            chunkCount = obj.optInt("chunkCount", 0),
            charCount = obj.optInt("charCount", 0),
            preview = obj.optString("preview"),
            favorite = obj.optBoolean("favorite", false),
            collection = obj.optString("collection", ""),
            originalFileName = obj.optString("originalFileName", ""),
            originalMimeType = obj.optString("originalMimeType", ""),
            pageCount = obj.optInt("pageCount", 0),
            partial = obj.optBoolean("partial", false),
            language = obj.optString("language", ""),
            catalogId = obj.optString("catalogId", ""),
            sentenceIndexVersion = obj.optInt("sentenceIndexVersion", 1)
        )
    }
}

data class QueueEntry(
    val documentId: String,
    val addedAt: Long
) {
    fun toJson(): JSONObject = JSONObject()
        .put("documentId", documentId)
        .put("addedAt", addedAt)

    companion object {
        fun fromJson(obj: JSONObject): QueueEntry = QueueEntry(
            documentId = obj.optString("documentId"),
            addedAt = obj.optLong("addedAt", System.currentTimeMillis())
        )
    }
}

data class ReadingHistoryEntry(
    val documentId: String,
    val title: String,
    val sourceLabel: String,
    val currentIndex: Int,
    val chunkCount: Int,
    val openedAt: Long
) {
    fun toJson(): JSONObject = JSONObject()
        .put("documentId", documentId)
        .put("title", title)
        .put("sourceLabel", sourceLabel)
        .put("currentIndex", currentIndex)
        .put("chunkCount", chunkCount)
        .put("openedAt", openedAt)

    companion object {
        fun fromJson(obj: JSONObject): ReadingHistoryEntry? {
            val documentId = obj.optString("documentId").trim()
            if (documentId.isBlank()) return null
            return ReadingHistoryEntry(
                documentId = documentId,
                title = obj.optString("title", "Untitled reading").ifBlank { "Untitled reading" },
                sourceLabel = obj.optString("sourceLabel", "Text").ifBlank { "Text" },
                currentIndex = obj.optInt("currentIndex", 0).coerceAtLeast(0),
                chunkCount = obj.optInt("chunkCount", 0).coerceAtLeast(0),
                openedAt = obj.optLong("openedAt", System.currentTimeMillis())
            )
        }
    }
}

data class GeneralNote(
    val id: String,
    val title: String,
    val content: String,
    val updatedAt: Long,
    val color: String? = null,
    val pinned: Boolean = false,
    val isChecklist: Boolean = false,
    val imageUrl: String? = null,
    val audioUrl: String? = null,
    val reminderAt: Long? = null,
    val audioUrls: List<String> = emptyList(),
    val createdAt: Long = updatedAt,
    val notebookId: String? = null,
    val deletedAt: Long? = null,
    val labelIds: List<String> = emptyList()
) {
    // Legacy notebook assignments become labels without rewriting existing backups.
    val allLabelIds: List<String>
        get() = (labelIds + listOfNotNull(notebookId)).filter { it.isNotBlank() }.distinct()

    fun withLabels(ids: List<String>): GeneralNote = copy(notebookId = null, labelIds = ids.filter { it.isNotBlank() }.distinct())

    val allAudioUrls: List<String>
        get() {
            val list = mutableListOf<String>()
            audioUrls.forEach { if (it.isNotBlank() && !list.contains(it)) list.add(it) }
            if (!audioUrl.isNullOrBlank() && !list.contains(audioUrl)) {
                list.add(0, audioUrl)
            }
            extractInlineAudios(content).forEach { if (!list.contains(it)) list.add(it) }
            return list
        }

    val primaryImageUrl: String?
        get() = imageUrl?.takeIf { it.isNotBlank() } ?: extractInlineImages(content).firstOrNull()

    val allImageUrls: List<String>
        get() = (listOfNotNull(imageUrl?.takeIf { it.isNotBlank() }) + extractInlineImages(content)).distinct()

    val allVideoUrls: List<String>
        get() = extractInlineVideos(content)

    fun toJson(): JSONObject {
        val urls = allAudioUrls
        val jsonAudioUrls = JSONArray()
        urls.forEach { jsonAudioUrls.put(it) }
        return JSONObject()
            .put("id", id)
            .put("title", title)
            .put("content", content)
            .put("updatedAt", updatedAt)
            .put("createdAt", createdAt)
            .put("color", color ?: "")
            .put("pinned", pinned)
            .put("isChecklist", isChecklist)
            .put("imageUrl", primaryImageUrl ?: "")
            .put("audioUrl", urls.firstOrNull() ?: "")
            .put("audioUrls", jsonAudioUrls)
            .put("reminderAt", reminderAt ?: 0L)
            .put("notebookId", allLabelIds.firstOrNull().orEmpty())
            .put("labelIds", JSONArray(allLabelIds))
            .put("deletedAt", deletedAt ?: 0L)
    }

    companion object {
        fun fromJson(obj: JSONObject): GeneralNote {
            val list = mutableListOf<String>()
            val arr = obj.optJSONArray("audioUrls")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val u = arr.optString(i, "")
                    if (u.isNotBlank() && !list.contains(u)) list.add(u)
                }
            }
            val single = obj.optString("audioUrl", "").takeIf { it.isNotBlank() }
            if (single != null && !list.contains(single)) {
                list.add(0, single)
            }
            return GeneralNote(
                id = obj.optString("id", ""),
                title = obj.optString("title", ""),
                content = obj.optString("content", ""),
                updatedAt = obj.optLong("updatedAt", 0L),
                createdAt = obj.optLong("createdAt", obj.optLong("updatedAt", 0L)),
                color = obj.optString("color", "").takeIf { it.isNotBlank() },
                pinned = obj.optBoolean("pinned", false),
                isChecklist = obj.optBoolean("isChecklist", false),
                imageUrl = obj.optString("imageUrl", "").takeIf { it.isNotBlank() },
                audioUrl = list.firstOrNull(),
                reminderAt = obj.optLong("reminderAt", 0L).takeIf { it > 0L },
                audioUrls = list,
                notebookId = if (obj.has("labelIds")) null else obj.optString("notebookId", "").takeIf { it.isNotBlank() },
                labelIds = obj.optJSONArray("labelIds")?.let { labels ->
                    (0 until labels.length()).map { labels.optString(it) }.filter { it.isNotBlank() }.distinct()
                }.orEmpty(),
                deletedAt = obj.optLong("deletedAt", 0L).takeIf { it > 0L }
            )
        }
    }
}

data class NoteNotebook(val id: String, val name: String, val createdAt: Long, val updatedAt: Long = createdAt) {
    fun toJson() = JSONObject().put("id", id).put("name", name).put("createdAt", createdAt).put("updatedAt", updatedAt)
    companion object {
        fun fromJson(json: JSONObject) = NoteNotebook(
            id = json.optString("id"), name = json.optString("name"),
            createdAt = json.optLong("createdAt"), updatedAt = json.optLong("updatedAt", json.optLong("createdAt"))
        )
    }
}

data class NoteRevision(val noteId: String, val savedAt: Long, val snapshot: GeneralNote)

private val INLINE_IMAGE_REGEX = Regex("""!\[(?:image|photo)?\]\(([^)]+)\)|\[image:([^]]+)\]""", RegexOption.IGNORE_CASE)
private val INLINE_AUDIO_REGEX = Regex("""\[audio\]\(([^)]+)\)|\[audio:([^]]+)\]""", RegexOption.IGNORE_CASE)
private val INLINE_VIDEO_REGEX = Regex("""\[video\]\(([^)]+)\)|\[video:([^]]+)\]""", RegexOption.IGNORE_CASE)

fun extractInlineImages(text: String): List<String> {
    if (text.isBlank()) return emptyList()
    val matches = mutableListOf<String>()
    INLINE_IMAGE_REGEX.findAll(text).forEach { m ->
        val path = m.groupValues[1].ifBlank { m.groupValues[2] }.trim()
        if (path.isNotEmpty() && !matches.contains(path)) matches.add(path)
    }
    return matches
}

fun extractInlineAudios(text: String): List<String> {
    if (text.isBlank()) return emptyList()
    val matches = mutableListOf<String>()
    INLINE_AUDIO_REGEX.findAll(text).forEach { m ->
        val path = m.groupValues[1].ifBlank { m.groupValues[2] }.trim()
        if (path.isNotEmpty() && !matches.contains(path)) matches.add(path)
    }
    return matches
}

fun extractInlineVideos(text: String): List<String> {
    if (text.isBlank()) return emptyList()
    val matches = mutableListOf<String>()
    INLINE_VIDEO_REGEX.findAll(text).forEach { m ->
        val path = m.groupValues[1].ifBlank { m.groupValues[2] }.trim()
        if (path.isNotEmpty() && !matches.contains(path)) matches.add(path)
    }
    return matches
}

enum class VeritasScreen {
    TEXT_EDITOR,
    FILE_BROWSER,
    PDF_IMPORT_TOOLS,
    READER_SETTINGS,
    PRONUNCIATION_RULES,
    VOICE_STUDIO,
    NARRATION_STUDIO,
    AI_STUDY_TOOLS,
    AI_CENTER,
    ASK_AI_SETTINGS,
    TRANSLATION_TOOLS,
    SLEEP_TIMER,
    READING_LISTS,
    READING_HISTORY,
    DOCUMENT_NOTES,
    SETTINGS_HUB,
    BACKUP_TOOLS,
    SYNC_CENTER,
    APP_HEALTH,
    TUTORIAL,
    CANVAS_VIEW,
    GENERAL_NOTES_EDITOR,
    USER_MANUAL,
    ACCESSIBILITY_SETTINGS,
    NOTES_SETTINGS
}

enum class AnnotationType {
    BOOKMARK,
    HIGHLIGHT,
    NOTE
}

data class ReaderAnnotation(
    val documentId: String,
    val chunkIndex: Int,
    val type: AnnotationType,
    val note: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val highlightColor: String? = null,
    val selectionGroupId: String? = null,
    val audioPath: String? = null,
    val audioDurationSeconds: Int = 0
) {
    val sentenceIndex: Int
        get() = chunkIndex

    val stableKey: String
        get() = "$documentId:$chunkIndex:${type.name}"

    fun toJson(): JSONObject = JSONObject()
        .put("documentId", documentId)
        .put("chunkIndex", chunkIndex)
        .put("type", type.name)
        .put("note", note)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)
        .put("highlightColor", highlightColor)
        .put("selectionGroupId", selectionGroupId)
        .put("audioPath", audioPath)
        .put("audioDurationSeconds", audioDurationSeconds)

    companion object {
        fun fromJson(obj: JSONObject): ReaderAnnotation? {
            val type = runCatching { AnnotationType.valueOf(obj.optString("type")) }.getOrNull() ?: return null
            val documentId = obj.optString("documentId")
            if (documentId.isBlank()) return null
            return ReaderAnnotation(
                documentId = documentId,
                chunkIndex = obj.optInt("chunkIndex", 0),
                type = type,
                note = obj.optString("note"),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                highlightColor = if (obj.has("highlightColor")) obj.optString("highlightColor") else null,
                selectionGroupId = if (obj.has("selectionGroupId")) obj.optString("selectionGroupId") else null,
                audioPath = if (obj.has("audioPath") && !obj.isNull("audioPath")) obj.optString("audioPath").ifBlank { null } else null,
                audioDurationSeconds = obj.optInt("audioDurationSeconds", 0)
            )
        }
    }
}

data class FlashcardProgress(
    val id: String,
    val documentId: String = "",
    val front: String,
    val back: String,
    // Cards are grouped into named sets (one per import). Legacy cards with no
    // setId are migrated into a per-document set on load.
    val setId: String = "",
    val setName: String = "",
    // Latest recall rating: "" (unrated), "again", "hard", "good", "easy".
    val recall: String = "",
    // Spaced repetition (SM-2 Lite) metadata
    val nextReviewDueTimestamp: Long = 0L,
    val intervalDays: Int = 0,
    val repetitionCount: Int = 0,
    val easeFactor: Float = 2.5f
) {
    fun isDue(nowTimestamp: Long = System.currentTimeMillis()): Boolean =
        recall.isBlank() || nextReviewDueTimestamp <= nowTimestamp

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("documentId", documentId)
        .put("front", front)
        .put("back", back)
        .put("setId", setId)
        .put("setName", setName)
        .put("recall", recall)
        .put("nextReviewDueTimestamp", nextReviewDueTimestamp)
        .put("intervalDays", intervalDays)
        .put("repetitionCount", repetitionCount)
        .put("easeFactor", easeFactor.toDouble())

    companion object {
        val RECALL_BUCKETS = listOf("again", "hard", "good", "easy")

        fun fromJson(json: JSONObject): FlashcardProgress {
            return FlashcardProgress(
                id = json.getString("id"),
                documentId = json.getString("documentId"),
                front = json.getString("front"),
                back = json.getString("back"),
                setId = json.optString("setId", ""),
                setName = json.optString("setName", ""),
                recall = json.optString("recall", ""),
                nextReviewDueTimestamp = json.optLong("nextReviewDueTimestamp", 0L),
                intervalDays = json.optInt("intervalDays", 0),
                repetitionCount = json.optInt("repetitionCount", 0),
                easeFactor = json.optDouble("easeFactor", 2.5).toFloat()
            )
        }
    }
}

/** A named group of flashcards with per-bucket recall counts for the tiles and mastery stats. */
data class FlashcardSet(
    val setId: String,
    val name: String,
    val cards: List<FlashcardProgress>
) {
    val recallCounts: Map<String, Int>
        get() = cards.groupingBy { it.recall }.eachCount().filterKeys { it.isNotBlank() }

    val dueCount: Int
        get() = cards.count { it.isDue() }

    val masteryPercent: Int
        get() {
            if (cards.isEmpty()) return 0
            val goodOrEasy = cards.count { it.recall == "good" || it.recall == "easy" }
            return ((goodOrEasy.toFloat() / cards.size) * 100).toInt().coerceIn(0, 100)
        }
}


private const val DOCUMENT_NOTE_STABLE_KEY_PREFIX = "document-note:"

fun documentNoteStableKey(documentId: String): String = "$DOCUMENT_NOTE_STABLE_KEY_PREFIX$documentId"

fun documentIdFromDocumentNoteStableKey(stableKey: String): String? {
    if (!stableKey.startsWith(DOCUMENT_NOTE_STABLE_KEY_PREFIX)) return null
    return stableKey.removePrefix(DOCUMENT_NOTE_STABLE_KEY_PREFIX).takeIf { it.isNotBlank() }
}

data class ReaderDocument(
    val id: String?,
    val title: String,
    val sourceLabel: String,
    val rawText: String,
    val sentences: List<String>,
    val pageCount: Int = 0,
    val partial: Boolean = false
) {
    // Legacy alias while older playback/storage code is migrated from chunks to sentences.
    val chunks: List<String>
        get() = sentences
}

sealed interface VeritasTextEditTarget {
    data class SentenceRange(
        val startSentenceIndex: Int,
        val endSentenceIndexExclusive: Int,
        val label: String
    ) : VeritasTextEditTarget

    data class Part(
        val partIndex: Int,
        val label: String
    ) : VeritasTextEditTarget
}

data class PronunciationRule(
    val id: String,
    val find: String,
    val replaceWith: String,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("find", find)
        .put("replaceWith", replaceWith)
        .put("enabled", enabled)
        .put("createdAt", createdAt)

    companion object {
        fun fromJson(obj: JSONObject): PronunciationRule? {
            val id = obj.optString("id").ifBlank { UUID.randomUUID().toString() }
            val find = obj.optString("find")
            if (find.isBlank()) return null
            return PronunciationRule(
                id = id,
                find = find,
                replaceWith = obj.optString("replaceWith"),
                enabled = obj.optBoolean("enabled", true),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }
}


object VeritasThemePackCatalog {
    const val DEFAULT_ID = "veritas_media"

    val packOptions: List<Pair<String, String>> = listOf(
        "veritas_media" to "Vern Media",
        "material_you" to "Material You",
        "liquid_glass" to "Liquid Glass",
        "one_ui" to "One UI"
    )

    fun normalizePackId(id: String): String {
        return packOptions.firstOrNull { it.first == id }?.first ?: DEFAULT_ID
    }

    fun displayName(id: String): String {
        val normalized = normalizePackId(id)
        return packOptions.firstOrNull { it.first == normalized }?.second ?: "Vern Media"
    }
}

object VeritasThemeCatalog {
    const val DEFAULT_ID = "system"
    const val DEFAULT_FAMILY = "sage"
    const val DEFAULT_MODE = "system"

    data class ThemeFamilyInfo(
        val id: String,
        val displayName: String,
        val lightThemeId: String,
        val darkThemeId: String,
        val isAccent: Boolean = false
    )

    val families: List<ThemeFamilyInfo> = listOf(
        ThemeFamilyInfo("sage", "Sage", "light", "dark"),
        ThemeFamilyInfo("midnight", "Midnight", "midnight_light", "midnight_dark"),
        ThemeFamilyInfo("dracula", "Dracula", "dracula_light", "dracula"),
        ThemeFamilyInfo("github", "GitHub", "github_light", "github_dark"),
        ThemeFamilyInfo("bw_gradient", "B/W Gradient", "bw_gradient_light", "bw_gradient_dark"),
        ThemeFamilyInfo("neon", "Neon", "neon", "neon", isAccent = true)
    )

    val themeOptions: List<Pair<String, String>> = listOf(
        "system" to "System Default",
        "light" to "Sage Light",
        "dark" to "Sage Dark",
        "dracula_system" to "Dracula (System)",
        "dracula_light" to "Dracula Light",
        "dracula" to "Dracula",
        "midnight_system" to "Midnight (System)",
        "midnight_light" to "Midnight Light",
        "midnight_dark" to "Midnight Dark",
        "github_system" to "GitHub (System)",
        "github_light" to "GitHub Light",
        "github_dark" to "GitHub Dark",
        "bw_gradient_system" to "B/W Gradient (System)",
        "bw_gradient_light" to "B/W Gradient Light",
        "bw_gradient_dark" to "B/W Gradient Dark",
        "neon" to "Neon",
        "one_light" to "One Light",
        "one_dark_pro" to "One Dark Pro"
    )

    fun familyForThemeId(id: String): String {
        val normalized = normalizeThemeId(id)
        return when (normalized) {
            "midnight", "midnight_system", "midnight_light", "midnight_dark" -> "midnight"
            "dracula", "dracula_system", "dracula_light" -> "dracula"
            "github", "github_system", "github_light", "github_dark", "one_light", "one_dark_pro" -> "github"
            "bw_gradient", "bw_gradient_system", "bw_gradient_light", "bw_gradient_dark" -> "bw_gradient"
            "neon" -> "neon"
            else -> "sage"
        }
    }

    fun modeForThemeId(id: String): String {
        val normalized = normalizeThemeId(id)
        return when (normalized) {
            "system", "sage", "midnight_system", "dracula_system", "github_system", "bw_gradient_system" -> "system"
            "light", "white_high_contrast", "midnight_light", "dracula_light", "github_light", "one_light", "bw_gradient_light" -> "light"
            "dark", "dark_high_contrast", "amoled", "midnight_dark", "dracula", "github_dark", "one_dark_pro", "bw_gradient_dark", "neon" -> "dark"
            else -> "system"
        }
    }

    fun resolveThemeIdForStorage(familyId: String, mode: String): String {
        val family = families.firstOrNull { it.id == familyId } ?: families.first()
        if (family.isAccent) return family.darkThemeId
        return when (mode) {
            "light" -> family.lightThemeId
            "dark" -> family.darkThemeId
            else -> if (family.id == "sage") "system" else "${family.id}_system"
        }
    }

    fun resolveConcreteThemeId(themeId: String, systemInDarkTheme: Boolean = false): String {
        return when (themeId) {
            "system" -> if (systemInDarkTheme) "dark" else "light"
            "midnight_system" -> if (systemInDarkTheme) "midnight_dark" else "midnight_light"
            "dracula_system" -> if (systemInDarkTheme) "dracula" else "dracula_light"
            "github_system" -> if (systemInDarkTheme) "github_dark" else "github_light"
            "bw_gradient_system" -> if (systemInDarkTheme) "bw_gradient_dark" else "bw_gradient_light"
            "one_light" -> "github_light"
            "one_dark_pro" -> "github_dark"
            else -> normalizeThemeId(themeId)
        }
    }

    fun normalizeThemeId(id: String): String {
        val mapped = when (id) {
            "default_dark_2026" -> "dark"
            "one_light" -> "github_light"
            "one_dark_pro" -> "github_dark"
            "blue_high_contrast" -> "dark"
            else -> id
        }
        // Accessibility presets stay valid even though the palette picker omits them.
        if (mapped in setOf("amoled", "dark_high_contrast", "white_high_contrast")) return mapped
        return themeOptions.firstOrNull { it.first == mapped }?.first ?: DEFAULT_ID
    }

    fun displayName(id: String): String {
        val normalized = normalizeThemeId(id)
        return when (normalized) {
            "dark_high_contrast" -> "Dark High Contrast"
            "white_high_contrast" -> "White High Contrast"
            "amoled" -> "AMOLED Pure Black"
            else -> themeOptions.firstOrNull { it.first == normalized }?.second ?: "Sage"
        }
    }

    fun isDark(themeId: String, systemInDarkTheme: Boolean = false): Boolean {
        val concrete = resolveConcreteThemeId(themeId, systemInDarkTheme)
        return when (concrete) {
            "light", "white_high_contrast", "bw_gradient_light", "github_light", "dracula_light", "midnight_light", "one_light" -> false
            else -> true
        }
    }
}

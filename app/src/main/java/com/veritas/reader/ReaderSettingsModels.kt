package com.veritas.reader

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ReaderSettings(
    val fontSizeSp: Int = 18,
    val sectionSpacingDp: Int = 10,
    val showSectionNumbers: Boolean = true,
    val autoPlayQueue: Boolean = true,
    val themeId: String = VeritasThemeCatalog.DEFAULT_ID,
    val previousThemeId: String? = null,
    val themePackId: String = "veritas_media",
    val adaptiveCover: Boolean = false,
    // false = subtle container-tone hero card (theme-matched); true = the original
    // vivid accent-gradient "poster" style. User-selectable — neither is imposed.
    val vibrantHero: Boolean = false,
    // Weekly data-only backup written to app storage (last 4 kept), so there is
    // always a recent backup even if the user never exports manually.
    val autoBackupWeekly: Boolean = true,
    // Opt-in daily reading target in minutes. 0 = no goal set — the hero goal bar
    // and the reminder only appear once the user chooses to set one.
    val dailyGoalMinutes: Int = 0,
    val streakReminderEnabled: Boolean = false,
    // Accessibility: collapse decorative motion (entrances, pulses) to instants.
    val reduceMotion: Boolean = false,
    // Typeface for the whole app, chrome and reading text alike. "system" keeps the
    // platform default; see VeritasUiFont.
    val uiFontId: String = "system",
    // Accessibility / Focus: bold initial word letters to guide eye fixation
    val bionicReading: Boolean = false,
    // Shake phone in final 60s of sleep timer to extend by 10 minutes
    val shakeToExtendSleepTimer: Boolean = true,
    // Accessibility / Reader: toggle whether tapping canvas collapses top & bottom bars
    val collapsibleReaderBars: Boolean = true,
    // Accessibility / Navigation: show or hide text labels beneath navigation bar icons
    val showNavLabels: Boolean = true,
    // Paper tone: "active_theme", "dark", "natural_white", "warm_sepia"
    val paperToneMode: String = "active_theme",
    // AMOLED pure black background mode for dark themes
    val amoledMode: Boolean = false,
    // Contextual gestures: show helpful tips from time to time (pinch, double tap, long press)
    val showGestureTips: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject()
        .put("fontSizeSp", fontSizeSp)
        .put("sectionSpacingDp", sectionSpacingDp)
        .put("showSectionNumbers", showSectionNumbers)
        .put("autoPlayQueue", autoPlayQueue)
        .put("themeId", themeId)
        .put("previousThemeId", previousThemeId)
        .put("themePackId", themePackId)
        .put("adaptiveCover", adaptiveCover)
        .put("vibrantHero", vibrantHero)
        .put("autoBackupWeekly", autoBackupWeekly)
        .put("dailyGoalMinutes", dailyGoalMinutes)
        .put("streakReminderEnabled", streakReminderEnabled)
        .put("reduceMotion", reduceMotion)
        .put("uiFontId", uiFontId)
        .put("bionicReading", bionicReading)
        .put("shakeToExtendSleepTimer", shakeToExtendSleepTimer)
        .put("collapsibleReaderBars", collapsibleReaderBars)
        .put("showNavLabels", showNavLabels)
        .put("paperToneMode", paperToneMode)
        .put("amoledMode", amoledMode)
        .put("showGestureTips", showGestureTips)

    companion object {
        fun fromJson(obj: JSONObject): ReaderSettings {
            val rawThemeId = obj.optString("themeId", VeritasThemeCatalog.DEFAULT_ID)
            val migratedPack = if (rawThemeId == "material_you") "material_you" else obj.optString("themePackId", "veritas_media")
            val migratedTheme = if (rawThemeId == "material_you") "default_dark_2026" else rawThemeId
            return ReaderSettings(
                fontSizeSp = obj.optInt("fontSizeSp", 18).coerceIn(10, 28),
                sectionSpacingDp = obj.optInt("sectionSpacingDp", 10).coerceIn(6, 24),
                showSectionNumbers = obj.optBoolean("showSectionNumbers", true),
                autoPlayQueue = obj.optBoolean("autoPlayQueue", true),
                themeId = VeritasThemeCatalog.normalizeThemeId(migratedTheme),
                previousThemeId = obj.optString("previousThemeId").takeIf { it.isNotBlank() },
                themePackId = VeritasThemePackCatalog.normalizePackId(migratedPack),
                adaptiveCover = obj.optBoolean("adaptiveCover", false),
                vibrantHero = obj.optBoolean("vibrantHero", false),
                autoBackupWeekly = obj.optBoolean("autoBackupWeekly", true),
                dailyGoalMinutes = obj.optInt("dailyGoalMinutes", 0).coerceIn(0, 180),
                streakReminderEnabled = obj.optBoolean("streakReminderEnabled", false),
                reduceMotion = obj.optBoolean("reduceMotion", false),
                uiFontId = obj.optString("uiFontId", "system"),
                bionicReading = obj.optBoolean("bionicReading", false),
                shakeToExtendSleepTimer = obj.optBoolean("shakeToExtendSleepTimer", true),
                collapsibleReaderBars = obj.optBoolean("collapsibleReaderBars", true),
                showNavLabels = obj.optBoolean("showNavLabels", true),
                paperToneMode = obj.optString("paperToneMode", "active_theme"),
                amoledMode = obj.optBoolean("amoledMode", false),
                showGestureTips = obj.optBoolean("showGestureTips", true)
            )
        }
    }
}


data class VoiceSettings(
    val enginePackage: String = "",
    val engineLabel: String = "System default",
    val voiceName: String = "",
    val voiceLabel: String = "System default voice",
    val localeTag: String = "",
    val profileName: String = "Balanced",
    val preferredRate: Float = 1.0f,
    val preferredPitch: Float = 1.0f,
    val showNetworkVoices: Boolean = false
) {
    val displayName: String
        get() = if (voiceName.isBlank()) voiceLabel.ifBlank { "System default voice" } else voiceLabel.ifBlank { voiceName }

    fun toJson(): JSONObject = JSONObject()
        .put("enginePackage", enginePackage)
        .put("engineLabel", engineLabel)
        .put("voiceName", voiceName)
        .put("voiceLabel", voiceLabel)
        .put("localeTag", localeTag)
        .put("profileName", profileName)
        .put("preferredRate", preferredRate.toDouble())
        .put("preferredPitch", preferredPitch.toDouble())
        .put("showNetworkVoices", showNetworkVoices)

    companion object {
        fun fromJson(obj: JSONObject): VoiceSettings = VoiceSettings(
            enginePackage = obj.optString("enginePackage"),
            engineLabel = obj.optString("engineLabel", "System default"),
            voiceName = obj.optString("voiceName"),
            voiceLabel = obj.optString("voiceLabel", "System default voice"),
            localeTag = obj.optString("localeTag"),
            profileName = obj.optString("profileName", "Balanced"),
            preferredRate = obj.optDouble("preferredRate", 1.0).toFloat().coerceIn(0.5f, 2.0f),
            preferredPitch = obj.optDouble("preferredPitch", 1.0).toFloat().coerceIn(0.7f, 1.4f),
            showNetworkVoices = obj.optBoolean("showNetworkVoices", false)
        )
    }
}




data class BookCharacter(
    val id: String,
    val name: String,
    val genderLabel: String = "Neutral",
    val voiceName: String? = null,
    val pitchMultiplier: Float = 1.0f,
    val rateMultiplier: Float = 1.0f
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("genderLabel", genderLabel)
        .put("voiceName", voiceName ?: "")
        .put("pitchMultiplier", pitchMultiplier.toDouble())
        .put("rateMultiplier", rateMultiplier.toDouble())

    companion object {
        fun fromJson(obj: JSONObject): BookCharacter = BookCharacter(
            id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = obj.optString("name", "Character"),
            genderLabel = obj.optString("genderLabel", "Neutral"),
            voiceName = obj.optString("voiceName").takeIf { it.isNotBlank() },
            pitchMultiplier = obj.optDouble("pitchMultiplier", 1.0).toFloat().coerceIn(0.70f, 1.40f),
            rateMultiplier = obj.optDouble("rateMultiplier", 1.0).toFloat().coerceIn(0.50f, 2.00f)
        )
    }
}

data class NarrationSettings(
    val enabled: Boolean = false,
    val dialogueDetection: Boolean = true,
    val narratorRateMultiplier: Float = 1.0f,
    val narratorPitchMultiplier: Float = 1.0f,
    val dialogueRateMultiplier: Float = 1.02f,
    val dialoguePitchMultiplier: Float = 1.03f,
    val showDialogueBadges: Boolean = true,
    val fullCastEnabled: Boolean = true,
    val characterProfiles: List<BookCharacter> = listOf(
        BookCharacter(id = "narrator", name = "Narrator", genderLabel = "Neutral", pitchMultiplier = 1.0f, rateMultiplier = 1.0f),
        BookCharacter(id = "dialogue", name = "Dialogue (Default)", genderLabel = "Female", pitchMultiplier = 1.03f, rateMultiplier = 1.02f)
    )
) {
    fun toJson(): JSONObject {
        val array = JSONArray()
        characterProfiles.forEach { array.put(it.toJson()) }
        return JSONObject()
            .put("enabled", enabled)
            .put("dialogueDetection", dialogueDetection)
            .put("narratorRateMultiplier", narratorRateMultiplier.toDouble())
            .put("narratorPitchMultiplier", narratorPitchMultiplier.toDouble())
            .put("dialogueRateMultiplier", dialogueRateMultiplier.toDouble())
            .put("dialoguePitchMultiplier", dialoguePitchMultiplier.toDouble())
            .put("showDialogueBadges", showDialogueBadges)
            .put("fullCastEnabled", fullCastEnabled)
            .put("characterProfiles", array)
    }

    companion object {
        fun fromJson(obj: JSONObject): NarrationSettings {
            val list = mutableListOf<BookCharacter>()
            val array = obj.optJSONArray("characterProfiles")
            if (array != null) {
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.let { list.add(BookCharacter.fromJson(it)) }
                }
            }
            if (list.isEmpty()) {
                list.add(BookCharacter(id = "narrator", name = "Narrator", genderLabel = "Neutral", pitchMultiplier = 1.0f, rateMultiplier = 1.0f))
                list.add(BookCharacter(id = "dialogue", name = "Dialogue (Default)", genderLabel = "Female", pitchMultiplier = 1.03f, rateMultiplier = 1.02f))
            }
            return NarrationSettings(
                enabled = obj.optBoolean("enabled", false),
                dialogueDetection = obj.optBoolean("dialogueDetection", true),
                narratorRateMultiplier = obj.optDouble("narratorRateMultiplier", 1.0).toFloat().coerceIn(0.80f, 1.20f),
                narratorPitchMultiplier = obj.optDouble("narratorPitchMultiplier", 1.0).toFloat().coerceIn(0.92f, 1.08f),
                dialogueRateMultiplier = obj.optDouble("dialogueRateMultiplier", 1.02).toFloat().coerceIn(0.80f, 1.20f),
                dialoguePitchMultiplier = obj.optDouble("dialoguePitchMultiplier", 1.03).toFloat().coerceIn(0.92f, 1.08f),
                showDialogueBadges = obj.optBoolean("showDialogueBadges", true),
                fullCastEnabled = obj.optBoolean("fullCastEnabled", true),
                characterProfiles = list
            )
        }
    }
}

data class AskAiSettings(
    val assistantId: String = "chooser",
    val assistantLabel: String = "Choose each time",
    val packageName: String = "",
    val promptTemplate: String = "Answer clearly using this selected Vern text:\n\n{selection}"
) {
    fun toJson(): JSONObject = JSONObject()
        .put("assistantId", assistantId)
        .put("assistantLabel", assistantLabel)
        .put("packageName", packageName)
        .put("promptTemplate", promptTemplate)

    companion object {
        fun fromJson(obj: JSONObject): AskAiSettings = AskAiSettings(
            assistantId = obj.optString("assistantId", "chooser"),
            assistantLabel = obj.optString("assistantLabel", "Choose each time"),
            packageName = obj.optString("packageName"),
            promptTemplate = obj.optString(
                "promptTemplate",
                "Answer clearly using this selected Vern text:\n\n{selection}"
            )
        )
    }
}

object NarrationAnalyzer {
    // Spoken speech attribution verbs
    private val speakerVerbPattern = Regex(
        "\\b(said|asked|replied|answered|whispered|shouted|cried|murmured|continued|responded|exclaimed|yelled|muttered|insisted|warned|demanded|explained|added|remarked|grunted|sighed|urged|begged|groaned|thought|screamed|barked|snapped)\\b",
        RegexOption.IGNORE_CASE
    )

    // Dash dialogue: e.g. "— Where are you going?"
    private val dashDialoguePattern = Regex("^\\s*[—–-]\\s*[\"“'‘]?\\S+")

    // Spoken dialogue inside quotes: must contain spoken punctuation (?, !, comma-quote) or conversational pronouns/contractions
    private val spokenDialogueQuotePattern = Regex("[\"“]([^\"”]*[?!,]|[^\"”]{3,}\\b(I|you|we|me|my|your|us|our|can't|don't|won't|I'm|you're|it's|what|where|why|how|who)\\b[^\"”]*?)[\"”]")

    // Non-dialogue quotes: single word quotes, scare quotes, title references, e.g. "Chapter 1", "Section A", "so-called"
    private val nonDialogueTitlePattern = Regex("^[A-Z0-9][A-Za-z0-9\\s]{1,25}$")

    fun isDialogue(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return false

        // 1. Dash-prefixed speech lines (very common in novel dialogue)
        if (dashDialoguePattern.containsMatchIn(trimmed) && trimmed.length > 3) return true

        // 2. Explicit speaker attribution verbs accompanied by quotes (e.g. "Yes," he said)
        val hasQuoteChar = trimmed.any { it == '\"' || it == '“' || it == '”' || it == '‘' || it == '’' }
        val hasSpeakerVerb = speakerVerbPattern.containsMatchIn(trimmed)
        if (hasSpeakerVerb && hasQuoteChar) return true

        // 3. Spoken dialogue in quotes with spoken punctuation or conversational pronouns/contractions
        val match = spokenDialogueQuotePattern.find(trimmed)
        if (match != null) {
            val contentInsideQuote = match.groupValues.getOrNull(1)?.trim() ?: ""
            // Filter out titles or single scare-quoted terms like "Chapter 1" or "so-called"
            if (contentInsideQuote.isNotBlank() && !nonDialogueTitlePattern.matches(contentInsideQuote)) {
                return true
            }
        }

        return false
    }

    fun extractSpeakerName(text: String): String? {
        val pattern = Regex("[\"”']\\s*(?:said|asked|replied|whispered|shouted|cried|muttered|insisted|exclaimed|yelled|grunted|sighed|demanded|warned)\\s+([A-Z][a-z]+)", RegexOption.IGNORE_CASE)
        val match = pattern.find(text)
        if (match != null) return match.groupValues.getOrNull(1)

        val reversePattern = Regex("([A-Z][a-z]+)\\s+(?:said|asked|replied|whispered|shouted|cried|muttered|insisted|exclaimed|yelled|grunted|sighed|demanded|warned)\\s*[\"“']", RegexOption.IGNORE_CASE)
        val matchRev = reversePattern.find(text)
        if (matchRev != null) return matchRev.groupValues.getOrNull(1)

        return null
    }

    fun getActiveCharacter(text: String, settings: NarrationSettings): BookCharacter {
        if (!settings.enabled || !settings.dialogueDetection || !isDialogue(text)) {
            return settings.characterProfiles.firstOrNull { it.id == "narrator" }
                ?: BookCharacter("narrator", "Narrator", pitchMultiplier = settings.narratorPitchMultiplier, rateMultiplier = settings.narratorRateMultiplier)
        }
        val speakerName = extractSpeakerName(text)
        if (speakerName != null) {
            val found = settings.characterProfiles.firstOrNull { it.name.equals(speakerName, ignoreCase = true) }
            if (found != null) return found
        }
        return settings.characterProfiles.firstOrNull { it.id == "dialogue" }
            ?: BookCharacter("dialogue", "Dialogue (Default)", pitchMultiplier = settings.dialoguePitchMultiplier, rateMultiplier = settings.dialogueRateMultiplier)
    }

    fun labelFor(text: String, settings: NarrationSettings): String {
        if (!settings.enabled) return "Narrator"
        val char = getActiveCharacter(text, settings)
        return char.name
    }

    fun effectiveRate(baseRate: Float, settings: NarrationSettings, text: String): Float {
        if (!settings.enabled) return baseRate
        val char = getActiveCharacter(text, settings)
        return (baseRate * char.rateMultiplier).coerceIn(0.5f, 2.0f)
    }

    fun effectivePitch(basePitch: Float, settings: NarrationSettings, text: String): Float {
        if (!settings.enabled) return basePitch
        val char = getActiveCharacter(text, settings)
        return (basePitch * char.pitchMultiplier).coerceIn(0.60f, 1.50f)
    }
}

data class ExtractedImport(
    val title: String,
    val text: String,
    val sourceLabel: String,
    val note: String? = null,
    val pageCount: Int = 0,
    val partial: Boolean = false
)

data class DocumentCreateResult(
    val document: SavedDocument,
    val originalCopyError: String? = null
)


data class StorageBreakdown(
    val textBytes: Long,
    val originalsBytes: Long,
    val coversBytes: Long,
    val cacheBytes: Long = 0L,
    val databaseBytes: Long = 0L,
    val documentCount: Int
) {
    val totalBytes: Long get() = textBytes + originalsBytes + coversBytes + cacheBytes + databaseBytes
}

data class LibrarySearchHit(
    val document: SavedDocument,
    val sentenceIndex: Int,
    val snippet: String
)

data class BackupRestoreResult(
    val documentCount: Int,
    val annotationCount: Int,
    val queueCount: Int,
    val readingListCount: Int,
    val pronunciationRuleCount: Int,
    val restoredReaderSettings: Boolean,
    val restoredVoiceSettings: Boolean,
    val generalNoteCount: Int = 0,
    val trackerDayCount: Int = 0,
    val flashcardCount: Int = 0
)

data class AiPromptTemplate(
    val id: String,
    val title: String,
    val instruction: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("instruction", instruction)
        .put("createdAt", createdAt)

    companion object {
        fun fromJson(obj: JSONObject): AiPromptTemplate? {
            val instruction = obj.optString("instruction").trim()
            if (instruction.isBlank()) return null
            return AiPromptTemplate(
                id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                title = obj.optString("title", "Custom prompt").ifBlank { "Custom prompt" },
                instruction = instruction,
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }
}

data class AiPromptHistoryEntry(
    val id: String,
    val documentTitle: String,
    val promptType: String,
    val scope: String,
    val promptPreview: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("documentTitle", documentTitle)
        .put("promptType", promptType)
        .put("scope", scope)
        .put("promptPreview", promptPreview)
        .put("createdAt", createdAt)

    companion object {
        fun fromJson(obj: JSONObject): AiPromptHistoryEntry? {
            val preview = obj.optString("promptPreview").trim()
            if (preview.isBlank()) return null
            return AiPromptHistoryEntry(
                id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                documentTitle = obj.optString("documentTitle", "Untitled document"),
                promptType = obj.optString("promptType", "AI prompt"),
                scope = obj.optString("scope", "whole document"),
                promptPreview = preview,
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }
}

data class VeritasDocumentOutlineEntry(
    val title: String,
    val targetIndex: Int,
    val pageNumber: Int? = null,
    val level: Int = 0,
    val source: String = "Smart outline"
)

data class ReaderTrackerDay(
    val dateKey: String,
    val appOpenCount: Int = 0,
    val usageMillis: Long = 0L,
    val readDocumentIds: Set<String> = emptySet()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("dateKey", dateKey)
        .put("appOpenCount", appOpenCount)
        .put("usageMillis", usageMillis)
        .put("readDocumentIds", JSONArray().also { array -> readDocumentIds.sorted().forEach(array::put) })

    companion object {
        fun fromJson(obj: JSONObject): ReaderTrackerDay {
            val ids = mutableSetOf<String>()
            val array = obj.optJSONArray("readDocumentIds") ?: JSONArray()
            for (i in 0 until array.length()) {
                array.optString(i).trim().takeIf { it.isNotBlank() }?.let(ids::add)
            }
            return ReaderTrackerDay(
                dateKey = obj.optString("dateKey"),
                appOpenCount = obj.optInt("appOpenCount", 0).coerceAtLeast(0),
                usageMillis = obj.optLong("usageMillis", 0L).coerceAtLeast(0L),
                readDocumentIds = ids
            )
        }
    }
}

data class ReaderTrackerCompletion(
    val documentId: String,
    val title: String,
    val completedAt: Long
) {
    fun toJson(): JSONObject = JSONObject()
        .put("documentId", documentId)
        .put("title", title)
        .put("completedAt", completedAt)

    companion object {
        fun fromJson(obj: JSONObject): ReaderTrackerCompletion? {
            val documentId = obj.optString("documentId").trim()
            if (documentId.isBlank()) return null
            return ReaderTrackerCompletion(
                documentId = documentId,
                title = obj.optString("title", "Untitled reading").ifBlank { "Untitled reading" },
                completedAt = obj.optLong("completedAt", 0L)
            )
        }
    }
}

/** One week of Monday-first daily reading totals, for the swipeable weekly bar chart. */
data class WeekBars(
    val label: String,
    val values: List<Long>,
    val totalMillis: Long,
    val isCurrentWeek: Boolean
)

data class ReaderTrackerSnapshot(
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val weeklyUsageMillis: Long = 0L,
    val weeklyAverageMillis: Long = 0L,
    val documentsReadThisWeek: Int = 0,
    val documentsCompletedThisMonth: Int = 0,
    val weeklyUsageByDay: List<Long> = List(7) { 0L },
    val weeklyHistory: List<WeekBars> = emptyList(),
    val recentCompletions: List<ReaderTrackerCompletion> = emptyList(),
    val activeDateKeys: Set<String> = emptySet(),
    val openedDateKeys: Set<String> = emptySet()
) {
    companion object {
        fun empty(): ReaderTrackerSnapshot = ReaderTrackerSnapshot()
    }
}

object ReaderTrackerMath {
    const val COMPLETION_THRESHOLD = 0.90f

    fun currentStreak(openDateKeys: Set<String>, todayKey: String): Int {
        return streakEndingAt(openDateKeys, todayKey)
    }

    fun longestStreak(openDateKeys: Set<String>): Int {
        if (openDateKeys.isEmpty()) return 0
        var longest = 0
        openDateKeys.sorted().forEach { key ->
            longest = maxOf(longest, streakEndingAt(openDateKeys, key))
        }
        return longest
    }

    fun weeklyUsage(days: List<ReaderTrackerDay>, weekKeys: List<String>): Pair<Long, Long> {
        val byDate = days.associateBy { it.dateKey }
        val usage = weekKeys.map { key -> byDate[key]?.usageMillis ?: 0L }
        val total = usage.sum()
        return total to if (usage.isEmpty()) 0L else total / usage.size
    }

    fun monthCompletionCount(completions: List<ReaderTrackerCompletion>, monthPrefix: String, dateKeyFor: (Long) -> String): Int {
        return completions
            .filter { completion -> dateKeyFor(completion.completedAt).startsWith(monthPrefix) }
            .distinctBy { it.documentId }
            .size
    }

    private fun streakEndingAt(openDateKeys: Set<String>, endingKey: String): Int {
        var cursor = calendarFromDateKey(endingKey) ?: return 0
        var streak = 0
        while (true) {
            val key = dateKey(cursor.timeInMillis)
            if (key !in openDateKeys) return streak
            streak += 1
            cursor.add(Calendar.DAY_OF_YEAR, -1)
        }
    }

    fun dateKey(timestamp: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestamp))

    private fun calendarFromDateKey(key: String): Calendar? {
        val date = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(key) }.getOrNull() ?: return null
        return Calendar.getInstance().apply { time = date }
    }
}

data class QuestProgress(
    val tourDone: Boolean,
    val importDone: Boolean,
    val speedDone: Boolean,
    val bookmarkDone: Boolean
)

data class PersistedResumePoint(
    val documentId: String,
    val chunkIndex: Int,
    val charOffset: Int,
    val wordCount: Int
)

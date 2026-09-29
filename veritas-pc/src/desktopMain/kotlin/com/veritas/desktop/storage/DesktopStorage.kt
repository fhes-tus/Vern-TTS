package com.veritas.desktop.storage

import com.veritas.desktop.models.Bookmark
import com.veritas.desktop.models.ChecklistItem
import com.veritas.desktop.models.DesktopDocument
import com.veritas.desktop.models.DesktopFontFamily
import com.veritas.desktop.models.DesktopThemeType
import com.veritas.desktop.models.HabitTracker
import com.veritas.desktop.models.PronunciationRule
import com.veritas.desktop.models.ReaderSettings
import com.veritas.desktop.models.ReadingList
import com.veritas.desktop.models.RichNote
import com.veritas.desktop.models.TextAnnotation
import com.veritas.desktop.models.VoiceSettings
import com.veritas.desktop.parser.TextChunker
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DesktopStorage {
    private val dataDir: File by lazy {
        val userHome = System.getProperty("user.home") ?: "."
        val dir = File(userHome, ".veritas_desktop")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    private val libraryFile: File get() = File(dataDir, "library.json")
    private val libraryBakFile: File get() = File(dataDir, "library.json.bak")
    private val settingsFile: File get() = File(dataDir, "settings.json")
    private val rulesFile: File get() = File(dataDir, "pronunciation_rules.json")
    private val annotationsFile: File get() = File(dataDir, "annotations.json")
    private val bookmarksFile: File get() = File(dataDir, "bookmarks.json")
    private val richNotesFile: File get() = File(dataDir, "rich_notes.json")
    private val readingListsFile: File get() = File(dataDir, "reading_lists.json")
    private val habitsFile: File get() = File(dataDir, "habits.json")

    // --- Library Management ---

    fun loadLibrary(): List<DesktopDocument> {
        val file = if (libraryFile.exists()) libraryFile else if (libraryBakFile.exists()) libraryBakFile else null
        if (file == null || !file.exists()) {
            val initial = createDefaultLibrary()
            saveLibrary(initial)
            return initial
        }

        return try {
            val content = file.readText(Charsets.UTF_8)
            val jsonArray = JSONArray(content)
            val list = mutableListOf<DesktopDocument>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val chunksArray = obj.optJSONArray("chunks") ?: JSONArray()
                val chunksList = mutableListOf<String>()
                for (j in 0 until chunksArray.length()) {
                    chunksList.add(chunksArray.getString(j))
                }

                list.add(
                    DesktopDocument(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        sourceLabel = obj.optString("sourceLabel", "Document"),
                        filePath = obj.optString("filePath").takeIf { it.isNotBlank() },
                        rawText = obj.optString("rawText", ""),
                        chunks = chunksList,
                        currentIndex = obj.optInt("currentIndex", 0),
                        collection = obj.optString("collection", "All"),
                        isFavorite = obj.optBoolean("isFavorite", false),
                        isQueued = obj.optBoolean("isQueued", false),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        lastReadAt = obj.optLong("lastReadAt", System.currentTimeMillis())
                    )
                )
            }
            if (list.isEmpty()) {
                val initial = createDefaultLibrary()
                saveLibrary(initial)
                initial
            } else {
                list
            }
        } catch (e: Exception) {
            createDefaultLibrary()
        }
    }

    @Synchronized
    fun saveLibrary(documents: List<DesktopDocument>) {
        try {
            val jsonArray = JSONArray()
            for (doc in documents) {
                val obj = JSONObject()
                obj.put("id", doc.id)
                obj.put("title", doc.title)
                obj.put("sourceLabel", doc.sourceLabel)
                obj.put("filePath", doc.filePath ?: "")
                obj.put("rawText", doc.rawText)
                obj.put("chunks", JSONArray(doc.chunks))
                obj.put("currentIndex", doc.currentIndex)
                obj.put("collection", doc.collection)
                obj.put("isFavorite", doc.isFavorite)
                obj.put("isQueued", doc.isQueued)
                obj.put("createdAt", doc.createdAt)
                obj.put("lastReadAt", doc.lastReadAt)
                jsonArray.put(obj)
            }
            val jsonStr = jsonArray.toString(2)

            if (libraryFile.exists()) {
                libraryFile.copyTo(libraryBakFile, overwrite = true)
            }
            libraryFile.writeText(jsonStr, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- Habit Analytics Tracker ---

    fun loadHabitTracker(): HabitTracker {
        if (!habitsFile.exists()) return HabitTracker(currentStreak = 1, longestStreak = 1, totalMinutesRead = 15L, weeklyMinutesRead = 15L, todayMinutesRead = 15L)
        return try {
            val obj = JSONObject(habitsFile.readText(Charsets.UTF_8))
            val map = mutableMapOf<String, Int>()
            val historyObj = obj.optJSONObject("dailyMinutesHistory")
            if (historyObj != null) {
                for (k in historyObj.keys()) {
                    map[k] = historyObj.getInt(k)
                }
            }
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val todayMin = map[todayStr]?.toLong() ?: 0L

            HabitTracker(
                currentStreak = obj.optInt("currentStreak", 1),
                longestStreak = obj.optInt("longestStreak", 1),
                totalMinutesRead = obj.optLong("totalMinutesRead", 0L),
                weeklyMinutesRead = obj.optLong("weeklyMinutesRead", 0L),
                todayMinutesRead = todayMin,
                completedBooksCount = obj.optInt("completedBooksCount", 0),
                lastReadDate = obj.optString("lastReadDate", todayStr),
                dailyMinutesHistory = map
            )
        } catch (e: Exception) {
            HabitTracker()
        }
    }

    @Synchronized
    fun recordReadingMinutes(additionalMinutes: Int) {
        if (additionalMinutes <= 0) return
        val current = loadHabitTracker()
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val map = current.dailyMinutesHistory.toMutableMap()
        val todayMin = (map[todayStr] ?: 0) + additionalMinutes
        map[todayStr] = todayMin

        // Streak check
        val newStreak = if (current.lastReadDate == todayStr) {
            current.currentStreak
        } else {
            current.currentStreak + 1
        }

        val updated = current.copy(
            currentStreak = newStreak,
            longestStreak = maxOf(current.longestStreak, newStreak),
            totalMinutesRead = current.totalMinutesRead + additionalMinutes,
            weeklyMinutesRead = current.weeklyMinutesRead + additionalMinutes,
            todayMinutesRead = todayMin.toLong(),
            lastReadDate = todayStr,
            dailyMinutesHistory = map
        )

        try {
            val obj = JSONObject()
            obj.put("currentStreak", updated.currentStreak)
            obj.put("longestStreak", updated.longestStreak)
            obj.put("totalMinutesRead", updated.totalMinutesRead)
            obj.put("weeklyMinutesRead", updated.weeklyMinutesRead)
            obj.put("completedBooksCount", updated.completedBooksCount)
            obj.put("lastReadDate", updated.lastReadDate)

            val hObj = JSONObject()
            for ((k, v) in updated.dailyMinutesHistory) {
                hObj.put(k, v)
            }
            obj.put("dailyMinutesHistory", hObj)

            habitsFile.writeText(obj.toString(2), Charsets.UTF_8)
        } catch (e: Exception) {}
    }

    // --- Rich Notes Studio ---

    fun loadRichNotes(): List<RichNote> {
        if (!richNotesFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(richNotesFile.readText(Charsets.UTF_8))
            val list = mutableListOf<RichNote>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val checkItems = mutableListOf<ChecklistItem>()
                val checkArr = obj.optJSONArray("checklistItems")
                if (checkArr != null) {
                    for (j in 0 until checkArr.length()) {
                        val cObj = checkArr.getJSONObject(j)
                        checkItems.add(ChecklistItem(cObj.getString("text"), cObj.optBoolean("isChecked", false)))
                    }
                }

                list.add(
                    RichNote(
                        id = obj.getString("id"),
                        documentId = obj.optString("documentId").takeIf { it.isNotBlank() },
                        documentTitle = obj.optString("documentTitle").takeIf { it.isNotBlank() },
                        chunkIndex = obj.optInt("chunkIndex", -1).takeIf { it >= 0 },
                        title = obj.optString("title", ""),
                        content = obj.optString("content", ""),
                        colorTag = obj.optString("colorTag", "Yellow"),
                        isPinned = obj.optBoolean("isPinned", false),
                        isChecklist = obj.optBoolean("isChecklist", false),
                        checklistItems = checkItems,
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
            list.sortedByDescending { it.isPinned }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun saveRichNote(note: RichNote) {
        val all = loadRichNotes().toMutableList()
        all.removeAll { it.id == note.id }
        all.add(0, note.copy(updatedAt = System.currentTimeMillis()))
        saveAllRichNotes(all)
    }

    @Synchronized
    fun deleteRichNote(noteId: String) {
        val all = loadRichNotes().toMutableList()
        all.removeAll { it.id == noteId }
        saveAllRichNotes(all)
    }

    private fun saveAllRichNotes(notes: List<RichNote>) {
        try {
            val arr = JSONArray()
            for (n in notes) {
                val obj = JSONObject()
                obj.put("id", n.id)
                obj.put("documentId", n.documentId ?: "")
                obj.put("documentTitle", n.documentTitle ?: "")
                obj.put("chunkIndex", n.chunkIndex ?: -1)
                obj.put("title", n.title)
                obj.put("content", n.content)
                obj.put("colorTag", n.colorTag)
                obj.put("isPinned", n.isPinned)
                obj.put("isChecklist", n.isChecklist)
                obj.put("createdAt", n.createdAt)
                obj.put("updatedAt", n.updatedAt)

                val checkArr = JSONArray()
                for (item in n.checklistItems) {
                    val cObj = JSONObject()
                    cObj.put("text", item.text)
                    cObj.put("isChecked", item.isChecked)
                    checkArr.put(cObj)
                }
                obj.put("checklistItems", checkArr)

                arr.put(obj)
            }
            richNotesFile.writeText(arr.toString(2), Charsets.UTF_8)
        } catch (e: Exception) {}
    }

    // --- Reading Lists ---

    fun loadReadingLists(): List<ReadingList> {
        if (!readingListsFile.exists()) return listOf(ReadingList(title = "Favorites Playlist", colorTag = "Rose"))
        return try {
            val arr = JSONArray(readingListsFile.readText(Charsets.UTF_8))
            val list = mutableListOf<ReadingList>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val docIds = mutableListOf<String>()
                val dArr = obj.optJSONArray("documentIds")
                if (dArr != null) {
                    for (j in 0 until dArr.length()) docIds.add(dArr.getString(j))
                }
                list.add(
                    ReadingList(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        documentIds = docIds,
                        colorTag = obj.optString("colorTag", "Blue"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun saveReadingList(readingList: ReadingList) {
        val all = loadReadingLists().toMutableList()
        all.removeAll { it.id == readingList.id }
        all.add(readingList)
        saveAllReadingLists(all)
    }

    private fun saveAllReadingLists(lists: List<ReadingList>) {
        try {
            val arr = JSONArray()
            for (l in lists) {
                val obj = JSONObject()
                obj.put("id", l.id)
                obj.put("title", l.title)
                obj.put("colorTag", l.colorTag)
                obj.put("documentIds", JSONArray(l.documentIds))
                obj.put("createdAt", l.createdAt)
                arr.put(obj)
            }
            readingListsFile.writeText(arr.toString(2), Charsets.UTF_8)
        } catch (e: Exception) {}
    }

    // --- Bookmarks & Annotations ---

    fun loadAnnotations(documentId: String): List<TextAnnotation> {
        if (!annotationsFile.exists()) return emptyList()
        return try {
            val jsonArray = JSONArray(annotationsFile.readText(Charsets.UTF_8))
            val list = mutableListOf<TextAnnotation>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (obj.getString("documentId") == documentId) {
                    list.add(
                        TextAnnotation(
                            id = obj.getString("id"),
                            documentId = obj.getString("documentId"),
                            chunkIndex = obj.getInt("chunkIndex"),
                            selectedText = obj.getString("selectedText"),
                            noteContent = obj.getString("noteContent"),
                            colorTag = obj.optString("colorTag", "Yellow"),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun saveAnnotation(annotation: TextAnnotation) {
        val all = loadAllAnnotations().toMutableList()
        all.removeAll { it.id == annotation.id }
        all.add(annotation)
        saveAllAnnotations(all)
    }

    @Synchronized
    fun deleteAnnotation(annotationId: String) {
        val all = loadAllAnnotations().toMutableList()
        all.removeAll { it.id == annotationId }
        saveAllAnnotations(all)
    }

    private fun loadAllAnnotations(): List<TextAnnotation> {
        if (!annotationsFile.exists()) return emptyList()
        return try {
            val jsonArray = JSONArray(annotationsFile.readText(Charsets.UTF_8))
            val list = mutableListOf<TextAnnotation>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    TextAnnotation(
                        id = obj.getString("id"),
                        documentId = obj.getString("documentId"),
                        chunkIndex = obj.getInt("chunkIndex"),
                        selectedText = obj.getString("selectedText"),
                        noteContent = obj.getString("noteContent"),
                        colorTag = obj.optString("colorTag", "Yellow"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveAllAnnotations(annotations: List<TextAnnotation>) {
        try {
            val arr = JSONArray()
            for (a in annotations) {
                val obj = JSONObject()
                obj.put("id", a.id)
                obj.put("documentId", a.documentId)
                obj.put("chunkIndex", a.chunkIndex)
                obj.put("selectedText", a.selectedText)
                obj.put("noteContent", a.noteContent)
                obj.put("colorTag", a.colorTag)
                obj.put("createdAt", a.createdAt)
                arr.put(obj)
            }
            annotationsFile.writeText(arr.toString(2), Charsets.UTF_8)
        } catch (e: Exception) {}
    }

    // --- Bookmarks ---

    fun loadBookmarks(documentId: String): List<Bookmark> {
        if (!bookmarksFile.exists()) return emptyList()
        return try {
            val jsonArray = JSONArray(bookmarksFile.readText(Charsets.UTF_8))
            val list = mutableListOf<Bookmark>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (obj.getString("documentId") == documentId) {
                    list.add(
                        Bookmark(
                            id = obj.getString("id"),
                            documentId = obj.getString("documentId"),
                            chunkIndex = obj.getInt("chunkIndex"),
                            previewText = obj.getString("previewText"),
                            note = obj.optString("note", ""),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun toggleBookmark(documentId: String, chunkIndex: Int, previewText: String): Boolean {
        val all = loadAllBookmarks().toMutableList()
        val existing = all.firstOrNull { it.documentId == documentId && it.chunkIndex == chunkIndex }
        val added = if (existing != null) {
            all.remove(existing)
            false
        } else {
            all.add(Bookmark(documentId = documentId, chunkIndex = chunkIndex, previewText = previewText.take(120)))
            true
        }
        saveAllBookmarks(all)
        return added
    }

    private fun loadAllBookmarks(): List<Bookmark> {
        if (!bookmarksFile.exists()) return emptyList()
        return try {
            val jsonArray = JSONArray(bookmarksFile.readText(Charsets.UTF_8))
            val list = mutableListOf<Bookmark>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    Bookmark(
                        id = obj.getString("id"),
                        documentId = obj.getString("documentId"),
                        chunkIndex = obj.getInt("chunkIndex"),
                        previewText = obj.getString("previewText"),
                        note = obj.optString("note", ""),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveAllBookmarks(bookmarks: List<Bookmark>) {
        try {
            val arr = JSONArray()
            for (b in bookmarks) {
                val obj = JSONObject()
                obj.put("id", b.id)
                obj.put("documentId", b.documentId)
                obj.put("chunkIndex", b.chunkIndex)
                obj.put("previewText", b.previewText)
                obj.put("note", b.note)
                obj.put("createdAt", b.createdAt)
                arr.put(obj)
            }
            bookmarksFile.writeText(arr.toString(2), Charsets.UTF_8)
        } catch (e: Exception) {}
    }

    // --- Pronunciation Rules ---

    fun loadPronunciationRules(): List<PronunciationRule> {
        if (!rulesFile.exists()) return defaultPronunciationRules()
        return try {
            val jsonArray = JSONArray(rulesFile.readText(Charsets.UTF_8))
            val list = mutableListOf<PronunciationRule>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    PronunciationRule(
                        id = obj.getString("id"),
                        find = obj.getString("find"),
                        replaceWith = obj.getString("replaceWith"),
                        enabled = obj.optBoolean("enabled", true),
                        matchCase = obj.optBoolean("matchCase", false)
                    )
                )
            }
            list
        } catch (e: Exception) {
            defaultPronunciationRules()
        }
    }

    @Synchronized
    fun savePronunciationRules(rules: List<PronunciationRule>) {
        try {
            val arr = JSONArray()
            for (r in rules) {
                val obj = JSONObject()
                obj.put("id", r.id)
                obj.put("find", r.find)
                obj.put("replaceWith", r.replaceWith)
                obj.put("enabled", r.enabled)
                obj.put("matchCase", r.matchCase)
                arr.put(obj)
            }
            rulesFile.writeText(arr.toString(2), Charsets.UTF_8)
        } catch (e: Exception) {}
    }

    private fun defaultPronunciationRules(): List<PronunciationRule> = listOf(
        PronunciationRule(find = "e.g.", replaceWith = "for example"),
        PronunciationRule(find = "i.e.", replaceWith = "that is"),
        PronunciationRule(find = "etc.", replaceWith = "et cetera"),
        PronunciationRule(find = "vs.", replaceWith = "versus")
    )

    // --- Settings Storage ---

    fun loadSettings(): Pair<ReaderSettings, VoiceSettings> {
        if (!settingsFile.exists()) return ReaderSettings() to VoiceSettings()
        return try {
            val obj = JSONObject(settingsFile.readText(Charsets.UTF_8))
            val rObj = obj.optJSONObject("reader") ?: JSONObject()
            val vObj = obj.optJSONObject("voice") ?: JSONObject()

            val readerSettings = ReaderSettings(
                fontSize = rObj.optDouble("fontSize", 18.0).toFloat(),
                lineHeightMultiplier = rObj.optDouble("lineHeightMultiplier", 1.6).toFloat(),
                fontFamily = DesktopFontFamily.values().firstOrNull { it.name == rObj.optString("fontFamily") } ?: DesktopFontFamily.DEFAULT,
                themeType = DesktopThemeType.values().firstOrNull { it.name == rObj.optString("themeType") } ?: DesktopThemeType.SLATE_DARK,
                maxReadingWidthDp = rObj.optInt("maxReadingWidthDp", 850),
                isTwoColumnSpread = rObj.optBoolean("isTwoColumnSpread", false),
                highlightActiveSentence = rObj.optBoolean("highlightActiveSentence", true),
                autoScrollToSentence = rObj.optBoolean("autoScrollToSentence", true),
                showSentenceIndices = rObj.optBoolean("showSentenceIndices", false)
            )

            val voiceSettings = VoiceSettings(
                voiceName = vObj.optString("voiceName", ""),
                rate = vObj.optDouble("rate", 1.0).toFloat(),
                pitch = vObj.optDouble("pitch", 1.0).toFloat(),
                volume = vObj.optDouble("volume", 1.0).toFloat(),
                autoAdvance = vObj.optBoolean("autoAdvance", true),
                pauseAtPunctuation = vObj.optBoolean("pauseAtPunctuation", true)
            )

            readerSettings to voiceSettings
        } catch (e: Exception) {
            ReaderSettings() to VoiceSettings()
        }
    }

    @Synchronized
    fun saveSettings(readerSettings: ReaderSettings, voiceSettings: VoiceSettings) {
        try {
            val root = JSONObject()

            val rObj = JSONObject()
            rObj.put("fontSize", readerSettings.fontSize)
            rObj.put("lineHeightMultiplier", readerSettings.lineHeightMultiplier)
            rObj.put("fontFamily", readerSettings.fontFamily.name)
            rObj.put("themeType", readerSettings.themeType.name)
            rObj.put("maxReadingWidthDp", readerSettings.maxReadingWidthDp)
            rObj.put("isTwoColumnSpread", readerSettings.isTwoColumnSpread)
            rObj.put("highlightActiveSentence", readerSettings.highlightActiveSentence)
            rObj.put("autoScrollToSentence", readerSettings.autoScrollToSentence)
            rObj.put("showSentenceIndices", readerSettings.showSentenceIndices)
            root.put("reader", rObj)

            val vObj = JSONObject()
            vObj.put("voiceName", voiceSettings.voiceName)
            vObj.put("rate", voiceSettings.rate)
            vObj.put("pitch", voiceSettings.pitch)
            vObj.put("volume", voiceSettings.volume)
            vObj.put("autoAdvance", voiceSettings.autoAdvance)
            vObj.put("pauseAtPunctuation", voiceSettings.pauseAtPunctuation)
            root.put("voice", vObj)

            settingsFile.writeText(root.toString(2), Charsets.UTF_8)
        } catch (e: Exception) {}
    }

    private fun createDefaultLibrary(): List<DesktopDocument> {
        val welcomeText = """
            Welcome to Veritas Reader for Desktop!
            
            Veritas Reader is your personal desktop reading sanctuary and high-fidelity text-to-speech companion.
            
            You can drag and drop any PDF, EPUB, Word DOCX, or text file directly onto this window to begin reading immediately.
            
            Here are some quick shortcuts to help you get started:
            Press the Spacebar at any time to toggle voice playback.
            Use the Left and Right arrow keys to jump between sentences.
            Press Up and Down arrow keys to adjust playback speed smoothly.
            Press Ctrl+F to search for any keyword inside the current document.
            
            You can switch to the Floating Quick-Reader pill using the button in the top bar. The floater stays on top of any other application, letting you read while you work in Word, browse the web in Chrome, or review notes.
            
            Highlight any word or sentence to take a note, look up definitions, or generate instant AI study flashcards and quizzes.
            
            Enjoy a distraction-free, accessible reading experience crafted for your PC.
        """.trimIndent()

        val classicCheeseText = """
            Who Moved My Cheese? An A-Mazing Way to Deal with Change in Your Work and in Your Life.
            
            By Dr. Spencer Johnson.
            
            Once, long ago in a land far away, there lived four little characters who ran through a Maze looking for cheese to nourish them and make them happy.
            
            Two were mice, named "Sniff" and "Scurry" and two were littlepeople—beings who were as small as mice but who looked and acted a lot like people today. Their names were "Hem" and "Haw".
            
            Due to their small size, it would be easy not to notice what the four of them were doing. But if you looked closely enough, you could discover the most amazing things!
            
            Every day the mice and the littlepeople spent time in the Maze looking for their own special cheese.
            
            Sniff and Scurry, possessing only simple non-complex brains, but good instincts, searched for the hard nibbling cheese they liked, as mice often do.
            
            The two littlepeople, Hem and Haw, used their complex brains, filled with many beliefs and emotions, to search for a very different kind of Cheese—with a capital C—which they believed would lead them to happiness and success.
            
            As different as the mice and littlepeople were, they shared something in common: every morning, they each put on their running suits and running shoes, left their little homes, and raced out into the Maze looking for their favorite cheese.
            
            The Maze was a labyrinth of corridors and chambers, some containing delicious cheese. But there were also dark corners and blind alleys leading nowhere. It was an easy place for anyone to get lost.
            
            However, for those who found their way, the Maze held secrets that let them enjoy a better life.
            
            The mice, Sniff and Scurry, used the simple trial-and-error method of finding cheese. They ran down one corridor, and if it proved empty, they turned around and ran down another.
            
            Sniff would smell out the general direction of the cheese, using his great nose, and Scurry would race ahead. They got lost, as you might expect, went off in the wrong direction and often bumped into walls. But after a while, they found their way.
            
            Like the mice, the two littlepeople, Hem and Haw, also used their ability to think and learn from their past experiences. However, they relied on their complex brains to develop more sophisticated methods of finding Cheese.
            
            Sometimes they did well, but at other times their powerful human beliefs and emotions took over and clouded the way they looked at things. It made life in the Maze more complicated and challenging.
            
            Nonetheless, all four of them in their own way eventually found what they were looking for. One day they came upon their own special kind of cheese at the end of one of the corridors in Cheese Station C.
            
            Every morning after that, the mice and the littlepeople dressed in their running gear and headed over to Cheese Station C. It wasn't long before they each established their own routine.
            
            Change happens. They keep moving the cheese.
            
            Anticipate change. Get ready for the cheese to move.
            
            Monitor change. Smell the cheese often so you know when it is getting old.
            
            Adapt to change quickly. The quicker you let go of old cheese, the sooner you can enjoy new cheese.
            
            Change. Move with the cheese.
            
            Enjoy change! Savor the adventure and enjoy the taste of new cheese!
            
            Be ready to change quickly and enjoy it again and again. They keep moving the cheese.
        """.trimIndent()

        val welcomeDoc = DesktopDocument(
            id = "welcome_guide",
            title = "Veritas Welcome Guide",
            sourceLabel = "Interactive Guide",
            rawText = welcomeText,
            chunks = TextChunker.chunk(welcomeText),
            currentIndex = 0,
            isFavorite = true
        )

        val cheeseDoc = DesktopDocument(
            id = "who_moved_my_cheese",
            title = "Who Moved My Cheese?",
            sourceLabel = "Classic Book",
            rawText = classicCheeseText,
            chunks = TextChunker.chunk(classicCheeseText),
            currentIndex = 0,
            isFavorite = true
        )

        return listOf(welcomeDoc, cheeseDoc)
    }
}

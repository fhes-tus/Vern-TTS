package com.veritas.reader

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.*
import com.veritas.reader.ui.*
import com.veritas.reader.ui.screens.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ClassicLibraryInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository by lazy { DocumentRepository(context) }
    private val ownedDocuments = mutableSetOf<String>()
    private val ownedWork = mutableSetOf<UUID>()
    private val book = CURATED_CLASSICS.first()

    @Before fun prepare() {
        check(context.packageName.endsWith(".checks")) { "Use the isolated .checks package" }
        context.stopService(Intent(context, PlaybackService::class.java))
        InstrumentationRegistry.getInstrumentation().runOnMainSync { PlaybackStateStore.reset() }
        repository.markOnboardingComplete("Library check")
        repository.setQuestChecklistDismissed(true)
        context.getSharedPreferences("veritas_reader_library", Context.MODE_PRIVATE).edit()
            .putBoolean("battery_unrestricted_never_ask", true).commit()
    }

    @After fun cleanup() {
        val manager = WorkManager.getInstance(context)
        ownedWork.forEach { manager.cancelWorkById(it).result.get(10, TimeUnit.SECONDS) }
        ownedDocuments.forEach { repository.deleteDocument(it) }
        context.stopService(Intent(context, PlaybackService::class.java))
        InstrumentationRegistry.getInstrumentation().runOnMainSync { PlaybackStateStore.reset() }
    }

    private fun await(label: String, timeout: Long = 25_000, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (!predicate()) { check(SystemClock.elapsedRealtime() < deadline) { "Timed out: $label" }; SystemClock.sleep(100) }
    }
    private fun fixture(title: String = "Library fixture ${UUID.randomUUID()}"): SavedDocument = repository.createDocument(
        title, "A first sentence to read.\n\nA second sentence remains in place.\n\nThe third sentence completes the fixture.", "TXT")
        .also { ownedDocuments.add(it.id) }
    private fun tab(label: String) = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and hasContentDescription(label))
    private fun screenshot(name: String) {
        compose.waitForIdle()
        SystemClock.sleep(500)
        val directory = File(context.getExternalFilesDir(null), "stage3-review").apply { mkdirs() }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            ?: error("Could not capture review screenshot")
        File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun paperGuidesFollowWrappedAndFormattedEditorText() {
        val template = androidx.compose.runtime.mutableStateOf(NotesPaperTemplate.RULED)
        val spacing = androidx.compose.runtime.mutableStateOf(1f)
        val value = androidx.compose.runtime.mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(
            "# A heading\nA longer thought wraps naturally onto several lines while keeping its paper guides aligned.\nOne more line."))
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                androidx.compose.material3.MaterialTheme {
                    val base = androidx.compose.material3.MaterialTheme.typography
                    androidx.compose.material3.MaterialTheme(typography = base.copy(bodyLarge = base.bodyLarge.copy(
                        lineHeight = base.bodyLarge.lineHeight * spacing.value))) {
                        androidx.compose.material3.Surface(color = androidx.compose.ui.graphics.Color.White) {
                            NoteTextBlockItem(NoteBlock.Text(value.value), { value.value = it }, {}, { false },
                                RichTextVisualTransformation(androidx.compose.ui.graphics.Color.Black),
                                androidx.compose.ui.graphics.Color.Black, true, template.value,
                                androidx.compose.ui.Modifier.width(300.dp))
                        }
                    }
                }
            } }
            for (scale in listOf(1f, 1.5f)) {
                compose.runOnIdle { spacing.value = scale }
                val node = compose.onNode(hasSetTextAction())
                val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                node.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(results) }
                val layout = results.single()
                assertTrue("Fixture must include wrapping", layout.lineCount > 3)
                val image = node.captureToImage()
                val pixels = image.toPixelMap()
                val padding = ((image.width - layout.size.width) / 2).coerceAtLeast(0)
                val x = image.width - padding - 4
                val density = context.resources.displayMetrics.density
                for (line in 0 until layout.lineCount) {
                    val y = (padding + layout.getLineBottom(line) - density).toInt()
                    assertTrue("Guide must follow measured line $line at spacing $scale",
                        (y-2..y+2).filter { it in 0 until image.height }.any { pixels[x, it].red < .98f })
                }
            }
            for (paper in NotesPaperTemplate.entries) {
                compose.runOnIdle { template.value = paper }
                screenshot("notes-paper-editor-${paper.name.lowercase()}")
            }
            val original = value.value.text
            compose.onNode(hasSetTextAction()).performTextInputSelection(androidx.compose.ui.text.TextRange(original.length))
            compose.onNode(hasSetTextAction()).performTextInput(" Still here.")
            compose.runOnIdle { assertEquals(original + " Still here.", value.value.text) }
        }
    }

    @Test fun themePickerKeepsFamilyWhenAppearanceModeChanges() {
        val selected = androidx.compose.runtime.mutableStateOf("midnight_light")
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                androidx.compose.material3.MaterialTheme { VeritasThemePicker(selected.value) { selected.value = it } }
            } }
            compose.onNodeWithText("Dark", substring = false).performClick()
            compose.runOnIdle { assertEquals("midnight_dark", selected.value) }
            compose.onNodeWithText("Dracula", substring = false).performClick()
            compose.runOnIdle { assertEquals("dracula", selected.value) }
            compose.onNodeWithText("System", substring = false).performClick()
            compose.runOnIdle { assertEquals("dracula_system", selected.value) }
            compose.onNodeWithText("Light", substring = false).performClick()
            compose.runOnIdle { assertEquals("dracula_light", selected.value) }
            val mono = compose.onNodeWithText("B/W Gradient").fetchSemanticsNode().boundsInRoot
            val neon = compose.onNodeWithText("Neon", substring = false).fetchSemanticsNode().boundsInRoot
            assertEquals(mono.top, neon.top, 1f)
            assertTrue(neon.left > mono.left)
            compose.onNodeWithText("Neon", substring = false).performClick()
            compose.runOnIdle { assertEquals("neon", selected.value) }
            screenshot("ui-polish-theme-pairs")
        }
    }

    @Test fun pilgrimEditionsShareARowAndRemainSelectable() {
        val pilgrim = CURATED_CLASSICS.single { it.id == "classic_pilgrims_progress" }
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            scenario.onActivity { activity -> activity.setContent { VeritasTheme {
                ClassicBookDetailSheet(pilgrim, emptyList(), onDismiss = {}, onDownloadBook = {}, onOpenBook = {})
            } } }
            compose.onNodeWithTag("edition_modern").performScrollTo()
            val modern = compose.onNodeWithTag("edition_modern").fetchSemanticsNode().boundsInRoot
            val original = compose.onNodeWithTag("edition_original").fetchSemanticsNode().boundsInRoot
            assertEquals(modern.top, original.top, 1f)
            assertEquals(modern.height, original.height, 1f)
            assertTrue(original.left > modern.left)
            compose.onNodeWithTag("edition_original").performClick()
            compose.onNode(isSelected() and hasAnyAncestor(hasTestTag("edition_original")), useUnmergedTree = true).assertExists()
            screenshot("ui-polish-edition-pairs")
        }
    }

    @Test fun catalogListToggleAndMissingTitleArchiveSearchKeepTheQuery() {
        val browse = java.util.concurrent.atomic.AtomicReference<Triple<String, String, String>>()
        val query = "For you I'd steal a goat"
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            scenario.onActivity { activity -> activity.setContent { VeritasTheme {
                ClassicsCatalogContent(emptyList(), emptyMap(), {}, {}, {}, { url, name, title ->
                    browse.set(Triple(url, name, title))
                })
            } } }
            compose.onNodeWithTag("classics_view_mode").performClick()
            compose.onNodeWithTag("classic_featured").assertIsDisplayed()
            compose.onNodeWithTag("classics_catalog").performScrollToNode(hasTestTag("classic_row_classic_wizard_of_oz"))
            screenshot("ui-polish-featured-list")
            compose.onNodeWithTag("classics_catalog").performScrollToIndex(0)
            compose.onNodeWithTag("classics_view_mode").performClick()
            compose.onNodeWithTag("classics_search").performTextInput(query)
            FREE_BOOK_SITES.forEach { site ->
                compose.onNodeWithTag("classics_catalog").performScrollToNode(hasTestTag("classic_archive_${site.name}"))
                compose.onNodeWithTag("classic_archive_${site.name}").performClick()
                compose.runOnIdle { assertEquals(Triple(site.url, site.name, query), browse.get()) }
            }
            screenshot("library-compact-archive-search")
            compose.onNodeWithTag("classics_catalog").performScrollToIndex(0)
            compose.onNodeWithTag("classics_search").performTextReplacement("Alice")
            compose.onNodeWithTag("classics_view_mode").performClick()
            compose.onNodeWithTag("classic_row_classic_alice_wonderland").assertIsDisplayed()
            screenshot("library-compact-catalog-list")
            compose.onNodeWithTag("classics_view_mode").performClick()
            compose.onNodeWithTag("classics_search").assertTextContains("Alice")
            compose.onNodeWithTag("classic_card_classic_alice_wonderland").assertExists()
            screenshot("library-compact-catalog-shelves")
        }
    }

    @Test fun mergedCatalogCoversAndNotesSettingsWorkWithLibraryNavigation() {
        val addedIds = listOf("classic_pilgrims_progress", "classic_good_morning_holy_spirit", "classic_power_of_imagination")
        addedIds.forEach { id ->
            assertNotNull(CURATED_CLASSICS.firstOrNull { it.id == id })
            assertNotNull(BookCoverLoader.asset(context, id))
        }
        val previous = NotesSettingsStore.load(context)
        val previousTheme = VeritasThemeState.themeId
        val previousPack = VeritasThemeState.themePackId
        val previousAmoled = VeritasThemeState.amoledMode
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                compose.waitUntil(25_000) { compose.onAllNodesWithContentDescription("Notes").fetchSemanticsNodes().isNotEmpty() }
                tab("Notes").performClick()
                compose.onNodeWithContentDescription("Notes Settings").performClick()
                compose.onNodeWithText("Notes settings").assertExists()
                scenario.onActivity {
                    VeritasThemeState.themeId = "light"
                    VeritasThemeState.themePackId = "liquid_glass"
                    VeritasThemeState.amoledMode = true
                }
                screenshot("notes-visual-light")
                compose.onNodeWithText(NotesPaperTemplate.DOTS.label).performScrollTo().performClick()
                await("notes template persisted") { NotesSettingsStore.load(context).paperTemplate == NotesPaperTemplate.DOTS }
                compose.onNodeWithTag("notes_writing_preview").performScrollTo()
                screenshot("notes-visual-writing-light")
                scenario.onActivity { VeritasThemeState.themeId = "dark" }
                screenshot("notes-visual-writing-dark")
                compose.onNodeWithContentDescription("Close Notes settings").performClick()
                scenario.recreate()
                lateinit var vm: ReaderViewModel
                scenario.onActivity { vm = ViewModelProvider(it)[ReaderViewModel::class.java] }
                await("notes preferences restored") { vm.uiState.value.notesSettings.paperTemplate == NotesPaperTemplate.DOTS }
                scenario.onActivity { vm.openLibrarySection(LibrarySection.CLASSICS) }
                compose.waitUntil(25_000) { compose.onAllNodesWithTag("classics_search").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("classics_search").performTextInput("Pilgrim")
                compose.onNodeWithText("The Pilgrim's Progress").assertExists()
            }
        } finally {
            NotesSettingsStore.save(context, previous)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                VeritasThemeState.themeId = previousTheme
                VeritasThemeState.themePackId = previousPack
                VeritasThemeState.amoledMode = previousAmoled
            }
        }
    }

    @Test fun notesPreferencesDriveCardsAndResetWithoutChangingNotes() {
        val previous = NotesSettingsStore.load(context)
        val previousNotes = repository.loadGeneralNotes()
        val marker = UUID.randomUUID().toString()
        val image = File(context.cacheDir, "notes-preview-$marker.png")
        val bitmap = android.graphics.Bitmap.createBitmap(100, 80, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.MAGENTA)
        image.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        assertNotNull(loadNoteThumbnail(context, image.path, false))
        val fixture = GeneralNote("notes-$marker", "Settings fixture", "A visible snippet", System.currentTimeMillis(), imageUrl = image.path)
        try {
            repository.saveGeneralNotes(listOf(fixture))
            NotesSettingsStore.save(context, NotesSettings())
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                lateinit var vm: ReaderViewModel
                scenario.onActivity { vm = ViewModelProvider(it)[ReaderViewModel::class.java] }
                compose.waitUntil(25_000) { compose.onAllNodesWithContentDescription("Notes").fetchSemanticsNodes().isNotEmpty() }
                tab("Notes").performClick()
                compose.onNodeWithText("A visible snippet").assertExists()
                screenshot("stage5-note-card")
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("note_thumbnail_${fixture.id}", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
                scenario.onActivity {
                    // Multiple rapid settings changes must leave the latest snapshot persisted.
                    repeat(8) { vm.saveNotesSettings(vm.uiState.value.notesSettings.copy(previewLines = if (it == 7) 0 else 2,
                        richAttachmentPreviews = it != 7, isGridView = it != 7, defaultSortOrder = "title")) }
                }
                await("latest settings saved") { NotesSettingsStore.load(context).previewLines == 0 && !NotesSettingsStore.load(context).isGridView }
                compose.onNodeWithText("A visible snippet").assertDoesNotExist()
                compose.onNodeWithTag("note_thumbnail_${fixture.id}", useUnmergedTree = true).assertDoesNotExist()
                compose.onNodeWithContentDescription("Notes Settings").performClick()
                compose.onNodeWithText("Reset Notes preferences").performScrollTo().performClick()
                compose.onNodeWithText("Reset preferences").performClick()
                await("defaults persisted") { NotesSettingsStore.load(context) == NotesSettings() }
                compose.onNodeWithContentDescription("Close Notes settings").performClick()
                compose.onNodeWithText("A visible snippet").assertExists()
                assertEquals(listOf(fixture), repository.loadGeneralNotes())
            }
        } finally {
            repository.saveGeneralNotes(previousNotes)
            NotesSettingsStore.save(context, previous)
            image.delete()
        }
    }

    @Test fun editorSettingsPreserveLiveDraftAndEditionPickerSelectsActualText() {
        val savedNotes = repository.loadGeneralNotes()
        val previousSettings = NotesSettingsStore.load(context)
        val marker = UUID.randomUUID().toString()
        val fixture = GeneralNote("editor-$marker", "Draft fixture", "Draft to retain", System.currentTimeMillis())
        try {
            NotesSettingsStore.save(context, NotesSettings())
            repository.saveGeneralNotes(listOf(fixture))
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                lateinit var vm: ReaderViewModel
                scenario.onActivity { vm = ViewModelProvider(it)[ReaderViewModel::class.java] }
                compose.waitUntil(25_000) { compose.onAllNodesWithContentDescription("Notes").fetchSemanticsNodes().isNotEmpty() }
                scenario.onActivity { vm.updateState { it.withVisibility(VeritasScreen.GENERAL_NOTES_EDITOR, true).copy(generalNoteEditorTarget = fixture) } }
                compose.onNodeWithText("Draft to retain").performTextInputSelection(androidx.compose.ui.text.TextRange(15))
                compose.onNodeWithText("Draft to retain").performTextInput(" plus edits")
                compose.onNodeWithContentDescription("Notes Settings").performClick()
                compose.onNodeWithText("Large", substring = false).performScrollTo().performClick()
                compose.onNodeWithText("Wide").performScrollTo().performClick()
                compose.onNodeWithContentDescription("Close Notes settings").performClick()
                compose.onNodeWithText("Draft to retain plus edits").assertExists()
                await("edited note saved") { repository.loadGeneralNotes().any { it.id == fixture.id && it.content.contains("plus edits") } }
                assertEquals(1.5f, vm.uiState.value.notesSettings.editorLineSpacing, 0.001f)
                val selected = java.util.concurrent.atomic.AtomicReference<ClassicBookEntry>()
                scenario.onActivity { activity ->
                    activity.setContent { VeritasTheme {
                        ClassicBookDetailSheet(CURATED_CLASSICS.single { it.id == "classic_pilgrims_progress" }, emptyList(),
                            onDismiss = {}, onDownloadBook = { selected.set(it) }, onOpenBook = {})
                    } }
                }
                compose.onNodeWithText("Original 1678 English").performScrollTo().performClick()
                compose.onNodeWithText("Add to library").performScrollTo().performClick()
                assertEquals("classic_pilgrims_progress:original", selected.get().id)
                assertTrue(selected.get().downloadUrl.endsWith("pg131.txt"))
                compose.onNodeWithText("Modern English").performScrollTo().performClick()
                compose.onNodeWithText("Add to library").performScrollTo().performClick()
                assertEquals("classic_pilgrims_progress:modern", selected.get().id)
                assertTrue(selected.get().downloadUrl.endsWith("pg39452.txt"))
            }
        } finally { repository.saveGeneralNotes(savedNotes); NotesSettingsStore.save(context, previousSettings) }
    }

    @Test fun mergedOnboardingWelcomeAdvancesWithoutBreakingLibrary() {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            lateinit var vm: ReaderViewModel
            scenario.onActivity { vm = ViewModelProvider(it)[ReaderViewModel::class.java] }
            compose.waitUntil(25_000) { compose.onAllNodesWithContentDescription("Home").fetchSemanticsNodes().isNotEmpty() }
            scenario.onActivity { vm.updateState { it.copy(showTutorial = true, hasCompletedOnboarding = false) } }
            compose.onNodeWithText("Reading, Elevated to Art.").assertExists()
            compose.onNodeWithText("Begin the Journey").performClick()
            compose.onAllNodesWithText(ReaderPersonas.first().title).onFirst().assertExists()
            scenario.onActivity { vm.updateState { it.copy(showTutorial = false, hasCompletedOnboarding = true) } }
            tab("Library").performClick()
            compose.onNodeWithText("My Library").assertExists()
            compose.onNodeWithText("Classics").assertExists()
        }
    }

    @Test fun newLightThemesKeepTheirColorsWhenAmoledIsEnabled() {
        val previousTheme = VeritasThemeState.themeId
        val previousPack = VeritasThemeState.themePackId
        val previousAmoled = VeritasThemeState.amoledMode
        val previousAdaptive = VeritasThemeState.adaptiveCover
        val colors = java.util.concurrent.atomic.AtomicReference<androidx.compose.material3.ColorScheme>()
        val opacity = java.util.concurrent.atomic.AtomicReference<Float>()
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                scenario.onActivity { activity ->
                    VeritasThemeState.adaptiveCover = false
                    activity.setContent {
                        VeritasTheme {
                            val scheme = androidx.compose.material3.MaterialTheme.colorScheme
                            val alpha = VeritasPackStyle.surfaceAlpha()
                            androidx.compose.runtime.SideEffect { colors.set(scheme); opacity.set(alpha) }
                            androidx.compose.material3.Surface(androidx.compose.ui.Modifier.fillMaxSize()) {
                                ClassicsCatalogContent(emptyList(), emptyMap(), {}, {}, {}, { _, _, _ -> })
                            }
                        }
                    }
                }
                for (theme in listOf("dracula_light", "midnight_light", "one_light")) {
                    for (pack in listOf("vern_media", "liquid_glass", "one_ui", "material_you")) {
                        compose.runOnIdle {
                            VeritasThemeState.themeId = theme
                            VeritasThemeState.themePackId = pack
                            VeritasThemeState.amoledMode = false
                        }
                        compose.waitForIdle()
                        val before = colors.get()
                        val beforeOpacity = opacity.get()
                        assertTrue("$theme / $pack must be light", before.background.luminance() > 0.8f)
                        compose.runOnIdle { VeritasThemeState.amoledMode = true }
                        compose.waitForIdle()
                        val after = colors.get()
                        assertEquals("$theme / $pack background", before.background, after.background)
                        assertEquals("$theme / $pack surface", before.surface, after.surface)
                        assertEquals(before.surfaceVariant, after.surfaceVariant)
                        assertEquals(before.onBackground, after.onBackground)
                        assertEquals(before.onSurface, after.onSurface)
                        assertEquals(beforeOpacity, opacity.get())
                        val contrast = (after.surface.luminance() + 0.05f) / (after.onSurface.luminance() + 0.05f)
                        assertTrue("$theme / $pack text contrast", contrast >= 4.5f)
                        compose.onNodeWithTag("classics_search").assertIsDisplayed()
                        if (pack == "vern_media") screenshot("$theme-amoled")
                    }
                }
                compose.runOnIdle {
                    VeritasThemeState.themeId = "dark"
                    VeritasThemeState.themePackId = "vern_media"
                    VeritasThemeState.amoledMode = true
                }
                compose.waitForIdle()
                assertEquals(androidx.compose.ui.graphics.Color.Black, colors.get().background)
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                VeritasThemeState.themeId = previousTheme
                VeritasThemeState.themePackId = previousPack
                VeritasThemeState.amoledMode = previousAmoled
                VeritasThemeState.adaptiveCover = previousAdaptive
            }
        }
    }

    @Test fun legacyAdoptionRenameRetryAndDeletionPreserveReadingData() {
        val legacy = fixture("${book.title} - ${book.author}")
        repository.dbHelper.upsertDocument(legacy.copy(sourceLabel = "Classic Book", favorite = true, collection = "My favourites"))
        repository.saveProgress(legacy.id, 1)
        val before = repository.findDocument(legacy.id)!!
        val after = repository.migrateClassicProvenance().first { it.id == legacy.id }
        assertEquals(before.copy(catalogId = book.id), after)
        assertEquals(after, SavedDocument.fromJson(after.toJson()))
        repository.renameDocument(after.id, "Renamed classic")
        val installed = repository.installClassic(book, "This replacement must never overwrite the original.")
        assertEquals(after.id, installed.id)
        assertEquals("Renamed classic", installed.title)
        assertTrue(repository.readText(installed).contains("A first sentence"))
        assertEquals(1, installed.currentIndex)
        assertEquals("My favourites", installed.collection)
        assertTrue(installed.favorite)
        repository.deleteDocument(installed.id)
        val downloadedAgain = repository.installClassic(book, "A fresh reading after deletion.").also { ownedDocuments.add(it.id) }
        assertNotEquals(installed.id, downloadedAgain.id)
        assertEquals(book.id, downloadedAgain.catalogId)
    }

    @Test fun concurrentPublicationAndCancellationDoNotDuplicateBooks() {
        val results = java.util.Collections.synchronizedList(mutableListOf<SavedDocument>())
        val threads = (1..4).map { Thread { results.add(DocumentRepository(context).installClassic(book, "A fixture sentence.")) }.apply { start() } }
        threads.forEach { it.join(20_000) }
        assertEquals(4, results.size)
        assertEquals(1, results.map { it.id }.distinct().size)
        ownedDocuments.add(results.first().id)
        val another = CURATED_CLASSICS[1]
        try { repository.installClassic(another, "Should not be published.") { throw kotlinx.coroutines.CancellationException() }; fail("Cancellation must stop publication") }
        catch (_: kotlinx.coroutines.CancellationException) { }
        assertNull(findCatalogDocument(another, repository.loadDocuments()))
    }

    @Test fun databaseUpgradeAddsOnlyProvenanceAndKeepsExistingRows() {
        val databaseFile = File(context.cacheDir, "classic-v1-${UUID.randomUUID()}.db")
        val isolated = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String): File = databaseFile
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
                SQLiteDatabase.openOrCreateDatabase(databaseFile, factory)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: android.database.DatabaseErrorHandler?): SQLiteDatabase =
                SQLiteDatabase.openOrCreateDatabase(databaseFile.path, factory, errorHandler)
        }
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { database ->
            database.execSQL("""CREATE TABLE documents (id TEXT PRIMARY KEY, title TEXT NOT NULL, file_name TEXT NOT NULL, source_label TEXT NOT NULL,
                created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, current_index INTEGER NOT NULL, chunk_count INTEGER NOT NULL,
                char_count INTEGER NOT NULL, preview TEXT NOT NULL, favorite INTEGER NOT NULL DEFAULT 0, collection TEXT NOT NULL DEFAULT '',
                original_file_name TEXT NOT NULL DEFAULT '', original_mime_type TEXT NOT NULL DEFAULT '', page_count INTEGER NOT NULL DEFAULT 0,
                partial INTEGER NOT NULL DEFAULT 0, language TEXT NOT NULL DEFAULT '')""")
            database.execSQL("INSERT INTO documents (id,title,file_name,source_label,created_at,updated_at,current_index,chunk_count,char_count,preview) VALUES ('old','Old book','old.txt','TXT',10,20,7,30,200,'Keep me')")
            database.version = 1
        }
        try {
            VeritasDatabaseHelper(isolated).use { helper ->
                helper.readableDatabase.rawQuery("SELECT catalog_id,current_index,preview FROM documents WHERE id='old'", null).use { row ->
                    assertTrue(row.moveToFirst()); assertEquals("", row.getString(0)); assertEquals(7, row.getInt(1)); assertEquals("Keep me", row.getString(2))
                }
                assertEquals(2, helper.readableDatabase.version)
            }
        } finally { databaseFile.delete(); File(databaseFile.path + "-wal").delete(); File(databaseFile.path + "-shm").delete() }
    }

    @Test fun workManagerKeepsOneRequestAndReportsFailureCancellationAndCompletion() {
        val manager = WorkManager.getInstance(context)
        val key = "classic-test-${UUID.randomUUID()}"
        fun request(delay: Boolean = false, id: String = "missing-book") = OneTimeWorkRequestBuilder<ClassicBookDownloadWorker>()
            .setInputData(workDataOf(ClassicBookDownloadWorker.BOOK_ID to id)).addTag(ClassicBookDownloadWorker.TAG)
            .addTag("book:$id").addTag("created:${System.currentTimeMillis()}")
            .apply { if (delay) setInitialDelay(1, TimeUnit.HOURS) }.build().also { ownedWork.add(it.id) }
        val pending = request(true)
        manager.enqueueUniqueWork(key, ExistingWorkPolicy.KEEP, pending).result.get()
        manager.enqueueUniqueWork(key, ExistingWorkPolicy.KEEP, request(true)).result.get()
        assertEquals(1, manager.getWorkInfosForUniqueWork(key).get().size)
        manager.cancelUniqueWork(key).result.get()
        val cancelled = manager.getWorkInfoById(pending.id).get()
        assertEquals(ClassicDownloadPhase.CANCELLED, classicDownloadStates(listOf(cancelled))["missing-book"]?.phase)
        val failure = request()
        manager.enqueueUniqueWork(key, ExistingWorkPolicy.KEEP, failure).result.get()
        await("worker failure") { manager.getWorkInfoById(failure.id).get().state.isFinished }
        assertEquals(ClassicDownloadPhase.FAILED, classicDownloadStates(listOf(manager.getWorkInfoById(failure.id).get()))["missing-book"]?.phase)
        val installed = repository.installClassic(book, "A fixture sentence.").also { ownedDocuments.add(it.id) }
        val success = request(id = book.id)
        manager.enqueueUniqueWork(key, ExistingWorkPolicy.KEEP, success).result.get()
        await("existing book no-op success") { manager.getWorkInfoById(success.id).get().state.isFinished }
        assertEquals(WorkInfo.State.SUCCEEDED, manager.getWorkInfoById(success.id).get().state)
        assertEquals(installed.id, manager.getWorkInfoById(success.id).get().outputData.getString("documentId"))
        assertEquals(1, repository.loadDocuments().count { it.catalogId == book.id })
    }

    @Test fun genreShelvesSearchSortAndReaderReturnSurviveRecreation() {
        val doc = repository.installClassic(book, "First sentence. Second sentence.").also { ownedDocuments.add(it.id) }
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            lateinit var vm: ReaderViewModel
            scenario.onActivity { vm = ViewModelProvider(it)[ReaderViewModel::class.java]; vm.openLibrarySection(LibrarySection.CLASSICS) }
            compose.waitUntil(25_000) { compose.onAllNodesWithTag("classics_catalog").fetchSemanticsNodes().isNotEmpty() }
            screenshot("classics-default")
            compose.onNodeWithTag("classics_search").performTextInput("Alice")
            compose.onNodeWithTag("classics_sort").performClick()
            compose.onNodeWithText("Title", useUnmergedTree = true).performClick()
            compose.onNodeWithText("My Library").performClick()
            compose.onNodeWithText("Classics", useUnmergedTree = true).performClick()
            compose.onNodeWithTag("classics_search").assertTextContains("Alice")
            scenario.recreate()
            compose.onNodeWithTag("classics_search").assertTextContains("Alice")
            compose.onNodeWithText("Sort: Title").assertExists()
            scenario.onActivity { vm = ViewModelProvider(it)[ReaderViewModel::class.java]; vm.openSavedDocument(doc) }
            await("reader") { vm.uiState.value.activeDocument?.id == doc.id }
            scenario.onActivity { vm.returnToLibrary() }
            compose.waitUntil(25_000) { compose.onAllNodesWithTag("classics_search").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("classics_search").assertTextContains("Alice")
            compose.onNodeWithContentDescription("Clear search").performClick()
            compose.onNodeWithTag("classic_genre_Family & Youth").performClick()
            compose.onNodeWithTag("classic_shelf_Family & Youth").performScrollToIndex(4)
            screenshot("classics-genre")
            compose.onNodeWithText("My Library").performClick()
            screenshot("my-library")
            tab("Home").performClick()
            screenshot("home")
        }
    }

    @Test fun floaterStepsTheAudioBookWithoutMovingAnotherReader() {
        val audio = fixture("Listening controls")
        val reading = fixture("Separate reading position")
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            lateinit var vm: ReaderViewModel
            scenario.onActivity { activity ->
                vm = ViewModelProvider(activity)[ReaderViewModel::class.java]
                vm.openSavedDocument(reading)
            }
            await("separate reader opened") { vm.uiState.value.activeDocument?.id == reading.id }
            scenario.onActivity {
                vm.moveTo(2)
                vm.returnToLibrary()
                PlaybackStateStore.activeDocumentId = audio.id
                PlaybackStateStore.documentTitle = audio.title
                PlaybackStateStore.currentIndex = 1
                PlaybackStateStore.chunkCount = audio.chunkCount
                PlaybackStateStore.isPlaying = false
            }
            compose.waitUntil(25_000) { compose.onAllNodesWithTag("floater_next").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("floater_next").performClick()
            await("next audio sentence") { PlaybackStateStore.currentIndex == 2 }
            compose.onNodeWithTag("floater_previous").performClick()
            await("previous audio sentence") { PlaybackStateStore.currentIndex == 1 }
            assertEquals(audio.id, PlaybackStateStore.activeDocumentId)
            assertFalse(PlaybackStateStore.isPlaying)
            assertEquals(2, repository.findDocument(reading.id)?.currentIndex)
            screenshot("home-live-sentence-controls")
        }
    }

    @Test fun floaterUsesTheAudioBookAndOpeningAnotherReaderKeepsItsSession() {
        val audio = fixture("The listening book")
        val reading = fixture("Another reading book")
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            lateinit var vm: ReaderViewModel
            scenario.onActivity { activity ->
                vm = ViewModelProvider(activity)[ReaderViewModel::class.java]
                PlaybackStateStore.activeDocumentId = audio.id; PlaybackStateStore.documentTitle = audio.title
                PlaybackStateStore.currentIndex = 1; PlaybackStateStore.chunkCount = audio.chunkCount
                PlaybackStateStore.isPlaying = true
                vm.openLibrarySection(LibrarySection.CLASSICS)
            }
            compose.waitUntil(25_000) { compose.onAllNodesWithTag("live_playback_floater").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("live_playback_floater").assert(hasAnyDescendant(hasText(audio.title)))
            screenshot("classics-live-player")
            scenario.onActivity { vm.openSavedDocument(reading) }
            await("other reader") { vm.uiState.value.activeDocument?.id == reading.id }
            assertEquals(audio.id, PlaybackStateStore.activeDocumentId); assertEquals(1, PlaybackStateStore.currentIndex); assertTrue(PlaybackStateStore.isPlaying)
            scenario.onActivity { vm.moveTo(2); vm.returnToLibrary() }
            await("reading progress") { repository.findDocument(reading.id)?.currentIndex == 2 }
            assertEquals(audio.id, PlaybackStateStore.activeDocumentId); assertEquals(1, PlaybackStateStore.currentIndex)
            compose.waitUntil(25_000) { compose.onAllNodesWithTag("live_playback_floater").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("floater_open_book").performClick()
            await("floater opens audio book") { vm.uiState.value.activeDocument?.id == audio.id }
            assertEquals(1, vm.currentReaderIndex)
        }
    }

    @Test fun realCuratedDownloadAddsInBackgroundWithoutOpeningOrRetargetingPlayback() {
        val quickBook = CURATED_CLASSICS.first { it.genre == "Quick Reads" }
        assertNull(findCatalogDocument(quickBook, repository.loadDocuments()))
        val audio = fixture("Paused listening fixture")
        val manager = WorkManager.getInstance(context)
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            lateinit var vm: ReaderViewModel
            scenario.onActivity { activity ->
                vm = ViewModelProvider(activity)[ReaderViewModel::class.java]
                PlaybackStateStore.activeDocumentId = audio.id
                PlaybackStateStore.documentTitle = audio.title
                PlaybackStateStore.currentIndex = 1; PlaybackStateStore.chunkCount = audio.chunkCount
                vm.openLibrarySection(LibrarySection.CLASSICS)
                vm.downloadClassicBook(quickBook); vm.downloadClassicBook(quickBook)
            }
            await("request is persisted") { manager.getWorkInfosForUniqueWork(ClassicBookDownloadWorker.uniqueName(quickBook.id)).get().isNotEmpty() }
            val work = manager.getWorkInfosForUniqueWork(ClassicBookDownloadWorker.uniqueName(quickBook.id)).get()
            ownedWork.addAll(work.map { it.id })
            assertEquals(1, work.count { !it.state.isFinished })
            scenario.recreate()
            scenario.onActivity { vm = ViewModelProvider(it)[ReaderViewModel::class.java] }
            try {
                await("real Gutenberg download", 100_000) {
                    val info = manager.getWorkInfoById(work.single().id).get()
                    check(info.state != WorkInfo.State.FAILED) { info.outputData.getString(ClassicBookDownloadWorker.ERROR).orEmpty() }
                    info.state == WorkInfo.State.SUCCEEDED
                }
                val installed = findCatalogDocument(quickBook, repository.loadDocuments())!!
                ownedDocuments.add(installed.id)
                await("library receives completed book") { vm.uiState.value.documents.any { it.id == installed.id } }
                assertEquals(1, vm.uiState.value.documents.count { it.catalogId == quickBook.id })
                assertFalse(vm.uiState.value.importInProgress)
                assertNull(vm.uiState.value.activeDocument)
                assertEquals(audio.id, PlaybackStateStore.activeDocumentId)
                assertEquals(1, PlaybackStateStore.currentIndex)
                assertTrue(repository.readText(installed).length > 500)
                assertEquals(quickBook.id, installed.catalogId)
                val cover = BookCoverLoader.document(context, installed.id, installed.catalogId)
                if (cover != null) { assertTrue(cover.width <= 400); assertTrue(cover.height <= 600) }
            } finally { findCatalogDocument(quickBook, repository.loadDocuments())?.let { ownedDocuments.add(it.id) } }
        }
    }

    @Test fun narrowLargeTextCatalogKeepsActionsReachableInLightAndDarkThemes() {
        val originalTheme = VeritasThemeState.themeId
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            var dark by androidx.compose.runtime.mutableStateOf(false)
            scenario.onActivity { activity ->
                activity.setContentForCatalogReview(
                    dark = { dark },
                    bookStates = mapOf(book.id to ClassicDownloadState(ClassicDownloadPhase.DOWNLOADING, 45)))
            }
            compose.waitUntil(25_000) { compose.onAllNodesWithTag("classics_search").fetchSemanticsNodes().isNotEmpty() }
            screenshot("classics-light-large-text")
            compose.onNodeWithTag("classics_search").performTextInput(book.title)
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("classics_catalog").performScrollToNode(hasTestTag("classic_shelf_${book.genre}"))
            compose.onNodeWithText("Cancel").performScrollTo().assertIsDisplayed().assertHasClickAction()
            screenshot("classics-light-large-text-action")
            compose.onNodeWithTag("classics_catalog").performScrollToIndex(0)
            compose.onNodeWithContentDescription("Clear search").performClick()
            compose.runOnIdle { dark = true }
            screenshot("classics-dark-large-text")
            compose.onNodeWithTag("classics_search").performTextInput(book.title)
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            compose.onNodeWithTag("classics_catalog").performScrollToNode(hasTestTag("classic_shelf_${book.genre}"))
            compose.onNodeWithText("Cancel").performScrollTo().assertIsDisplayed().assertHasClickAction()
            screenshot("classics-dark-large-text-action")
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync { VeritasThemeState.themeId = originalTheme }
    }
}

private fun MainActivity.setContentForCatalogReview(dark: () -> Boolean, bookStates: Map<String, ClassicDownloadState>) {
    setContent {
        val theme = if (dark()) "dark" else "light"
        androidx.compose.runtime.SideEffect { VeritasThemeState.themeId = theme }
        val density = androidx.compose.ui.platform.LocalDensity.current
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 1.6f)) {
            VeritasTheme {
                androidx.compose.material3.Surface(androidx.compose.ui.Modifier.fillMaxSize()) {
                    androidx.compose.foundation.layout.Box(contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
                        ClassicsCatalogContent(emptyList(), bookStates, {}, {}, {}, { _, _, _ -> }, androidx.compose.ui.Modifier.width(320.dp))
                    }
                }
            }
        }
    }
}

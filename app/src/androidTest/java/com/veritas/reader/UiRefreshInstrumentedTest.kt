package com.veritas.reader

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.*
import com.veritas.reader.ui.screens.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

class UiRefreshInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository by lazy { DocumentRepository(context) }
    private lateinit var previousNotes: List<GeneralNote>
    private lateinit var previousSettings: NotesSettings
    private val ownedDocuments = mutableListOf<String>()

    @Before fun prepare() {
        check(context.packageName.endsWith(".checks"))
        previousNotes = repository.loadGeneralNotes()
        previousSettings = NotesSettingsStore.load(context)
        repository.markOnboardingComplete("UI refresh check")
        repository.setQuestChecklistDismissed(true)
        context.getSharedPreferences("veritas_reader_library", 0).edit().putBoolean("battery_unrestricted_never_ask", true).commit()
        NotesSettingsStore.save(context, NotesSettings())
        InstrumentationRegistry.getInstrumentation().runOnMainSync { PlaybackStateStore.reset() }
    }

    @After fun restore() {
        repository.saveGeneralNotes(previousNotes)
        NotesSettingsStore.save(context, previousSettings)
        ownedDocuments.forEach { repository.deleteDocument(it) }
        InstrumentationRegistry.getInstrumentation().runOnMainSync { PlaybackStateStore.reset() }
    }

    private fun launch() = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
    private fun tab(label: String) = compose.onNode(hasContentDescription(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
    private fun awaitTab(label: String) {
        compose.waitUntil(25_000) {
            compose.onAllNodes(hasContentDescription(label) and isSelected() and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
                .fetchSemanticsNodes().size == 1
        }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val directory = File(context.getExternalFilesDir(null), "ui-refresh").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun libraryHeaderStaysFixedAndSearchKeepsSeparateSectionQueries() {
        launch().use { scenario ->
            awaitTab("Home"); tab("Library").performClick(); awaitTab("Library")
            compose.onNodeWithText("Added").assertIsSelected()
            val before = compose.onNodeWithTag("shared_home_header").fetchSemanticsNode().boundsInRoot
            compose.onNodeWithTag("home_pager").performTouchInput {
                swipe(androidx.compose.ui.geometry.Offset(right - 12, centerY), androidx.compose.ui.geometry.Offset(left + 12, centerY))
            }
            compose.onNodeWithText("Classics").assertIsSelected()
            assertEquals(before, compose.onNodeWithTag("shared_home_header").fetchSemanticsNode().boundsInRoot)
            compose.onAllNodesWithTag("shared_home_header").assertCountEquals(1)
            compose.onNodeWithTag("library_search_toggle").performClick()
            compose.onNodeWithTag("classics_search").performTextInput("Meditations")
            compose.onNodeWithTag("classics_search").performImeAction()
            compose.onNodeWithTag("classics_search").assertDoesNotExist()
            compose.onNodeWithText("Added").performClick()
            compose.onNodeWithTag("library_search_toggle").performClick()
            compose.onNodeWithTag("library_search").assertTextContains("")
            compose.onNodeWithTag("library_search").performTextInput("Separate library query")
            compose.onNodeWithText("Classics").performClick()
            compose.onNodeWithTag("classics_search").assertTextContains("Meditations")
            scenario.recreate()
            awaitTab("Library")
            compose.onNodeWithTag("classics_search").assertTextContains("Meditations")
            compose.onNodeWithTag("classics_search").performImeAction()
            capture("classics-shared-header")
        }
    }

    @Test fun closedNotesStayCompactAndPreviewSettingPersistsWithoutChangingContent() {
        val attachments = (1..8).map { NoteBlock.File("/data/file$it.pdf", "Document $it.pdf", 200) }
        val body = VeritasNoteEditing.serializeNoteBlocks(listOf(NoteBlock.Text(TextFieldValue("**A compact thought**"))) + attachments)
        val link = NoteLink("https://example.invalid/verse", "Galatians 1:10", "https://example.invalid/cover.png")
        val fixture = GeneralNote("refresh-card", "Reflection", NoteLinks.write(body, listOf(link)), 123)
        repository.saveGeneralNotes(listOf(fixture))
        launch().use { scenario ->
            awaitTab("Home"); tab("Notes").performClick(); awaitTab("Notes")
            compose.onNodeWithText("A compact thought").assertExists()
            compose.onNodeWithTag("note_link_preview", useUnmergedTree = true).assertExists()
            compose.onAllNodes(hasText("Document 8.pdf")).assertCountEquals(0)
            val bounds = compose.onNodeWithTag("note_card_refresh-card").fetchSemanticsNode().boundsInRoot
            assertTrue("Eight attachments must not stretch the card", bounds.height < 280 * context.resources.displayMetrics.density)
            val footer = compose.onNodeWithTag("note_link_preview", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(footer.bottom > bounds.bottom - 12 * context.resources.displayMetrics.density)
            capture("notes-compact-footer")
            compose.onNodeWithContentDescription("Notes options").performClick()
            compose.onNodeWithText("Notes settings").performClick()
            compose.onNodeWithText("Display rich link previews").performClick()
            compose.onNodeWithContentDescription("Close Notes settings").performClick()
            compose.onNodeWithTag("note_link_preview", useUnmergedTree = true).assertDoesNotExist()
            scenario.recreate(); awaitTab("Notes")
            compose.onNodeWithTag("note_link_preview", useUnmergedTree = true).assertDoesNotExist()
            assertFalse(NotesSettingsStore.load(context).showRichLinkPreviews)
            assertEquals(listOf(fixture), repository.loadGeneralNotes())
            compose.onNodeWithText("Reflection").performClick()
            compose.onAllNodesWithTag("note_link_preview").assertCountEquals(0)
        }
    }

    @Test fun formattedEditorRendersCleanTextAndTypingPreservesFormatting() {
        val value = mutableStateOf(TextFieldValue("## Introduction\n**Bold words** and *italic*."))
        launch().use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                MaterialTheme { Surface(Modifier.fillMaxSize(), color = Color.White) {
                    NoteTextBlockItem(NoteBlock.Text(value.value), { value.value = it }, {}, { false },
                        RichTextVisualTransformation(Color.Black), Color.Black, true, NotesPaperTemplate.RULED)
                } }
            } }
            val field = compose.onNode(hasSetTextAction())
            val layouts = mutableListOf<TextLayoutResult>()
            field.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals("Introduction\nBold words and italic.", layouts.last().layoutInput.text.text)
            compose.runOnIdle { value.value = value.value.copy(selection = TextRange(value.value.text.indexOf("** and"))) }
            field.performTextInput(" added")
            compose.runOnIdle { assertTrue(value.value.text.contains("**Bold words added**")) }
            capture("notes-clean-formatting")
        }
    }

    @Test fun notesSearchExpandsFromHeaderAndSurvivesRecreation() {
        repository.saveGeneralNotes(listOf(GeneralNote("follow-search", "Find this reflection", "Searchable text", 123)))
        launch().use { scenario ->
            awaitTab("Home"); tab("Notes").performClick(); awaitTab("Notes")
            compose.onNodeWithTag("notes_search").assertDoesNotExist()
            compose.onNodeWithTag("notes_search_toggle").performClick()
            compose.onNodeWithTag("notes_search").performTextInput("reflection")
            scenario.recreate(); awaitTab("Notes")
            compose.onNodeWithTag("notes_search").assertTextContains("reflection")
            compose.onNodeWithTag("notes_search").performImeAction()
            compose.onNodeWithTag("notes_search").assertDoesNotExist()
            compose.onNodeWithText("Find this reflection").assertExists()
            compose.onNodeWithTag("notes_search_toggle").performClick()
            compose.onNodeWithTag("notes_search").assertTextContains("reflection")
            compose.onNodeWithTag("notes_search_toggle").performClick()
            capture("followup-notes-search-closed")
        }
    }

    @Test fun navigationTearIsTemporaryAndSettlesIntoSeparatedPills() {
        val selected = mutableStateOf(VeritasHomeTab.HOME)
        launch().use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                MaterialTheme { Surface(Modifier.fillMaxSize()) {
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                        LibraryBottomNavBar(selected.value, true, { selected.value = it },
                            Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
                    }
                } }
            } }
            compose.mainClock.autoAdvance = false
            compose.runOnIdle { selected.value = VeritasHomeTab.NOTES }
            compose.mainClock.advanceTimeBy(240)
            capture("followup-navigation-tear-midpoint")
            compose.mainClock.advanceTimeBy(500)
            compose.mainClock.autoAdvance = true
            val main = compose.onNodeWithTag("navigation_main_surface", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val notes = compose.onNodeWithTag("navigation_notes_surface", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(notes.left > main.right)
            capture("followup-navigation-tear-settled")
        }
    }

    @Test fun thumbnailToggleRetainsValidAndMissingMediaNotes() {
        val photo = File(context.cacheDir, "followup-thumbnail.png")
        val bitmap = android.graphics.Bitmap.createBitmap(800, 600, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(70, 135, 180))
        photo.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val valid = GeneralNote("follow-photo", "Photo reflection", "Text stays visible\n![image](${photo.absolutePath})", 123, imageUrl = photo.absolutePath)
        val missing = GeneralNote("follow-missing", "Missing video reflection", "Still here\n[video](/missing/video.mp4)", 124)
        repository.saveGeneralNotes(listOf(valid, missing))
        NotesSettingsStore.save(context, NotesSettings(richAttachmentPreviews = false))
        try {
            launch().use { scenario ->
                awaitTab("Home"); tab("Notes").performClick(); awaitTab("Notes")
                compose.onNodeWithText("Photo reflection").assertExists()
                compose.onNodeWithText("Missing video reflection").assertExists()
                compose.onNodeWithContentDescription("Notes options").performClick()
                compose.onNodeWithText("Notes settings").performClick()
                compose.onNodeWithText("Attachment thumbnails").performClick()
                compose.onNodeWithContentDescription("Close Notes settings").performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("note_thumbnail_follow-photo", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Text stays visible").assertExists()
                compose.onNodeWithText("Still here").assertExists()
                scenario.recreate(); awaitTab("Notes")
                compose.onNodeWithText("Photo reflection").assertExists()
                compose.onNodeWithText("Missing video reflection").assertExists()
                assertEquals(setOf(valid, missing), repository.loadGeneralNotes().toSet())
                capture("followup-thumbnail-notes")
            }
        } finally { photo.delete() }
    }

    @Test fun editorPreviewSwitchHidesCardsButPreservesLinksAndSpacing() {
        val settings = mutableStateOf(NotesSettings())
        val raw = NoteLinks.write("A reflection", listOf(NoteLink("https://example.invalid/verse", "Verse", "https://example.invalid/cover.png")))
        var saved = ""
        launch().use { scenario ->
            scenario.onActivity { activity -> activity.setContent { MaterialTheme {
                GeneralNotesEditor(GeneralNote("refresh-editor", "Reflection", raw, 123), notesSettings = settings.value,
                    onSaveNotesSettings = { settings.value = it }, onSave = { _, body, _, _, _, _, _, _, _, _ -> saved = body },
                    onDelete = {}, onDismiss = {})
            } } }
            val preview = compose.onNodeWithTag("note_link_preview").fetchSemanticsNode().boundsInRoot
            val body = compose.onNode(hasSetTextAction() and hasText("A reflection")).fetchSemanticsNode().boundsInRoot
            assertTrue(preview.top - body.bottom >= 70 * context.resources.displayMetrics.density)
            capture("notes-editor-spacing")
            compose.runOnIdle { settings.value = settings.value.copy(showRichLinkPreviews = false) }
            compose.onNodeWithTag("note_link_preview").assertDoesNotExist()
            compose.onNode(hasSetTextAction() and hasText("A reflection")).performTextReplacement("A revised reflection")
            compose.onNodeWithContentDescription("Save Note").performClick()
            compose.runOnIdle { assertEquals(NoteLinks.read(raw), NoteLinks.read(saved)) }
        }
    }

    @Test fun playerOverlaysContentAndNotesDetachesWithoutResettingSession() {
        val doc = repository.createDocument("A floating player", "One sentence. Another sentence. A final sentence.", "Text")
        ownedDocuments.add(doc.id)
        launch().use { scenario ->
            awaitTab("Home")
            scenario.onActivity {
                PlaybackStateStore.activeDocumentId = doc.id; PlaybackStateStore.documentTitle = doc.title
                PlaybackStateStore.chunkCount = doc.chunkCount; PlaybackStateStore.currentIndex = 1
                PlaybackStateStore.isPlaying = false
            }
            compose.onNodeWithTag("live_playback_floater").assertExists()
            val page = compose.onNodeWithTag("home_pager").fetchSemanticsNode().boundsInRoot
            val player = compose.onNodeWithTag("live_playback_floater").fetchSemanticsNode().boundsInRoot
            assertTrue("Content must extend behind the floating controls", page.bottom > player.bottom)
            capture("home-floating-player")
            tab("Library").performClick(); awaitTab("Library")
            val expanded = compose.onNodeWithTag("live_playback_floater").fetchSemanticsNode().boundsInRoot
            val raisedFab = compose.onNodeWithText("Add", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            compose.onNodeWithTag("floater_collapse").performClick()
            val compact = compose.onNodeWithTag("live_playback_floater").fetchSemanticsNode().boundsInRoot
            assertEquals(expanded.left, compact.left, 1f)
            assertEquals(expanded.top, compact.top, 1f)
            assertTrue(compact.width < expanded.width / 2)
            assertTrue(compose.onNodeWithText("Add", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top > raisedFab.top + 40)
            assertEquals(doc.id, PlaybackStateStore.activeDocumentId)
            capture("followup-player-collapsed")
            scenario.recreate(); awaitTab("Library")
            compose.onNodeWithTag("floater_expand").performClick()
            assertEquals(expanded.width, compose.onNodeWithTag("live_playback_floater").fetchSemanticsNode().boundsInRoot.width, 1f)
            capture("followup-player-expanded")
            compose.mainClock.autoAdvance = false
            tab("Notes").performClick()
            compose.mainClock.advanceTimeBy(240)
            capture("followup-navigation-separating")
            compose.mainClock.advanceTimeBy(600)
            compose.mainClock.autoAdvance = true
            awaitTab("Notes")
            compose.onNodeWithTag("live_playback_floater").assertDoesNotExist()
            compose.onNodeWithContentDescription("Write note").assertExists()
            val main = compose.onNodeWithTag("navigation_main_surface", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val notes = compose.onNodeWithTag("navigation_notes_surface", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(notes.left > main.right)
            assertEquals(doc.id, PlaybackStateStore.activeDocumentId)
            assertEquals(1, PlaybackStateStore.currentIndex)
            capture("notes-detached-navigation")
            tab("Home").performClick(); awaitTab("Home")
            compose.onNodeWithTag("live_playback_floater").assertExists()
        }
    }
}

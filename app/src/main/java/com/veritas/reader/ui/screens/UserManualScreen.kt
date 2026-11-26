package com.veritas.reader.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PictureInPicture
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material.icons.outlined.ViewStream
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.reader.VernDialog as Dialog
import androidx.compose.ui.window.DialogProperties
import com.veritas.reader.R
import com.veritas.reader.VeritasPackStyle

data class ManualChapter(
    val title: String,
    val icon: ImageVector,
    val subtitle: String,
    val content: String,
    val ctaText: String? = null,
    val ctaAction: String? = null,
    val flowSteps: List<String> = emptyList(),
    val imageResId: Int? = null,
    val tags: List<String> = emptyList()
)

data class ManualSection(
    val title: String,
    val icon: ImageVector,
    val description: String,
    val chapters: List<ManualChapter>
)

internal fun vernManualSections(): List<ManualSection> {
    return run {
        listOf(
            ManualSection(
                title = "Home & Navigation",
                icon = Icons.Outlined.Home,
                description = "4-tab navigation, daily goals, activity charts, and quick quests",
                chapters = listOf(
                    ManualChapter(
                        title = "About Vern",
                        icon = Icons.Outlined.Info,
                        subtitle = "Origin, philosophy, and privacy-first reading",
                        content = "Vern (root word Vernehmen — German: to hear/perceive) combines synchronized audio narration, custom voices, study cards and local reading insights. Core reading and downloaded neural voices work offline. Optional online features contact your chosen provider; Google ML Kit can collect SDK diagnostics. See the privacy policy for details.",
                        ctaText = "Go to Settings",
                        ctaAction = "settings_hub",
                        flowSteps = listOf(
                            "Learn with synchronized text and audio",
                            "Keep your library and reading statistics locally; optional online features and SDK diagnostics are explained in the privacy policy",
                            "Open Settings → Help to find the User manual, Tutorial, and About screens"
                        ),
                        imageResId = R.drawable.manual_settings_hub,
                        tags = listOf("vern", "about", "origin", "philosophy", "privacy", "vernehmen", "vernemen")
                    ),
                    ManualChapter(
                        title = "Home & First Chapter",
                        icon = Icons.Outlined.Dashboard,
                        subtitle = "Resume reading, set a goal, or take the tour",
                        content = "Home shows recent books, reading activity, and a Continue reading card when a book is in progress. First Chapter introduces importing, narration speed, bookmarks, and notes.",
                        ctaText = "Go to Settings",
                        ctaAction = "settings_hub",
                        flowSteps = listOf(
                            "Tap Continue to pick up where you left off",
                            "Tap Set a daily reading goal or open Reading Insights",
                            "Open First Chapter when it appears and tap Take a tour through Vern"
                        ),
                        imageResId = R.drawable.manual_first_chapter,
                        tags = listOf("home", "dashboard", "quest", "goal", "streak", "continue reading", "hero", "getting started walkthrough", "resume where i left off")
                    ),
                    ManualChapter(
                        title = "Four Main Workspaces",
                        icon = Icons.Outlined.ViewStream,
                        subtitle = "Home, Library, Study, and Notes Hub",
                        content = "Vern is organized into 4 primary tabs: 'Home' for daily reading momentum, 'Library' for your document catalog and public classics, 'Study' for flashcards and reading insights, and 'Notes' for voice memos, web link bookmarks, and reflections. Open app Settings anytime from the top bar.",
                        ctaText = "Explore Settings",
                        ctaAction = "settings_hub",
                        flowSteps = listOf(
                            "Use the bottom bar to switch between Home, Library, Study, and Notes",
                            "Enjoy fluid animated transitions between all four tabs",
                            "Access Settings and tools via the top header bar"
                        ),
                        imageResId = R.drawable.manual_home_dashboard,
                        tags = listOf("tabs", "navigation", "workspaces", "home", "library", "study", "notes", "settings hub")
                    ),
                    ManualChapter(
                        title = "Adaptive Window & Foldable Layouts",
                        icon = Icons.Outlined.ViewCarousel,
                        subtitle = "Tablets, foldables, rotation, and multi-window",
                        content = "Vern adapts seamlessly across phones, foldables, tablets, and landscape orientations. On wider screens, Notes arranges into adaptive masonry columns, Study displays side-by-side study decks, and dialogs avoid folding hinges automatically. Rotation and layout transitions preserve your active reading position, drafts, and quiz progress.",
                        ctaText = "Explore Settings",
                        ctaAction = "settings_hub",
                        flowSteps = listOf(
                            "Rotate your phone or open your foldable to landscape mode",
                            "Enjoy side-by-side split panels and expanded reading margins",
                            "Use compact two-column menus on shorter window heights"
                        ),
                        tags = listOf("adaptive", "tablet", "foldable", "landscape", "rotation", "large screen", "split pane", "multi-window")
                    )
                )
            ),
            ManualSection(
                title = "Library & Files",
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                description = "Manage your books, documents, and web imports",
                chapters = listOf(
                    ManualChapter(
                        title = "My Library",
                        icon = Icons.Outlined.CollectionsBookmark,
                        subtitle = "Find and sort saved readings",
                        content = "Open Added to find saved readings. Use the filter chips to narrow the shelf, Sort to change the order, the magnifying glass to search, or the layout icon to switch views.",
                        ctaText = "Open My Library",
                        ctaAction = "library",
                        flowSteps = listOf(
                            "Open My Library and select Added",
                            "Tap a filter chip or use Search",
                            "Tap a book to open it or use its three-dot menu"
                        ),
                        imageResId = R.drawable.manual_library_main,
                        tags = listOf("dashboard", "library", "books", "documents", "articles", "lists", "shelf", "who moved my cheese", "filter", "find my books", "change library layout", "sort by title")
                    ),
                    ManualChapter(
                        title = "Document Actions & Metadata",
                        icon = Icons.Outlined.MoreVert,
                        subtitle = "Edit document titles, authors, and options",
                        content = "Use the three-dot menu on a book card to rename it, organize it, view details or save it to your phone. Choose 'Save to phone' and select a destination in Android's file picker. The copy remains available after uninstalling Vern. Available originals keep their format; catalog books and extracted text are saved as TXT.",
                        ctaText = "Document Options Tip",
                        ctaAction = "library_options",
                        flowSteps = listOf(
                            "Find the document in your Library list",
                            "Tap the three-dot options menu icon",
                            "Choose an action, or Save to phone to keep an external copy"
                        ),
                        imageResId = R.drawable.manual_library_item_menu,
                        tags = listOf("rename", "author", "metadata", "delete", "options", "edit", "tags", "categories")
                    ),
                    ManualChapter(
                        title = "Batch Organization & Import Floater",
                        icon = Icons.Outlined.Checklist,
                        subtitle = "Manage multiple documents and monitor imports",
                        content = "Organize a large library efficiently using batch mode. Long-press any book card to activate multi-select mode. When importing files or downloading books, a movable circular floater shows live progress without blocking your screen.",
                        ctaText = "Bulk Edit Tip",
                        ctaAction = "bulk_edit",
                        flowSteps = listOf(
                            "Long-press any document card in the Library to enter multi-select",
                            "Select multiple items to categorize or delete in bulk",
                            "Drag the import progress floater to either edge of the screen as files load"
                        ),
                        tags = listOf("batch", "bulk", "multi-select", "select all", "delete multiple", "organize", "import floater")
                    ),
                    ManualChapter(
                        title = "Import Files, Text & Web Articles",
                        icon = Icons.Outlined.AddCircleOutline,
                        subtitle = "Add a document, article, or scanned text",
                        content = "In My Library, tap Add. Choose File browser for local files, Browse phone folders for Android’s file picker, Paste text for copied content, From web for a link, or Scan / OCR for a photo.",
                        ctaText = "Open File Browser",
                        ctaAction = "file_browser",
                        flowSteps = listOf(
                            "Tap Add in My Library",
                            "Choose an import method",
                            "Follow the prompt and open the saved reading from Added"
                        ),
                        imageResId = R.drawable.manual_add_sheet,
                        tags = listOf("add", "import", "plus", "url", "web", "scrape", "link", "new", "epub", "pdf", "docx", "pptx", "txt", "add a book", "open a file from downloads", "copy and paste a document", "read a website article", "turn a photo into text")
                    ),
                    ManualChapter(
                        title = "Integrated File Browser",
                        icon = Icons.Outlined.Folder,
                        subtitle = "Scan and import device storage files",
                        content = "Browse your device's directories locally with the integrated file navigator. It automatically highlights and filters compatible document formats such as EPUB, PDF, DOCX, PPTX, and TXT, making imports quick and reliable.",
                        ctaText = "Start File Import",
                        ctaAction = "file_browser",
                        flowSteps = listOf(
                            "Choose 'Local File Browser' from the '+' sheet",
                            "Navigate through folders to find your documents",
                            "Tap a file to copy and register it in the Library"
                        ),
                        imageResId = R.drawable.manual_file_browser,
                        tags = listOf("file browser", "storage", "directory", "folder", "epub", "pdf", "docx", "txt", "import")
                    ),
                    ManualChapter(
                        title = "PDF & Import Tools",
                        icon = Icons.Outlined.Description,
                        subtitle = "Choose an extraction mode or fix garbled text",
                        content = "In Settings → PDF and import tools, choose an extraction mode, page range, and cleanup options. Try Prefer OCR when text is poor for weak text layers, or Force OCR for scans. Use Text encoding when an imported text file shows garbled characters.",
                        ctaText = "Configure PDF Import",
                        ctaAction = "pdf_tools",
                        flowSteps = listOf(
                            "Open Settings → PDF and import tools",
                            "Choose Extraction mode or Text encoding",
                            "Tap Open file browser to select a file"
                        ),
                        imageResId = R.drawable.manual_import_settings,
                        tags = listOf("pdf", "ocr", "extraction", "encoding", "header", "footer", "cleanup", "page numbers", "pdf is blank", "scanned pdf has no text", "garbled characters when importing")
                    ),
                    ManualChapter(
                        title = "Classics & Book Explorer",
                        icon = Icons.Outlined.Book,
                        subtitle = "Browse the catalog or open a book website",
                        content = "The Classics tab in My Library shows a featured pick and books by category. Book Explorer opens external book websites. Downloading from those websites requires a connection.",
                        ctaText = "Browse Classics",
                        ctaAction = "classics_catalog",
                        flowSteps = listOf(
                            "Open My Library and select Classics",
                            "Tap Add to library or open the featured pick to preview it",
                            "Tap Book Explorer to choose an external website"
                        ),
                        imageResId = R.drawable.manual_classics_catalog,
                        tags = listOf("classics", "public domain", "gutenberg", "standard ebooks", "ocean of pdf", "open library", "manybooks", "download", "free books", "find free books", "public domain books", "project gutenberg")
                    )
                )
            ),
            ManualSection(
                title = "Reader & Display",
                icon = Icons.Outlined.AutoStories,
                description = "Reflowed text, bionic reading, theme packs, and PDF zoom",
                chapters = listOf(
                    ManualChapter(
                        title = "Reader Views & Navigation",
                        icon = Icons.AutoMirrored.Outlined.Article,
                        subtitle = "Switch between Text, Listen, and Original where available",
                        content = "Text opens Vern’s reader. Listen opens the full player. For supported documents, Original opens the source layout. The search and contents icons help you navigate the current reading.",
                        ctaText = "Select Text Tip",
                        ctaAction = "text_selection",
                        flowSteps = listOf(
                            "Open a reading and choose Text, Listen, or Original",
                            "Tap the magnifying glass to find text in the document",
                            "Tap the contents icon to filter the outline or jump to a page where available"
                        ),
                        imageResId = R.drawable.manual_reader_screen,
                        tags = listOf("reflow", "text mode", "canvas", "paragraphs", "font size", "margin", "padding", "swipe", "pages", "find a chapter", "table of contents", "skip to a page", "search inside a book", "find a word in my book")
                    ),
                    ManualChapter(
                        title = "Bionic Reading Mode",
                        icon = Icons.Outlined.Bolt,
                        subtitle = "Emphasize the first letters of each word",
                        content = "Turn on Bionic reading in Settings → Accessibility to emphasize the first letters of each word.",
                        ctaText = "Reader Settings",
                        ctaAction = "reader_settings",
                        flowSteps = listOf(
                            "Tap the typography gear icon in the reader bar",
                            "Toggle 'Bionic Reading' ON",
                            "Experience fast, effortless eye tracking across paragraphs"
                        ),
                        tags = listOf("bionic", "speed reading", "fixation", "eye tracking", "font", "focus")
                    ),
                    ManualChapter(
                        title = "RSVP Speed Reader",
                        icon = Icons.Outlined.Info,
                        subtitle = "Read one word at a time",
                        content = "RSVP Speed Reader displays one word at a time. Adjust the words-per-minute slider and turn on Silent visual RSVP to read without audio.",
                        flowSteps = listOf(
                            "Open Reader tools → RSVP Speed Reader",
                            "Set your pace and tap Play"
                        ),
                        imageResId = R.drawable.manual_rsvp,
                        tags = listOf("words flash one at a time", "speed reading", "words per minute")
                    ),
                    ManualChapter(
                        title = "Original PDF Layout Mode",
                        icon = Icons.Outlined.PictureInPicture,
                        subtitle = "Read fixed-page PDF documents with zoom",
                        content = "Switch to Original Mode to view documents in their native layout. Ideal for textbooks, multi-column research papers, and books with charts. Pinch to zoom, pan freely, and tap sentences to trigger synchronized speech narration.",
                        ctaText = "PDF Tools Settings",
                        ctaAction = "pdf_tools",
                        flowSteps = listOf(
                            "Open a PDF document in the reader",
                            "Tap 'ORIGINAL' tab in the top bar",
                            "View pages with full original styling and zoom support"
                        ),
                        imageResId = R.drawable.manual_reader_original,
                        tags = listOf("pdf", "original", "fixed layout", "columns", "zoom", "pan", "textbook")
                    ),
                    ManualChapter(
                        title = "Edit Extracted Text",
                        icon = Icons.Outlined.Info,
                        subtitle = "Fix OCR errors or replace words",
                        content = "Use the search and replace controls to find a mistake or fix repeated text. Tap Save to keep your changes to Vern’s extracted text.",
                        flowSteps = listOf(
                            "Open a reading and choose Reader tools → Edit extracted text",
                            "Use search or replace, review the changes, and tap Save"
                        ),
                        tags = listOf("fix a typo in a book", "find and replace words", "correct extracted text", "edit ocr errors")
                    ),
                    ManualChapter(
                        title = "Theme Packs & Paired Colours",
                        icon = Icons.Outlined.Palette,
                        subtitle = "Liquid Glass, One UI, Material You, and Dark palettes",
                        content = "Open Display & theme to select a theme pack, a colour palette, and System, Light, or Dark appearance. Custom accent changes the colour used for buttons and highlights.",
                        ctaText = "Configure Appearance",
                        ctaAction = "reader_settings",
                        flowSteps = listOf(
                            "Go to Settings -> Display & theme",
                            "Select your favorite Theme Pack and Colour Theme",
                            "Enjoy a cohesive visual style with dedicated card surfaces and container tones"
                        ),
                        imageResId = R.drawable.manual_reader_settings,
                        tags = listOf("theme", "theme packs", "liquid glass", "one ui", "material you", "dark", "make the screen dark", "night mode", "dracula", "dracula light", "midnight light", "midnight dark", "one light", "github", "neon")
                    ),
                    ManualChapter(
                        title = "Reading Layout & Accessibility",
                        icon = Icons.Outlined.Info,
                        subtitle = "Change page movement, contrast, or motion",
                        content = "Display & theme offers Page swipe or Vertical scroll and separate Original PDF navigation. Accessibility offers Collapsible reader bars, Bionic reading, Reduce motion, Reduce transparency, and high-contrast presets.",
                        flowSteps = listOf(
                            "Open Settings → Display & theme to choose a layout or swipe effect",
                            "Open Settings → Accessibility for reading focus and visibility options"
                        ),
                        tags = listOf("scroll instead of swiping", "hide reader controls", "turn off animations", "make text easier to see", "high contrast")
                    ),
                    ManualChapter(
                        title = "Reader Tools",
                        icon = Icons.Outlined.Tune,
                        subtitle = "Find bookmarks, notes, timers, and study tools",
                        content = "Tap the three-dot menu in the reader. Available actions depend on the reading. In Text view you can change Reading layout, jump to a page, or open Bookmarks and Document notes.",
                        ctaText = "Reader Options Tip",
                        ctaAction = "reader_tools",
                        flowSteps = listOf(
                            "Open a reading and tap the three-dot menu",
                            "Choose a tool under Read, Notes and bookmarks, or Study"
                        ),
                        imageResId = R.drawable.manual_reader_tools_menu,
                        tags = listOf("bookmark", "search text", "export notes", "markdown", "reset progress", "three dot menu", "reader overflow", "find my bookmarks")
                    ),
                    ManualChapter(
                        title = "Translation Handoff",
                        icon = Icons.Outlined.Translate,
                        subtitle = "Send text to a translator and listen to the result",
                        content = "Translation handoff opens an external translator or assistant with text from your reading. You can also listen to the original sentence or paste a translation into Vern and have it read aloud.",
                        flowSteps = listOf(
                            "Open the reader menu and choose Translation handoff",
                            "Choose a target language and a translator or assistant",
                            "Paste the result into Translated Text and tap Read Out Translation to listen"
                        ),
                        imageResId = R.drawable.manual_translation_handoff,
                        tags = listOf("translate", "translation", "language", "multilingual", "read aloud", "translator")
                    )
                )
            ),
            ManualSection(
                title = "Audio & Narration",
                icon = Icons.Outlined.RecordVoiceOver,
                description = "Vern Studio & Lite offline neural voices, narration studio, and sleep timer",
                chapters = listOf(
                    ManualChapter(
                        title = "Listen & Playback Controls",
                        icon = Icons.Outlined.GraphicEq,
                        subtitle = "Play, skip, and adjust narration speed",
                        content = "Tap Listen to open the full player. The speed, Voice, and Timer controls are below the playback controls. In Text view, expand the compact player using its chevron to adjust settings.",
                        ctaText = "Configure Sleep Timer",
                        ctaAction = "sleep_timer",
                        flowSteps = listOf(
                            "Open a reading and tap Listen",
                            "Tap Play or move through the reading with the progress slider",
                            "Tap the speed control to change the narration pace"
                        ),
                        imageResId = R.drawable.manual_audio_mode,
                        tags = listOf("audio", "tts", "play", "pause", "speed", "playback", "listen", "narrate", "wavy seekbar", "transport", "read out loud", "make the voice slower", "make narration faster", "speed up audio")
                    ),
                    ManualChapter(
                        title = "Voice Studio & Offline Neural Voices",
                        icon = Icons.Outlined.RecordVoiceOver,
                        subtitle = "Vern Studio (Kokoro) & Vern Lite (Piper)",
                        content = "Use your phone’s text-to-speech or choose a Vern voice. In Voice and language, expand Vern voice models to download a model for offline reading. Use Sample to hear the active voice.",
                        ctaText = "Manage Voice Settings",
                        ctaAction = "voice_studio",
                        flowSteps = listOf(
                            "Open Settings -> Voice Studio",
                            "Select Vern Studio or Vern Lite",
                            "Download voice models and audition voice presets"
                        ),
                        imageResId = R.drawable.manual_voice_language,
                        tags = listOf("voice", "engine", "tts", "vern studio", "kokoro", "vern lite", "piper", "offline", "neural voice")
                    ),
                    ManualChapter(
                        title = "Personal Cloud Voices (BYOK)",
                        icon = Icons.Outlined.RecordVoiceOver,
                        subtitle = "OpenAI, ElevenLabs, Gemini & Custom Endpoints",
                        content = "Choose Connect my cloud voice account in Voice and language and enter the settings required by your provider. Reading and previews send text to that provider, and a preview may incur a charge. Vern keeps the API key encrypted on this device and excludes it from backups. Keep cloud audio for repeat reading is optional.",
                        ctaText = "Configure Cloud Voices",
                        ctaAction = "voice_studio",
                        flowSteps = listOf(
                            "Open Settings -> Voice Studio -> Cloud Voices",
                            "Enter your API key (OpenAI, ElevenLabs, Gemini, or custom)",
                            "Audition available voices and start streaming speech"
                        ),
                        imageResId = R.drawable.manual_cloud_voice,
                        tags = listOf("cloud voice", "openai", "elevenlabs", "gemini", "tts", "byok", "api key", "custom endpoint")
                    ),
                    ManualChapter(
                        title = "Speech Syntax & Pronunciation Guide",
                        icon = Icons.Outlined.Spellcheck,
                        subtitle = "Choose what Vern skips or says aloud",
                        content = "Open Pronunciation rules to control how Vern reads words, abbreviations, and recurring patterns. Start with an example or create a Text, Pattern, or Regex rule.",
                        ctaText = "Configure Pronunciation",
                        ctaAction = "pronunciation",
                        flowSteps = listOf(
                            "Open Settings -> Speech Edits",
                            "Choose Text, Pattern, or Regex to specify what to match",
                            "Choose Skip or Say instead and save the rule"
                        ),
                        tags = listOf("pronunciation", "phonetics", "acronyms", "syntax", "pause", "cadence", "speech edits", "markup")
                    ),
                    ManualChapter(
                        title = "Full-Cast Narration Studio",
                        icon = Icons.Outlined.Tune,
                        subtitle = "Multi-voice character speech and pitch control",
                        content = "Assign distinct TTS voices, pitch multipliers, and speeds to different book characters under Narration Studio. Smart dialogue analysis automatically detects character quotes and speaks them in their assigned voice.",
                        ctaText = "Narration Controls",
                        ctaAction = "narration_studio",
                        flowSteps = listOf(
                            "Go to Settings -> Narration Studio",
                            "Enable Full-cast multi-voice mode",
                            "Create character profiles and customize pitch/speed"
                        ),
                        tags = listOf("full-cast", "character", "multi-voice", "pitch", "narration", "dialogue", "speech")
                    ),
                    ManualChapter(
                        title = "Pronunciation Correction Rules",
                        icon = Icons.Outlined.Spellcheck,
                        subtitle = "Define phonetics for complex words",
                        content = "Fix incorrect TTS pronunciations by setting up custom replacement rules. If your device's voice engine mispronounces character names, medical acronyms, or specific technical jargon, add a rule translating the spelling to phonetics.",
                        ctaText = "Configure Pronunciation",
                        ctaAction = "pronunciation",
                        flowSteps = listOf(
                            "Select Speech Edits in Settings",
                            "Tap '+' to add a word replacement pair",
                            "Write target word and its phonetic correction"
                        ),
                        imageResId = R.drawable.manual_pronunciation_rules,
                        tags = listOf("pronunciation", "phonetics", "acronyms", "mispronounce", "dictionary", "replace word", "speech edits")
                    ),
                    ManualChapter(
                        title = "Background Playback & Battery",
                        icon = Icons.Outlined.Info,
                        subtitle = "Keep listening when the screen locks",
                        content = "If Android stops playback when the screen locks, use the Unrestricted Battery Playback prompt or open Vern’s App info → Battery and choose Unrestricted if available.",
                        flowSteps = listOf(
                            "Tap Turn On Unrestricted when prompted",
                            "Choose Allow if Android asks"
                        ),
                        imageResId = R.drawable.manual_battery_playback,
                        tags = listOf("audio stops when i lock my phone", "audio stop when i lock my phone", "read with screen off", "playback pauses in background")
                    ),
                    ManualChapter(
                        title = "Sleep Timer Settings",
                        icon = Icons.Outlined.Timer,
                        subtitle = "Automatically pause playback",
                        content = "Prevent narration from running all night. The Sleep Timer allows choosing a countdown duration (from 5 minutes to 1 hour) or stopping speech at the end of the active section. Choose whether it should pause or stop playback upon completion.",
                        ctaText = "Configure Sleep Timer",
                        ctaAction = "sleep_timer",
                        flowSteps = listOf(
                            "Start speech playback inside the reader",
                            "Tap clock timer icon in the audio bar",
                            "Select duration limit and click 'Start'"
                        ),
                        tags = listOf("sleep timer", "timer", "countdown", "night", "auto pause", "stop audio")
                    )
                )
            ),
            ManualSection(
                title = "Study & Progress",
                icon = Icons.Outlined.AutoAwesome,
                description = "Track reading history, streak insights, study cards, and notes",
                chapters = listOf(
                    ManualChapter(
                        title = "Dictionary & Vocabulary",
                        icon = Icons.AutoMirrored.Outlined.MenuBook,
                        subtitle = "Find definitions with the optional offline English pack",
                        content = "Open Dictionary in Study Hub and choose Offline or Online. Browse or filter the offline word list, then open an entry and tap Save to vocabs to keep it.",
                        ctaText = "Open Study Hub",
                        ctaAction = "study_general",
                        flowSteps = listOf(
                            "Open Study Hub → Dictionary",
                            "Open the three-dot menu and choose Offline dictionary to manage the English pack",
                            "Open a word and tap Save to vocabs"
                        ),
                        imageResId = R.drawable.manual_dictionary,
                        tags = listOf("dictionary", "wordnet", "offline dictionary", "definitions", "vocab", "vocabulary", "synonyms", "examples", "what does this word mean", "look up a word without internet", "save a definition")
                    ),
                    ManualChapter(
                        title = "Study General & Vocabulary",
                        icon = Icons.Outlined.BookmarkBorder,
                        subtitle = "Central list of highlights and words",
                        content = "Manage vocabulary and highlights in the Study Hub. The general tab organizes all saved words and highlights in order. Tapping any item takes you back to the exact page and chapter where you saved it.",
                        ctaText = "Open Study Hub",
                        ctaAction = "study_general",
                        flowSteps = listOf(
                            "Open Study screen from navigation bar",
                            "Browse vocabulary list or text highlights",
                            "Tap on a highlight to view it in context"
                        ),
                        imageResId = R.drawable.manual_study_general,
                        tags = listOf("study", "vocabulary", "highlights", "notes", "bookmarks", "words", "saved")
                    ),
                    ManualChapter(
                        title = "Reading History & Daily Goals",
                        icon = Icons.Outlined.History,
                        subtitle = "Review sessions, set a goal, or manage the reading timer",
                        content = "Open Reading Insights → History to review activity, set a daily goal, add a manual session, or export sessions as CSV when available. Automatic reading tracks visible reader time after interaction.",
                        ctaText = "View Reading History",
                        ctaAction = "history",
                        flowSteps = listOf(
                            "Open Reading Insights and select History",
                            "Choose a Daily goal or adjust the Reading timer",
                            "To time a particular reading, open Books and tap Start timer on that book"
                        ),
                        imageResId = R.drawable.manual_study_history,
                        tags = listOf("history", "recent", "log", "reading session", "timeline", "resume", "how long did i read", "set reading timer", "track minutes read", "export reading sessions")
                    ),
                    ManualChapter(
                        title = "Reading Insights & Streaks",
                        icon = Icons.Outlined.Insights,
                        subtitle = "Check weekly statistics and streaks",
                        content = "Review reading statistics in the Insights tab. This panel tracks daily and weekly reading time, calculates average reading speed, and counts consecutive reading days to help build and maintain a consistent habit.",
                        ctaText = "Manage Collections",
                        ctaAction = "reading_lists",
                        flowSteps = listOf(
                            "Select Insights from menu or drawer",
                            "Examine reading duration bar chart",
                            "Check active reading streak and weekly average"
                        ),
                        imageResId = R.drawable.manual_reading_insights,
                        tags = listOf("insights", "stats", "analytics", "streak", "daily goal", "chart", "speed", "time")
                    ),
                    ManualChapter(
                        title = "Spaced Repetition Flashcards",
                        icon = Icons.Outlined.Psychology,
                        subtitle = "Anki-style vocabulary & fact reviews",
                        content = "Review vocabulary and key facts using flashcards in the Study Hub. The deck automatically compiles cards and schedules them using the SuperMemo-2 (SM-2) algorithm. Tap 'Review Now' to launch interactive flipping cards and rate memory recall.",
                        ctaText = "Review Flashcards",
                        ctaAction = "study_flashcards",
                        flowSteps = listOf(
                            "Navigate to Study -> Flashcards tab",
                            "Tap 'Review Now' to start a session",
                            "Rate your recall to update the card's interval"
                        ),
                        tags = listOf("flashcards", "anki", "sm2", "spaced repetition", "quiz", "memorize", "card review", "deck")
                    ),
                    ManualChapter(
                        title = "AI Study Studio & Ask AI",
                        icon = Icons.Outlined.Info,
                        subtitle = "Study a passage or send it to another app",
                        content = "Choose a study focus and a prompt for flashcards, a practice quiz, or a summary. In-app generation requires a provider API key. You can also use an external assistant and import supported cards or quizzes from the clipboard.",
                        flowSteps = listOf(
                            "Open Study Hub → Choose a book to study, or Reader tools → AI Study tools",
                            "Choose a focus and a prompt or assistant",
                            "Copy supported cards or a quiz back and tap Check clipboard for cards or a quiz"
                        ),
                        imageResId = R.drawable.manual_ai_study,
                        tags = listOf("summarize a chapter", "make flashcards from a book", "send a passage to chatgpt")
                    ),
                    ManualChapter(
                        title = "Quiz Lab & Performance",
                        icon = Icons.Outlined.Psychology,
                        subtitle = "Retention metrics, mastery scores & practice exams",
                        content = "Test your reading retention with Quiz Lab. Track completion rates, mastered concepts, score distributions, and create new practice quizzes to reinforce critical takeaways.",
                        ctaText = "Open Study Hub",
                        ctaAction = "study_general",
                        flowSteps = listOf(
                            "Open Study Hub -> Quizzes or Quiz Lab",
                            "Select a book quiz to begin answering questions",
                            "Review detailed score breakdown and retention gauge"
                        ),
                        tags = listOf("quiz", "quiz lab", "exam", "score", "retention", "mastery", "practice")
                    ),
                    ManualChapter(
                        title = "Notes & Voice Memos Studio",
                        icon = Icons.Outlined.EditNote,
                        subtitle = "Pinned cards, real-time waveform & markdown",
                        content = "Organize document highlights and record audio memos with live sound waveforms in the Notes & Annotations workspace. Pin your most critical notes to the top of your deck.",
                        ctaText = "Go to Notes",
                        ctaAction = "notes_tab",
                        flowSteps = listOf(
                            "Tap 'Notes' on the bottom navigation bar",
                            "Review pinned cards or listen to recorded voice notes",
                            "Tap the floating '+ Note' button to jot down insights"
                        ),
                        imageResId = R.drawable.manual_notes_main,
                        tags = listOf("notes", "voice memo", "waveform", "annotations", "pinned", "audio recording")
                    ),
                    ManualChapter(
                        title = "Rich Note Editor & Audio Reflections",
                        icon = Icons.Outlined.GraphicEq,
                        subtitle = "Waveforms, hashtags & web card embeds",
                        content = "Create multimedia notes with text, photos, files, voice memos and web links. From another app, use Share and choose Share to Notes to import text and attachments. The imported note is saved before it opens, even if you make no edits. Sharing a note outward includes accessible attachments and keeps device file paths out of the text.",
                        ctaText = "Go to Notes",
                        ctaAction = "notes_tab",
                        flowSteps = listOf(
                            "Tap '+ Note' or open any existing note in your list",
                            "Record voice memos or type rich markdown reflections",
                            "Share text or attachments from another app to Share to Notes"
                        ),
                        tags = listOf("note editor", "voice memo", "waveform", "tags", "link preview", "bible.com", "audio notes")
                    )
                )
            ),
            ManualSection(
                title = "Data, Storage & Backup",
                icon = Icons.Outlined.Settings,
                description = "Storage manager, sync center, backups, and interactive selection tools",
                chapters = listOf(
                    ManualChapter(
                        title = "Storage Manager & Smart Cleanup",
                        icon = Icons.Outlined.Storage,
                        subtitle = "Inspect file sizes and clear cached TTS audio",
                        content = "Monitor disk usage from Settings → Storage. View storage consumed by your library books, extracted PDFs, downloaded voice models, and generated speech cache, with one-tap smart cache clearing.",
                        ctaText = "Open Storage Manager",
                        ctaAction = "storage_manager",
                        flowSteps = listOf(
                            "Go to Settings → Storage",
                            "Inspect breakdown of documents and voice models",
                            "Tap Clear temporary cache to free up space"
                        ),
                        imageResId = R.drawable.manual_storage,
                        tags = listOf("storage", "cache", "cleanup", "disk", "free space", "delete voice", "audio cache", "app taking up space", "clear cache")
                    ),
                    ManualChapter(
                        title = "Select Text, Bookmark & Annotate",
                        icon = Icons.Outlined.ContentCut,
                        subtitle = "Use the floating menu for actions on a passage",
                        content = "Press and hold text in the reader and drag the selection handles. The floating menu can offer Bookmark, Note, and Search definitions. Open its three-dot menu for more actions where available.",
                        ctaText = "Text Selection Tip",
                        ctaAction = "text_selection",
                        flowSteps = listOf(
                            "Press and hold a word and drag the handles",
                            "Tap Bookmark to save the passage or Note to write about it",
                            "Tap Search definitions to look up a word"
                        ),
                        imageResId = R.drawable.manual_text_selection_bar,
                        tags = listOf("copy", "select text", "highlight", "read from here", "clipboard", "selection bar", "highlight a passage", "save a quote for later", "write a note on a sentence", "copy text")
                    ),
                    ManualChapter(
                        title = "Portable Backup & Restore",
                        icon = Icons.Outlined.Sync,
                        subtitle = "Move your data between devices without an account",
                        content = "Export a portable backup from the Sync Center, copy or share it to your other device, then import it there. The portable archive includes managed Notes attachments. Import merges with local data and keeps existing notes on an ID conflict. Save a backup of the receiving device first. Transfers are manual; no cloud account is needed.",
                        ctaText = "Manage Sync Center",
                        ctaAction = "sync_center",
                        flowSteps = listOf(
                            "Open Settings → Sync & Backup Center",
                            "Choose the JSON backup for text and settings or the ZIP backup to include media",
                            "On the receiving device, back up first, then tap Import Sync / Backup Pack"
                        ),
                        imageResId = R.drawable.manual_sync_center,
                        tags = listOf("sync", "portable", "transfer", "backup", "restore", "move my books to a new phone", "transfer library to another device", "export notes with attachments")
                    ),
                    ManualChapter(
                        title = "Backup & Recovery",
                        icon = Icons.Outlined.Backup,
                        subtitle = "Keep recent local backups and save a copy elsewhere",
                        content = "Automatic Weekly Backups keeps the four latest data backups on your device. Save a copy outside Vern if you want to keep it after uninstalling. For original files and media, export a full ZIP backup.",
                        ctaText = "Open Sync & Backup Center",
                        ctaAction = "sync_center",
                        flowSteps = listOf(
                            "Open Settings → Sync & Backup Center",
                            "Turn on Automatic Weekly Backups or tap Back up now",
                            "Tap Show saved backups to find your local copies"
                        ),
                        imageResId = R.drawable.manual_backup_restore,
                        tags = listOf("backup", "export database", "restore", "recovery", "data loss", "automatic weekly backups", "find saved backups", "back up before uninstalling")
                    ),
                    ManualChapter(
                        title = "About Vern & Community",
                        icon = Icons.Outlined.Info,
                        subtitle = "Version, developer contact, GitHub & social channels",
                        content = "Learn more about Vern, check for the latest releases, submit feedback or bug reports on GitHub, visit the official website, and join community channels on Telegram and X.",
                        ctaText = "Open About",
                        ctaAction = "about",
                        flowSteps = listOf(
                            "Go to Settings -> About",
                            "Check installed version build number",
                            "Tap social, website, and GitHub links to join the community"
                        ),
                        imageResId = R.drawable.manual_about_veritas,
                        tags = listOf("about", "version", "github", "website", "contact", "developer", "feedback", "social", "telegram")
                    )
                )
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManualDialog(
    onDismiss: () -> Unit,
    onNavigateToSetting: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val allSections = remember { vernManualSections() }
    val filteredSections = remember(searchQuery, allSections) { searchManual(allSections, searchQuery) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .background(VeritasPackStyle.backgroundBrush(MaterialTheme.colorScheme))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_back),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_user_manual_ea99fa3),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search Bar with Theme Pack Awareness
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_search_features_e_g_read_out_loud_dark_mode_pdf),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_search),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_clear_search),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = VeritasPackStyle.cardShape(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = VeritasPackStyle.surfaceAlpha()),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = VeritasPackStyle.surfaceAlpha()),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Sections List
                if (filteredSections.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_no_matching_manual_sections_found_try_searching_for_voice_dark_mo),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(filteredSections, key = { it.title }) { section ->
                            SectionCard(
                                section = section,
                                searchQuery = searchQuery,
                                onNavigateToSetting = onNavigateToSetting
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionCard(
    section: ManualSection,
    searchQuery: String,
    onNavigateToSetting: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            isExpanded = true
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        shape = VeritasPackStyle.cardShape(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = VeritasPackStyle.surfaceAlpha())
        ),
        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                val cardBorder = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = VeritasPackStyle.compactShape()
                        )
                        .then(Modifier.border(cardBorder, VeritasPackStyle.compactShape())),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = section.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = section.description,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_collapse) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_expand),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    section.chapters.forEach { chapter ->
                        androidx.compose.runtime.key(chapter.title) {
                            ChapterCard(chapter = chapter, onNavigateToSetting = onNavigateToSetting)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChapterCard(
    chapter: ManualChapter,
    onNavigateToSetting: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        shape = VeritasPackStyle.cardShape(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            // Title block
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = VeritasPackStyle.compactShape()
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = chapter.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = chapter.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = chapter.subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_collapse) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_expand),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Content
                    Text(
                        text = chapter.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                    // Workflow steps if present
                    if (chapter.flowSteps.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(VeritasPackStyle.cardShape())
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_workflow_steps),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            chapter.flowSteps.forEachIndexed { index, step ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.padding(vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "${index + 1}. ",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = step,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Screenshot image if present
                    if (chapter.imageResId != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Image(
                            painter = painterResource(id = chapter.imageResId),
                            contentDescription = "Screenshot of ${chapter.title}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(VeritasPackStyle.cardShape()),
                            contentScale = ContentScale.FillWidth
                        )
                    }

                    // Action button if present
                    if (chapter.ctaText != null && chapter.ctaAction != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { onNavigateToSetting(chapter.ctaAction) },
                            modifier = Modifier.align(Alignment.End),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = VeritasPackStyle.chipShape(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = chapter.ctaText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

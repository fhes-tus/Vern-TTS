package com.veritas.reader.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot

/**
 * Revamped multi-step onboarding pages.
 */
enum class OnboardingPage(
    val pageIndex: Int,
    val title: String,
    val subtitle: String,
    val spokenDescription: String
) {
    WELCOME_HERO(
        pageIndex = 0,
        title = "Read at the Speed of Thought",
        subtitle = "Transform your reading experience with neural narration, intelligent study handoffs, and distraction-free clarity.",
        spokenDescription = "Welcome to Vern. Your personalized sanctuary for reading, listening, and study."
    ),
    PERSONA_SELECTION(
        pageIndex = 1,
        title = "How do you read?",
        subtitle = "Vern adapts its layout, AI summaries, and pacing to your focus.",
        spokenDescription = "Choose how you read to tailor your voice speed, document view, and study tools."
    ),
    AI_SELECTION(
        pageIndex = 2,
        title = "Pair Your AI Study Partner",
        subtitle = "Choose your default assistant for instant study handoffs, summaries, quizzes, and vocabulary.",
        spokenDescription = "Pair your preferred AI partner for contextual summaries, quizzes, and active recall study decks."
    ),
    VOICE_AUDITION(
        pageIndex = 3,
        title = "Choose Your Voice",
        subtitle = "Experience voices crafted for effortless immersion.",
        spokenDescription = "Audition live voice presets to find your ideal narration rhythm."
    ),
    READY_CELEBRATION(
        pageIndex = 4,
        title = "Your Library Awaits",
        subtitle = "Enter your name to personalize your reading journey.",
        spokenDescription = "What should we call you? Enter your name so Vern can personalize your library and study greetings."
    );

    companion object {
        // Compatibility aliases for legacy step references
        val NAME_INPUT get() = READY_CELEBRATION
        val FEATURE_SHOWCASE get() = READY_CELEBRATION
    }
}

/**
 * Persona options inspired by modern onboarding rules.
 */
data class ReaderPersona(
    val id: String,
    val title: String,
    val subtitle: String,
    val defaultPresetId: String,
    val defaultAiFocus: String
)

val ReaderPersonas = listOf(
    ReaderPersona(
        id = "student",
        title = "Deep Study",
        subtitle = "Textbooks, research, flashcards & notes",
        defaultPresetId = "speed",
        defaultAiFocus = "flashcards"
    ),
    ReaderPersona(
        id = "professional",
        title = "Speed & Work",
        subtitle = "Fast audio, reports & instant summaries",
        defaultPresetId = "speed",
        defaultAiFocus = "summary"
    ),
    ReaderPersona(
        id = "book_lover",
        title = "Literature",
        subtitle = "Novels, calm pacing & immersive reading",
        defaultPresetId = "calm",
        defaultAiFocus = "deep_dive"
    ),
    ReaderPersona(
        id = "educator",
        title = "Audio First",
        subtitle = "Hands-free listening & fluid pronunciation",
        defaultPresetId = "expressive",
        defaultAiFocus = "deep_dive"
    )
)

/**
 * Voice audition presets with speed and pitch configurations.
 */
data class VoiceAuditionPreset(
    val id: String,
    val title: String,
    val description: String,
    val iconEmoji: String,
    val speed: Float,
    val pitch: Float,
    val sampleText: String
)

val VoiceAuditionPresets = listOf(
    VoiceAuditionPreset(
        id = "natural",
        title = "Aura",
        description = "Balanced, clear, and easy to follow for long reading sessions.",
        iconEmoji = "🎙️",
        speed = 1.0f,
        pitch = 1.0f,
        sampleText = "Knowledge begins with curiosity, and understanding grows with every sentence."
    ),
    VoiceAuditionPreset(
        id = "speed",
        title = "Atlas",
        description = "Accelerated pace tuned for high-speed information capture.",
        iconEmoji = "⏩",
        speed = 1.25f,
        pitch = 1.01f,
        sampleText = "Absorb articles, papers, and research in half the time without missing key details."
    ),
    VoiceAuditionPreset(
        id = "calm",
        title = "Nova",
        description = "Relaxed, soothing cadence for thoughtful literature and quiet focus.",
        iconEmoji = "☕",
        speed = 0.92f,
        pitch = 0.98f,
        sampleText = "Take a breath, slow down, and immerse yourself in the beauty of the written word."
    ),
    VoiceAuditionPreset(
        id = "expressive",
        title = "Sol",
        description = "Dynamic pitch and pacing for dialogue and narrative depth.",
        iconEmoji = "🎭",
        speed = 1.05f,
        pitch = 1.03f,
        sampleText = "Stories come alive when every character and nuance finds its true voice."
    )
)

/**
 * Primary reading interest options.
 */
data class ReadingInterestOption(
    val id: String,
    val label: String,
    val iconEmoji: String,
    val detail: String
)

val ReadingInterestOptions = listOf(
    ReadingInterestOption(
        id = "pdf",
        label = "PDF & Research",
        iconEmoji = "📄",
        detail = "Academic papers, reports, technical docs"
    ),
    ReadingInterestOption(
        id = "books",
        label = "Books & Novels",
        iconEmoji = "📚",
        detail = "EPUBs, fiction, biographies & literature"
    ),
    ReadingInterestOption(
        id = "web",
        label = "Web & Articles",
        iconEmoji = "🌐",
        detail = "Online journalism, essays & blogs"
    ),
    ReadingInterestOption(
        id = "study",
        label = "Study & Learning",
        iconEmoji = "🎓",
        detail = "Textbooks, flashcard decks & lecture notes"
    )
)

/**
 * AI Study Focus presets.
 */
data class AiStudyFocusOption(
    val id: String,
    val label: String,
    val iconEmoji: String
)

val AiStudyFocusOptions = listOf(
    AiStudyFocusOption("deep_dive", "Deep Explanations", "💡"),
    AiStudyFocusOption("summary", "Key Point Summaries", "📝"),
    AiStudyFocusOption("flashcards", "Flashcards & Quizzes", "🗂️"),
    AiStudyFocusOption("translation", "Spoken Translation", "🗣️")
)

// --------------------------------------------------------------------
// Contextual Spotlight & Tour steps (Preserved for in-app walkthrough)
// --------------------------------------------------------------------

enum class OnboardingStep(
    val key: String,
    val targetKey: String?,
    val title: String,
    val body: String
) {
    WELCOME(
        key = "welcome",
        targetKey = null,
        title = "Interactive App Tour",
        body = "Follow the highlights to discover your library, reading tools, study notes, and settings."
    ),
    NAME_INPUT(
        key = "name_input",
        targetKey = null,
        title = "What should we call you?",
        body = "Please enter your name so we can personalize your dashboard and messages."
    ),
    FAB_SPOTLIGHT(
        key = "fab",
        targetKey = "add_fab",
        title = "Quest 1: Add a Document",
        body = "This is the Add button. Tap it to import PDFs, EPUBs, Word files, text files, images, or web links."
    ),
    CHECKLIST_SPOTLIGHT(
        key = "checklist",
        targetKey = "quest_checklist",
        title = "Your Quest Checklist",
        body = "This floating checklist shows your active missions. Complete all quests to master the app!"
    ),
    CLASSICS_SPOTLIGHT(
        key = "classics",
        targetKey = "classics_shelves",
        title = "Classic Books Catalog",
        body = "Browse timeless books by genre, swipe through each shelf, and add a favourite to My Library. Your reading and listening stay close at hand."
    ),
    INSIGHTS_SPOTLIGHT(
        key = "insights",
        targetKey = "insights_trigger",
        title = "Reading Insights Dashboard",
        body = "This menu button opens your personalized dashboard. Tap Next to see your reading analytics."
    ),
    INSIGHTS_PAGE_SPOTLIGHT(
        key = "insights_page",
        targetKey = null,
        title = "Your Insights at a Glance",
        body = "Track daily reading streaks on the heatmap, see how your library splits by source, and watch where your listening time goes each month — all stored privately on your device."
    ),
    NOTES_TAB_SPOTLIGHT(
        key = "notes_tab",
        targetKey = null,
        title = "Notes & Voice Memos",
        body = "Here is your notes studio. Review all your document annotations, standalone notes, bookmarks, and audio recordings in one dedicated hub."
    ),
    STUDY_TAB_SPOTLIGHT(
        key = "study_tab",
        targetKey = null,
        title = "AI Study Hub & Flashcards",
        body = "Here is your study studio. Generate AI summaries, practice interactive flashcards, and review active recall quizzes across your library."
    ),
    SETTINGS_SPOTLIGHT(
        key = "settings",
        targetKey = null,
        title = "Settings & Customization",
        body = "Here is your settings hub. Tune voice engines, playback speeds, themes, cloud sync, backup tools, and pronunciation rules anytime."
    ),
    DOCUMENT_SPOTLIGHT(
        key = "document",
        targetKey = "document_card_0",
        title = "Open your Document",
        body = "Your readings appear here. Tap a card to open it and enter the reader screen."
    ),
    MODE_TOGGLE_SPOTLIGHT(
        key = "mode_toggle",
        targetKey = "reader_mode_toggle",
        title = "Layout Mode Switcher",
        body = "Toggle layouts dynamically: TEXT for clean reading, LISTEN for lyrics/sentence highlights, and ORIGINAL for PDF layouts."
    ),
    PLAYER_PANEL_SPOTLIGHT(
        key = "player_panel",
        targetKey = "player_panel_header",
        title = "Expandable Playback Studio",
        body = "Tap or drag this panel up to access speed, pitch, premium voice selections, and sleep timers."
    ),
    READER_TEXT_SPOTLIGHT(
        key = "reader_text",
        targetKey = "reader_text_view",
        title = "Highlight & Study Actions",
        body = "Long-press any sentence to bookmark it, add study notes, translate text, or consult the AI Assistant."
    ),
    CONGRATULATIONS(
        key = "congratulations",
        targetKey = null,
        title = "Tour Complete!",
        body = "Awesome! You have completed the guided tour. Now complete the remaining quests on your checklist to unlock the full app!"
    )
}

object OnboardingController {
    var activeStep by mutableStateOf<OnboardingStep?>(null)
    val componentBounds = mutableStateMapOf<String, Rect>()
    
    fun updateBounds(key: String, coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) {
            componentBounds[key] = coordinates.boundsInRoot()
        } else {
            componentBounds.remove(key)
        }
    }
}

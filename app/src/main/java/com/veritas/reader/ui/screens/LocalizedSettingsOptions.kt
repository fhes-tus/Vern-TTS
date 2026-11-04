package com.veritas.reader.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.veritas.reader.R

/** Translate fixed legacy catalogue labels only at display time; stored IDs and values stay intact. */
@Composable
internal fun localizedSettingsOption(label: String): String {
    val resource = when (label) {
        "System default" -> R.string.ui_system_default
        "System Default" -> R.string.settings_option_system_default
        "System default voice" -> R.string.ui_system_default_voice
        "Default language" -> R.string.settings_option_default_language
        "Network voice" -> R.string.settings_option_network_voice
        "Offline voice" -> R.string.settings_option_offline_voice
        "Balanced" -> R.string.settings_option_balanced
        "Study focus" -> R.string.settings_option_study_focus
        "Quick scan" -> R.string.settings_option_quick_scan
        "Storyteller" -> R.string.settings_option_storyteller
        "Lecture bright" -> R.string.settings_option_lecture_bright
        "Bedtime calm" -> R.string.settings_option_bedtime_calm
        "Natural everyday audio reading with standard timing." -> R.string.settings_option_natural_everyday_audio_reading_with_standard_timing
        "Deliberate pacing with grounded tone for deep comprehension." -> R.string.settings_option_deliberate_pacing_with_grounded_tone_for_deep_comprehension
        "Fast energetic review with crisp consonant clarity." -> R.string.settings_option_fast_energetic_review_with_crisp_consonant_clarity
        "Warm, chest-resonant narration for fiction and novels." -> R.string.settings_option_warm_chest_resonant_narration_for_fiction_and_novels
        "Confident, articulate delivery for academic and business text." -> R.string.settings_option_confident_articulate_delivery_for_academic_and_business_text
        "Slow, soothing, mellow tone for quiet night-time listening." -> R.string.settings_option_slow_soothing_mellow_tone_for_quiet_night_time_listening
        "Blank" -> R.string.settings_option_blank
        "Ruled Lines" -> R.string.settings_option_ruled_lines
        "Dot Matrix" -> R.string.settings_option_dot_matrix
        "Narrator" -> R.string.settings_option_narrator
        "Dialogue" -> R.string.settings_option_dialogue
        "Literal" -> R.string.settings_option_literal
        "Pattern" -> R.string.settings_option_pattern
        "Regex" -> R.string.settings_option_regex
        "Skip" -> R.string.ui_skip
        "On" -> R.string.settings_option_on
        "Off" -> R.string.ui_off
        "Open a reading first." -> R.string.settings_option_open_a_reading_first
        "Save a reading first." -> R.string.settings_option_save_a_reading_first
        "Add books to the queue first." -> R.string.settings_option_add_books_to_the_queue_first
        "Black" -> R.string.settings_option_black
        "Grey" -> R.string.settings_option_grey
        "White" -> R.string.settings_option_white
        "Red" -> R.string.settings_option_red
        "Orange" -> R.string.settings_option_orange
        "Yellow" -> R.string.settings_option_yellow
        "Green" -> R.string.settings_option_green
        "Cyan" -> R.string.settings_option_cyan
        "Blue" -> R.string.settings_option_blue
        "Purple" -> R.string.settings_option_purple
        "Colour shade" -> R.string.settings_option_colour_shade
        "Hue" -> R.string.settings_option_hue
        "More saturated" -> R.string.settings_option_more_saturated
        "Less saturated" -> R.string.settings_option_less_saturated
        "Brighter" -> R.string.settings_option_brighter
        "Darker" -> R.string.settings_option_darker
        "Startup preview" -> R.string.settings_option_startup_preview
        "Text" -> R.string.ui_text
        "HTML with images" -> R.string.ui_html_with_images
        "Plain text" -> R.string.settings_option_plain_text
        "Prefer OCR when text is poor" -> R.string.settings_option_prefer_ocr_when_text_is_poor
        "Force OCR" -> R.string.settings_option_force_ocr
        "Auto-detect" -> R.string.settings_option_auto_detect
        "Declared in HTML" -> R.string.settings_option_declared_in_html
        "References [n]" -> R.string.settings_option_references_n
        "Underscore text" -> R.string.settings_option_underscore_text
        "Starred text" -> R.string.settings_option_starred_text
        "Keep marked words" -> R.string.settings_option_keep_marked_words
        "Richest, most expressive voices — 28 English speakers in one 126MB download. Heavy: on mid-range phones it pauses between sentences." -> R.string.settings_option_richest_most_expressive_voices_28_english_speakers_in_one_126mb_d
        "12 UK and US voices, about 20MB each. Select several to download together; additional downloads wait their turn." -> R.string.settings_option_12_uk_and_us_voices_about_20mb_each_select_several_to_download_to
        else -> return label
    }
    return stringResource(resource)
}

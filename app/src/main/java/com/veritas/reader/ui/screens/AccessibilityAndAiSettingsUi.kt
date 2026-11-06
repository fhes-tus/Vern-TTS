package com.veritas.reader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.veritas.reader.AskAiSettings
import com.veritas.reader.ReaderSettings
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.aiAssistantIcon
import com.veritas.reader.aiAssistantOptions
import com.veritas.reader.installedPackageForOption
import com.veritas.reader.ui.VeritasSleekSlider
import com.veritas.reader.ui.VeritasSwitch

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AccessibilitySettingsDialog(
    settings: ReaderSettings,
    onDismiss: () -> Unit,
    onThemeChange: (String) -> Unit,
    onToggleContrastTheme: (String, String?) -> Unit = { theme, _ -> onThemeChange(theme) },
    onToggleAdaptiveCover: () -> Unit,
    onToggleSectionNumbers: () -> Unit,
    onGoalMinutesChange: (Int) -> Unit,
    onToggleStreakReminder: () -> Unit,
    onToggleReduceMotion: () -> Unit,
    onToggleReduceTransparency: () -> Unit,
    onToggleBionicReading: () -> Unit = {},
    onToggleShakeToExtend: () -> Unit = {},
    onToggleCollapsibleBars: () -> Unit = {},
    onToggleNavLabels: () -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    FullScreenSettingsScaffold(title = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_accessibility), onBack = onDismiss) {
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_comfort_comes_first), style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
            Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_shape_your_reading_space_around_what_feels_good_to_you), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        AccessibilityGroup(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_reading_focus)) {
            AccessibilityToggle(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_collapsible_reader_bars), com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_tap_the_page_to_hide_or_show_reader_controls), settings.collapsibleReaderBars, onToggleCollapsibleBars)
            AccessibilityToggle(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_bionic_reading), com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_emphasise_the_first_letters_of_each_word), settings.bionicReading, onToggleBionicReading)
            AccessibilityToggle(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_section_labels), com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_show_section_numbers_for_easier_study_and_search), settings.showSectionNumbers, onToggleSectionNumbers)
        }
        AccessibilityGroup(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_motion_clarity)) {
            AccessibilityToggle(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_reduce_motion), com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_keep_decorative_animations_still), settings.reduceMotion, onToggleReduceMotion)
            AccessibilityToggle(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_reduce_transparency), com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_use_solid_panels_for_clearer_text_and_controls), settings.reduceTransparency, onToggleReduceTransparency)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_high_contrast), style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_tap_the_selected_preset_again_to_restore_your_previous_theme), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("dark_high_contrast" to com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_dark), "white_high_contrast" to com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_light), "amoled" to com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_amoled)).forEach { (id, label) ->
                        androidx.compose.material3.FilterChip(settings.themeId == id, {
                            if (settings.themeId == id) {
                                val previous = settings.previousThemeId?.takeUnless { it in listOf("dark_high_contrast", "white_high_contrast", "amoled") || it.isBlank() }
                                    ?: if (id == "white_high_contrast") "light" else "dark"
                                onToggleContrastTheme(previous, null)
                            } else onToggleContrastTheme(id, if (settings.themeId in listOf("dark_high_contrast", "white_high_contrast", "amoled")) settings.previousThemeId else settings.themeId)
                        }, label = { Text(label) })
                    }
                }
            }
        }
        AccessibilityGroup(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_daily_rhythm)) {
            val goalOn = settings.dailyGoalMinutes > 0
            AccessibilityToggle(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_daily_reading_goal), com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_a_small_target_to_help_make_reading_a_habit), goalOn, { onGoalMinutesChange(if (goalOn) 0 else 20) })
            if (goalOn) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(androidx.compose.ui.res.pluralStringResource(com.veritas.reader.R.plurals.reading_minutes_daily, settings.dailyGoalMinutes, settings.dailyGoalMinutes), style = MaterialTheme.typography.titleMedium, color = scheme.primary)
                    VeritasSleekSlider(value = settings.dailyGoalMinutes.coerceIn(5, 180).toFloat(),
                        onValueChange = { onGoalMinutesChange(it.toInt().coerceIn(5, 180)) }, valueRange = 5f..180f, steps = 34)
                }
                AccessibilityToggle(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_streak_reminder), com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_an_evening_nudge_when_your_goal_is_at_risk), settings.streakReminderEnabled, onToggleStreakReminder)
            }
            AccessibilityToggle(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_shake_to_extend_timer), com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_shake_in_the_final_minute_to_add_ten_minutes), settings.shakeToExtendSleepTimer, onToggleShakeToExtend)
        }
        AccessibilityGroup(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_appearance)) {
            AccessibilityToggle(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_adaptive_cover_theme), com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_let_your_current_book_colour_the_reading_space), settings.adaptiveCover, onToggleAdaptiveCover)
            AccessibilityToggle(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_navigation_labels), com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_show_names_beneath_navigation_icons), settings.showNavLabels, onToggleNavLabels)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun AccessibilityGroup(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Text(title, Modifier.padding(top = 16.dp, bottom = 4.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    Card(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(content = content)
    }
}

@Composable
private fun AccessibilityToggle(title: String, description: String, checked: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        VeritasSwitch(checked = checked, onCheckedChange = { onToggle() })
    }
}

private fun getAiOptionIcon(optionId: String): ImageVector = aiAssistantIcon(optionId)

@Composable
fun AskAiSettingsDialog(
    settings: AskAiSettings,
    onSettingsChange: (AskAiSettings) -> Unit,
    onInstallAssistant: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var promptDraft by remember(settings.promptTemplate) { mutableStateOf(settings.promptTemplate) }

    FullScreenSettingsScaffold(title = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_ask_ai), onBack = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = VeritasPackStyle.cardShape(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_ai_assistant_preference),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_choose_the_assistant_veritas_should_launch_when_you_select_text_i),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_select_preferred_ai_app),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        aiAssistantOptions.forEach { option ->
            val selected = settings.assistantId == option.id
            val installedPackage = installedPackageForOption(context, option)
            val installed = option.packageName.isBlank() || installedPackage != null

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (installed || option.packageName.isBlank()) {
                            onSettingsChange(
                                settings.copy(
                                    assistantId = option.id,
                                    assistantLabel = option.label,
                                    packageName = installedPackage ?: option.packageName,
                                    promptTemplate = promptDraft
                                )
                            )
                        } else {
                            onInstallAssistant(option.packageName)
                        }
                    }
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val cardBorder = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = VeritasPackStyle.compactShape()
                        )
                        .then(Modifier.border(cardBorder, VeritasPackStyle.compactShape())),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getAiOptionIcon(option.id),
                        contentDescription = option.label,
                        tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = com.veritas.reader.ui.screens.localizedCatalogLabel(option.label),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (installed) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_ready_to_launch) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_install_from_play_store),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_selected),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                } else if (!installed) {
                    TextButton(onClick = { onInstallAssistant(option.packageName) }) {
                        Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_install), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_custom_prompt_template),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        OutlinedTextField(
            value = promptDraft,
            onValueChange = {
                promptDraft = it
                onSettingsChange(settings.copy(promptTemplate = it))
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            shape = VeritasPackStyle.cardShape(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            label = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_prompt_template)) },
            placeholder = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_use_selection_where_the_selected_text_should_appear)) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                onSettingsChange(settings.copy(promptTemplate = promptDraft))
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = VeritasPackStyle.chipShape()
        ) {
            Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_save_ai_settings), style = MaterialTheme.typography.labelLarge)
        }
    }
}

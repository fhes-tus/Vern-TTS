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
    FullScreenSettingsScaffold(title = "Accessibility", onBack = onDismiss) {
        // Reading focus & comprehension
        SettingsHubSectionTitle("Focus & comprehension")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = VeritasPackStyle.cardShape(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Collapsible reader bars", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Single tap on reading canvas hides/shows top and bottom bars. Toggle off to keep bars permanently pinned.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    VeritasSwitch(checked = settings.collapsibleReaderBars, onCheckedChange = { onToggleCollapsibleBars() })
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bionic reading mode", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Bold leading letters to guide eye fixation and speed comprehension", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    VeritasSwitch(checked = settings.bionicReading, onCheckedChange = { onToggleBionicReading() })
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Shake to extend sleep timer", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Gently shake phone in final minute to add 10 min without unlocking", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    VeritasSwitch(checked = settings.shakeToExtendSleepTimer, onCheckedChange = { onToggleShakeToExtend() })
                }
            }
        }

        // Reading goal
        SettingsHubSectionTitle("Daily reading goal")
        val goalOn = settings.dailyGoalMinutes > 0
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = VeritasPackStyle.cardShape(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Set daily reading goal", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Shows daily progress ring on the home card", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    VeritasSwitch(
                        checked = goalOn,
                        onCheckedChange = { on -> onGoalMinutesChange(if (on) 20 else 0) }
                    )
                }
                if (goalOn) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Daily target", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("${settings.dailyGoalMinutes} min", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    VeritasSleekSlider(
                        value = settings.dailyGoalMinutes.coerceIn(5, 180).toFloat(),
                        onValueChange = { onGoalMinutesChange(it.toInt().coerceIn(5, 180)) },
                        valueRange = 5f..180f,
                        steps = 34
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Streak reminder", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text("Evening nudge when daily goal is at risk", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        VeritasSwitch(checked = settings.streakReminderEnabled, onCheckedChange = { onToggleStreakReminder() })
                    }
                }
            }
        }

        SettingsHubSectionTitle("Motion & contrast")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = VeritasPackStyle.cardShape(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Reduce motion", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Disable decorative animations and pulses", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    VeritasSwitch(checked = settings.reduceMotion, onCheckedChange = { onToggleReduceMotion() })
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Reduce transparency", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Use solid surfaces for clearer text and controls", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    VeritasSwitch(checked = settings.reduceTransparency, onCheckedChange = { onToggleReduceTransparency() })
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("High-contrast quick presets", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    
                    val isDarkContrast = settings.themeId == "dark_high_contrast"
                    val isLightContrast = settings.themeId == "white_high_contrast"
                    val isAmoled = settings.themeId == "amoled"
                    val isHighContrast = isDarkContrast || isLightContrast || isAmoled

                    fun toggleContrast(targetThemeId: String) {
                        if (settings.themeId == targetThemeId) {
                            // Toggle OFF: restore previous theme before high contrast was activated
                            val restoreTheme = settings.previousThemeId?.takeIf {
                                it.isNotBlank() && it != "dark_high_contrast" && it != "white_high_contrast" && it != "amoled"
                            } ?: if (targetThemeId == "white_high_contrast") "light" else "dark"
                            onToggleContrastTheme(restoreTheme, null)
                        } else {
                            // Toggle ON: remember current theme (if not already high contrast) and switch
                            val savedPrev = if (!isHighContrast) settings.themeId else settings.previousThemeId
                            onToggleContrastTheme(targetThemeId, savedPrev)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isDarkContrast) {
                            Button(
                                onClick = { toggleContrast("dark_high_contrast") },
                                modifier = Modifier.weight(1f),
                                shape = VeritasPackStyle.chipShape()
                            ) {
                                Text("Contrast Dark", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { toggleContrast("dark_high_contrast") },
                                modifier = Modifier.weight(1f),
                                shape = VeritasPackStyle.chipShape()
                            ) {
                                Text("Contrast Dark")
                            }
                        }

                        if (isLightContrast) {
                            Button(
                                onClick = { toggleContrast("white_high_contrast") },
                                modifier = Modifier.weight(1f),
                                shape = VeritasPackStyle.chipShape()
                            ) {
                                Text("Contrast Light", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { toggleContrast("white_high_contrast") },
                                modifier = Modifier.weight(1f),
                                shape = VeritasPackStyle.chipShape()
                            ) {
                                Text("Contrast Light")
                            }
                        }
                    }
                }
            }
        }

        SettingsHubSectionTitle("Appearance extras")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = VeritasPackStyle.cardShape(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Adaptive cover theme", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Blend active book cover palette into theme", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    VeritasSwitch(checked = settings.adaptiveCover, onCheckedChange = { onToggleAdaptiveCover() })
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Show section labels", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Display indices for study and search jumps", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    VeritasSwitch(checked = settings.showSectionNumbers, onCheckedChange = { onToggleSectionNumbers() })
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Show navigation tab labels", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Display text labels beneath floating navigation bar icons. Toggle off for minimalist icon-only navigation.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    VeritasSwitch(checked = settings.showNavLabels, onCheckedChange = { onToggleNavLabels() })
                }
            }
        }
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

    FullScreenSettingsScaffold(title = "Ask AI", onBack = onDismiss) {
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
                    text = "AI Assistant Preference",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Choose the assistant Veritas should launch when you select text in the reader. If your chosen app is not installed, Veritas can redirect you to the Play Store.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Select Preferred AI App",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
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
                        text = option.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (installed) "Ready to launch" else "Install from Play Store",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                } else if (!installed) {
                    TextButton(onClick = { onInstallAssistant(option.packageName) }) {
                        Text("Install", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Custom Prompt Template",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
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
            label = { Text("Prompt Template") },
            placeholder = { Text("Use {selection} where the selected text should appear") }
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
            Text("Save AI Settings", fontWeight = FontWeight.Bold)
        }
    }
}

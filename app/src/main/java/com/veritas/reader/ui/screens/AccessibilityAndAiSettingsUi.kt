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
    FullScreenSettingsScaffold(title = "Accessibility", onBack = onDismiss) {
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Comfort comes first", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
            Text("Shape your reading space around what feels good to you.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        AccessibilityGroup("Reading & focus") {
            AccessibilityToggle("Collapsible reader bars", "Tap the page to hide or show reader controls.", settings.collapsibleReaderBars, onToggleCollapsibleBars)
            AccessibilityToggle("Bionic reading", "Emphasise the first letters of each word.", settings.bionicReading, onToggleBionicReading)
            AccessibilityToggle("Section labels", "Show section numbers for easier study and search.", settings.showSectionNumbers, onToggleSectionNumbers)
        }
        AccessibilityGroup("Motion & clarity") {
            AccessibilityToggle("Reduce motion", "Keep decorative animations still.", settings.reduceMotion, onToggleReduceMotion)
            AccessibilityToggle("Reduce transparency", "Use solid panels for clearer text and controls.", settings.reduceTransparency, onToggleReduceTransparency)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("High contrast", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                Text("Tap the selected preset again to restore your previous theme.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("dark_high_contrast" to "Dark", "white_high_contrast" to "Light", "amoled" to "AMOLED").forEach { (id, label) ->
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
        AccessibilityGroup("Daily rhythm") {
            val goalOn = settings.dailyGoalMinutes > 0
            AccessibilityToggle("Daily reading goal", "A small target to help make reading a habit.", goalOn, { onGoalMinutesChange(if (goalOn) 0 else 20) })
            if (goalOn) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("${settings.dailyGoalMinutes} minutes a day", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = scheme.primary)
                    VeritasSleekSlider(value = settings.dailyGoalMinutes.coerceIn(5, 180).toFloat(),
                        onValueChange = { onGoalMinutesChange(it.toInt().coerceIn(5, 180)) }, valueRange = 5f..180f, steps = 34)
                }
                AccessibilityToggle("Streak reminder", "An evening nudge when your goal is at risk.", settings.streakReminderEnabled, onToggleStreakReminder)
            }
            AccessibilityToggle("Shake to extend timer", "Shake in the final minute to add ten minutes.", settings.shakeToExtendSleepTimer, onToggleShakeToExtend)
        }
        AccessibilityGroup("Appearance") {
            AccessibilityToggle("Adaptive cover theme", "Let your current book colour the reading space.", settings.adaptiveCover, onToggleAdaptiveCover)
            AccessibilityToggle("Navigation labels", "Show names beneath navigation icons.", settings.showNavLabels, onToggleNavLabels)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun AccessibilityGroup(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Text(title, Modifier.padding(top = 16.dp, bottom = 4.dp), style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
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
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
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

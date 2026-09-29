package com.veritas.reader.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.veritas.reader.ResolvedVeritasFeature
import com.veritas.reader.SavedDocument
import com.veritas.reader.StorageBreakdown
import com.veritas.reader.UnrestrictedBatteryDialog
import com.veritas.reader.VeritasFeatureContext
import com.veritas.reader.VeritasFeatureId
import com.veritas.reader.VeritasFeatureRegistry
import com.veritas.reader.VeritasFeatureSurface
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.isBatteryOptimizationIgnored
import com.veritas.reader.requestIgnoreBatteryOptimizations
import com.veritas.reader.ui.OnboardingController
import com.veritas.reader.ui.OnboardingStep
import com.veritas.reader.ui.ReaderUiState
import com.veritas.reader.ui.VeritasSleekSlider
import java.util.Locale

@Composable
fun SettingsHubDialog(
    uiState: ReaderUiState,
    onDismiss: () -> Unit,
    onOpenReaderSettings: () -> Unit,
    onOpenVoiceStudio: () -> Unit,
    onOpenNarrationStudio: () -> Unit,
    onOpenPronunciationRules: () -> Unit,
    onOpenBackupRestore: () -> Unit,
    onOpenSyncCenter: () -> Unit,
    onOpenAiCenter: () -> Unit,
    onOpenAskAiSettings: () -> Unit,
    onStartRecord: () -> Unit,
    onOpenTextEditor: () -> Unit,
    onOpenTutorial: () -> Unit,
    onOpenPdfTools: () -> Unit,
    onOpenFileBrowser: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenReadingLists: () -> Unit,
    onOpenUserManual: () -> Unit,
    onOpenStorage: () -> Unit = {},
    onOpenAccessibility: () -> Unit = {},
    onCheckForUpdates: () -> Unit = {}
) {
    val context = LocalContext.current
    val documentCount = uiState.documents.size
    val hasActiveDocument = uiState.activeDocument != null
    val queueCount = uiState.queuedDocuments.size
    val settingsFeatures = remember(hasActiveDocument, documentCount, queueCount) {
        VeritasFeatureRegistry.resolve(
            VeritasFeatureSurface.SETTINGS_HUB,
            VeritasFeatureContext(
                hasActiveDocument = hasActiveDocument,
                hasSavedDocument = documentCount > 0,
                queueCount = queueCount
            )
        ).associateBy { it.definition.id }
    }

    fun settingsFeature(id: VeritasFeatureId): ResolvedVeritasFeature =
        settingsFeatures.requireResolvedFeature(id)

    var showAboutDialog by remember { mutableStateOf(false) }
    var showBatteryDialog by remember { mutableStateOf(false) }

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
                modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                SettingsHubSectionTitle("Voice & Audio")
                SettingsHubGroup(listOf(
                    featureRowSpec(
                        settingsFeature(VeritasFeatureId.VOICE_STUDIO),
                        "Voice Studio", "TTS engine, installed voices & pitch presets",
                        Icons.Outlined.RecordVoiceOver, onOpenVoiceStudio
                    ),
                    SettingsRowSpec(
                        "Narration Studio", "Narrator & dialogue delivery controls",
                        Icons.Outlined.TheaterComedy, onOpenNarrationStudio
                    ),
                    featureRowSpec(
                        settingsFeature(VeritasFeatureId.PRONUNCIATION_RULES),
                        "Pronunciation", "Word replacements before playback",
                        Icons.Outlined.Translate, onOpenPronunciationRules
                    ),
                    featureRowSpec(
                        settingsFeature(VeritasFeatureId.SLEEP_TIMER),
                        "Sleep timer", "Pause or stop reading after chosen time",
                        Icons.Outlined.Timer, onOpenSleepTimer
                    ),
                    featureRowSpec(
                        settingsFeature(VeritasFeatureId.QUEUE_AUDIO_EXPORT),
                        "Record sound file", "Render current reading into audio file",
                        Icons.Outlined.GraphicEq, onStartRecord
                    )
                ))

                SettingsHubSectionTitle("Appearance")
                SettingsHubGroup(listOf(
                    SettingsRowSpec(
                        "Display theme", "Theme packs, colour themes & text size",
                        Icons.Outlined.Palette, onOpenReaderSettings
                    ),
                    SettingsRowSpec(
                        "Accessibility", "Reading goal, reduce motion & contrast",
                        Icons.Outlined.Accessibility, onOpenAccessibility
                    ),
                    SettingsRowSpec(
                        "Background playback",
                        if (isBatteryOptimizationIgnored(LocalContext.current)) "Battery optimization unrestricted (Optimal)" else "Battery optimized (Tap to enable unrestricted)",
                        Icons.Outlined.PowerSettingsNew,
                        {
                            if (isBatteryOptimizationIgnored(context)) {
                                requestIgnoreBatteryOptimizations(context)
                            } else {
                                showBatteryDialog = true
                            }
                        }
                    )
                ))

                SettingsHubSectionTitle("AI & Language")
                SettingsHubGroup(listOf(
                    featureRowSpec(
                        settingsFeature(VeritasFeatureId.OFFLINE_STUDY_TOOLS),
                        "AI & study mode", "AI app handoff, flashcards & quizzes",
                        Icons.Outlined.AutoAwesome, onOpenAiCenter
                    ),
                    featureRowSpec(
                        settingsFeature(VeritasFeatureId.AI_APP_HANDOFF),
                        "Ask AI", "Choose assistant & selected-text prompt",
                        Icons.Outlined.Psychology, onOpenAskAiSettings
                    )
                ))

                SettingsHubSectionTitle("Import Defaults")
                SettingsHubGroup(listOf(
                    featureRowSpec(
                        settingsFeature(VeritasFeatureId.PDF_IMPORT_CONTROLS),
                        "PDF and import tools", "Defaults, OCR fallback & page cleanup",
                        Icons.Outlined.PictureAsPdf, onOpenPdfTools
                    ),
                    featureRowSpec(
                        settingsFeature(VeritasFeatureId.FILE_BROWSER),
                        "File browser", "Scan approved folders & import local files",
                        Icons.Outlined.Folder, onOpenFileBrowser
                    ),
                    featureRowSpec(
                        settingsFeature(VeritasFeatureId.EXTRACTED_TEXT_EDITOR),
                        "Edit extracted text", "Open text editor to correct text",
                        Icons.Outlined.EditNote, onOpenTextEditor
                    )
                ))

                SettingsHubSectionTitle("Data & Backup")
                SettingsHubGroup(listOf(
                    featureRowSpec(
                        settingsFeature(VeritasFeatureId.LOCAL_SYNC_PACK),
                        "Sync & Backup Center", "Sync & backup library, flashcards, notes, and progress",
                        Icons.Filled.Sync, onOpenSyncCenter
                    ),
                    featureRowSpec(
                        settingsFeature(VeritasFeatureId.READING_LISTS),
                        "Reading lists", "Create local lists & add saved readings",
                        Icons.AutoMirrored.Filled.List, onOpenReadingLists
                    ),
                    SettingsRowSpec(
                        "Storage", "See what Vern is using & free up space",
                        Icons.Outlined.Storage, onOpenStorage
                    )
                ))

                SettingsHubSectionTitle("Help")
                SettingsHubGroup(listOf(
                    SettingsRowSpec(
                        "Check for updates",
                        uiState.updateStatusMessage ?: "Check for the latest Vern version",
                        Icons.Filled.Sync,
                        onCheckForUpdates
                    ),
                    SettingsRowSpec(
                        "User manual", "Interactive guide to Vern features",
                        Icons.AutoMirrored.Outlined.HelpOutline, onOpenUserManual
                    ),
                    SettingsRowSpec(
                        "Tutorial", "Learn about Vern through guided actions",
                        Icons.Outlined.School, onOpenTutorial
                    ),
                    SettingsRowSpec(
                        "About", "Version, developer contact, social links & feedback",
                        Icons.Outlined.Info, { showAboutDialog = true }
                    )
                ))
                }
            }

            // Onboarding tour card: rendered inside SettingsHubDialog window
            if (OnboardingController.activeStep == OnboardingStep.SETTINGS_SPOTLIGHT) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .widthIn(max = 380.dp)
                        .padding(16.dp)
                        .navigationBarsPadding()
                        .shadow(16.dp, RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = OnboardingStep.SETTINGS_SPOTLIGHT.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = {
                                    OnboardingController.activeStep = null
                                    onDismiss()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss Tour",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                            text = OnboardingStep.SETTINGS_SPOTLIGHT.body,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = {
                                onDismiss()
                                OnboardingController.activeStep = OnboardingStep.STUDY_TAB_SPOTLIGHT
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Back", style = MaterialTheme.typography.labelMedium)
                            }

                            Button(
                                onClick = {
                                    onDismiss()
                                    OnboardingController.activeStep = OnboardingStep.DOCUMENT_SPOTLIGHT
                                },
                                shape = RoundedCornerShape(50)
                            ) {
                                Text("Next", style = MaterialTheme.typography.labelMedium)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Next",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAboutDialog) {
        AboutDialog(
            uiState = uiState,
            onCheckForUpdates = onCheckForUpdates,
            onDismiss = { showAboutDialog = false }
        )
    }

    if (showBatteryDialog) {
        UnrestrictedBatteryDialog(
            onDismiss = { showBatteryDialog = false },
            onOpenSettings = { requestIgnoreBatteryOptimizations(context) }
        )
    }
}

fun formatVeritasBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return when {
        mb >= 1024 -> String.format(Locale.US, "%.2f GB", mb / 1024.0)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    }
}

@Composable
fun StorageDialog(
    breakdown: StorageBreakdown?,
    candidates: List<Pair<SavedDocument, Long>>,
    cleanupMessage: String?,
    onSmartCleanup: () -> Unit,
    onClearCache: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Storage") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (breakdown == null) {
                    Text("Measuring…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(
                        "Vern is using ${formatVeritasBytes(breakdown.totalBytes)} across ${breakdown.documentCount} document${if (breakdown.documentCount == 1) "" else "s"}",
                        fontWeight = FontWeight.Bold
                    )
                    Text("Original books & files: ${formatVeritasBytes(breakdown.originalsBytes)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Extracted text: ${formatVeritasBytes(breakdown.textBytes)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Covers & thumbnails: ${formatVeritasBytes(breakdown.coversBytes)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Database & storage index: ${formatVeritasBytes(breakdown.databaseBytes)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Temporary cache & audio: ${formatVeritasBytes(breakdown.cacheBytes)}", color = MaterialTheme.colorScheme.onSurfaceVariant)

                    if (onClearCache != null) {
                        val cacheBytes = breakdown.cacheBytes
                        OutlinedButton(
                            onClick = onClearCache,
                            enabled = cacheBytes > 0L,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (cacheBytes <= 0L) "Temporary cache is clean"
                                else "Clear Temporary Cache — free ${formatVeritasBytes(cacheBytes)}"
                            )
                        }
                    }

                    val reclaimable = candidates.sumOf { it.second }
                    Button(
                        onClick = onSmartCleanup,
                        enabled = candidates.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (candidates.isEmpty()) "Nothing to clean up"
                            else "Smart Cleanup — free ${formatVeritasBytes(reclaimable)}"
                        )
                    }
                    Text(
                        if (candidates.isEmpty())
                            "Smart Cleanup removes original files of fully-read or 90+ day dormant, non-favorite documents. Text, progress, notes, and highlights always stay."
                        else
                            "Removes the original files of ${candidates.size} fully-read or dormant document${if (candidates.size == 1) "" else "s"} (favorites are never touched). Reading text, progress, notes, highlights and cover art stay — only Original View is lost until a re-import.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (!cleanupMessage.isNullOrBlank()) {
                        Text(cleanupMessage, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    )
}

@Composable
fun SettingsHubSectionTitle(title: String) {
    val currentLocale = LocalConfiguration.current.locales[0]
    Text(
        title.uppercase(currentLocale),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp, start = 4.dp)
    )
}

/** One row of the settings hub, described so a group can lay several out together. */
data class SettingsRowSpec(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val disabledReason: String? = null
)

fun featureRowSpec(
    feature: ResolvedVeritasFeature,
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) = SettingsRowSpec(title, subtitle, icon, onClick, feature.enabled, feature.disabledReason)

/**
 * A section's rows sharing a single card, separated by hairlines.
 */
@Composable
fun SettingsHubGroup(rows: List<SettingsRowSpec>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = VeritasPackStyle.cardShape(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = VeritasPackStyle.surfaceAlpha())
        ),
        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
    ) {
        Column {
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 70.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                    )
                }
                SettingsHubRow(row)
            }
        }
    }
}

@Composable
fun SettingsHubRow(row: SettingsRowSpec) {
    val enabled = row.enabled
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { row.onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    color = if (enabled) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    },
                    shape = VeritasPackStyle.compactShape()
                )
                .then(
                    Modifier.border(
                        VeritasPackStyle.cardBorder(MaterialTheme.colorScheme),
                        VeritasPackStyle.compactShape()
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = row.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = if (enabled) 1f else 0.45f),
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = row.disabledReason ?: row.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 0.85f else 0.5f)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 0.55f else 0.25f),
            modifier = Modifier.size(20.dp)
        )
    }
}

/** Full-screen top bar with a back action for settings sub-pages. */
@Composable
internal fun FullScreenSettingsScaffold(
    title: String,
    onBack: () -> Unit,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .background(VeritasPackStyle.backgroundBrush(MaterialTheme.colorScheme))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
fun VeritasRoundSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    VeritasSleekSlider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        modifier = modifier,
        enabled = enabled
    )
}

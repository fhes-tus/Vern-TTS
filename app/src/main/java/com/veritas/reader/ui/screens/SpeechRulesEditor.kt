package com.veritas.reader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veritas.reader.*
import com.veritas.reader.R
import com.veritas.reader.ui.VeritasSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PronunciationRulesDialog(rules: List<PronunciationRule>, voiceSettings: VoiceSettings,
    draft: SpeechRuleDraft, saveError: String?, onDraftChange: (SpeechRuleDraft) -> Unit,
    onAddRule: () -> Unit, onToggleRule: (PronunciationRule) -> Unit,
    onRemoveRule: (PronunciationRule) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colours = MaterialTheme.colorScheme
    val fieldColours = OutlinedTextFieldDefaults.colors(focusedLabelColor = colours.onSurface, cursorColor = colours.onSurface)
    var testInput by rememberSaveable { mutableStateOf("The result[12] includes _an aside_ and *an editor note*.") }
    var showExamples by rememberSaveable { mutableStateOf(false) }
    var showGuide by rememberSaveable(draft.kind) { mutableStateOf(false) }
    var showMatchingHelp by rememberSaveable { mutableStateOf(false) }
    val canCollapseSavedRules = rules.size > 1
    var showSavedRules by rememberSaveable(canCollapseSavedRules) { mutableStateOf(false) }
    val editorHeader = remember { BringIntoViewRequester() }
    val scrollScope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<SpeechRulePreview.Result?>(null) }
    val validation = remember(draft) { if (draft.find.isBlank()) null else draft.error() }
    DisposableEffect(Unit) { onDispose { VoiceManager.releasePreviewEngine() } }
    LaunchedEffect(testInput, rules, draft, voiceSettings) {
        preview = null
        delay(150)
        preview = withContext(Dispatchers.Default) { SpeechRulePreview.build(testInput, rules, draft, voiceSettings) }
    }
    fun audition() { preview?.spoken?.takeIf(String::isNotBlank)?.let { text ->
        VoiceManager.previewVoice(context = context, enginePackage = voiceSettings.enginePackage,
            voiceName = voiceSettings.voiceName, rate = voiceSettings.preferredRate,
            pitch = voiceSettings.preferredPitch, text = text, voiceShaping = voiceSettings.voiceShaping)
    } }
    val quietButton = ButtonDefaults.textButtonColors(contentColor = colours.onSurface)
    FullScreenSettingsScaffold(title = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_pronunciation_rules_bf2c874), onBack = onDismiss) {
        // The scaffold supplies the single 18 dp page margin; cards add 16 dp internally.
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_change_what_vern_says_without_changing_the_book),
                style = MaterialTheme.typography.bodyMedium, color = colours.onSurfaceVariant)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_start_with_an_example), style = MaterialTheme.typography.titleSmall)
                FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = 2, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    speechRuleExamples.take(if (showExamples) speechRuleExamples.size else 4).forEach { (label, preset) ->
                        SuggestionChip(onClick = { onDraftChange(preset) }, label = { Text(localizedSettingsOption(label)) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp))
                    }
                }
                TextButton({ showExamples = !showExamples }, colors = quietButton,
                    contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.testTag("speech-rule-more-examples")) {
                    Text(if (showExamples) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_fewer_examples) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_more_examples))
                }
            }

            Card(Modifier.fillMaxWidth(), shape = VeritasPackStyle.cardShape(),
                colors = CardDefaults.cardColors(containerColor = colours.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth().bringIntoViewRequester(editorHeader), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (draft.id == null) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_new_rule) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_edit_rule), style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f))
                        if (draft.find.isNotEmpty()) TextButton({ onDraftChange(SpeechRuleDraft()) }, colors = quietButton) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_clear)) }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_match), style = MaterialTheme.typography.labelLarge, color = colours.onSurfaceVariant)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            SpeechRuleKind.entries.forEachIndexed { index, kind ->
                                SegmentedButton(selected = draft.kind == kind, onClick = { onDraftChange(draft.copy(kind = kind)) },
                                    shape = SegmentedButtonDefaults.itemShape(index, SpeechRuleKind.entries.size), icon = {},
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("speech-kind-${kind.name}")) { Text(localizedSettingsOption(kind.label)) }
                            }
                        }
                        OutlinedTextField(value = draft.find, onValueChange = { onDraftChange(draft.copy(find = it)) },
                            modifier = Modifier.fillMaxWidth().testTag("speech-rule-find"), singleLine = true,
                            label = { Text(if (draft.kind == SpeechRuleKind.LITERAL) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_find_word_or_phrase) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_match_pattern)) },
                            isError = validation != null, shape = VeritasPackStyle.compactShape(), colors = fieldColours)
                        Text(when (draft.kind) {
                            SpeechRuleKind.LITERAL -> com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_matches_complete_words_and_phrases)
                            SpeechRuleKind.PATTERN -> com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_match_changing_text_with_markers_such_as_n_or_text)
                            SpeechRuleKind.REGEX -> com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_use_a_regular_expression_for_advanced_matching)
                        }, style = MaterialTheme.typography.bodySmall, color = colours.onSurfaceVariant)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_match_case), style = MaterialTheme.typography.bodyMedium)
                        VeritasSwitch(checked = draft.caseSensitive, onCheckedChange = { onDraftChange(draft.copy(caseSensitive = it)) },
                            modifier = Modifier.testTag("speech-rule-case"))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_then), style = MaterialTheme.typography.labelLarge, color = colours.onSurfaceVariant)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            listOf(true, false).forEachIndexed { index, skip ->
                                SegmentedButton(selected = draft.skip == skip, onClick = { onDraftChange(draft.copy(skip = skip)) },
                                    shape = SegmentedButtonDefaults.itemShape(index, 2), icon = {},
                                    modifier = Modifier.heightIn(min = 48.dp).testTag(if (skip) "speech-action-skip" else "speech-action-say")) {
                                    Text(menuString(if (skip) R.string.ui_skip else R.string.ui_say_instead))
                                }
                            }
                        }
                        if (!draft.skip) OutlinedTextField(value = draft.replaceWith, onValueChange = { onDraftChange(draft.copy(replaceWith = it)) },
                            modifier = Modifier.fillMaxWidth().testTag("speech-rule-replacement"), label = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_say_instead)) },
                            supportingText = if (draft.kind == SpeechRuleKind.LITERAL) {
                                { Text(menuString(R.string.ui_for_example_sequel_instead_of_sql)) }
                            } else null,
                            singleLine = true, shape = VeritasPackStyle.compactShape(), colors = fieldColours)
                    }
                    if (validation != null || saveError != null) Text(saveError ?: validation.orEmpty(),
                        color = colours.error, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("speech-rule-error"))
                    Button(onClick = onAddRule, enabled = draft.find.isNotBlank() && validation == null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("speech-rule-save"), shape = VeritasPackStyle.chipShape()) {
                        Text(if (draft.id != null || rules.any { SpeechPatternRules.identity(it) == SpeechPatternRules.identity(draft.rule()) }) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_update_rule) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_save_rule))
                    }
                    if (draft.kind != SpeechRuleKind.LITERAL) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton({ showGuide = !showGuide }, colors = quietButton,
                                contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.testTag("speech-rule-pattern-guide")) {
                                Text(menuString(if (showGuide) R.string.ui_hide_syntax_guide else R.string.ui_syntax_guide))
                            }
                            if (showGuide) {
                                SpeechSyntaxExample(draft.kind) { example, sample ->
                                    onDraftChange(example)
                                    testInput = sample
                                }
                                SpeechSyntaxReference(draft.kind)
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_try_your_rules), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = ::audition, enabled = preview?.spoken?.isNotBlank() == true,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("speech-rule-audition")) {
                        Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_listen))
                    }
                }
                OutlinedTextField(value = testInput, onValueChange = { testInput = it.take(4000) },
                    label = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_original_passage)) }, supportingText = { Text("${testInput.length}/4000") },
                    modifier = Modifier.fillMaxWidth().testTag("speech-rule-test-input"), minLines = 2, maxLines = 4,
                    shape = VeritasPackStyle.compactShape(), colors = fieldColours)
                Surface(modifier = Modifier.testTag("speech-rule-preview-panel"), shape = VeritasPackStyle.compactShape(), color = colours.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_spoken_result), style = MaterialTheme.typography.labelLarge, color = colours.onSurfaceVariant)
                        Text(preview?.spoken?.ifBlank { com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_nothing_will_be_spoken) } ?: com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_preparing_preview),
                            style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().testTag("speech-rule-preview"))
                        Text(preview?.let { "${it.matches} ${if (it.matches == 1) "match" else "matches"} · " +
                            if (it.includesDraft) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_includes_unsaved_rule) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_saved_rules) } ?: com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_preparing_preview),
                            style = MaterialTheme.typography.bodySmall, color = colours.onSurfaceVariant)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val disclosure = if (canCollapseSavedRules) {
                    Modifier.heightIn(min = 48.dp).testTag("speech-rule-list-toggle")
                        .clickable(role = Role.Button, onClickLabel = menuString(
                            if (showSavedRules) R.string.ui_collapse else R.string.ui_expand
                        )) { showSavedRules = !showSavedRules }
                } else Modifier
                Row(Modifier.fillMaxWidth().then(disclosure), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(menuString(R.string.ui_saved_rules), style = MaterialTheme.typography.titleMedium)
                        Text(menuString(R.string.l10n_dynamic_218, localizedUiValue(rules.size),
                            localizedUiValue(rules.count { it.enabled })),
                            style = MaterialTheme.typography.bodySmall, color = colours.onSurfaceVariant)
                    }
                    if (canCollapseSavedRules) {
                        Icon(if (showSavedRules) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null, tint = colours.onSurfaceVariant)
                    }
                }
                if (rules.isEmpty()) Surface(shape = VeritasPackStyle.compactShape(), color = colours.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_no_rules_yet), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_save_an_example_or_create_your_own_above), style = MaterialTheme.typography.bodySmall, color = colours.onSurfaceVariant)
                    }
                }
                if (!canCollapseSavedRules || showSavedRules) {
                    rules.forEach { rule ->
                        key(rule.id) {
                            SavedPronunciationRule(
                                rule = rule,
                                onToggle = { onToggleRule(rule) },
                                onEdit = {
                                    onDraftChange(SpeechRuleDraft.from(rule))
                                    scrollScope.launch { editorHeader.bringIntoView() }
                                },
                                onRemove = { onRemoveRule(rule) }
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton({ showMatchingHelp = !showMatchingHelp }, colors = quietButton,
                    contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text(if (showMatchingHelp) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_hide_matching_details) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_how_rules_are_matched))
                }
                if (showMatchingHelp) Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_rules_apply_to_all_readings_and_audio_exports_within_each_spoken),
                    style = MaterialTheme.typography.bodySmall, color = colours.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SavedPronunciationRule(
    rule: PronunciationRule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    val colours = MaterialTheme.colorScheme
    val error = remember(rule) { SpeechPatternRules.error(rule) }
    val status = menuString(if (rule.enabled) R.string.settings_option_on else R.string.ui_off)
    val replacement = rule.replaceWith.ifEmpty { menuString(R.string.ui_skip) }
    Surface(Modifier.fillMaxWidth(), shape = VeritasPackStyle.compactShape(), color = colours.surfaceContainerLow) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                Modifier.weight(1f).heightIn(min = 48.dp)
                    .testTag("speech-rule-edit-${rule.id}")
                    .clickable(role = Role.Button, onClickLabel = menuString(R.string.ui_edit_rule), onClick = onEdit),
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
            ) {
                Text("${rule.find} → $replacement", style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(14.dp),
                        tint = colours.onSurfaceVariant)
                    Text("${localizedSettingsOption(rule.matchKind.label)} · $status",
                        style = MaterialTheme.typography.bodySmall, color = colours.onSurfaceVariant)
                }
                if (rule.caseSensitive) {
                    Text(menuString(R.string.ui_match_case), style = MaterialTheme.typography.bodySmall)
                }
                if (error != null) {
                    Text(menuString(R.string.l10n_dynamic_181, localizedUiValue(error)),
                        style = MaterialTheme.typography.bodySmall, color = colours.error)
                }
            }
            VeritasSwitch(checked = rule.enabled, onCheckedChange = onToggle,
                modifier = Modifier.testTag("speech-rule-toggle-${rule.id}"))
            IconButton(onClick = onRemove, modifier = Modifier.testTag("speech-rule-remove-${rule.id}")) {
                Icon(Icons.Outlined.Delete, contentDescription = menuString(R.string.action_delete))
            }
        }
    }
}

private val speechRuleExamples = listOf(
    "References [n]" to SpeechRuleDraft("[n]", kind = SpeechRuleKind.PATTERN, skip = true),
    "Underscore text" to SpeechRuleDraft("_{text}_", kind = SpeechRuleKind.PATTERN, skip = true),
    "Starred text" to SpeechRuleDraft("*{text}*", kind = SpeechRuleKind.PATTERN, skip = true),
    "Dr. → Doctor" to SpeechRuleDraft("Dr.", "Doctor"),
    "Keep marked words" to SpeechRuleDraft("_{text}_", "\$1", SpeechRuleKind.PATTERN),
    "e.g. → for example" to SpeechRuleDraft("e.g.", "for example"),
    "i.e. → that is" to SpeechRuleDraft("i.e.", "that is"),
    "etc. → etcetera" to SpeechRuleDraft("etc.", "etcetera"),
    "vs. → versus" to SpeechRuleDraft("vs.", "versus"),
    "approx. → approximately" to SpeechRuleDraft("approx.", "approximately"),
    "w/ → with" to SpeechRuleDraft("w/", "with"),
    "w/o → without" to SpeechRuleDraft("w/o", "without"),
    "ft. → featuring" to SpeechRuleDraft("ft.", "featuring"),
    "no. → number" to SpeechRuleDraft("no.", "number")
)

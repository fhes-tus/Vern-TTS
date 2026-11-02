package com.veritas.reader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veritas.reader.*
import com.veritas.reader.ui.VeritasSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    FullScreenSettingsScaffold(title = "Pronunciation rules", onBack = onDismiss) {
        // The scaffold supplies the single 18 dp page margin; cards add 16 dp internally.
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text("Change what Vern says without changing the book.",
                style = MaterialTheme.typography.bodyMedium, color = colours.onSurfaceVariant)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Start with an example", style = MaterialTheme.typography.titleSmall)
                FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = 2, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    speechRuleExamples.take(if (showExamples) speechRuleExamples.size else 4).forEach { (label, preset) ->
                        SuggestionChip(onClick = { onDraftChange(preset) }, label = { Text(label) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp))
                    }
                }
                TextButton({ showExamples = !showExamples }, colors = quietButton,
                    contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.testTag("speech-rule-more-examples")) {
                    Text(if (showExamples) "Fewer examples" else "More examples")
                }
            }

            Card(Modifier.fillMaxWidth(), shape = VeritasPackStyle.cardShape(),
                colors = CardDefaults.cardColors(containerColor = colours.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (draft.id == null) "New rule" else "Edit rule", style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f))
                        if (draft.find.isNotEmpty()) TextButton({ onDraftChange(SpeechRuleDraft()) }, colors = quietButton) { Text("Clear") }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Match", style = MaterialTheme.typography.labelLarge, color = colours.onSurfaceVariant)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            SpeechRuleKind.entries.forEachIndexed { index, kind ->
                                SegmentedButton(selected = draft.kind == kind, onClick = { onDraftChange(draft.copy(kind = kind)) },
                                    shape = SegmentedButtonDefaults.itemShape(index, SpeechRuleKind.entries.size), icon = {},
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("speech-kind-${kind.name}")) { Text(kind.label) }
                            }
                        }
                        OutlinedTextField(value = draft.find, onValueChange = { onDraftChange(draft.copy(find = it)) },
                            modifier = Modifier.fillMaxWidth().testTag("speech-rule-find"), singleLine = true,
                            label = { Text(if (draft.kind == SpeechRuleKind.LITERAL) "Find word or phrase" else "Match pattern") },
                            isError = validation != null, shape = VeritasPackStyle.compactShape(), colors = fieldColours)
                        Text(when (draft.kind) {
                            SpeechRuleKind.LITERAL -> "Matches complete words and phrases."
                            SpeechRuleKind.PATTERN -> "Match changing text with markers such as [n] or {text}."
                            SpeechRuleKind.REGEX -> "Use a regular expression for advanced matching."
                        }, style = MaterialTheme.typography.bodySmall, color = colours.onSurfaceVariant)
                        if (draft.kind != SpeechRuleKind.LITERAL) {
                            TextButton({ showGuide = !showGuide }, colors = quietButton,
                                contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.testTag("speech-rule-pattern-guide")) {
                                Text(if (showGuide) "Hide syntax guide" else "Syntax guide")
                            }
                            if (showGuide) Text(if (draft.kind == SpeechRuleKind.PATTERN)
                                "[n] matches [1], [12]… Use {number}, {word} or {text}. For example, _{text}_ matches an underscore-marked phrase. Inserted words stay separate when replacing enclosing markers."
                            else "RE2 regex example: \\[([0-9]+)\\]. Look-around and pattern backreferences are unsupported. Empty-position matches are ignored.",
                                style = MaterialTheme.typography.bodySmall, color = colours.onSurfaceVariant)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Match case", style = MaterialTheme.typography.bodyMedium)
                        VeritasSwitch(checked = draft.caseSensitive, onCheckedChange = { onDraftChange(draft.copy(caseSensitive = it)) },
                            modifier = Modifier.testTag("speech-rule-case"))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Then", style = MaterialTheme.typography.labelLarge, color = colours.onSurfaceVariant)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            listOf(true to "Skip", false to "Say instead").forEachIndexed { index, (skip, label) ->
                                SegmentedButton(selected = draft.skip == skip, onClick = { onDraftChange(draft.copy(skip = skip)) },
                                    shape = SegmentedButtonDefaults.itemShape(index, 2), icon = {},
                                    modifier = Modifier.heightIn(min = 48.dp).testTag(if (skip) "speech-action-skip" else "speech-action-say")) { Text(label) }
                            }
                        }
                        if (!draft.skip) OutlinedTextField(value = draft.replaceWith, onValueChange = { onDraftChange(draft.copy(replaceWith = it)) },
                            modifier = Modifier.fillMaxWidth().testTag("speech-rule-replacement"), label = { Text("Say instead") },
                            supportingText = { Text(if (draft.kind == SpeechRuleKind.LITERAL) "For example, sequel instead of SQL."
                                else "Use \$1, \$2… for captured parts; \$0 for the whole match and \$\$ for a dollar sign.") },
                            singleLine = true, shape = VeritasPackStyle.compactShape(), colors = fieldColours)
                    }
                    if (validation != null || saveError != null) Text(saveError ?: validation.orEmpty(),
                        color = colours.error, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("speech-rule-error"))
                    Button(onClick = onAddRule, enabled = draft.find.isNotBlank() && validation == null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("speech-rule-save"), shape = VeritasPackStyle.chipShape()) {
                        Text(if (draft.id != null || rules.any { SpeechPatternRules.identity(it) == SpeechPatternRules.identity(draft.rule()) }) "Update rule" else "Save rule")
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Try your rules", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = ::audition, enabled = preview?.spoken?.isNotBlank() == true,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("speech-rule-audition")) {
                        Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Listen")
                    }
                }
                OutlinedTextField(value = testInput, onValueChange = { testInput = it.take(4000) },
                    label = { Text("Original passage") }, supportingText = { Text("${testInput.length}/4000") },
                    modifier = Modifier.fillMaxWidth().testTag("speech-rule-test-input"), minLines = 2, maxLines = 4,
                    shape = VeritasPackStyle.compactShape(), colors = fieldColours)
                Surface(modifier = Modifier.testTag("speech-rule-preview-panel"), shape = VeritasPackStyle.compactShape(), color = colours.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Spoken result", style = MaterialTheme.typography.labelLarge, color = colours.onSurfaceVariant)
                        Text(preview?.spoken?.ifBlank { "Nothing will be spoken." } ?: "Preparing preview…",
                            style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().testTag("speech-rule-preview"))
                        Text(preview?.let { "${it.matches} ${if (it.matches == 1) "match" else "matches"} · " +
                            if (it.includesDraft) "Includes unsaved rule" else "Saved rules" } ?: "Preparing preview…",
                            style = MaterialTheme.typography.bodySmall, color = colours.onSurfaceVariant)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Saved rules", style = MaterialTheme.typography.titleMedium)
                Text("${rules.size} ${if (rules.size == 1) "rule" else "rules"} · ${rules.count { it.enabled }} enabled",
                    style = MaterialTheme.typography.bodySmall, color = colours.onSurfaceVariant)
                if (rules.isEmpty()) Surface(shape = VeritasPackStyle.compactShape(), color = colours.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("No rules yet", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("Save an example or create your own above.", style = MaterialTheme.typography.bodySmall, color = colours.onSurfaceVariant)
                    }
                }
                rules.forEach { rule ->
                    val ruleError = remember(rule) { SpeechPatternRules.error(rule) }
                    Surface(Modifier.fillMaxWidth(), shape = VeritasPackStyle.compactShape(), color = colours.surfaceContainerLow) {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).clickable(onClickLabel = "Edit rule") { onDraftChange(SpeechRuleDraft.from(rule)) },
                                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${rule.find} → ${rule.replaceWith.ifEmpty { "Skip" }}", style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = colours.onSurfaceVariant)
                                    Text("${rule.matchKind.label} · ${if (rule.enabled) "On" else "Off"}" + if (rule.caseSensitive) " · Match case" else "",
                                        style = MaterialTheme.typography.bodySmall, color = colours.onSurfaceVariant)
                                }
                                if (ruleError != null) Text("Invalid rule · edit to fix: $ruleError",
                                    style = MaterialTheme.typography.bodySmall, color = colours.error)
                            }
                            VeritasSwitch(checked = rule.enabled, onCheckedChange = { onToggleRule(rule) },
                                modifier = Modifier.testTag("speech-rule-toggle-${rule.id}"))
                            IconButton(onClick = { onRemoveRule(rule) }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Remove rule ${rule.find}", tint = colours.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton({ showMatchingHelp = !showMatchingHelp }, colors = quietButton,
                    contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text(if (showMatchingHelp) "Hide matching details" else "How rules are matched")
                }
                if (showMatchingHelp) Text("Rules apply to all readings and audio exports, within each spoken passage. Earliest match wins, then longest; the most recently saved rule breaks a tie. The book text stays as it is.",
                    style = MaterialTheme.typography.bodySmall, color = colours.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
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

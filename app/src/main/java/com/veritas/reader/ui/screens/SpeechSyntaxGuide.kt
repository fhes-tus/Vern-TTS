package com.veritas.reader.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.veritas.reader.R
import com.veritas.reader.SpeechRuleDraft
import com.veritas.reader.SpeechRuleKind
import com.veritas.reader.VeritasPackStyle

@Composable
internal fun SpeechSyntaxExample(kind: SpeechRuleKind, onUseExample: (SpeechRuleDraft, String) -> Unit) {
    val pattern = kind == SpeechRuleKind.PATTERN
    val find = if (pattern) "[{number}]" else """\[([0-9]+)\]"""
    val replacement = "\$1"
    val sample = "[12]"
    Surface(Modifier.fillMaxWidth(), shape = VeritasPackStyle.compactShape(),
        color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(menuString(R.string.speech_syntax_walkthrough), style = MaterialTheme.typography.titleSmall)
            SpeechSyntaxStep(1, menuString(R.string.ui_match_pattern)) {
                SyntaxCode(find)
                SyntaxNote(menuString(if (pattern) R.string.speech_syntax_step_pattern else R.string.speech_syntax_step_regex))
            }
            SpeechSyntaxStep(2, menuString(R.string.ui_say_instead)) {
                SyntaxCode(replacement)
                SyntaxNote(menuString(R.string.speech_syntax_step_say))
            }
            SpeechSyntaxStep(3, menuString(R.string.ui_try_your_rules)) {
                SyntaxNote(menuString(R.string.speech_syntax_step_try))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        SyntaxNote(menuString(R.string.ui_original_passage))
                        SyntaxCode(sample)
                    }
                    Column(Modifier.weight(1f)) {
                        SyntaxNote(menuString(R.string.ui_spoken_result))
                        SyntaxCode("12")
                    }
                }
            }
            TextButton(onClick = { onUseExample(SpeechRuleDraft(find, replacement, kind), sample) },
                modifier = Modifier.testTag("speech-rule-use-example")) {
                Text(menuString(R.string.speech_syntax_use_example))
            }
        }
    }
}

@Composable
private fun SpeechSyntaxStep(number: Int, title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${localizedUiValue(number)}. $title", style = MaterialTheme.typography.labelLarge)
        content()
    }
}

@Composable
private fun SyntaxNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun SpeechSyntaxReference(kind: SpeechRuleKind) {
    val tokens = if (kind == SpeechRuleKind.PATTERN) listOf(
        "{number}" to R.string.speech_syntax_digits,
        "{word}" to R.string.speech_syntax_word,
        "{text}" to R.string.speech_syntax_text,
        "{{  }}" to R.string.speech_syntax_braces
    ) else listOf(
        "[0-9]+" to R.string.speech_syntax_digits,
        "(...)" to R.string.speech_syntax_capture,
        """\[  \]""" to R.string.speech_syntax_brackets
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (tokens + listOf("\$1" to R.string.speech_syntax_first_group,
            "\$0" to R.string.speech_syntax_whole_match, "\$\$" to R.string.speech_syntax_dollar)).forEach { (token, explanation) ->
            Column {
                SyntaxCode(token)
                Text(menuString(explanation), style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(menuString(R.string.speech_syntax_skip), style = MaterialTheme.typography.bodySmall)
        if (kind == SpeechRuleKind.REGEX) {
            Text(menuString(R.string.speech_syntax_re2_limits), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SyntaxCode(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace,
        textDirection = TextDirection.Ltr), color = MaterialTheme.colorScheme.onSurface)
}

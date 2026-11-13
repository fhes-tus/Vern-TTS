package com.veritas.reader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.veritas.reader.DictionaryWordPage
import com.veritas.reader.OfflineDictionaryStore
import com.veritas.reader.ui.VeritasUiFont
import com.veritas.reader.ui.fontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal fun OfflineDictionaryWordList(prefix: String, after: String?, pageNumber: Int,
    onPrefixChange: (String) -> Unit, onPrevious: () -> Unit, onNext: (String) -> Unit, onOpenWord: (String) -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    var page by remember { mutableStateOf<DictionaryWordPage?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(prefix, after, retry) {
        loading = true; failed = false; page = null
        try {
            delay(150)
            page = withContext(Dispatchers.IO) { OfflineDictionaryStore.browse(context, prefix, after) }
            listState.scrollToItem(0)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    Column(Modifier.fillMaxWidth().testTag("dictionary-word-list"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Browse words", style = MaterialTheme.typography.titleMedium)
        Text("Choose a letter or type above to filter. Tap a word to open its definition.",
            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = prefix.isBlank(), onClick = { onPrefixChange("") }, label = { Text("All") })
            for (letter in 'A'..'Z') FilterChip(selected = prefix.equals(letter.toString(), ignoreCase = true),
                onClick = { onPrefixChange(letter.lowercaseChar().toString()) }, label = { Text(letter.toString()) },
                modifier = Modifier.testTag("dictionary-letter-$letter"))
        }
        when {
            loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Loading words…", style = MaterialTheme.typography.bodySmall)
            }
            failed -> {
                Text("Could not load the word list.", color = colors.error)
                TextButton(onClick = { retry++ }) { Text("Try again") }
            }
            page?.words.isNullOrEmpty() -> Text("No words start with “$prefix”. Try a different spelling or search online.",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            else -> {
                val current = page!!
                LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()
                    .height((LocalConfiguration.current.screenHeightDp * .45f).coerceIn(160f, 400f).dp)
                    .testTag("dictionary-words")) {
                    items(current.words, key = { it }) { word ->
                        Row(Modifier.fillMaxWidth().clickable { onOpenWord(word) }.heightIn(min = 48.dp)
                            .padding(horizontal = 4.dp, vertical = 12.dp).testTag("dictionary-word-$word"),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(word, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = VeritasUiFont.GAZETTE.fontFamily()))
                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                        }
                        HorizontalDivider(color = colors.outlineVariant.copy(alpha = .4f))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onPrevious, enabled = after != null, modifier = Modifier.testTag("dictionary-previous")) { Text("Previous") }
                    Text("Page $pageNumber", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    TextButton(onClick = { onNext(current.words.last()) }, enabled = current.hasMore,
                        modifier = Modifier.testTag("dictionary-next")) { Text("Next") }
                }
            }
        }
    }
}

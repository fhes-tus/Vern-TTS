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
import com.veritas.reader.R
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
        loading = true; failed = false
        try {
            val result = withContext(Dispatchers.IO) { OfflineDictionaryStore.browse(context, prefix, after) }
            page = result
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    LaunchedEffect(pageNumber, prefix) {
        if (page != null) runCatching { listState.scrollToItem(0) }
    }
    Column(Modifier.fillMaxWidth().testTag("dictionary-word-list"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(menuString(R.string.dict_browse_words), style = MaterialTheme.typography.titleMedium)
        Text(menuString(R.string.dict_browse_hint),
            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = prefix.isBlank(), onClick = { onPrefixChange("") }, label = { Text(menuString(R.string.dict_all_letters)) })
            for (letter in 'A'..'Z') FilterChip(selected = prefix.equals(letter.toString(), ignoreCase = true),
                onClick = { onPrefixChange(letter.lowercaseChar().toString()) }, label = { Text(letter.toString()) },
                modifier = Modifier.testTag("dictionary-letter-$letter"))
        }
        when {
            loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(menuString(R.string.dict_loading_words), style = MaterialTheme.typography.bodySmall)
            }
            failed -> {
                Text(menuString(R.string.dict_load_failed), color = colors.error)
                TextButton(onClick = { retry++ }) { Text(menuString(R.string.dict_try_again)) }
            }
            page?.words.isNullOrEmpty() -> Text(menuString(R.string.dict_no_words_prefix, prefix),
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
                    TextButton(onClick = onPrevious, enabled = after != null, modifier = Modifier.testTag("dictionary-previous")) { Text(menuString(R.string.dict_previous)) }
                    Text(menuString(R.string.dict_page, pageNumber), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    TextButton(onClick = { onNext(current.words.last()) }, enabled = current.hasMore,
                        modifier = Modifier.testTag("dictionary-next")) { Text(menuString(R.string.dict_next)) }
                }
            }
        }
    }
}

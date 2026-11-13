package com.veritas.reader.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.veritas.reader.*
import com.veritas.reader.ui.VeritasUiFont
import com.veritas.reader.ui.fontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OfflineDictionarySheet(query: String, anchor: DictionaryLookupAnchor,
    onSaved: (List<GeneralNote>) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * .86f).dp)
            .verticalScroll(rememberScrollState()).imePadding()) {
            OfflineDictionaryPanel(query, anchor, onSaved, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), onDismiss)
        }
    }
}

/** The same real dictionary content is used in Study Hub and the contextual reader sheet. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun OfflineDictionaryPanel(initialQuery: String = "", anchor: DictionaryLookupAnchor = DictionaryLookupAnchor(),
    onSaved: (List<GeneralNote>) -> Unit = {}, modifier: Modifier = Modifier, onClose: (() -> Unit)? = null) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf(initialQuery.take(80)) }
    var submitted by rememberSaveable { mutableStateOf(initialQuery.take(80)) }
    var request by rememberSaveable { mutableStateOf(if (WordNetDictionary.query(initialQuery) == null) "" else UUID.randomUUID().toString()) }
    var result by remember { mutableStateOf<SavedDictionaryLookup?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pack by remember { mutableStateOf<DictionaryPackInfo?>(null) }
    var work by remember { mutableStateOf<WorkInfo?>(null) }
    var showRemove by remember { mutableStateOf(false) }
    var showSource by remember { mutableStateOf(false) }
    var showPackDetails by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var lookupSource by rememberSaveable { mutableStateOf(if (OfflineDictionaryStore.isInstalled(context)) DictionaryLookupSource.OFFLINE else DictionaryLookupSource.ONLINE) }
    var browsing by rememberSaveable { mutableStateOf(onClose == null && initialQuery.isBlank() && lookupSource == DictionaryLookupSource.OFFLINE) }
    var browsePrefix by rememberSaveable { mutableStateOf("") }
    var browsePages by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val manager = remember(context) { WorkManager.getInstance(context) }
    val savedCallback by rememberUpdatedState(onSaved)
    LaunchedEffect(Unit) {
        if (OfflineDictionaryStore.isInstalled(context)) pack = withContext(Dispatchers.IO) { OfflineDictionaryStore.info(context) }
        manager.getWorkInfosForUniqueWorkFlow(DictionaryDownloadWorker.NAME).collect { infos ->
            work = infos.firstOrNull { !it.state.isFinished } ?: infos.firstOrNull()
            if (work?.state?.isFinished != false) pack = withContext(Dispatchers.IO) { OfflineDictionaryStore.info(context) }
        }
    }
    LaunchedEffect(request) {
        if (request.isBlank()) return@LaunchedEffect
        loading = true; result = null; error = null
        try {
            val found = lookupAndSaveDictionaryWord(context, submitted, anchor, request, lookupSource)
            result = found
            savedCallback(found.notes)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Could not finish this lookup. Please try again." }
        finally { loading = false }
    }
    fun search(word: String = query) {
        if (WordNetDictionary.query(word) == null || (lookupSource == DictionaryLookupSource.OFFLINE && pack == null)) return
        browsing = false
        query = word.take(80); submitted = query; request = UUID.randomUUID().toString()
        focus.clearFocus(); keyboard?.hide()
    }
    fun selectSource(source: DictionaryLookupSource) {
        if (source == lookupSource) return
        val reopenWord = !browsing && request.isNotBlank() && WordNetDictionary.query(query) != null
        lookupSource = source; result = null; error = null; request = ""
        browsing = source == DictionaryLookupSource.OFFLINE && onClose == null && !reopenWord
        if (reopenWord) search()
    }
    fun cancelDownload() { scope.launch { withContext(Dispatchers.IO) {
        manager.cancelUniqueWork(DictionaryDownloadWorker.NAME).result.get()
        OfflineDictionaryStore.clearInterrupted(context)
    } } }
    LaunchedEffect(pack != null, lookupSource) {
        if (pack != null && lookupSource == DictionaryLookupSource.OFFLINE && !browsing && request.isBlank() && WordNetDictionary.query(query) != null) search()
    }
    if (showPackDetails) OfflineDictionaryPackDialog(pack, work,
        onDownload = { DictionaryDownloadWorker.enqueue(context) }, onCancel = ::cancelDownload,
        onRemove = { showPackDetails = false; showRemove = true }, onSource = { showSource = true },
        onDismiss = { showPackDetails = false })
    if (showRemove) VernAlertDialog(onDismissRequest = { showRemove = false },
        title = { Text("Remove offline dictionary?") },
        text = { Text("Saved vocabulary and examples will remain. You can download the dictionary again.") },
        confirmButton = { TextButton(onClick = {
            showRemove = false
            scope.launch { try {
                withContext(Dispatchers.IO) { manager.cancelUniqueWork(DictionaryDownloadWorker.NAME).result.get(); OfflineDictionaryStore.remove(context) }
                pack = null
            } catch (_: Exception) { error = "Could not remove the dictionary. Try again." } }
        }) { Text("Remove") } }, dismissButton = { TextButton({ showRemove = false }) { Text("Cancel") } })
    if (showSource) VernAlertDialog(onDismissRequest = { showSource = false }, title = { Text("Dictionary source") },
        text = { Text(EnglishDictionaryPack.CREDIT + "\n\nThe offline pack contains English nouns, verbs, adjectives and adverbs. Switch to Online to search the online dictionary services.") },
        confirmButton = { TextButton({ context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(EnglishDictionaryPack.LICENSE))) }) { Text("License") } },
        dismissButton = { TextButton({ showSource = false }) { Text("Done") } })

    Column(modifier.widthIn(max = 760.dp).fillMaxWidth().testTag("offline-dictionary"), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                listOf(DictionaryLookupSource.OFFLINE, DictionaryLookupSource.ONLINE).forEachIndexed { index, source ->
                    SegmentedButton(selected = lookupSource == source, onClick = { selectSource(source) },
                        shape = SegmentedButtonDefaults.itemShape(index, 2), modifier = Modifier.testTag("dictionary-source-${source.name.lowercase()}")) {
                        Text(if (source == DictionaryLookupSource.OFFLINE) "Offline" else "Online")
                    }
                }
            }
            Box {
                IconButton(onClick = { showOptions = true }) { Icon(Icons.Outlined.MoreVert, "Dictionary options") }
                DropdownMenu(expanded = showOptions, onDismissRequest = { showOptions = false }) {
                    DropdownMenuItem(text = { Text("Offline dictionary") }, onClick = { showOptions = false; showPackDetails = true })
                    if (lookupSource == DictionaryLookupSource.OFFLINE) DropdownMenuItem(text = { Text("Source & license") },
                        onClick = { showOptions = false; showSource = true })
                }
            }
            if (onClose != null) IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close dictionary") }
        }
        OutlinedTextField(query, {
            query = it.take(80)
            if (onClose == null && lookupSource == DictionaryLookupSource.OFFLINE) { browsing = true; browsePrefix = query; browsePages = emptyList() }
        }, singleLine = true,
            label = { Text(if (browsing && pack != null) "Filter word list" else "Word or phrase") }, modifier = Modifier.fillMaxWidth().testTag("dictionary-query"),
            shape = VeritasPackStyle.compactShape(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }),
            trailingIcon = { IconButton(onClick = { search() }, enabled = query.isNotBlank() && !loading && (lookupSource == DictionaryLookupSource.ONLINE || pack != null),
                modifier = Modifier.testTag("dictionary-search")) { Icon(Icons.Outlined.Search, "Search definitions") } })

        if (lookupSource == DictionaryLookupSource.OFFLINE && pack == null) {
            Text("Download the English dictionary to browse words and look up definitions offline.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            OfflineDictionaryDownloadControls(work, onDownload = { DictionaryDownloadWorker.enqueue(context) }, onCancel = ::cancelDownload)
        }
        if (lookupSource == DictionaryLookupSource.OFFLINE && onClose == null && pack != null) {
            if (browsing) OfflineDictionaryWordList(
                prefix = browsePrefix, after = browsePages.lastOrNull(), pageNumber = browsePages.size + 1,
                onPrefixChange = { browsePrefix = it; query = it; browsePages = emptyList() },
                onPrevious = { browsePages = browsePages.dropLast(1) },
                onNext = { if (browsePages.lastOrNull() != it) browsePages = browsePages + it }, onOpenWord = { search(it) })
            else TextButton(onClick = { browsing = true; query = browsePrefix }, modifier = Modifier.testTag("dictionary-browse")) {
                Text("Back to word list")
            }
        }
        if (loading) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("Looking up $submitted…", style = MaterialTheme.typography.bodyMedium)
        }
        error?.let { Text(it, color = colors.error) }
        result?.takeUnless { browsing }?.let { found ->
            val entry = found.entry
            val senses = entry?.senses ?: found.meaning.senses.ifEmpty {
                if (found.meaning.definition == VocabularyLookup.UNAVAILABLE) emptyList()
                else listOf(DictionarySense("", found.meaning.definition, found.meaning.examples, emptyList()))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(entry?.headword ?: submitted, style = MaterialTheme.typography.headlineLarge.copy(fontFamily = VeritasUiFont.GAZETTE.fontFamily()), fontWeight = FontWeight.Normal)
                found.meaning.pronunciation?.let { Text(it, color = colors.onSurfaceVariant, fontStyle = FontStyle.Italic) }
                if (entry != null && entry.headword != WordNetDictionary.query(submitted)) Text("$submitted → ${entry.headword}", style = MaterialTheme.typography.bodySmall, color = colors.primary)
                if (found.saved) Text(if (senses.isNotEmpty()) "Saved to Vocabulary" else "Saved to Vocabulary · meaning unavailable", style = MaterialTheme.typography.bodySmall, color = colors.primary)
            }
            if (senses.isEmpty()) Text(if (lookupSource == DictionaryLookupSource.OFFLINE) "This word is not in the offline dictionary. Try Online or another spelling."
                else "No online definition is available right now. Check your connection or try another spelling.", color = colors.onSurfaceVariant)
            senses.forEachIndexed { index, sense ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = .45f))
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("${index + 1}.", style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (sense.partOfSpeech.isNotBlank()) Text(sense.partOfSpeech, style = MaterialTheme.typography.labelMedium, color = colors.primary)
                            Text(sense.definition, style = MaterialTheme.typography.bodyLarge)
                            sense.examples.forEach { Text("“$it”", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = colors.onSurfaceVariant) }
                            if (sense.synonyms.isNotEmpty()) {
                                Text("Synonyms", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    sense.synonyms.forEach { synonym -> SuggestionChip(onClick = { search(synonym) }, label = { Text(synonym) }) }
                                }
                            }
                        }
                    }
                }
            }
            if (senses.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { copyTextToClipboard(context, "Dictionary definition", "$submitted\n${found.meaning.definition}\n${found.meaning.examples.joinToString("\n")}") }) {
                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Copy definition")
                }
                TextButton(enabled = entry != null || found.meaning.sourceUrl != null,
                    onClick = { if (entry != null) showSource = true else found.meaning.sourceUrl?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } }) { Text("Source") }
            }
        }
        if (request.isBlank() && lookupSource == DictionaryLookupSource.ONLINE) Text("Search for a word online. Opened words are saved to Vocabulary with their meaning and available examples.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
    }
}

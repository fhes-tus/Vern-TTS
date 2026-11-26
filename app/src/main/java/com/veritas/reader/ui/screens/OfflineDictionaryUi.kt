package com.veritas.reader.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Translate
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.veritas.reader.R
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
    // A fixed viewport keeps scrolling from repeatedly moving the sheet's height anchors.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetGesturesEnabled = false, dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().height((LocalConfiguration.current.screenHeightDp * .86f).dp)
            .verticalScroll(rememberScrollState()).imePadding()) {
            OfflineDictionaryPanel(query, anchor, onSaved, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), onDismiss,
                saveLookupsAutomatically = true)
        }
    }
}

/** The same real dictionary content is used in Study Hub and the contextual reader sheet. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun OfflineDictionaryPanel(initialQuery: String = "", anchor: DictionaryLookupAnchor = DictionaryLookupAnchor(),
    onSaved: (List<GeneralNote>) -> Unit = {}, modifier: Modifier = Modifier, onClose: (() -> Unit)? = null,
    saveLookupsAutomatically: Boolean = false) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf(initialQuery.take(80)) }
    var submitted by rememberSaveable { mutableStateOf(initialQuery.take(80)) }
    var request by rememberSaveable { mutableStateOf(if (WordNetDictionary.query(initialQuery) == null) "" else UUID.randomUUID().toString()) }
    var result by remember { mutableStateOf<SavedDictionaryLookup?>(null) }
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pack by remember { mutableStateOf<DictionaryPackInfo?>(null) }
    var packChecked by remember { mutableStateOf(false) }
    var work by remember { mutableStateOf<WorkInfo?>(null) }
    var showRemove by remember { mutableStateOf(false) }
    var showSource by remember { mutableStateOf(false) }
    var translationText by remember { mutableStateOf<String?>(null) }
    var showPackDetails by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var lookupSource by rememberSaveable { mutableStateOf<DictionaryLookupSource?>(null) }
    var browsing by rememberSaveable { mutableStateOf(false) }
    var browsePrefix by rememberSaveable { mutableStateOf("") }
    var browsePages by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val savedCallback by rememberUpdatedState(onSaved)
    LaunchedEffect(context) {
        val (hasInstalledFile, installedPack, manager) = withContext(Dispatchers.IO) {
            val installed = OfflineDictionaryStore.isInstalled(context)
            Triple(installed, if (installed) OfflineDictionaryStore.info(context) else null, WorkManager.getInstance(context))
        }
        pack = installedPack
        if (lookupSource == null) {
            lookupSource = if (hasInstalledFile) DictionaryLookupSource.OFFLINE else DictionaryLookupSource.ONLINE
            browsing = onClose == null && initialQuery.isBlank() && hasInstalledFile
        }
        packChecked = true
        manager.getWorkInfosForUniqueWorkFlow(DictionaryDownloadWorker.NAME).collect { infos ->
            work = infos.firstOrNull { !it.state.isFinished } ?: infos.firstOrNull()
            if (work?.state?.isFinished == true) pack = withContext(Dispatchers.IO) { OfflineDictionaryStore.info(context) }
        }
    }
    val lookupAvailable = lookupSource != DictionaryLookupSource.OFFLINE || pack != null
    LaunchedEffect(request, lookupSource, packChecked, lookupAvailable) {
        val source = lookupSource ?: return@LaunchedEffect
        if (!packChecked || request.isBlank()) return@LaunchedEffect
        if (!lookupAvailable) {
            loading = false
            return@LaunchedEffect
        }
        val lookupRequest = request
        val lookupWord = submitted
        loading = true; result = null; error = null
        try {
            val found = if (!saveLookupsAutomatically) lookupDictionaryWord(context, lookupWord, source, anchor)
                else lookupAndSaveDictionaryWord(context, lookupWord, anchor, lookupRequest, source)
            if (request != lookupRequest) return@LaunchedEffect
            result = found
            if (found.saved) savedCallback(found.notes)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { if (request == lookupRequest) error = "Could not finish this lookup. Please try again." }
        finally { if (request == lookupRequest) loading = false }
    }
    fun search(word: String = query, updateField: Boolean = true) {
        if (!packChecked || WordNetDictionary.query(word) == null || (updateField && !lookupAvailable)) return
        browsing = false
        submitted = word.take(80)
        if (updateField) query = submitted
        result = null; error = null; request = UUID.randomUUID().toString()
        loading = lookupAvailable
        focus.clearFocus(); keyboard?.hide()
    }
    fun selectSource(source: DictionaryLookupSource) {
        if (source == lookupSource) return
        val reopenWord = !browsing && request.isNotBlank() && WordNetDictionary.query(submitted) != null
        lookupSource = source; result = null; error = null; request = ""; loading = false
        browsing = source == DictionaryLookupSource.OFFLINE && onClose == null && !reopenWord
        if (reopenWord) search(submitted, updateField = false)
    }
    fun cancelDownload() { scope.launch { withContext(Dispatchers.IO) {
        WorkManager.getInstance(context).cancelUniqueWork(DictionaryDownloadWorker.NAME).result.get()
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
        title = { Text(menuString(R.string.dict_remove_title)) },
        text = { Text(menuString(R.string.dict_remove_message)) },
        confirmButton = { TextButton(onClick = {
            showRemove = false
            scope.launch { try {
                withContext(Dispatchers.IO) {
                    WorkManager.getInstance(context).cancelUniqueWork(DictionaryDownloadWorker.NAME).result.get()
                    OfflineDictionaryStore.remove(context)
                }
                pack = null
            } catch (_: Exception) { error = "Could not remove the dictionary. Try again." } }
        }) { Text(menuString(R.string.dict_remove_action)) } }, dismissButton = { TextButton({ showRemove = false }) { Text(menuString(R.string.dict_cancel)) } })
    translationText?.let { DefinitionTranslationDialog(it, onClose = { translationText = null }) }
    if (showSource) VernAlertDialog(onDismissRequest = { showSource = false }, title = { Text(menuString(R.string.dict_source_title)) },
        text = { Text(EnglishDictionaryPack.CREDIT + "\n\nThe offline pack contains English nouns, verbs, adjectives and adverbs. Switch to Online to search the online dictionary services.") },
        confirmButton = { TextButton({ context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(EnglishDictionaryPack.LICENSE))) }) { Text(menuString(R.string.dict_license)) } },
        dismissButton = { TextButton({ showSource = false }) { Text(menuString(R.string.dict_done)) } })

    Column(modifier.widthIn(max = 760.dp).fillMaxWidth().testTag("offline-dictionary"), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (!packChecked) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(menuString(R.string.ui_loading), Modifier.weight(1f))
                if (onClose != null) IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, menuString(R.string.dict_close)) }
            }
            return@Column
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                listOf(DictionaryLookupSource.OFFLINE, DictionaryLookupSource.ONLINE).forEachIndexed { index, source ->
                    SegmentedButton(selected = lookupSource == source, onClick = { selectSource(source) },
                        shape = SegmentedButtonDefaults.itemShape(index, 2), modifier = Modifier.testTag("dictionary-source-${source.name.lowercase()}")) {
                        Text(if (source == DictionaryLookupSource.OFFLINE) menuString(R.string.dict_offline) else menuString(R.string.dict_online))
                    }
                }
            }
            Box {
                IconButton(onClick = { showOptions = true }) { Icon(Icons.Outlined.MoreVert, menuString(R.string.dict_options)) }
                DropdownMenu(expanded = showOptions, onDismissRequest = { showOptions = false }) {
                    DropdownMenuItem(
                        text = { Text(menuString(R.string.definition_google_translate)) },
                        leadingIcon = { Icon(Icons.Outlined.Translate, null) },
                        enabled = result != null || query.isNotBlank(),
                        onClick = {
                            showOptions = false
                            translationText = result?.translationContent() ?: query.trim()
                        }
                    )
                    DropdownMenuItem(text = { Text(menuString(R.string.dict_offline_pack_title)) }, onClick = { showOptions = false; showPackDetails = true })
                    if (lookupSource == DictionaryLookupSource.OFFLINE) DropdownMenuItem(text = { Text(menuString(R.string.dict_source_license)) },
                        onClick = { showOptions = false; showSource = true })
                    if (lookupSource == DictionaryLookupSource.ONLINE) result?.meaning?.sourceUrl?.let { sourceUrl ->
                        DropdownMenuItem(text = { Text(menuString(R.string.dict_source)) }, onClick = {
                            showOptions = false
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sourceUrl))) }
                        })
                    }
                }
            }
            if (onClose != null) IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, menuString(R.string.dict_close)) }
        }
        OutlinedTextField(query, {
            query = it.take(80)
            if (onClose == null && lookupSource == DictionaryLookupSource.OFFLINE) { browsing = true; browsePrefix = query; browsePages = emptyList() }
        }, singleLine = true,
            label = { Text(if (browsing && pack != null) menuString(R.string.dict_filter_word_list) else menuString(R.string.dict_word_or_phrase)) }, modifier = Modifier.fillMaxWidth().testTag("dictionary-query"),
            shape = VeritasPackStyle.compactShape(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }),
            trailingIcon = { IconButton(onClick = { search() }, enabled = query.isNotBlank() && !loading && (lookupSource == DictionaryLookupSource.ONLINE || pack != null),
                modifier = Modifier.testTag("dictionary-search")) { Icon(Icons.Outlined.Search, menuString(R.string.dict_search_definitions)) } })

        if (lookupSource == DictionaryLookupSource.OFFLINE && pack == null) {
            Text(menuString(R.string.dict_download_prompt), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            OfflineDictionaryDownloadControls(work, onDownload = { DictionaryDownloadWorker.enqueue(context) }, onCancel = ::cancelDownload)
        }
        if (lookupSource == DictionaryLookupSource.OFFLINE && onClose == null && pack != null) {
            if (browsing) OfflineDictionaryWordList(
                prefix = browsePrefix, after = browsePages.lastOrNull(), pageNumber = browsePages.size + 1,
                onPrefixChange = { browsePrefix = it; query = it; browsePages = emptyList() },
                onPrevious = { browsePages = browsePages.dropLast(1) },
                onNext = { if (browsePages.lastOrNull() != it) browsePages = browsePages + it }, onOpenWord = { search(it) })
            else TextButton(onClick = { browsing = true; query = browsePrefix }, modifier = Modifier.testTag("dictionary-browse")) {
                Text(menuString(R.string.dict_back_to_list))
            }
        }
        if (loading) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(menuString(R.string.dict_looking_up, submitted), style = MaterialTheme.typography.bodyMedium)
        }
        error?.let { Text(it, color = colors.error) }
        result?.takeUnless { browsing }?.let { found ->
            val entry = found.entry
            val senses = entry?.senses ?: found.meaning.senses.ifEmpty {
                if (found.meaning.definition == VocabularyLookup.UNAVAILABLE) emptyList()
                else listOf(DictionarySense("", found.meaning.definition, found.meaning.examples, emptyList()))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(found.displayedWord, style = MaterialTheme.typography.headlineLarge.copy(fontFamily = VeritasUiFont.GAZETTE.fontFamily()), fontWeight = FontWeight.Normal)
                found.meaning.pronunciation?.let { Text(it, color = colors.onSurfaceVariant, fontStyle = FontStyle.Italic) }
                if (entry != null && found.displayedWord != found.word) Text("${found.word} → ${found.displayedWord}", style = MaterialTheme.typography.bodySmall, color = colors.primary)
                if (found.saved) Text(if (senses.isNotEmpty()) menuString(R.string.dict_saved_to_vocab) else menuString(R.string.dict_saved_to_vocab_no_meaning), style = MaterialTheme.typography.bodySmall, color = colors.primary)
            }
            if (!saveLookupsAutomatically && senses.isNotEmpty()) TextButton(
                enabled = !found.saved && !saving,
                modifier = Modifier.testTag("dictionary-save-vocabulary"),
                onClick = {
                    val savingRequest = request
                    error = null
                    saving = true
                    scope.launch {
                        try {
                            val saved = saveDictionaryLookupResult(context, found)
                            if (request == savingRequest) result = saved
                            savedCallback(saved.notes)
                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (_: Exception) { if (request == savingRequest) error = resources.getString(R.string.dict_save_error) }
                        finally { saving = false }
                    }
                }) { Text(menuString(if (found.saved) R.string.dict_saved_vocabs else if (saving) R.string.dict_saving_vocabs else R.string.dict_save_vocabs)) }
            if (senses.isEmpty()) Text(if (found.source == DictionaryLookupSource.OFFLINE) menuString(R.string.dict_not_found_offline)
                else menuString(R.string.dict_not_found_online), color = colors.onSurfaceVariant)
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
                                Text(menuString(R.string.dict_synonyms), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    sense.synonyms.forEach { synonym -> SuggestionChip(onClick = { search(synonym) }, label = { Text(synonym) }) }
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (senses.isNotEmpty()) TextButton(onClick = { copyTextToClipboard(context, "Dictionary definition", "${found.displayedWord}\n${found.meaning.definition}\n${found.meaning.examples.joinToString("\n")}") }) {
                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(menuString(R.string.dict_copy_definition))
                }
                TextButton(onClick = { translationText = found.translationContent() }) {
                    Icon(Icons.Outlined.Translate, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(menuString(R.string.definition_google_translate))
                }
            }
        }
        if (request.isBlank() && lookupSource == DictionaryLookupSource.ONLINE) Text(
            if (!saveLookupsAutomatically) menuString(R.string.dict_optional_save_hint)
            else menuString(R.string.dict_online_hint), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
    }
}

private fun SavedDictionaryLookup.translationContent(): String = buildString {
    append(displayedWord)
    val definitions = entry?.senses ?: meaning.senses
    if (definitions.isEmpty()) {
        if (meaning.definition != VocabularyLookup.UNAVAILABLE) append("\n\n").append(meaning.definition)
    } else {
        definitions.forEach { sense ->
            append("\n\n")
            if (sense.partOfSpeech.isNotBlank()) append(sense.partOfSpeech).append(": ")
            append(sense.definition)
        }
    }
}

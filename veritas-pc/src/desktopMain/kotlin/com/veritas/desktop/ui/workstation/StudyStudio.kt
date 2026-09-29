package com.veritas.desktop.ui.workstation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.desktop.audio.PlaybackState
import com.veritas.desktop.models.DesktopDocument
import com.veritas.desktop.models.DesktopFontFamily
import com.veritas.desktop.models.ReaderSettings
import com.veritas.desktop.models.StudyPack
import com.veritas.desktop.models.TextAnnotation
import com.veritas.desktop.study.StudyAssistant

@Composable
fun StudyStudio(
    state: PlaybackState,
    annotations: List<TextAnnotation>,
    onAddAnnotation: (String, String) -> Unit,
    onDeleteAnnotation: (String) -> Unit,
    onSelectVoice: (String) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetPitch: (Float) -> Unit,
    onSetSleepTimer: (Int?) -> Unit,
    onUpdateReaderSettings: (ReaderSettings) -> Unit,
    onCloseStudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Study Hub, 1: Notes, 2: Voice & Reader
    val doc = state.activeDocument

    var studyPack by remember(doc?.id) {
        mutableStateOf<StudyPack?>(null)
    }

    Surface(
        modifier = modifier.fillMaxHeight().width(380.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Study & Voice Studio",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = onCloseStudio, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close Studio", modifier = Modifier.size(18.dp))
                }
            }

            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Study Hub", fontSize = 12.sp) },
                    icon = { Icon(Icons.Default.Psychology, null, modifier = Modifier.size(16.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Notes (${annotations.size})", fontSize = 12.sp) },
                    icon = { Icon(Icons.Default.NoteAlt, null, modifier = Modifier.size(16.dp)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Audio & Text", fontSize = 12.sp) },
                    icon = { Icon(Icons.Default.Tune, null, modifier = Modifier.size(16.dp)) }
                )
            }

            // Tab Content
            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                when (selectedTab) {
                    0 -> StudyHubTab(
                        doc = doc,
                        studyPack = studyPack,
                        onGenerate = {
                            if (doc != null) {
                                studyPack = StudyAssistant.generateStudyPack(doc.title, doc.chunks, state.currentIndex)
                            }
                        }
                    )
                    1 -> NotesTab(
                        annotations = annotations,
                        onDeleteAnnotation = onDeleteAnnotation
                    )
                    2 -> AudioAndTextTab(
                        state = state,
                        onSelectVoice = onSelectVoice,
                        onSetSpeed = onSetSpeed,
                        onSetPitch = onSetPitch,
                        onSetSleepTimer = onSetSleepTimer,
                        onUpdateReaderSettings = onUpdateReaderSettings
                    )
                }
            }
        }
    }
}

@Composable
private fun StudyHubTab(
    doc: DesktopDocument?,
    studyPack: StudyPack?,
    onGenerate: () -> Unit
) {
    if (doc == null) {
        Text("Please open a document first to generate study tools.", style = MaterialTheme.typography.bodySmall)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Button(
                onClick = onGenerate,
                modifier = Modifier.fillMaxWidth().height(42.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate AI Summary & Flashcards", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (studyPack != null) {
            // Summary Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Summarize, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Executive Summary", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(studyPack.summary, style = MaterialTheme.typography.bodySmall, lineHeight = 20.sp)
                    }
                }
            }

            // Key Points
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Key Takeaways", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        for (point in studyPack.keyPoints) {
                            Row(modifier = Modifier.padding(vertical = 3.dp)) {
                                Text("• ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Text(point, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // Flashcards Section
            item {
                Text("Interactive Flashcards (${studyPack.flashcards.size})", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }

            items(studyPack.flashcards) { card ->
                var isFlipped by remember { mutableStateOf(false) }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { isFlipped = !isFlipped },
                    color = if (isFlipped) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (isFlipped) "ANSWER:" else "QUESTION (click to reveal):",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isFlipped) card.answer else card.question,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isFlipped) FontWeight.Normal else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesTab(
    annotations: List<TextAnnotation>,
    onDeleteAnnotation: (String) -> Unit
) {
    if (annotations.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.EditNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("No notes added yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Click the note icon on any sentence to attach a note.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(annotations, key = { it.id }) { note ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Sentence ${note.chunkIndex + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        IconButton(onClick = { onDeleteAnnotation(note.id) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        }
                    }
                    if (note.selectedText.isNotBlank()) {
                        Text("\"${note.selectedText}\"", style = MaterialTheme.typography.bodySmall, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(note.noteContent, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun AudioAndTextTab(
    state: PlaybackState,
    onSelectVoice: (String) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetPitch: (Float) -> Unit,
    onSetSleepTimer: (Int?) -> Unit,
    onUpdateReaderSettings: (ReaderSettings) -> Unit
) {
    var voiceDropdownExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Voice Selection
        item {
            Text("Windows Speech Voice", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))

            Box {
                OutlinedButton(
                    onClick = { voiceDropdownExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        state.voiceSettings.voiceName.ifBlank { "Select Voice" },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.ArrowDropDown, null)
                }

                DropdownMenu(
                    expanded = voiceDropdownExpanded,
                    onDismissRequest = { voiceDropdownExpanded = false }
                ) {
                    for (v in state.availableVoices) {
                        DropdownMenuItem(
                            text = { Text(v.displayName) },
                            onClick = {
                                onSelectVoice(v.name)
                                voiceDropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Speed Slider
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Reading Speed", style = MaterialTheme.typography.labelMedium)
                Text(String.format(java.util.Locale.US, "%.2fx", state.voiceSettings.rate), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = state.voiceSettings.rate,
                onValueChange = onSetSpeed,
                valueRange = 0.5f..2.5f,
                steps = 19
            )
        }

        // Sleep Timer
        item {
            Text("Sleep Timer", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(null, 15, 30, 45, 60).forEach { mins ->
                    val isSelected = state.sleepTimerMinutesRemaining == mins
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSetSleepTimer(mins) },
                        label = { Text(if (mins == null) "Off" else "${mins}m", fontSize = 11.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }
        }

        item {
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        }

        // Typography settings
        item {
            Text("Typography & Ergonomics", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Font Size", style = MaterialTheme.typography.labelMedium)
                Text("${state.readerSettings.fontSize.toInt()} sp", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = state.readerSettings.fontSize,
                onValueChange = { onUpdateReaderSettings(state.readerSettings.copy(fontSize = it)) },
                valueRange = 14f..28f,
                steps = 13
            )
        }

        item {
            Text("Font Family", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(DesktopFontFamily.DEFAULT, DesktopFontFamily.ATKINSON, DesktopFontFamily.LITERATA).forEach { font ->
                    FilterChip(
                        selected = state.readerSettings.fontFamily == font,
                        onClick = { onUpdateReaderSettings(state.readerSettings.copy(fontFamily = font)) },
                        label = { Text(font.label, fontSize = 11.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }
        }
    }
}

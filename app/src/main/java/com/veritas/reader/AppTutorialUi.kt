package com.veritas.reader

import android.speech.tts.TextToSpeech
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

internal data class TutorialFrame(
    val number: String,
    val title: String,
    val body: String,
    val icon: String,
    val action: (() -> Unit)?
)

@Composable
internal fun TutorialDialog(
    initialName: String,
    onDismiss: (String) -> Unit,
    onImport: (String) -> Unit,
    onVoice: (String) -> Unit,
    onThemes: (String) -> Unit
) {
    val context = LocalContext.current
    var nameDraft by rememberSaveable { mutableStateOf(initialName) }
    var ttsReady by remember { mutableStateOf(false) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    val steps = remember {
        listOf(
            TutorialFrame(
                "1",
                "Welcome to Vern",
                "Transform your research, documents, and reading materials into focused audio experiences.",
                "📖",
                null
            ),
            TutorialFrame(
                "2",
                "What should we call you?",
                "Your name personalizes the Home tab and reading experience.",
                "👤",
                null
            ),
            TutorialFrame(
                "3",
                "Add a reading",
                "Import PDFs, EPUBs, Word documents, text files, images, or paste texts and links.",
                "➕",
                { onImport(nameDraft) }),
            TutorialFrame(
                "4",
                "Read your way",
                "Switch between Extracted Text, Listen Mode, and the Original PDF/Image layouts.",
                "📄",
                null
            ),
            TutorialFrame(
                "5",
                "Listen & synthesis",
                "Pick voices, adjust speed/pitch, and control playback from the expandable panel or system notification.",
                "🎧",
                { onVoice(nameDraft) }),
            TutorialFrame(
                "6",
                "Mark & remember",
                "Bookmark sentences to highlight them, add study notes, translate text, search, and fix pronunciation.",
                "🔖",
                null
            ),
            TutorialFrame(
                "7",
                "Make it yours",
                "Choose from 10+ premium color themes, set up widget shortcuts, and export standard WAV audio files.",
                "🎨",
                { onThemes(nameDraft) }),
            TutorialFrame(
                "8",
                "Ready to read?",
                "Your calm reading environment is configured. Open the library and import your first document.",
                "🚀",
                null
            )
        )
    }
    var stepIndex by remember { mutableIntStateOf(0) }
    val pulse by animateFloatAsState(
        targetValue = if (stepIndex % 2 == 0) 1.08f else 0.94f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "tutorialPulse"
    )
    DisposableEffect(context) {
        val engine = TextToSpeech(context.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
        }
        tts = engine
        onDispose {
            engine.stop()
            engine.shutdown()
        }
    }
    LaunchedEffect(stepIndex, ttsReady, nameDraft) {
        if (ttsReady) {
            val greeting = nameDraft.trim().ifBlank { "reader" }
            val frame = steps[stepIndex]
            val spoken = if (stepIndex == 0) {
                "Welcome to Vern. Transform your research, documents, and reading materials into focused audio experiences."
            } else if (stepIndex == 1) {
                "What should we call you? This name will appear on your dashboard."
            } else if (stepIndex == steps.lastIndex) {
                "Ready to read, $greeting. Your setup is complete."
            } else {
                "${frame.title}. ${frame.body}"
            }
            tts?.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "vern-onboarding-$stepIndex")
        }
    }
    Dialog(
        onDismissRequest = { onDismiss(nameDraft) },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Vern setup",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onDismiss(nameDraft) }) { Text("Skip") }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (stepIndex == 0) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(112.dp)
                                    .graphicsLayer(scaleX = pulse, scaleY = pulse)
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.shapes.large
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                BrandMark(compact = true)
                            }
                        }
                        Text(
                            "Welcome to Vern",
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "Transform your research, documents, and reading materials into high-quality, focused audio experiences.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (stepIndex == 1) {
                        Text(
                            "What should we call you?",
                            style = MaterialTheme.typography.headlineMedium)
                        OutlinedTextField(
                            value = nameDraft,
                            onValueChange = { nameDraft = it.take(48) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Your preferred name") },
                            placeholder = { Text("Name for the Home tab welcome") },
                            singleLine = true,
                            shape = RoundedCornerShape(50)
                        )
                        Text(
                            "This is used to personalize your Home tab and reading experience.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (stepIndex >= 2) {
                        TutorialStage(
                            frame = steps[stepIndex],
                            progress = (stepIndex + 1).toFloat() / steps.size.toFloat(),
                            pulse = pulse
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        steps.forEachIndexed { index, _ ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .width(if (index == stepIndex) 28.dp else 8.dp)
                                    .height(8.dp)
                                    .background(
                                        if (index == stepIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        CircleShape
                                    )
                                    .clickable { stepIndex = index }
                            )
                        }
                    }
                    steps.drop(2).dropLast(1).forEachIndexed { offset, frame ->
                        val index = offset + 2
                        TutorialStep(
                            number = frame.number,
                            title = frame.title,
                            body = frame.body,
                            action = frame.action,
                            selected = index == stepIndex,
                            onSelect = { stepIndex = index }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { stepIndex = (stepIndex - 1).coerceAtLeast(0) },
                        enabled = stepIndex > 0,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50)
                    ) { Text("Back") }
                    Button(
                        onClick = {
                            if (stepIndex >= steps.lastIndex) onDismiss(nameDraft) else stepIndex += 1
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(if (stepIndex >= steps.lastIndex) "Go to Home" else "Next")
                    }
                }
            }
        }
    }
}

@Composable
internal fun TutorialStage(frame: TutorialFrame, progress: Float, pulse: Float) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .background(
                        VeritasPackStyle.backgroundBrush(MaterialTheme.colorScheme),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(118.dp)
                        .graphicsLayer(scaleX = pulse, scaleY = pulse)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        frame.icon,
                        style = MaterialTheme.typography.displaySmall)
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp)
                        .fillMaxWidth(0.62f)
                        .height(6.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(6.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }
            }
            Text(
                frame.title,
                style = MaterialTheme.typography.titleMedium)
            Text(frame.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (frame.action != null) {
                Button(onClick = frame.action, shape = RoundedCornerShape(50)) { Text("Open") }
            }
        }
    }
}

@Composable
internal fun TutorialStep(
    number: String,
    title: String,
    body: String,
    action: (() -> Unit)?,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.02f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "tutorialStepBounce"
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clickable { onSelect() },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) { Text(number, fontWeight = FontWeight.Black) }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(title, fontWeight = FontWeight.Black)
                Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (action != null) {
                Button(onClick = action, shape = RoundedCornerShape(50)) { Text("Open") }
            }
        }
    }
}

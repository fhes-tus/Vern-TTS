package com.veritas.reader.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.veritas.reader.BrandMark
import com.veritas.reader.R
import com.veritas.reader.TutorialSpeaker
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.ui.LocalVeritasMotion
import com.veritas.reader.ui.ReadingInterestOptions
import com.veritas.reader.ui.VeritasMotion
import com.veritas.reader.ui.pressScale
import com.veritas.reader.ui.rememberPlaybackWavePhase
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.sin

private enum class SetupPage { WELCOME, READING, LISTENING, STUDY, READY }

private val SetupGutter = 20.dp
private const val SetupArtRatio = 9f / 6f
internal val OnboardingPaper = androidx.compose.ui.graphics.Color(0xFFF5ECE4)

private val setupAssistants = linkedMapOf(
    "gemini" to "Google Gemini",
    "chatgpt" to "ChatGPT",
    "claude" to "Claude",
    "copilot" to "Microsoft Copilot",
    "perplexity" to "Perplexity",
    "grok" to "Grok"
)

@Composable
fun RevampedOnboardingFlow(
    initialUserName: String,
    initialReadingInterest: String,
    initialAiAssistant: String,
    onComplete: (name: String, interest: String, aiAssistant: String, speed: Float, pitch: Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialRate: Float = 1f,
    initialPitch: Float = 1f
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val reduceMotion = LocalVeritasMotion.current.reduceMotion
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    val slideDistance = with(LocalDensity.current) { 22.dp.roundToPx() } * direction
    val fade = VeritasMotion.effects<Float>()
    val slide = VeritasMotion.spatial<IntOffset>()

    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable {
        mutableStateOf(initialUserName.takeUnless { it.equals("Reader", true) }.orEmpty())
    }
    var interest by rememberSaveable {
        mutableStateOf(initialReadingInterest.ifBlank { "Books & Novels" })
    }
    var assistant by rememberSaveable { mutableStateOf(initialAiAssistant.ifBlank { "chooser" }) }
    var rate by rememberSaveable { mutableFloatStateOf(initialRate.coerceIn(.5f, 2f)) }
    var finishing by remember { mutableStateOf(false) }
    val currentPage = SetupPage.entries[step]
    val continueInteraction = remember { MutableInteractionSource() }

    DisposableEffect(context) {
        TutorialSpeaker.init(context)
        onDispose { TutorialSpeaker.shutdown() }
    }

    fun leavePage() {
        focusManager.clearFocus()
        TutorialSpeaker.stop()
    }

    fun previous() {
        if (step > 0 && !finishing) {
            leavePage()
            step--
        }
    }

    BackHandler { previous() }

    OnboardingSurface(modifier) {
        Box(
            Modifier.statusBarsPadding().navigationBarsPadding().imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxSize()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = SetupGutter).heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BrandMark(Modifier.clearAndSetSemantics {}, compact = true, size = 22.dp)
                        Text(menuString(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                    }
                    if (currentPage != SetupPage.READY) {
                        TextButton(
                            enabled = !finishing,
                            onClick = {
                                finishing = true
                                leavePage()
                                onDismiss()
                            }
                        ) { Text(menuString(R.string.ui_set_up_later)) }
                    }
                }
                Box(Modifier.padding(horizontal = SetupGutter)) { SetupProgress(step) }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val bodyHeight = maxHeight
                    AnimatedContent(
                        targetState = currentPage,
                        modifier = Modifier.fillMaxSize().clipToBounds()
                            .pointerInput(step, direction, finishing) {
                                if (finishing) return@pointerInput
                                var distance = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { distance = 0f },
                                    onDragEnd = {
                                        val travel = distance * direction
                                        val threshold = 56.dp.toPx()
                                        if (travel > threshold) previous()
                                        if (travel < -threshold && step < SetupPage.entries.lastIndex) {
                                            leavePage()
                                            step++
                                        }
                                    },
                                    onHorizontalDrag = { _, amount -> distance += amount }
                                )
                            },
                        transitionSpec = {
                            val offset = if (targetState > initialState) slideDistance else -slideDistance
                            val enter = fadeIn(fade)
                            val exit = fadeOut(fade)
                            if (reduceMotion) {
                                enter.togetherWith(exit).using(null)
                            } else {
                                (enter + slideInHorizontally(slide) { offset })
                                    .togetherWith(exit + slideOutHorizontally(slide) { -offset })
                                    .using(null)
                            }
                        },
                        label = "Vern setup page"
                    ) { page ->
                        val active = page == currentPage && !finishing
                        val pageSemantics = if (page == currentPage) Modifier else Modifier.clearAndSetSemantics {}
                        Column(
                            Modifier.fillMaxWidth().then(pageSemantics)
                                .verticalScroll(rememberScrollState(), enabled = active)
                                .heightIn(min = bodyHeight)
                                .padding(vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(
                                16.dp,
                                if (page == SetupPage.READING) Alignment.Top else Alignment.CenterVertically
                            )
                        ) {
                            when (page) {
                                SetupPage.WELCOME -> {
                                    SetupHero(R.drawable.vern_setup_read)
                                    SetupDetails(R.string.vern_setup_welcome, R.string.vern_setup_welcome_body)
                                }
                                SetupPage.READING -> {
                                    SetupDetails(R.string.vern_setup_reading, R.string.vern_setup_reading_body)
                                    SetupHero(R.drawable.vern_setup_bookshelf)
                                    Box(Modifier.padding(horizontal = SetupGutter)) {
                                        SetupReadingInterests(interest, active) { interest = it }
                                    }
                                }
                                SetupPage.LISTENING -> {
                                    SetupHero(R.drawable.vern_setup_listen)
                                    SetupDetails(R.string.vern_setup_listen, R.string.vern_setup_listen_body) {
                                        SetupListening(rate, initialPitch, active) { rate = it }
                                    }
                                }
                                SetupPage.STUDY -> {
                                    SetupHero(R.drawable.vern_setup_study)
                                    SetupDetails(R.string.vern_setup_study, R.string.vern_setup_study_body) {
                                        SetupAssistant(assistant, active) { assistant = it }
                                    }
                                }
                                SetupPage.READY -> {
                                    SetupHero(R.drawable.vern_setup_ready)
                                    SetupDetails(R.string.vern_setup_ready, R.string.vern_setup_ready_body) {
                                        OutlinedTextField(
                                            value = name,
                                            onValueChange = { name = it.take(48) },
                                            enabled = active,
                                            label = { Text(menuString(R.string.ui_your_name_optional)) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = VeritasPackStyle.compactShape()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = SetupGutter), color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = SetupGutter, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (step > 0) {
                        OutlinedIconButton(
                            onClick = ::previous,
                            enabled = !finishing,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, menuString(R.string.ui_previous_setup_step))
                        }
                    }
                    Button(
                        onClick = {
                            leavePage()
                            if (currentPage == SetupPage.READY) {
                                finishing = true
                                onComplete(name.trim().ifBlank { "Reader" }, interest, assistant, rate, initialPitch)
                            } else {
                                step++
                            }
                        },
                        enabled = !finishing,
                        interactionSource = continueInteraction,
                        shape = VeritasPackStyle.chipShape(),
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp).pressScale(continueInteraction)
                    ) {
                        Text(
                            menuString(when {
                                finishing -> R.string.vern_setup_opening
                                currentPage == SetupPage.WELCOME -> R.string.vern_setup_begin
                                currentPage == SetupPage.READY -> R.string.vern_setup_open
                                else -> R.string.ui_continue
                            }),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        if (!finishing) Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingSurface(modifier: Modifier, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = remember(context) {
        val light = com.veritas.reader.veritasColorScheme("light", context)
        light.copy(background = OnboardingPaper, surface = OnboardingPaper, onBackground = light.onSurface)
    }
    MaterialTheme(colorScheme = colors) {
        Surface(modifier.fillMaxSize().testTag("onboarding-paper"), color = OnboardingPaper, content = content)
    }
}

@Composable
private fun SetupProgress(step: Int) {
    val count = SetupPage.entries.size
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            menuString(R.string.l10n_dynamic_127, localizedUiValue(step + 1), localizedUiValue(count)),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(count) { index ->
                val color by animateColorAsState(
                    targetValue = when {
                        index == step -> colors.primary
                        index < step -> colors.primary.copy(alpha = .55f)
                        else -> colors.surfaceContainerHighest
                    },
                    animationSpec = VeritasMotion.effects(),
                    label = "Setup progress segment"
                )
                Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(color))
            }
        }
    }
}

@Composable
private fun SetupHero(image: Int) {
    OnboardingArtwork(image, aspectRatio = SetupArtRatio, modifier = Modifier.testTag("onboarding-artwork"),
        shape = RectangleShape, paper = OnboardingPaper)
}

@Composable
private fun SetupDetails(title: Int, body: Int, content: @Composable ColumnScope.() -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = SetupGutter),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                menuString(title),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                menuString(body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        content()
    }
}

@Composable
private fun SetupReadingInterests(interest: String, active: Boolean, onInterest: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ReadingInterestOptions.forEach { option ->
            val selected = interest == option.label
            val color by animateColorAsState(
                if (selected) colors.secondaryContainer else colors.surfaceContainerLow,
                VeritasMotion.effects(),
                label = "Reading interest selection"
            )
            val shape = VeritasPackStyle.compactShape()
            Surface(
                modifier = Modifier.fillMaxWidth().clip(shape).selectable(
                    selected = selected,
                    enabled = active,
                    role = Role.RadioButton,
                    onClick = { onInterest(option.label) }
                ),
                color = color,
                shape = shape,
                border = BorderStroke(1.dp, if (selected) colors.secondary else colors.outlineVariant)
            ) {
                Row(
                    Modifier.heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        when (option.id) {
                            "pdf" -> Icons.Outlined.PictureAsPdf
                            "books" -> Icons.AutoMirrored.Filled.MenuBook
                            "web" -> Icons.Default.Language
                            else -> Icons.Outlined.School
                        },
                        contentDescription = null,
                        tint = colors.primary
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(option.label, style = MaterialTheme.typography.titleSmall)
                        Text(option.detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        if (selected) Icon(Icons.Default.Check, null, tint = colors.primary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupListening(rate: Float, pitch: Float, active: Boolean, onRate: (Float) -> Unit) {
    val sample = menuString(R.string.vern_setup_preview_script)
    val speedLabel = menuString(R.string.vern_setup_speed)
    val playing = TutorialSpeaker.previewActive && active
    val effect = VeritasMotion.effectsFast<Float>()
    var timedOut by remember { mutableStateOf(false) }
    LaunchedEffect(playing) {
        if (playing) {
            delay(20_000)
            TutorialSpeaker.stop()
            timedOut = true
        }
    }

    fun changeRate(newRate: Float) {
        TutorialSpeaker.stop()
        onRate(newRate.coerceIn(.5f, 2f))
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = VeritasPackStyle.cardShape(),
        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = {
                        timedOut = false
                        if (playing) TutorialSpeaker.stop()
                        else TutorialSpeaker.speakWithTuning(sample, rate, pitch)
                    },
                    enabled = active,
                    modifier = Modifier.weight(1f, fill = false).heightIn(min = 48.dp)
                ) {
                    AnimatedContent(
                        playing,
                        transitionSpec = { fadeIn(effect).togetherWith(fadeOut(effect)) },
                        label = "Voice sample play control"
                    ) { isPlaying ->
                        Icon(if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(menuString(if (playing) R.string.ui_stop_preview else R.string.ui_listen_to_a_sample))
                }
                if (LocalDensity.current.fontScale <= 1.3f) {
                    SetupPreviewWave(playing, Modifier.width(66.dp))
                }
            }
            if (timedOut || TutorialSpeaker.previewError != null) {
                Text(
                    menuString(R.string.vern_setup_audio_unavailable),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 5.dp))
            Text(
                menuString(R.string.l10n_dynamic_128, localizedUiValue(String.format(Locale.ROOT, "%.2f", rate))),
                style = MaterialTheme.typography.titleSmall
            )
            VeritasRoundSlider(
                value = rate,
                onValueChange = ::changeRate,
                valueRange = .5f..2f,
                enabled = active,
                modifier = Modifier.heightIn(min = 48.dp).semantics {
                    contentDescription = speedLabel
                    progressBarRangeInfo = ProgressBarRangeInfo(rate, .5f..2f)
                    if (active) setProgress {
                        changeRate(it)
                        true
                    }
                }
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    R.string.vern_setup_relaxed to .9f,
                    R.string.vern_setup_natural to 1f,
                    R.string.vern_setup_faster to 1.25f
                ).forEach { (label, value) ->
                    FilterChip(
                        selected = kotlin.math.abs(rate - value) < .01f,
                        onClick = { changeRate(value) },
                        enabled = active,
                        label = { Text(menuString(label)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupPreviewWave(playing: Boolean, modifier: Modifier = Modifier) {
    val motion = VeritasMotion.scheme
    val phase = rememberPlaybackWavePhase(playing && !motion.reduceMotion)
    val amplitude = animateFloatAsState(
        targetValue = if (playing && !motion.reduceMotion) 1f else 0f,
        animationSpec = motion.slowSpatial(),
        label = "Voice sample waveform amplitude"
    )
    val color by animateColorAsState(
        targetValue = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        animationSpec = motion.fastEffects(),
        label = "Voice sample waveform color"
    )
    Canvas(modifier.height(18.dp)) {
        val barCount = 15
        val stride = size.width / barCount
        for (index in 0 until barCount) {
            val envelope = sin((index + 1) * Math.PI / (barCount + 1)).toFloat()
            val pulse = (1f + sin(phase.value + index * .69f)) / 2f
            val halfHeight = size.height * (.08f + envelope * (.07f + amplitude.value * (.12f + .16f * pulse)))
            val x = (index + .5f) * stride
            drawLine(
                color = color,
                start = Offset(x, center.y - halfHeight),
                end = Offset(x, center.y + halfHeight),
                strokeWidth = minOf(2.dp.toPx(), stride / 2f),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun SetupAssistant(assistant: String, active: Boolean, onAssistant: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val choose = menuString(R.string.vern_setup_choose_assistant)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = VeritasPackStyle.compactShape()) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(menuString(R.string.vern_setup_assistant), style = MaterialTheme.typography.titleMedium)
            Text(
                menuString(R.string.vern_setup_assistant_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box {
                TextButton(
                    onClick = { expanded = true },
                    enabled = active,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(setupAssistants[assistant] ?: choose)
                    Icon(Icons.Default.ExpandMore, null, Modifier.padding(start = 6.dp))
                }
                DropdownMenu(expanded && active, onDismissRequest = { expanded = false }) {
                    (listOf("chooser" to choose) + setupAssistants.toList()).forEach { (id, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                expanded = false
                                onAssistant(id)
                            },
                            trailingIcon = if (id == assistant) {
                                { Icon(Icons.Default.Check, null) }
                            } else null
                        )
                    }
                }
            }
        }
    }
}

package com.veritas.reader.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.reader.TutorialSpeaker
import com.veritas.reader.ui.OnboardingPage
import com.veritas.reader.ui.ReaderPersonas
import com.veritas.reader.ui.ReadingInterestOptions
import com.veritas.reader.ui.VoiceAuditionPresets
import com.veritas.reader.ui.rememberVeritasHaptics

// --------------------------------------------------------------------
// 0. Revamped Full-Screen Onboarding Flow
// --------------------------------------------------------------------

@Composable
fun RevampedOnboardingFlow(
    initialUserName: String,
    initialReadingInterest: String,
    initialAiAssistant: String,
    onComplete: (name: String, interest: String, aiAssistant: String, speed: Float, pitch: Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = rememberVeritasHaptics()

    var currentPage by rememberSaveable { mutableStateOf(OnboardingPage.WELCOME_HERO) }
    var selectedPersona by rememberSaveable { mutableStateOf("student") }
    var userName by rememberSaveable { mutableStateOf(if (initialUserName.isBlank() || initialUserName.equals("Reader", ignoreCase = true)) "" else initialUserName) }
    var selectedInterest by rememberSaveable { mutableStateOf(initialReadingInterest.ifBlank { "Books & Novels" }) }
    var selectedPreset by remember { mutableStateOf(VoiceAuditionPresets[0]) }
    var selectedAi by rememberSaveable { mutableStateOf(initialAiAssistant.ifBlank { "gemini" }) }
    var selectedStudyFocus by rememberSaveable { mutableStateOf("deep_dive") }
    var isAuditionPlaying by remember { mutableStateOf(false) }
    var isNarrationMuted by rememberSaveable { mutableStateOf(false) }
    var showConfetti by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        TutorialSpeaker.init(context)
    }

    DisposableEffect(Unit) {
        onDispose {
            TutorialSpeaker.stop()
        }
    }

    // Auto-narrate subtle description for each page
    LaunchedEffect(currentPage, isNarrationMuted) {
        if (!isNarrationMuted && !isAuditionPlaying) {
            TutorialSpeaker.stop()
            TutorialSpeaker.speak(currentPage.spokenDescription)
        } else if (isNarrationMuted) {
            TutorialSpeaker.stop()
        }
    }

    LaunchedEffect(currentPage) {
        if (currentPage == OnboardingPage.READY_CELEBRATION) {
            showConfetti = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top App Bar with Step Dots, Audio Narration Toggle & Skip Action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Page Indicator Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OnboardingPage.values().forEach { page ->
                        val isCurrent = page == currentPage
                        val isPassed = page.pageIndex < currentPage.pageIndex
                        val animatedWidth by animateDpAsState(
                            targetValue = if (isCurrent) 24.dp else 8.dp,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "dotWidth"
                        )
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(animatedWidth)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    when {
                                        isCurrent -> MaterialTheme.colorScheme.primary
                                        isPassed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                        )
                    }
                }

                // Top Actions: Subtle Narration Toggle + Skip
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Spoken description narration toggle
                    IconButton(
                        onClick = {
                            haptic.toggle(isNarrationMuted)
                            isNarrationMuted = !isNarrationMuted
                            if (!isNarrationMuted) {
                                TutorialSpeaker.speak(currentPage.spokenDescription)
                            } else {
                                TutorialSpeaker.stop()
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isNarrationMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isNarrationMuted) "Unmute narration" else "Mute narration",
                            tint = if (isNarrationMuted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Skip / Close Button
                    if (currentPage != OnboardingPage.READY_CELEBRATION) {
                        TextButton(
                            onClick = {
                                haptic.success()
                                TutorialSpeaker.stop()
                                onComplete(
                                    userName.trim().ifBlank { "Reader" },
                                    selectedInterest,
                                    selectedAi,
                                    selectedPreset.speed,
                                    selectedPreset.pitch
                                )
                            }
                        ) {
                            Text(
                                "Skip",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(24.dp))
                    }
                }
            }

            // Animated Screen Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = currentPage,
                    transitionSpec = {
                        if (targetState.pageIndex > initialState.pageIndex) {
                            (slideInHorizontally { width -> width / 3 } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> -width / 3 } + fadeOut()
                            )
                        } else {
                            (slideInHorizontally { width -> -width / 3 } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> width / 3 } + fadeOut()
                            )
                        }
                    },
                    label = "onboarding_page_transition"
                ) { targetPage ->
                    when (targetPage) {
                        OnboardingPage.WELCOME_HERO -> OnboardingWelcomeHeroScreen()
                        OnboardingPage.PERSONA_SELECTION -> OnboardingPersonaSelectionScreen(
                            selectedPersona = selectedPersona,
                            onSelectPersona = { persona ->
                                selectedPersona = persona.id
                                selectedPreset = VoiceAuditionPresets.firstOrNull { it.id == persona.defaultPresetId } ?: VoiceAuditionPresets[0]
                                selectedStudyFocus = persona.defaultAiFocus
                                selectedInterest = when (persona.id) {
                                    "student" -> "PDF & Research"
                                    "educator" -> "Books & Novels"
                                    "professional" -> "Web & Articles"
                                    else -> "Books & Novels"
                                }
                            }
                        )
                        OnboardingPage.NAME_INPUT -> OnboardingNameInputScreen(
                            userName = userName,
                            onUserNameChange = { userName = it },
                            selectedInterest = selectedInterest,
                            onSelectInterest = {
                                haptic.select()
                                selectedInterest = it
                            }
                        )
                        OnboardingPage.VOICE_AUDITION -> OnboardingVoiceAuditionScreen(
                            selectedPreset = selectedPreset,
                            onSelectPreset = {
                                haptic.select()
                                selectedPreset = it
                                TutorialSpeaker.stop()
                                isAuditionPlaying = false
                            },
                            isPlaying = isAuditionPlaying,
                            onTogglePlay = {
                                haptic.toggle(!isAuditionPlaying)
                                if (isAuditionPlaying) {
                                    TutorialSpeaker.stop()
                                    isAuditionPlaying = false
                                } else {
                                    isAuditionPlaying = true
                                    TutorialSpeaker.speakWithTuning(
                                        text = selectedPreset.sampleText,
                                        speed = selectedPreset.speed,
                                        pitch = selectedPreset.pitch
                                    )
                                }
                            }
                        )
                        OnboardingPage.AI_SELECTION -> OnboardingAiSelectionScreen(
                            selectedAi = selectedAi,
                            onSelectAi = {
                                haptic.select()
                                selectedAi = it
                            },
                            selectedFocus = selectedStudyFocus,
                            onSelectFocus = {
                                haptic.select()
                                selectedStudyFocus = it
                            }
                        )
                        OnboardingPage.FEATURE_SHOWCASE -> OnboardingFeatureShowcaseScreen()
                        OnboardingPage.READY_CELEBRATION -> OnboardingReadyCelebrationScreen(
                            userName = userName,
                            personaTitle = ReaderPersonas.firstOrNull { it.id == selectedPersona }?.title ?: "Reader",
                            interest = selectedInterest,
                            voiceTitle = selectedPreset.title,
                            aiTitle = selectedAi.replaceFirstChar { it.uppercase() }
                        )
                    }
                }
            }

            // Bottom Navigation Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentPage != OnboardingPage.WELCOME_HERO && currentPage != OnboardingPage.READY_CELEBRATION) {
                    OutlinedButton(
                        onClick = {
                            TutorialSpeaker.stop()
                            isAuditionPlaying = false
                            val prevIndex = (currentPage.pageIndex - 1).coerceAtLeast(0)
                            currentPage = OnboardingPage.values()[prevIndex]
                        },
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Back")
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Button(
                    onClick = {
                        haptic.success()
                        TutorialSpeaker.stop()
                        isAuditionPlaying = false
                        if (currentPage == OnboardingPage.READY_CELEBRATION) {
                            onComplete(
                                userName.trim().ifBlank { "Reader" },
                                selectedInterest,
                                selectedAi,
                                selectedPreset.speed,
                                selectedPreset.pitch
                            )
                        } else {
                            val nextIndex = (currentPage.pageIndex + 1).coerceAtMost(OnboardingPage.values().lastIndex)
                            currentPage = OnboardingPage.values()[nextIndex]
                        }
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.height(48.dp)
                ) {
                    val label = when (currentPage) {
                        OnboardingPage.WELCOME_HERO -> "Get Started"
                        OnboardingPage.READY_CELEBRATION -> "Start Guided Tour"
                        else -> "Continue"
                    }
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (currentPage == OnboardingPage.READY_CELEBRATION) Icons.AutoMirrored.Filled.ArrowForward else Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = label,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (showConfetti) {
            ConfettiOverlay(
                modifier = Modifier.fillMaxSize(),
                onFinished = { showConfetti = false }
            )
        }
    }
}

// --------------------------------------------------------------------
// Sub-screens for Revamped Onboarding
// --------------------------------------------------------------------

@Composable
fun OnboardingWelcomeHeroScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "heroGlow")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Glowing Brand Badge - Sleek App Icon with Pulsing Gold Aura
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(110.dp)
        ) {
            // Pulsating gold glow aura
            Box(
                modifier = Modifier
                    .size(94.dp * glowPulse)
                    .background(
                        brush = Brush.radialGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(100)
                    )
            )
            // Sleek icon with subtle theme-dependent gold border
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(22.dp)
                    )
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                com.veritas.reader.BrandMark(compact = false)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Read at the Speed of Thought",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            lineHeight = 34.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Experience a next-generation reading environment with fluid voice narration, AI-powered study handoffs, and instant PDF canvas layout.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Feature Pillars
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OnboardingHeroPillarRow(
                icon = "🎙️",
                title = "Natural Voice Synthesis",
                desc = "Multi-voice narration with pitch, speed & pronunciation tuning."
            )
            OnboardingHeroPillarRow(
                icon = "🤖",
                title = "6 Official AI Assistants",
                desc = "Instant handoff to ChatGPT, Gemini, Claude, Copilot, Perplexity & Grok."
            )
            OnboardingHeroPillarRow(
                icon = "📑",
                title = "Dual Document Studio",
                desc = "Seamlessly switch between original PDF layouts and responsive text."
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun OnboardingHeroPillarRow(
    icon: String,
    title: String,
    desc: String
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(icon, fontSize = 24.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// --------------------------------------------------------------------
// Name Input & Reading Focus Screen
// --------------------------------------------------------------------

@Composable
fun OnboardingNameInputScreen(
    userName: String,
    onUserNameChange: (String) -> Unit,
    selectedInterest: String,
    onSelectInterest: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Make It Yours",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Enter your name so Vern can welcome you every time you open your library and personalize your study decks.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Name input field
        Text(
            text = "What should we call you?",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = userName,
            onValueChange = onUserNameChange,
            placeholder = { Text("e.g., Alex") },
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Name",
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(6.dp))

        if (userName.isNotBlank()) {
            Text(
                text = "🎉 Welcome aboard, ${userName.trim()}! Your profile is personalized.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        } else {
            Text(
                text = "✨ We'll use your name on your reading streak heatmap and study session greetings.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontWeight = FontWeight.Normal
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "What do you read most?",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(10.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            ReadingInterestOptions.forEach { option ->
                val isSelected = selectedInterest.equals(option.label, ignoreCase = true) || selectedInterest.equals(option.id, ignoreCase = true)
                Card(
                    onClick = { onSelectInterest(option.label) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(option.iconEmoji, fontSize = 24.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = option.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

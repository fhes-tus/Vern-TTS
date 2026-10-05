package com.veritas.reader

import com.veritas.reader.ui.withVisibility

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.veritas.reader.ui.OnboardingController
import com.veritas.reader.ui.OnboardingStep
import com.veritas.reader.ui.ReaderUiState
import com.veritas.reader.ui.ReaderViewModel
import com.veritas.reader.ui.completeQuestTour
import com.veritas.reader.ui.completeRevampedOnboarding
import com.veritas.reader.ui.createWelcomeDocumentSilently
import com.veritas.reader.ui.finishConfettiCelebration
import com.veritas.reader.ui.saveUserName
import com.veritas.reader.ui.screens.ConfettiOverlay
import com.veritas.reader.ui.screens.OnboardingSpotlightOverlay
import com.veritas.reader.ui.screens.RevampedOnboardingFlow
import com.veritas.reader.ui.updateUserNameInMemory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@Composable
internal fun MainOnboardingAndTourHost(
    viewModel: ReaderViewModel,
    uiState: ReaderUiState
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    if (uiState.showTutorial && !uiState.hasCompletedOnboarding && OnboardingController.activeStep == null) {
        RevampedOnboardingFlow(
            initialUserName = uiState.userName,
            initialReadingInterest = uiState.readingInterest,
            initialAiAssistant = uiState.askAiSettings.assistantId,
            initialRate = uiState.voiceSettings.preferredRate,
            initialPitch = uiState.voiceSettings.preferredPitch,
            onComplete = { name, interest, aiAssistant, speed, pitch ->
                viewModel.createWelcomeDocumentSilently()
                viewModel.completeRevampedOnboarding(name, interest, aiAssistant, speed, pitch)
                OnboardingController.activeStep = null
            },
            onDismiss = {
                viewModel.createWelcomeDocumentSilently()
                viewModel.completeRevampedOnboarding(
                    name = uiState.userName.ifBlank { "Reader" },
                    interest = uiState.readingInterest,
                    aiAssistant = uiState.askAiSettings.assistantId,
                    speedRate = uiState.voiceSettings.preferredRate,
                    pitchRate = uiState.voiceSettings.preferredPitch
                )
                OnboardingController.activeStep = null
            }
        )
    } else if (OnboardingController.activeStep != null) {
        var isTransitioningStep by remember { mutableStateOf(false) }
        val activeStep = OnboardingController.activeStep
        // Voice-assisted tutorial: read each step aloud automatically
        LaunchedEffect(activeStep) {
            if (activeStep != null) {
                TutorialSpeaker.init(context)
                TutorialSpeaker.speak("${activeStep.title}. ${activeStep.body}")
            } else {
                TutorialSpeaker.stop()
            }
            if (activeStep == OnboardingStep.SETTINGS_SPOTLIGHT) {
                viewModel.updateState { it.withVisibility(VeritasScreen.SETTINGS_HUB, true) }
            } else if (activeStep != null && uiState.showSettingsHub) {
                viewModel.updateState { it.withVisibility(VeritasScreen.SETTINGS_HUB, false) }
            }
        }
        // Shutdown speaker when tour is completed or dismissed
        LaunchedEffect(activeStep == null) {
            if (activeStep == null) {
                TutorialSpeaker.shutdown()
            }
        }
        // Dialog-hosted tours render locally; Classics now uses the shared Library host.
        if (activeStep != null && activeStep != OnboardingStep.INSIGHTS_PAGE_SPOTLIGHT && activeStep != OnboardingStep.SETTINGS_SPOTLIGHT) {
            OnboardingSpotlightOverlay(
                step = activeStep,
                userName = uiState.userName,
                onUserNameChanged = { viewModel.updateUserNameInMemory(it) },
                isTransitioning = isTransitioningStep,
                onNext = {
                    if (!isTransitioningStep) {
                        coroutineScope.launch {
                            isTransitioningStep = true
                            try {
                                TutorialSpeaker.stop() // stop current reading before moving to next step
                                val nextStep = when (activeStep) {
                                    OnboardingStep.WELCOME -> OnboardingStep.FAB_SPOTLIGHT
                                    OnboardingStep.NAME_INPUT -> {
                                        viewModel.saveUserName(uiState.userName)
                                        OnboardingStep.FAB_SPOTLIGHT
                                    }
                                    OnboardingStep.FAB_SPOTLIGHT -> OnboardingStep.CHECKLIST_SPOTLIGHT
                                    OnboardingStep.CHECKLIST_SPOTLIGHT -> {
                                        viewModel.updateState { it.copy(showClassicsCatalog = true) }
                                        OnboardingStep.CLASSICS_SPOTLIGHT
                                    }
                                    OnboardingStep.CLASSICS_SPOTLIGHT -> {
                                        viewModel.updateState { it.copy(showClassicsCatalog = false) }
                                        OnboardingStep.INSIGHTS_SPOTLIGHT
                                    }
                                    OnboardingStep.INSIGHTS_SPOTLIGHT -> OnboardingStep.INSIGHTS_PAGE_SPOTLIGHT
                                    OnboardingStep.INSIGHTS_PAGE_SPOTLIGHT -> OnboardingStep.NOTES_TAB_SPOTLIGHT
                                    OnboardingStep.NOTES_TAB_SPOTLIGHT -> OnboardingStep.STUDY_TAB_SPOTLIGHT
                                    OnboardingStep.STUDY_TAB_SPOTLIGHT -> OnboardingStep.SETTINGS_SPOTLIGHT
                                    OnboardingStep.SETTINGS_SPOTLIGHT -> OnboardingStep.DOCUMENT_SPOTLIGHT
                                    OnboardingStep.DOCUMENT_SPOTLIGHT -> {
                                        val targetDoc = uiState.documents.firstOrNull()
                                        if (targetDoc != null) {
                                            viewModel.openSavedDocument(targetDoc)
                                            // Wait for reader screen to load and render the mode toggle
                                            var elapsed = 0
                                            while (viewModel.uiState.value.activeDocument == null && elapsed < 40) {
                                                delay(50)
                                                elapsed++
                                            }
                                            elapsed = 0
                                            while (!OnboardingController.componentBounds.containsKey("reader_mode_toggle") && elapsed < 40) {
                                                delay(50)
                                                elapsed++
                                            }
                                            OnboardingStep.MODE_TOGGLE_SPOTLIGHT
                                        } else {
                                            OnboardingStep.CONGRATULATIONS
                                        }
                                    }
                                    OnboardingStep.MODE_TOGGLE_SPOTLIGHT -> OnboardingStep.PLAYER_PANEL_SPOTLIGHT
                                    OnboardingStep.PLAYER_PANEL_SPOTLIGHT -> OnboardingStep.READER_TEXT_SPOTLIGHT
                                    OnboardingStep.READER_TEXT_SPOTLIGHT -> {
                                        viewModel.returnToLibrary()
                                        // Wait for library screen to load
                                        var elapsed = 0
                                        while (viewModel.uiState.value.activeDocument != null && elapsed < 40) {
                                            delay(50)
                                            elapsed++
                                        }
                                        OnboardingStep.CONGRATULATIONS
                                    }
                                    OnboardingStep.CONGRATULATIONS -> null
                                }
                                if (nextStep == null) {
                                    OnboardingController.activeStep = null
                                    viewModel.completeQuestTour()
                                } else {
                                    OnboardingController.activeStep = nextStep
                                }
                            } finally {
                                isTransitioningStep = false
                            }
                        }
                    }
                },
                onBack = {
                    if (!isTransitioningStep) {
                        coroutineScope.launch {
                            isTransitioningStep = true
                            try {
                                TutorialSpeaker.stop() // stop current reading before moving to prev step
                                val prevStep = when (activeStep) {
                                    OnboardingStep.WELCOME -> null
                                    OnboardingStep.NAME_INPUT -> OnboardingStep.WELCOME
                                    OnboardingStep.FAB_SPOTLIGHT -> OnboardingStep.WELCOME
                                    OnboardingStep.CHECKLIST_SPOTLIGHT -> OnboardingStep.FAB_SPOTLIGHT
                                    OnboardingStep.CLASSICS_SPOTLIGHT -> {
                                        viewModel.updateState { it.copy(showClassicsCatalog = false) }
                                        OnboardingStep.CHECKLIST_SPOTLIGHT
                                    }
                                    OnboardingStep.INSIGHTS_SPOTLIGHT -> {
                                        viewModel.updateState { it.copy(showClassicsCatalog = true) }
                                        OnboardingStep.CLASSICS_SPOTLIGHT
                                    }
                                    OnboardingStep.INSIGHTS_PAGE_SPOTLIGHT -> OnboardingStep.INSIGHTS_SPOTLIGHT
                                    OnboardingStep.NOTES_TAB_SPOTLIGHT -> OnboardingStep.INSIGHTS_PAGE_SPOTLIGHT
                                    OnboardingStep.STUDY_TAB_SPOTLIGHT -> OnboardingStep.NOTES_TAB_SPOTLIGHT
                                    OnboardingStep.SETTINGS_SPOTLIGHT -> OnboardingStep.STUDY_TAB_SPOTLIGHT
                                    OnboardingStep.DOCUMENT_SPOTLIGHT -> OnboardingStep.SETTINGS_SPOTLIGHT
                                    OnboardingStep.MODE_TOGGLE_SPOTLIGHT -> {
                                        viewModel.returnToLibrary()
                                        // Wait for library screen to load and render the document card
                                        var elapsed = 0
                                        while (viewModel.uiState.value.activeDocument != null && elapsed < 40) {
                                            delay(50)
                                            elapsed++
                                        }
                                        elapsed = 0
                                        while (!OnboardingController.componentBounds.containsKey("document_card_0") && elapsed < 40) {
                                            delay(50)
                                            elapsed++
                                        }
                                        OnboardingStep.DOCUMENT_SPOTLIGHT
                                    }
                                    OnboardingStep.PLAYER_PANEL_SPOTLIGHT -> OnboardingStep.MODE_TOGGLE_SPOTLIGHT
                                    OnboardingStep.READER_TEXT_SPOTLIGHT -> OnboardingStep.PLAYER_PANEL_SPOTLIGHT
                                    OnboardingStep.CONGRATULATIONS -> {
                                        val targetDoc = uiState.documents.firstOrNull()
                                        if (targetDoc != null) {
                                            viewModel.openSavedDocument(targetDoc)
                                            // Wait for reader screen to load and render the reader text view
                                            var elapsed = 0
                                            while (viewModel.uiState.value.activeDocument == null && elapsed < 40) {
                                                delay(50)
                                                elapsed++
                                            }
                                            elapsed = 0
                                            while (!OnboardingController.componentBounds.containsKey("reader_text_view") && elapsed < 40) {
                                                delay(50)
                                                elapsed++
                                            }
                                            OnboardingStep.READER_TEXT_SPOTLIGHT
                                        } else {
                                            OnboardingStep.DOCUMENT_SPOTLIGHT
                                        }
                                    }
                                }
                                OnboardingController.activeStep = prevStep
                            } finally {
                                isTransitioningStep = false
                            }
                        }
                    }
                },
                onDismiss = {
                    coroutineScope.launch {
                        TutorialSpeaker.stop()
                        if (uiState.activeDocument != null) {
                            viewModel.returnToLibrary()
                        }
                        if (uiState.showClassicsCatalog) {
                            viewModel.updateState { it.copy(showClassicsCatalog = false) }
                        }
                        OnboardingController.activeStep = null
                    }
                }
            )
        }
    }

    if (uiState.showConfetti) {
        ConfettiOverlay(
            onFinished = {
                viewModel.finishConfettiCelebration()
            }
        )
    }
}

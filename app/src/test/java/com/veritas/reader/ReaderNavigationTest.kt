package com.veritas.reader

import com.veritas.reader.ui.ReaderUiState
import com.veritas.reader.ui.withVisibility
import org.junit.Assert.*
import org.junit.Test

class ReaderNavigationTest {
    @Test fun navigationIsExplicitAndOrdinaryUpdatesRetainHistory() {
        val state = ReaderUiState().withVisibility(VeritasScreen.SETTINGS_HUB, true)
            .withVisibility(VeritasScreen.VOICE_STUDIO, true)
        assertEquals(listOf(VeritasScreen.SETTINGS_HUB, VeritasScreen.VOICE_STUDIO), state.navStack)
        assertEquals(state.navStack, state.copy(userName = "Reader", generalNoteSaveStatus = "Saved").navStack)
        assertEquals(state.navStack, state.withVisibility(VeritasScreen.VOICE_STUDIO, true).navStack)
        val back = state.withVisibility(VeritasScreen.VOICE_STUDIO, false)
        assertEquals(listOf(VeritasScreen.SETTINGS_HUB), back.navStack)
        assertTrue(back.showSettingsHub); assertFalse(back.showVoiceStudio)
        assertTrue(back.withVisibility(VeritasScreen.SETTINGS_HUB, false).navStack.isEmpty())
    }
    @Test fun allScreensCanOpenAndCloseWithoutDuplicates() {
        var state = ReaderUiState()
        VeritasScreen.entries.forEach { state = state.withVisibility(it, true) }
        assertEquals(VeritasScreen.entries, state.navStack)
        VeritasScreen.entries.reversed().forEach { state = state.withVisibility(it, false) }
        assertTrue(state.navStack.isEmpty())
    }
}

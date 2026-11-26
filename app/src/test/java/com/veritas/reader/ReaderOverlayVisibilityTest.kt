package com.veritas.reader

import com.veritas.reader.ui.ReaderUiState
import com.veritas.reader.ui.coversReader
import com.veritas.reader.ui.withVisibility
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderOverlayVisibilityTest {
    @Test fun directlyOpenedDialogsPauseWithoutAHistoryEntry() {
        val reader = ReaderUiState()
        assertTrue(reader.copy(showSleepTimerDialog = true).coversReader())
        assertTrue(reader.copy(showWidgetReadingInsights = true).coversReader())
        assertTrue(reader.copy(noteTargetIndex = 0).coversReader())
    }

    @Test fun closingOneDialogKeepsTheOtherBlockingAndCanvasRemainsReadable() {
        val canvas = ReaderUiState().withVisibility(VeritasScreen.CANVAS_VIEW, true)
        val settings = canvas.withVisibility(VeritasScreen.VOICE_STUDIO, true)
        val both = settings.withVisibility(VeritasScreen.READER_SETTINGS, true)
        assertTrue(both.withVisibility(VeritasScreen.READER_SETTINGS, false).coversReader())
        assertFalse(canvas.coversReader())
    }
}

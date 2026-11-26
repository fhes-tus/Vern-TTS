package com.veritas.reader

import com.veritas.reader.ui.screens.NoteDraftSaveState
import com.veritas.reader.ui.screens.NoteDraftSubmission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteDraftSaveStateTest {
    private fun draft(body: String) = GeneralNote("", "Note", body, 0)

    @Test fun anOlderAcknowledgmentCannotCloseTheLatestDraft() {
        val first = NoteDraftSubmission(draft("First edit"))
        val latest = NoteDraftSubmission(draft("Latest edit"))
        val state = NoteDraftSaveState(draft("Original"), pending = latest)

        assertSame(state, state.complete(first, saved = true))
        assertFalse(state.canClose(latest.draft))
        val saved = state.complete(latest, saved = true)
        assertTrue(saved.canClose(latest.draft))
        assertFalse(saved.canClose(latest.draft.copy(audioUrls = listOf("memo.m4a"))))
    }

    @Test fun aFailedSaveKeepsTheLastAcknowledgedDraftAndRequiresRetry() {
        val original = draft("Original")
        val attempted = NoteDraftSubmission(draft("New body"))
        val pending = NoteDraftSaveState(original, pending = attempted)
        assertTrue(pending.needsSave(original))
        assertFalse(pending.canClose(original))

        val failed = pending.complete(attempted, saved = false)
        assertEquals(original, failed.acknowledged)
        assertEquals(attempted.draft, failed.failed)
        assertFalse(failed.canClose(attempted.draft))
        assertTrue(failed.needsSave(attempted.draft))
        assertFalse(failed.canClose(original))
        assertTrue(failed.needsSave(original))

        val retry = NoteDraftSubmission(attempted.draft)
        val recovered = failed.copy(pending = retry, failed = null).complete(retry, saved = true)
        assertTrue(recovered.canClose(attempted.draft))
    }
}

package com.veritas.reader

import com.veritas.reader.ui.withVisibility
import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.ReaderViewModel
import com.veritas.reader.ui.saveGeneralNote
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class NotesPersistenceInstrumentedTest {
    @Test fun unchangedSavePreservesTimestampAndEditedSaveUpdatesIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks"))
        val repository = DocumentRepository(context)
        val original = repository.loadGeneralNotes()
        val note = GeneralNote("no-op-${UUID.randomUUID()}", "Old note", "Original", 123)
        repository.mutateGeneralNotes { listOf(note) + it }
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            lateinit var vm: ReaderViewModel
            scenario.onActivity {
                vm = ViewModelProvider(it)[ReaderViewModel::class.java]
                vm.updateState { state -> state.copy(generalNoteEditorTarget = note) }
                vm.saveGeneralNote(note.title, note.content, closeEditor = false)
            }
            fun awaitSave() {
                val deadline = SystemClock.elapsedRealtime() + 20_000
                while (vm.uiState.value.generalNoteSaveStatus != "Saved") {
                    check(SystemClock.elapsedRealtime() < deadline)
                    SystemClock.sleep(50)
                }
            }
            awaitSave()
            assertEquals(note, repository.loadGeneralNotes().single { it.id == note.id })
            scenario.onActivity { vm.saveGeneralNote(note.title, "Edited", closeEditor = false) }
            awaitSave()
            val edited = repository.loadGeneralNotes().single { it.id == note.id }
            assertEquals("Edited", edited.content)
            assertTrue(edited.updatedAt > note.updatedAt)
        } finally { scenario.close(); repository.saveGeneralNotes(original) }
    }

    @Test fun rapidSavesCreateOneNoteKeepLatestDraftAndRetainConcurrentNotes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = DocumentRepository(context)
        val original = repository.loadGeneralNotes()
        val marker = UUID.randomUUID().toString()
        var targetId: String? = null
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            lateinit var viewModel: ReaderViewModel
            scenario.onActivity { activity ->
                viewModel = ViewModelProvider(activity)[ReaderViewModel::class.java]
                viewModel.updateState { it.withVisibility(VeritasScreen.GENERAL_NOTES_EDITOR, true).copy(
                    generalNoteEditorTarget = null
                ) }
                repeat(8) { version -> viewModel.saveGeneralNote("Rapid $marker", "Draft $version", closeEditor = false) }
                targetId = viewModel.uiState.value.generalNoteEditorTarget?.id
            }
            repository.mutateGeneralNotes { listOf(GeneralNote("parallel-$marker", "Other", "Keep this note", 1)) + it }
            val deadline = SystemClock.elapsedRealtime() + 20_000
            while (viewModel.uiState.value.generalNoteSaveStatus != "Saved") {
                check(SystemClock.elapsedRealtime() < deadline) { viewModel.uiState.value.generalNoteSaveStatus }
                SystemClock.sleep(100)
            }
            val reopened = DocumentRepository(context).loadGeneralNotes()
            assertEquals(1, reopened.count { it.title == "Rapid $marker" })
            assertEquals("Draft 7", reopened.single { it.id == targetId }.content)
            assertTrue(reopened.any { it.id == "parallel-$marker" })
        } finally { scenario.close(); repository.saveGeneralNotes(original) }
    }
}

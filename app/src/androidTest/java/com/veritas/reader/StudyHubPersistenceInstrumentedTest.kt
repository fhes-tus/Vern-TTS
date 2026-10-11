package com.veritas.reader

import android.content.Context
import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.ReaderViewModel
import com.veritas.reader.ui.rateFlashcardRecall
import com.veritas.reader.ui.recordQuizScore
import com.veritas.reader.ui.renameFlashcardSet
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class StudyHubPersistenceInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also {
        check(it.packageName.endsWith(".checks")) { "Run against the isolated checks application." }
    }

    @Test fun malformedFallbackCardDoesNotHideValidCards() {
        val repository = DocumentRepository(context)
        val original = repository.loadAllFlashcards()
        val prefs = context.getSharedPreferences("veritas_reader_library", Context.MODE_PRIVATE)
        try {
            repository.saveAllFlashcards(emptyList())
            prefs.edit().putString("study_flashcards", """[
                {"id":"broken","front":"Missing answer"},
                {"id":"valid","front":"Term","back":"Meaning"}
            ]""").commit()
            val restored = repository.loadAllFlashcards()
            assertEquals("valid", restored.single().id)
            assertEquals("legacy-pasted", restored.single().setId)
        } finally { repository.saveAllFlashcards(original) }
    }

    @Test fun concurrentReadCannotResurrectTheLastDeletedDeck() {
        val repository = DocumentRepository(context)
        val otherReader = DocumentRepository(context)
        val original = repository.loadAllFlashcards()
        val executor = Executors.newFixedThreadPool(2)
        try {
            repeat(20) {
                repository.saveAllFlashcards(listOf(FlashcardProgress("card", front = "Q", back = "A", setId = "deck")))
                val start = CountDownLatch(1)
                val delete = executor.submit { start.await(); repository.deleteFlashcardSet("deck") }
                val read = executor.submit { start.await(); otherReader.loadAllFlashcards() }
                start.countDown()
                delete.get(10, TimeUnit.SECONDS)
                read.get(10, TimeUnit.SECONDS)
                assertTrue(repository.loadAllFlashcards().isEmpty())
                assertTrue(repository.loadAllFlashcardsFromPrefsRaw().isEmpty())
            }
        } finally { executor.shutdownNow(); repository.saveAllFlashcards(original) }
    }

    @Test fun rapidRecallRenameAndScoresPreserveLatestStateAndDurableProgress() {
        val repository = DocumentRepository(context)
        val originalCards = repository.loadAllFlashcards()
        val originalQuizzes = repository.loadAllQuizzes()
        val marker = UUID.randomUUID().toString()
        val card = FlashcardProgress(marker, front = "Q", back = "A", setId = marker, setName = "Initial")
        val question = QuizQuestion("Question?", "Yes", listOf("Yes", "No"))
        val quiz = QuizSet(id = marker, title = "Quiz", questions = List(4) { question }, bestScore = 50)
        repository.saveAllFlashcards(listOf(card) + originalCards)
        repository.saveAllQuizzes(listOf(quiz) + originalQuizzes)
        val store = ViewModelStore()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var vm: ReaderViewModel
        try {
            instrumentation.runOnMainSync {
                vm = ReaderViewModel(context.applicationContext as Application)
                store.put("study", vm)
                vm.renameFlashcardSet(marker, "First")
                repeat(3) { vm.rateFlashcardRecall(marker, "good") }
                vm.renameFlashcardSet(marker, "Latest")
                vm.recordQuizScore(marker, 3)
                vm.recordQuizScore(marker, 1)
                vm.recordQuizScore(marker, 4)
            }
            val deadline = android.os.SystemClock.elapsedRealtime() + 20_000
            while (true) {
                val state = vm.uiState.value
                val saved = repository.loadAllFlashcards().firstOrNull { it.id == marker }
                val shown = state.flashcards.firstOrNull { it.id == marker }
                val shownQuiz = state.quizzes.firstOrNull { it.id == marker }
                if (saved?.repetitionCount == 3 && saved.setName == "Latest" && shown == saved && shownQuiz?.bestScore == 4) break
                check(android.os.SystemClock.elapsedRealtime() < deadline) { "Study state did not converge with saved progress." }
                Thread.sleep(25)
            }
            assertEquals(4, repository.loadAllQuizzes().first { it.id == marker }.bestScore)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            repository.saveAllFlashcards(originalCards)
            repository.saveAllQuizzes(originalQuizzes)
        }
    }
}

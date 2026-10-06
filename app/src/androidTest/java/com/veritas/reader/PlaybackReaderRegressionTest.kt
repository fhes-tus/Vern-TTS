package com.veritas.reader

import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.text.Selection
import android.text.Spannable
import android.view.ActionMode
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.ReaderViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PlaybackReaderRegressionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun await(label: String, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (!predicate()) {
            check(SystemClock.elapsedRealtime() < deadline) { "$label: ${PlaybackStateStore.statusMessage}" }
            SystemClock.sleep(100)
        }
    }

    private fun starts(): Set<String> {
        val fd = instrumentation.uiAutomation.executeShellCommand("logcat -d --pid=${android.os.Process.myPid()} -s VeritasSystemTts")
        val log = ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
        // Logcat is a rolling buffer: counts can shrink while the test is running.
        return log.lineSequence().filter { it.contains("Started utterance") }
            .map { it.substringAfter("Started utterance ") }.toSet()
    }

    private fun findText(view: View): TextView? {
        if (view is TextView && view.isShown && view.text.contains("my questions, and")) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findText(view.getChildAt(i))?.let { return it }
        return null
    }

    @Test fun coldReadFromHereAndRepeatedPlayKeepSpeechAndExactSelectionAlive() {
        val repository = DocumentRepository(context)
        repository.markOnboardingComplete("Regression reader")
        val previousVoice = repository.loadVoiceSettings()
        repository.saveVoiceSettings(VoiceSettings())
        val saved = repository.createDocument(title = "Playback check ${UUID.randomUUID()}",
            text = "## Heading\n\nHe would hardly reply to my questions, and busied himself all evening in an abstruse chemical analysis which involved much heating of retorts and distilling of vapors while the room gradually filled with a peculiar smell.\n\nI would like to read another long sentence while keeping an exact word selected in the previous sentence and watching the highlights change without losing the handles or altering the selected word.", sourceLabel = "TXT")
        context.stopService(Intent(context, PlaybackService::class.java))
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        lateinit var vm: ReaderViewModel
        try {
            scenario.onActivity {
                vm = ViewModelProvider(it)[ReaderViewModel::class.java]
                vm.openSavedDocument(saved)
            }
            await("Reader opens") { vm.uiState.value.activeDocument?.id == saved.id }
            val bodyIndex = vm.uiState.value.activeDocument!!.sentences.indexOfFirst { it.contains("my questions, and") }
            assertTrue(bodyIndex >= 0)
            val beforeCold = starts()
            scenario.onActivity {
                context.startService(Intent(context, PlaybackService::class.java)
                    .setAction(PlaybackActions.ACTION_JUMP_TO)
                    .putExtra(PlaybackActions.EXTRA_DOCUMENT_ID, saved.id)
                    .putExtra(PlaybackActions.EXTRA_START_INDEX, bodyIndex)
                    .putExtra(PlaybackActions.EXTRA_CHAR_OFFSET, 0))
            }
            await("Cold read-from-here starts actual speech") { starts().any { it !in beforeCold } }
            repeat(3) {
                val before = starts()
                scenario.onActivity { vm.moveTo(bodyIndex, autoPlay = true, forcePlaybackStart = true) }
                await("Restart speaks") { starts().any { it !in before } }
                SystemClock.sleep(900)
                assertTrue("Repeated Play must not pause from its own focus request: ${PlaybackStateStore.statusMessage}", PlaybackStateStore.isPlaying)
            }
            var native: TextView? = null
            await("Native prose appears") {
                scenario.onActivity { native = findText(it.window.decorView) }
                native != null
            }
            lateinit var buffer: Spannable
            var wordStart = 0
            scenario.onActivity {
                val tv = native!!
                buffer = tv.text as Spannable
                wordStart = tv.text.toString().indexOf("questions")
                tv.requestFocus()
                Selection.setSelection(buffer, wordStart, wordStart + "questions".length)
            }
            val beforeNext = starts()
            scenario.onActivity { vm.moveTo(bodyIndex + 1, autoPlay = true, forcePlaybackStart = true) }
            await("New sentence starts") { starts().any { it !in beforeNext } }
            SystemClock.sleep(900)
            scenario.onActivity {
                val tv = native!!
                assertSame("Highlight update must retain the native selection buffer", buffer, tv.text)
                assertEquals(wordStart, tv.selectionStart)
                assertEquals(wordStart + "questions".length, tv.selectionEnd)
                val menu = PopupMenu(it, tv).menu
                val mode = TestActionMode(it, menu)
                assertTrue(tv.customSelectionActionModeCallback!!.onCreateActionMode(mode, menu))
                val item = (0 until menu.size()).map { index -> menu.getItem(index) }.single { entry -> entry.title == "Search" }
                assertTrue(tv.customSelectionActionModeCallback!!.onActionItemClicked(mode, item))
                assertEquals("questions", vm.uiState.value.searchQuery)
            }
        } finally {
            scenario.onActivity { vm.stopServicePlayback() }
            context.stopService(Intent(context, PlaybackService::class.java))
            scenario.close()
            repository.deleteDocument(saved.id)
            repository.saveVoiceSettings(previousVoice)
        }
    }

    private class TestActionMode(private val context: Context, private val menu: Menu) : ActionMode() {
        override fun setTitle(title: CharSequence?) = Unit
        override fun setTitle(resId: Int) = Unit
        override fun setSubtitle(subtitle: CharSequence?) = Unit
        override fun setSubtitle(resId: Int) = Unit
        override fun setCustomView(view: View?) = Unit
        override fun invalidate() = Unit
        override fun finish() = Unit
        override fun getMenu(): Menu = menu
        override fun getTitle(): CharSequence = ""
        override fun getSubtitle(): CharSequence = ""
        override fun getCustomView(): View? = null
        override fun getMenuInflater(): MenuInflater = MenuInflater(context)
    }
}

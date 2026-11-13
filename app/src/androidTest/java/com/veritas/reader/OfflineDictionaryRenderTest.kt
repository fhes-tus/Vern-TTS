package com.veritas.reader

import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.screens.OfflineDictionaryPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Native window rendering without Espresso's incompatible API-37 input injector. */
class OfflineDictionaryRenderTest {
    @Test fun studyDictionaryRendersRealSavedDefinitions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        org.junit.Assume.assumeTrue("Dictionary checks require their own disposable package.", context.packageName.endsWith(".dictionarychecks"))
        org.junit.Assume.assumeFalse("A native window render requires an unlocked device.",
            context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked)
        if (OfflineDictionaryStore.info(context) == null) {
            val fixture = File(context.cacheDir, "dictionary-render-fixture.zip")
            instrumentation.context.assets.open("english-wordnet-2025.zip").use { input ->
                fixture.outputStream().use { output -> input.copyTo(output) }
            }
            try { OfflineDictionaryStore.install(context, fixture) } finally { fixture.delete() }
        }
        val saved = CountDownLatch(1)
        var scroll: androidx.compose.foundation.ScrollState? = null
        val activity = instrumentation.startActivitySync(Intent(context, ComponentActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComponentActivity
        try {
            instrumentation.runOnMainSync {
                activity.setContent {
                    VeritasTheme {
                        val state = rememberScrollState()
                        SideEffect { scroll = state }
                        OfflineDictionaryPanel("abandon", onSaved = { saved.countDown() }, modifier = Modifier
                            .fillMaxSize().background(MaterialTheme.colorScheme.background)
                            .verticalScroll(state).padding(20.dp))
                    }
                }
            }
            assertTrue("Lookup did not finish", saved.await(40, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            fun capture(name: String) {
                val root = activity.window.decorView
                assertTrue(root.width > 0 && root.height > 0)
                val frame = CountDownLatch(1)
                instrumentation.runOnMainSync { root.postOnAnimation { root.postOnAnimation { frame.countDown() } } }
                assertTrue("Window did not draw", frame.await(5, TimeUnit.SECONDS))
                val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                val copied = CountDownLatch(1)
                var result = -1
                PixelCopy.request(activity.window, bitmap, { result = it; copied.countDown() }, Handler(Looper.getMainLooper()))
                assertTrue(copied.await(5, TimeUnit.SECONDS))
                assertEquals(PixelCopy.SUCCESS, result)
                File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            capture("dictionary-native-top.png")
            runBlocking { withContext(Dispatchers.Main) { scroll!!.scrollTo(260) } }
            instrumentation.waitForIdleSync()
            capture("dictionary-native-definitions.png")
            val vocabulary = DocumentRepository(context).loadGeneralNotes().first { it.title == "__vocab__$DICTIONARY_VOCAB_ID" }
            val entry = parseVocabularyNoteContent(vocabulary.content).last { it.word == "abandon" }
            assertFalse(VocabularyLookup.needsDefinition(entry.explanation))
            assertTrue(entry.examples.isNotEmpty())
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}

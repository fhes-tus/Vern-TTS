package com.veritas.reader

import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.screens.PronunciationRulesDialog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PronunciationRulesUiTest {
    @get:Rule val ui = createComposeRule()
    private var expectedFontScale = 1f

    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    @Test fun examplesEditingAndPreviewStayUsableInLightDarkAndLargeType() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks"))
        var settings by mutableStateOf(ReaderSettings(themeId = "midnight_dark", customAccentColor = "#12ABEF"))
        var largeType by mutableStateOf(false)
        var rules by mutableStateOf(emptyList<PronunciationRule>())
        var draft by mutableStateOf(SpeechRuleDraft())
        ui.setContent {
            key(largeType) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(if (largeType) 1.3f else 1f)) {
                    // Dialogs use their host View's Android resources. Keep the host
                    // full size so input focus cannot pan the window from a zero-size anchor.
                    AndroidView(factory = { configuredContext -> ComposeView(configuredContext).apply { setContent {
                        CompositionLocalProvider(LocalVeritasPackId provides settings.themePackId) {
                            MaterialTheme(colorScheme = veritasReaderColorScheme(settings, context), shapes = veritasPackShapes(settings.themePackId)) {
                                PronunciationRulesDialog(rules, VoiceSettings(), draft, null, { draft = it }, {
                                    val candidate = draft.rule()
                                    rules = rules.filterNot { it.id == draft.id } + candidate
                                    draft = SpeechRuleDraft()
                                }, { target -> rules = rules.map { if (it.id == target.id) it.copy(enabled = !it.enabled) else it } },
                                    { target -> rules = rules.filterNot { it.id == target.id } }, {})
                            }
                        }
                    } } }, modifier = Modifier.fillMaxSize(), onReset = null, onRelease = { it.disposeComposition() })
                }
            }
        }
        for (name in listOf("dark", "light", "large-type")) {
            expectedFontScale = if (name == "large-type") 1.3f else 1f
            ui.runOnIdle {
                settings = settings.copy(themeId = if (name == "light") "midnight_light" else "midnight_dark")
                largeType = name == "large-type"
                rules = emptyList()
                draft = SpeechRuleDraft()
            }
            ui.onNodeWithText("Change what Vern says without changing the book.").performScrollTo()
            capture("$name-top")
            ui.onNodeWithTag("speech-rule-more-examples").performScrollTo().performClick()
            ui.onNodeWithText("w/o → without").performScrollTo().performClick()
            ui.onNodeWithTag("speech-rule-more-examples").performScrollTo().performClick()
            ui.onNodeWithTag("speech-rule-test-input").performScrollTo().performTextReplacement("Read w/o changes.")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            ui.waitUntil(10000) {
                runCatching { ui.onNodeWithTag("speech-rule-preview").fetchSemanticsNode().config[SemanticsProperties.Text]
                    .joinToString("") { it.text } == "Read without changes." }.getOrDefault(false)
            }
            ui.onNodeWithTag("speech-rule-preview-panel").performScrollTo()
            ui.onNodeWithTag("speech-rule-audition").assertIsEnabled()
            capture("$name-preview")
            ui.onNodeWithTag("speech-rule-save").performScrollTo().performClick()
            val saved = ui.runOnIdle { rules.single().also { assertEquals("w/o", it.find) } }
            val edit = ui.onNodeWithTag("speech-rule-edit-${saved.id}")
            edit.performScrollTo()
            capture("$name-saved")
            edit.performClick()
            ui.onNodeWithTag("speech-rule-find").performScrollTo().assertTextContains("w/o")
            ui.onNodeWithTag("speech-rule-replacement").performScrollTo().assertTextContains("without")
        }
    }

    private fun capture(name: String) {
        ui.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(500)
        val window = ui.runOnIdle {
            fun dialogWindow(view: View): Window? {
                if (view is DialogWindowProvider) return view.window
                if (view is ViewGroup) for (index in 0 until view.childCount) dialogWindow(view.getChildAt(index))?.let { return it }
                return null
            }
            android.view.inspector.WindowInspector.getGlobalWindowViews().asReversed()
                .filter { it.hasWindowFocus() }.firstNotNullOfOrNull(::dialogWindow) ?: error("No focused pronunciation dialog")
        }
        val bitmap = android.graphics.Bitmap.createBitmap(window.decorView.width, window.decorView.height, android.graphics.Bitmap.Config.ARGB_8888)
        assertEquals("Dialog must use the requested font scale", expectedFontScale,
            window.context.resources.configuration.fontScale, .01f)
        val copied = java.util.concurrent.CountDownLatch(1)
        var result = -1
        android.view.PixelCopy.request(window, bitmap, { result = it; copied.countDown() }, android.os.Handler(android.os.Looper.getMainLooper()))
        assertTrue(copied.await(5, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals(android.view.PixelCopy.SUCCESS, result)
        val directory = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "PronunciationUiChecksV2").apply { mkdirs() }
        try { java.io.File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}

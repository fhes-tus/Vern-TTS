package com.veritas.reader

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.LocalVeritasMotion
import com.veritas.reader.ui.VeritasMotionScheme
import com.veritas.reader.ui.screens.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class DarkReaderColorsUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun hamburgerUsesThePagePlaneAcrossPacksAndDarkModes() {
        var pack by mutableStateOf("veritas_media")
        var amoled by mutableStateOf(false)
        var expected = Color.Unspecified
        rule.setContent {
            val context = LocalContext.current
            val scheme = veritasReaderColorScheme(ReaderSettings(themeId = "midnight_dark", themePackId = pack, amoledMode = amoled), context)
            SideEffect { expected = scheme.background }
            MaterialTheme(colorScheme = scheme) {
                CompositionLocalProvider(LocalVeritasPackId provides pack, LocalVeritasMotion provides VeritasMotionScheme(true)) {
                    HomeSidebarDialog("Reader", ReaderTrackerSnapshot(currentStreak = 7), {}, {}, {}, {})
                }
            }
        }
        for (value in VeritasThemePackCatalog.packOptions.map { it.first }) for (black in listOf(false, true)) {
            rule.runOnIdle { pack = value; amoled = black }
            rule.onNodeWithTag("home-sidebar-sheet").assertIsDisplayed()
            val bitmap = rule.onNodeWithTag("home-sidebar-sheet").captureToImage().asAndroidBitmap()
            // Left gutter is exposed sheet, clear of content, corners and scrim.
            assertPixel(expected, bitmap.getPixel(3, bitmap.height / 2))
            save(bitmap, "drawer-$value-${if (black) "amoled" else "dark"}")
            bitmap.recycle()
            val layouts = mutableListOf<TextLayoutResult>()
            rule.onNodeWithText("7-Day Flame").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.single().layoutInput.style.color.luminance() > .7f)
        }
    }

    @Test fun formattedDocxInkUsesWhiteSepiaAndDarkPaperRatherThanTheAppSurface() {
        var theme by mutableStateOf("midnight_dark")
        var tone by mutableStateOf(PaperToneMode.NATURAL_WHITE)
        val page = DocxPage(1, listOf(DocxBlock.Paragraph("Source ink", DocxSourceFormat(listOf(DocxTextRun("Source ink", color = 0xff000000))))))
        var expectedPaper = Color.Unspecified
        rule.setContent {
            val scheme = veritasReaderColorScheme(ReaderSettings(themeId = theme, amoledMode = true), LocalContext.current)
            val paper = readerPaperColors(scheme, tone).first
            SideEffect { expectedPaper = paper }
            MaterialTheme(colorScheme = scheme) {
                DocxDocumentCanvas("Contrast fixture", page, 1, {}, {}, {}, paperToneMode = tone,
                    isLandscape = true, modifier = Modifier.fillMaxSize().testTag("docx-paper"))
            }
        }
        for ((palette, paper) in listOf("midnight_dark" to PaperToneMode.NATURAL_WHITE,
            "dracula" to PaperToneMode.WARM_SEPIA, "midnight_light" to PaperToneMode.DARK)) {
            rule.runOnIdle { theme = palette; tone = paper }
            val layouts = mutableListOf<TextLayoutResult>()
            rule.onNodeWithText("Source ink").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val ink = layouts.single().layoutInput.text.spanStyles.single().item.color
            val contrast = (maxOf(ink.luminance(), expectedPaper.luminance()) + .05f) /
                (minOf(ink.luminance(), expectedPaper.luminance()) + .05f)
            assertTrue("$palette / $paper contrast $contrast", contrast >= 4.5f)
            val bitmap = rule.onNodeWithTag("docx-paper").captureToImage().asAndroidBitmap()
            assertPixel(expectedPaper, bitmap.getPixel(bitmap.width / 2, bitmap.height - 10))
            save(bitmap, "docx-$palette-$paper")
            bitmap.recycle()
        }
    }

    @Test fun realReaderPaperDoesNotGainTonalTintWhenAmoledIsEnabled() {
        var amoled by mutableStateOf(false)
        var tone by mutableStateOf(PaperToneMode.ACTIVE_THEME)
        var expectedPaper = Color.Unspecified
        val text = "A readable page with a quiet neutral background."
        val model = ReaderTextIndex.build(text, 1)
        val document = ReaderDocument("appearance-fixture", "Appearance", "TXT", text, model.sentences.map { it.text }, 1)
        rule.setContent {
            val settings = ReaderSettings(themeId = "midnight_dark", amoledMode = amoled, paperToneMode = tone.name.lowercase(),
                reduceMotion = true, showGestureTips = false)
            val scheme = veritasReaderColorScheme(settings, LocalContext.current)
            SideEffect { expectedPaper = readerPaperColors(scheme, tone).first }
            MaterialTheme(colorScheme = scheme) {
                CompositionLocalProvider(LocalVeritasPackId provides "veritas_media", LocalVeritasMotion provides VeritasMotionScheme(true)) {
                    val state = ReaderScreenState(document, 0, false, false, 1f, 1f, "", 0, false, emptyList(), 0,
                        settings, VoiceSettings(), NarrationSettings(), AskAiSettings(), "", emptyList(), 0, emptyList(), false,
                        0L, 0L, VeritasSleepTimerAction.PAUSE, 0, 0, emptyList(), ReaderMode.TEXT)
                    ReaderScreen(state, rememberLazyListState(), false,
                        onBackToLibrary = {}, onSentenceClick = {}, onSentenceDoubleTap = {}, onPlayPause = {}, onStop = {},
                        onPrevious = {}, onNext = {}, onRateChange = {}, onPitchChange = {}, onFontSizeChange = {}, onToggleQueue = {},
                        onToggleBookmark = {}, onEditNote = {}, onEditNotes = {}, onSearchQueryChange = {}, onNextSearchMatch = {},
                        onPreviousSearchMatch = {}, onOpenReaderSettings = {}, onOpenPronunciationRules = {}, onOpenVoiceStudio = {},
                        onOpenNarrationStudio = {}, onOpenDocumentNotes = {}, onOpenCanvas = {}, onOpenStudyTools = {},
                        onOpenTranslationTools = {}, onOpenSleepTimer = {}, onOpenReadingLists = {}, onOpenReadingHistory = {},
                        onAskCurrentSection = {}, onSelectAskAiAssistant = { _, _ -> }, onOpenTextEditor = {}, onStartRecord = {},
                        onExportAudio = {}, onCopySelection = {}, onShareSelection = {}, onGoogleSelection = {}, onTranslateSelection = {},
                        onAskAiSelection = {}, onEditSpeechSelection = {}, onReadSelection = {}, onEditExtractedSelection = {},
                        onPlayQueue = {}, onVoiceSelected = {}, onReaderModeChange = {})
                }
            }
        }
        for (black in listOf(false, true)) for (paper in PaperToneMode.entries) {
            rule.runOnIdle { amoled = black; tone = paper }
            rule.onNodeWithTag("reader-paper-1", useUnmergedTree = true).assertIsDisplayed()
            val labels = mutableListOf<TextLayoutResult>()
            rule.onNodeWithText("Page 1 of 1").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(labels) }
            val labelInk = labels.single().layoutInput.style.color
            assertTrue((maxOf(labelInk.luminance(), expectedPaper.luminance()) + .05f) /
                (minOf(labelInk.luminance(), expectedPaper.luminance()) + .05f) >= 4.5f)
            val bitmap = rule.onNodeWithTag("reader-paper-1", useUnmergedTree = true).captureToImage().asAndroidBitmap()
            assertPixel(expectedPaper, bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
            save(bitmap, "reader-${if (black) "amoled" else "dark"}-$paper")
            bitmap.recycle()
        }
    }

    @Test fun enablingCurlDoesNotPaintWhitePaperOverTheDarkReaderGutter() {
        var curl by mutableStateOf(false)
        rule.setContent {
            Box(Modifier.fillMaxSize().background(Color.Black).testTag("curl-stage")) {
                BookPageCurl(curl, false, { 0f }, Color.White) {
                    Box(Modifier.fillMaxSize().padding(24.dp)) {
                        Box(Modifier.fillMaxSize().background(Color.White))
                    }
                }
            }
        }
        for (enabled in listOf(false, true)) {
            rule.runOnIdle { curl = enabled }
            val bitmap = rule.onNodeWithTag("curl-stage").captureToImage().asAndroidBitmap()
            assertPixel(Color.Black, bitmap.getPixel(5, bitmap.height / 2))
            assertPixel(Color.White, bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
            bitmap.recycle()
        }
    }

    private fun assertPixel(expected: Color, actual: Int) {
        val wanted = expected.toArgb()
        for (shift in listOf(0, 8, 16)) assertTrue("Expected ${Integer.toHexString(wanted)}, found ${Integer.toHexString(actual)}",
            kotlin.math.abs((wanted shr shift and 255) - (actual shr shift and 255)) <= 2)
    }

    private fun save(bitmap: Bitmap, name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "dark-reader-colors/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

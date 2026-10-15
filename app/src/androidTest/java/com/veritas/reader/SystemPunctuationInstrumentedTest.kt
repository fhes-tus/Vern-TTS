package com.veritas.reader

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Verifies the installed Android engine accepts the prepared text/delivery parameters. */
@RunWith(AndroidJUnit4::class)
class SystemPunctuationInstrumentedTest {
    @Test fun installedSystemVoiceSynthesizesNormalAndExpressiveQuestions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val initialized = CountDownLatch(1)
        var initStatus = TextToSpeech.ERROR
        lateinit var tts: TextToSpeech
        instrumentation.runOnMainSync {
            tts = TextToSpeech(instrumentation.targetContext) { status ->
                initStatus = status
                initialized.countDown()
            }
        }
        val folder = File(instrumentation.targetContext.cacheDir, "punctuation_${UUID.randomUUID()}").apply { mkdirs() }
        try {
            assertTrue("System TTS initialization timed out", initialized.await(30, TimeUnit.SECONDS))
            assertEquals("Installed system engine could not initialize", TextToSpeech.SUCCESS, initStatus)
            val sample = "“Are you ready？” Wait，listen：the reading begins！"
            val speech = SpeechSanitizer.forSpeech(sample)
            assertTrue(speech.contains("?"))
            assertTrue(speech.contains("!"))
            assertEquals(sample.length, speech.length)
            listOf(false, true).forEach { expressive ->
                val done = CountDownLatch(1)
                var failed = false
                val utteranceId = UUID.randomUUID().toString()
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) = Unit
                    override fun onDone(id: String?) { if (id == utteranceId) done.countDown() }
                    @Deprecated("Legacy engine callback")
                    override fun onError(id: String?) { if (id == utteranceId) { failed = true; done.countDown() } }
                    override fun onError(id: String?, errorCode: Int) { onError(id) }
                })
                val settings = NarrationSettings(punctuationExpressionEnabled = expressive)
                val question = "“Are you ready？”"
                assertEquals(TextToSpeech.SUCCESS, tts.setSpeechRate(NarrationAnalyzer.effectiveRate(1f, settings, question)))
                assertEquals(TextToSpeech.SUCCESS, tts.setPitch(NarrationAnalyzer.effectivePitch(1f, settings, question)))
                val file = File(folder, if (expressive) "expressive.wav" else "normal.wav")
                assertEquals(TextToSpeech.SUCCESS, tts.synthesizeToFile(SpeechSanitizer.forSpeech(question), null, file, utteranceId))
                assertTrue("Synthesis timed out", done.await(30, TimeUnit.SECONDS))
                assertFalse("System voice rejected punctuation/delivery", failed)
                assertTrue("No audible PCM data was synthesized", file.length() > 44)
                file.inputStream().use {
                    val header = ByteArray(4)
                    assertEquals(4, it.read(header))
                    assertEquals("RIFF", String(header, Charsets.US_ASCII))
                }
            }
            val merged = File(folder, "merged.wav")
            StreamingWavMerger.merge(listOf(File(folder, "normal.wav"), File(folder, "expressive.wav")), merged)
            assertTrue("The real system engine's WAV parts must be mergeable", merged.length() > 44)
        } finally {
            instrumentation.runOnMainSync { tts.shutdown() }
            folder.deleteRecursively()
        }
    }
}

package com.veritas.reader

import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.tts.PiperEngine
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PiperRateInstrumentedTest {
    @Test fun actualPiperRateChangesDurationAtFixedPitch() = runBlocking {
        withContext(Dispatchers.Default) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            check(context.packageName.endsWith(".checks"))
            val fixture = RealOfflineSpeechInstrumentedTest().fixtureContext()
            val engine = PiperEngine(fixture, "piper_en_us_lessac")
            val rows = JSONArray(); val slow = mutableListOf<Int>(); val fast = mutableListOf<Int>()
            try {
                assertTrue(engine.isReady())
                for (rate in listOf(.5f, 2f, 2f, .5f)) {
                    val audio = requireNotNull(engine.synthesizeFloat("The reader should preserve each word, adjust the reading speed, and finish this sentence clearly.", rate, 1f))
                    if (rate < 1) slow.add(audio.samples.size) else fast.add(audio.samples.size)
                    rows.put(JSONObject().put("rate", rate).put("pitch", 1).put("frames", audio.samples.size).put("sampleRate", audio.sampleRate))
                    val file = File(context.filesDir, "piper-rate-results.json").apply { writeText(rows.toString(2)) }
                    preserveSpeechTestArtifact(context, file)
                }
                assertTrue("Piper's 2x output should be substantially shorter than 0.5x: slow=$slow fast=$fast", fast.max() < slow.min() * .7)
            } finally { engine.shutdown() }
        }
    }
}

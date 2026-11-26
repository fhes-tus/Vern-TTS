package com.veritas.reader

import com.veritas.reader.tts.KokoroPackageShape
import com.veritas.reader.tts.OfflineEngineType
import com.veritas.reader.tts.VoiceModelManager
import org.junit.Assert.*
import org.junit.Test

class KokoroPackageShapeTest {
    @Test fun allEnglishSpeakersAreSelectableFromTheExistingStudioArchive() {
        val voices = VoiceModelManager.availableVoices.filter { it.engineType == OfflineEngineType.KOKORO }
        val base = voices.single { it.isCoreRequired }
        assertEquals(28, voices.size)
        assertEquals((0..27).toSet(), voices.map { KokoroPackageShape.speakerId(it.id) }.toSet())
        assertTrue(voices.all { it.modelUrl == base.modelUrl && it.expectedChecksum == base.expectedChecksum })
        assertNotNull(base.expectedChecksum)
        assertEquals(0, KokoroPackageShape.speakerId("kokoro_af_alloy"))
        assertEquals(4, KokoroPackageShape.speakerId("kokoro_af_jessica"))
        assertEquals(7, KokoroPackageShape.speakerId("kokoro_af_nova"))
        assertEquals(8, KokoroPackageShape.speakerId("kokoro_af_river"))
    }

    @Test fun acceptsOldAndUpdatedV10ExportsWithUnchangedEnglishVoices() {
        assertEquals(53, KokoroPackageShape.speakerCount(53L * 510 * 256 * 4))
        assertEquals(54, KokoroPackageShape.speakerCount(54L * 510 * 256 * 4))
        assertTrue(KokoroPackageShape.supports(53))
        assertTrue(KokoroPackageShape.supports(54))
    }

    @Test fun rejectsTruncatedAndIncompatibleSpeakerPackages() {
        assertNull(KokoroPackageShape.speakerCount(54L * 510 * 256 * 4 - 1))
        assertNull(KokoroPackageShape.speakerCount(11L * 510 * 256 * 4))
        assertNull(KokoroPackageShape.speakerCount(103L * 510 * 256 * 4))
        assertFalse(KokoroPackageShape.supports(103))
    }
}

package com.veritas.reader

import com.veritas.reader.tts.KokoroPackageShape
import org.junit.Assert.*
import org.junit.Test

class KokoroPackageShapeTest {
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

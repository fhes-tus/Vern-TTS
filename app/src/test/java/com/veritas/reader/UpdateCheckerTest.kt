package com.veritas.reader

import com.veritas.reader.ui.ReaderViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test fun postUpdateNotesDistinguishFirstLaunchUpgradeAndDowngrade() {
        assertFalse(isInstalledUpgrade("", 0, "3.0.0", 53))
        assertTrue(isInstalledUpgrade("2.7.1", 0, "3.0.0", 53))
        assertTrue(isInstalledUpgrade("3.0.0", 52, "3.0.0", 53))
        assertFalse(isInstalledUpgrade("3.0.0", 53, "3.0.0", 53))
        assertFalse(isInstalledUpgrade("3.0.1", 54, "3.0.0", 53))
    }

    @Test fun postUpdateNotesSelectOnlyTheExactInstalledVersion() {
        val bundled = """{"2.7.1":["Old change"],"3.0.0":["New change","",null]}"""
        assertEquals(listOf("New change"), releaseNotesForVersion(bundled, "3.0.0"))
        assertEquals(emptyList<String>(), releaseNotesForVersion(bundled, "3.0.1"))
    }


    @Test
    fun testVersionComparisonCorrectlyIdentifiesNewerVersions() {
        // Remote version is newer
        assertTrue(ReaderViewModel.isVersionNewer("1.0.0", "1.0.1"))
        assertTrue(ReaderViewModel.isVersionNewer("1.0.0", "1.1.0"))
        assertTrue(ReaderViewModel.isVersionNewer("1.0.9", "1.1.0"))
        assertTrue(ReaderViewModel.isVersionNewer("1.0.0", "2.0.0"))

        // Remote version is equal or older
        assertFalse(ReaderViewModel.isVersionNewer("1.0.1", "1.0.1"))
        assertFalse(ReaderViewModel.isVersionNewer("1.1.0", "1.0.9"))
        assertFalse(ReaderViewModel.isVersionNewer("1.1.0", "1.1.0"))
        assertFalse(ReaderViewModel.isVersionNewer("2.0.0", "1.0.0"))
    }

    @Test
    fun testSelectOptimalApkUrlPrefers64BitWhenSupported() {
        val assetsJson = org.json.JSONArray().apply {
            // Put 32-bit first (as GitHub API does alphabetically)
            put(org.json.JSONObject().apply {
                put("name", "Veritas_Reader_v2.3.1-armeabi-v7a-release.apk")
                put("browser_download_url", "https://github.com/releases/armeabi-v7a.apk")
            })
            put(org.json.JSONObject().apply {
                put("name", "Veritas_Reader_v2.3.1-universal-release.apk")
                put("browser_download_url", "https://github.com/releases/universal.apk")
            })
            put(org.json.JSONObject().apply {
                put("name", "Veritas_Reader_v2.3.1_arm64-release.apk")
                put("browser_download_url", "https://github.com/releases/arm64.apk")
            })
        }

        // 64-bit device supporting both 64-bit and 32-bit: MUST pick arm64!
        val supported64 = arrayOf("arm64-v8a", "armeabi-v7a", "armeabi")
        val selected64 = ReaderViewModel.selectOptimalApkUrl(assetsJson, supported64)
        org.junit.Assert.assertEquals("https://github.com/releases/arm64.apk", selected64)

        // 32-bit only device: MUST pick armeabi-v7a!
        val supported32 = arrayOf("armeabi-v7a", "armeabi")
        val selected32 = ReaderViewModel.selectOptimalApkUrl(assetsJson, supported32)
        org.junit.Assert.assertEquals("https://github.com/releases/armeabi-v7a.apk", selected32)

        // Device without ARM: falls back to universal!
        val supportedX86 = arrayOf("x86_64", "x86")
        val selectedX86 = ReaderViewModel.selectOptimalApkUrl(assetsJson, supportedX86)
        org.junit.Assert.assertEquals("https://github.com/releases/universal.apk", selectedX86)
    }
}

package com.veritas.reader

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SecurityBoundaryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun privatePlaybackCommandsRejectMissingAndAlteredCapabilities() {
        val intent = Intent(context, PlaybackService::class.java).setAction(PlaybackActions.ACTION_STOP)
        assertFalse(PlaybackCommandSecurity.isAuthorized(context, intent))
        PlaybackCommandSecurity.authorize(context, intent)
        assertTrue(PlaybackCommandSecurity.isAuthorized(context, Intent(intent)))
        val key = requireNotNull(intent.extras).keySet().single()
        intent.putExtra(key, "0".repeat(64))
        assertFalse(PlaybackCommandSecurity.isAuthorized(context, intent))
    }

    @Test fun sharingDeniesPrivateDataAndStagesExplicitCacheExports() {
        val authority = "${context.packageName}.fileprovider"
        val private = File.createTempFile("security_private_", ".txt", context.filesDir)
        val export = File.createTempFile("security_export_", ".txt", context.cacheDir)
        try {
            private.writeText("synthetic private content")
            export.writeText("synthetic export")
            assertThrows(IllegalArgumentException::class.java) { SafeFileSharing.uri(context, authority, private) }
            val uri = SafeFileSharing.uri(context, authority, export)
            assertTrue(uri.path!!.startsWith("/shared_exports/"))
            assertEquals("synthetic export", context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })
            context.contentResolver.delete(uri, null, null)
        } finally { private.delete(); export.delete() }
    }

    @Test fun cameraOutputStillWritesToTheOriginalCaptureFile() {
        val directory = File(context.cacheDir, "note_camera").apply { mkdirs() }
        val capture = File.createTempFile("security_capture_", ".jpg", directory)
        try {
            val uri = SafeFileSharing.uri(context, "${context.packageName}.fileprovider", capture)
            assertTrue(uri.path!!.startsWith("/note_camera/"))
            context.contentResolver.openOutputStream(uri)!!.use { it.write(byteArrayOf(1, 2, 3)) }
            assertArrayEquals(byteArrayOf(1, 2, 3), capture.readBytes())
        } finally { capture.delete() }
    }

    @Test fun installerRejectsAnOtherwiseValidApkThatIsNotANewerUpdate() {
        val installed = File(context.applicationInfo.sourceDir)
        val error = assertThrows(IllegalArgumentException::class.java) { ApkUpdateSafety.validate(context, installed) }
        assertTrue(error.message!!.contains("not a newer version"))
    }
}

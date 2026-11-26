package com.veritas.reader

import com.veritas.reader.tts.VoicePackageFiles
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VoicePackageFilesTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun model(directory: File) = File(directory, "model.onnx")

    private fun complete(directory: File) = model(directory).length() > 0

    private fun writePackage(directory: File, content: String): File {
        assertTrue(directory.mkdirs())
        model(directory).writeText(content)
        return directory
    }

    @Test fun failedPublicationRestoresTheWorkingPackage() {
        val live = writePackage(File(temporary.root, "kokoro"), "working model")
        val stage = writePackage(File(temporary.root, "stage"), "new model")

        assertFalse(VoicePackageFiles.publish(stage, live, ::complete) { source, destination ->
            source != stage && source.renameTo(destination)
        })

        assertEquals("working model", model(live).readText())
        assertTrue(complete(stage))
        assertFalse(VoicePackageFiles.previous(live).exists())
    }

    @Test fun interruptedReplacementRecoversThePreviousPackageBeforeUse() {
        val live = writePackage(File(temporary.root, "kitten"), "working model")
        val stage = writePackage(File(temporary.root, "stage"), "new model")
        val previous = VoicePackageFiles.previous(live)
        assertFalse(VoicePackageFiles.publish(stage, live, ::complete) { source, destination ->
            source == live && source.renameTo(destination)
        })
        assertFalse(live.exists())
        assertEquals("working model", model(previous).readText())

        assertEquals(live, VoicePackageFiles.installedDirectory(live, ::complete))
        assertEquals("working model", model(live).readText())
        assertFalse(previous.exists())
    }

    @Test fun interruptedCleanupKeepsACompletePublicationAndExplicitDeletionRemovesBoth() {
        val live = writePackage(File(temporary.root, "kokoro"), "new model")
        val previous = writePackage(VoicePackageFiles.previous(live), "old model")

        val selected = VoicePackageFiles.installedDirectory(live, ::complete)
        assertEquals("new model", model(selected).readText())
        assertTrue(VoicePackageFiles.delete(live))
        assertFalse(live.exists())
        assertFalse(previous.exists())
    }

    @Test fun newPublicationMustBeCompleteBeforeTheRecoveryCopyIsDiscarded() {
        val live = writePackage(File(temporary.root, "kokoro"), "working model")
        val stage = writePackage(File(temporary.root, "stage"), "new model")

        assertFalse(VoicePackageFiles.publish(stage, live, ::complete) { source, destination ->
            source.renameTo(destination).also { moved ->
                if (moved && source == stage) model(destination).writeText("")
            }
        })

        assertEquals("working model", model(live).readText())
    }

    @Test fun repairedPublicationReplacesADamagedTargetAndRetiresThePreviousCopy() {
        val live = writePackage(File(temporary.root, "kokoro"), "")
        val previous = writePackage(VoicePackageFiles.previous(live), "working model")
        val stage = writePackage(File(temporary.root, "stage"), "new model")
        assertEquals(previous, VoicePackageFiles.installedDirectory(live, ::complete))

        assertTrue(VoicePackageFiles.publish(stage, live, ::complete))

        assertEquals("new model", model(live).readText())
        assertFalse(previous.exists())
    }
}

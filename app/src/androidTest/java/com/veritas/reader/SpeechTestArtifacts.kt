package com.veritas.reader

import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Transfer explicit fixture output through stdin; executeShellCommand does not imply shell redirection. */
internal fun preserveSpeechTestArtifact(context: Context, file: File) {
    check(context.packageName.endsWith(".checks"))
    check(Build.VERSION.SDK_INT >= 31)
    require(file.name.matches(Regex("[A-Za-z0-9_.-]+")))
    require(file.canonicalFile.toPath().startsWith(context.filesDir.canonicalFile.toPath()))
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    val pipes = automation.executeShellCommandRw("dd of=/data/local/tmp/vern-${file.name}")
    try {
        ParcelFileDescriptor.AutoCloseOutputStream(pipes[1]).use { output -> file.inputStream().use { it.copyTo(output) } }
        ParcelFileDescriptor.AutoCloseInputStream(pipes[0]).use { it.readBytes() }
    } finally { pipes.forEach { runCatching { it.close() } } }
}

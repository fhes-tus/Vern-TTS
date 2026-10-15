package com.veritas.reader

import android.content.Context
import android.content.Intent
import android.util.AtomicFile
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom

/** Persistent capability for app-only commands and immutable notification intents. */
internal object PlaybackCommandSecurity {
    private const val EXTRA = "com.veritas.reader.extra.PLAYBACK_CAPABILITY"
    private var cached: String? = null

    @Synchronized private fun capability(context: Context): String {
        cached?.let { return it }
        // Neither FileProvider nor Android backup exposes noBackupFilesDir.
        val file = AtomicFile(File(context.applicationContext.noBackupFilesDir, "playback-command.key"))
        val bytes = if (file.baseFile.exists()) {
            file.openRead().use { input ->
                val result = ByteArray(32)
                var count = 0
                while (count < result.size) {
                    val read = input.read(result, count, result.size - count)
                    check(read > 0) { "Invalid playback command key" }
                    count += read
                }
                check(input.read() == -1) { "Invalid playback command key" }
                result
            }
        } else {
            ByteArray(32).also { generated ->
                SecureRandom().nextBytes(generated)
                val output = file.startWrite()
                try { output.write(generated); file.finishWrite(output) }
                catch (error: Throwable) { file.failWrite(output); throw error }
            }
        }
        return bytes.joinToString("") { "%02x".format(it) }.also { cached = it }
    }

    fun authorize(context: Context, intent: Intent): Intent =
        intent.putExtra(EXTRA, capability(context))

    fun isAuthorized(context: Context, intent: Intent): Boolean {
        val supplied = intent.getStringExtra(EXTRA) ?: return false
        if (supplied.length != 64) return false
        return runCatching {
            MessageDigest.isEqual(supplied.toByteArray(Charsets.US_ASCII), capability(context).toByteArray(Charsets.US_ASCII))
        }.getOrDefault(false)
    }
}

package com.veritas.reader

import android.app.LocaleManager
import android.app.DownloadManager
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class DeviceMenuTranslationInstrumentedTest {
    @Test
    fun modelTranslatesVernSettingsLabels() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("downloadMenuTranslationModel") == "true")
        val context = instrumentation.targetContext
        val manager = context.getSystemService(LocaleManager::class.java)
        val system = manager.systemLocales
        val override = manager.applicationLocales
        val target = args.getString("menuTranslationTestTarget") ?: system[0]?.let { DeviceMenuTranslation.target(it, "") }
            ?: requireNotNull(DeviceMenuTranslation.target(Locale.ITALY, ""))
        val english = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        })
        val labels = listOf(R.string.ui_settings, R.string.app_language, R.string.language_follow_device, R.string.ui_save_changes)
            .map(english::getString)
        withTimeout(180_000) { DeviceMenuTranslation.install(context) { } }
        val client = DeviceMenuTranslation.client(target)
        try {
            reportModelDownloads(context)
            withTimeout(180_000) { DeviceMenuTranslation.prepare(client, args.getString("allowMobileData") == "true") }
            val output = labels.map { withTimeout(15_000) { DeviceMenuTranslation.translate(client, it) } }
            assertTrue(output.all(String::isNotBlank))
            assertTrue("Model returned the original English labels", output != labels)
            instrumentation.sendStatus(0, Bundle().apply {
                putString("stream", "\nTarget $target\n" + labels.zip(output).joinToString("\n") { (a, b) -> "$a → $b" } + "\n")
            })
        } finally {
            reportModelDownloads(context)
            client.close()
            assertEquals(system, manager.systemLocales)
            assertEquals(override, manager.applicationLocales)
        }
    }

    private fun reportModelDownloads(context: Context) {
        val manager = context.getSystemService(DownloadManager::class.java)
        val rows = runCatching {
            manager.query(DownloadManager.Query())?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val host = Uri.parse(cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_URI))).host
                        if (host?.endsWith(".gvt1.com") != true && host != "dl.google.com") continue
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        val received = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        add("status=$status reason=$reason bytes=$received/$total")
                    }
                }
            }.orEmpty()
        }.getOrNull()
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
            putString("stream", "\nModel downloads: ${rows?.joinToString()?.ifEmpty { "none" } ?: "status unavailable"}\n")
        })
    }
}

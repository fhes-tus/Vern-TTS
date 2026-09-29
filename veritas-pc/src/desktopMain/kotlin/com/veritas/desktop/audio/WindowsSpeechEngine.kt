package com.veritas.desktop.audio

import com.veritas.desktop.models.PronunciationRule
import com.veritas.desktop.models.VoiceProfile
import com.veritas.desktop.models.VoiceSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

class WindowsSpeechEngine {
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private var listenerThread: Thread? = null

    @Volatile private var currentUtteranceIndex: Int = -1
    @Volatile private var isEngineReady: Boolean = false

    var onSentenceStarted: ((index: Int) -> Unit)? = null
    var onSentenceCompleted: ((index: Int) -> Unit)? = null
    var onSpeechError: ((error: String) -> Unit)? = null

    init {
        // Start persistent background speech worker
        startPersistentWorker()
    }

    private fun startPersistentWorker() {
        val os = System.getProperty("os.name").lowercase()
        if (!os.contains("win")) return

        Thread {
            try {
                // Persistent PowerShell speech host with command protocol
                val command = "Add-Type -AssemblyName System.Speech; " +
                        "\$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                        "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8; " +
                        "[Console]::WriteLine('READY'); " +
                        "while (\$line = [Console]::ReadLine()) { " +
                        "  if (\$line -eq 'QUIT') { break } " +
                        "  if (\$line -eq 'STOP') { \$s.SpeakAsyncCancelAll(); continue } " +
                        "  if (\$line.StartsWith('VOICE:')) { try { \$s.SelectVoice(\$line.Substring(6)) } catch {} ; continue } " +
                        "  if (\$line.StartsWith('RATE:')) { try { \$s.Rate = [int]\$line.Substring(5) } catch {} ; continue } " +
                        "  if (\$line.StartsWith('VOLUME:')) { try { \$s.Volume = [int]\$line.Substring(7) } catch {} ; continue } " +
                        "  if (\$line.StartsWith('SPEAK:')) { " +
                        "    \$txt = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String(\$line.Substring(6))); " +
                        "    try { \$s.Speak(\$txt) } catch {} " +
                        "    [Console]::WriteLine('DONE'); " +
                        "  } " +
                        "}"

                val pb = ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", command)
                val proc = pb.start()
                process = proc

                val w = BufferedWriter(OutputStreamWriter(proc.outputStream, StandardCharsets.UTF_8))
                val r = BufferedReader(InputStreamReader(proc.inputStream, StandardCharsets.UTF_8))
                writer = w
                reader = r

                // Reader loop
                var line: String? = null
                while (r.readLine().also { line = it } != null) {
                    if (line == "READY") {
                        isEngineReady = true
                    } else if (line == "DONE") {
                        val finishedIdx = currentUtteranceIndex
                        if (finishedIdx >= 0) {
                            javax.swing.SwingUtilities.invokeLater {
                                onSentenceCompleted?.invoke(finishedIdx)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Fallback
            }
        }.apply {
            isDaemon = true
            name = "Veritas-Speech-Host"
            start()
        }
    }

    suspend fun getInstalledVoicesAsync(): List<VoiceProfile> = withContext(Dispatchers.IO) {
        val os = System.getProperty("os.name").lowercase()
        val list = mutableListOf<VoiceProfile>()

        if (os.contains("win")) {
            try {
                val script = "Add-Type -AssemblyName System.Speech; \$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer; \$synth.GetInstalledVoices() | ForEach-Object { \$_.VoiceInfo.Name + '|' + \$_.VoiceInfo.Culture.Name + '|' + \$_.VoiceInfo.Gender }"
                val proc = ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", script).start()
                proc.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                    lines.forEach { line ->
                        val parts = line.split("|")
                        if (parts.isNotEmpty() && parts[0].isNotBlank()) {
                            val name = parts[0].trim()
                            val culture = if (parts.size > 1) parts[1].trim() else "en-US"
                            val gender = if (parts.size > 2) parts[2].trim() else "Neutral"
                            list.add(VoiceProfile(name, name, culture, gender))
                        }
                    }
                }
                proc.waitFor()
            } catch (e: Exception) {
                // Ignore
            }
        }

        if (list.isEmpty()) {
            list.add(VoiceProfile("Microsoft David Desktop", "Microsoft David (English US)", "en-US", "Male"))
            list.add(VoiceProfile("Microsoft Zira Desktop", "Microsoft Zira (English US)", "en-US", "Female"))
            list.add(VoiceProfile("System Default", "System Default Voice", "en-US", "Neutral"))
        }
        list
    }

    fun getDefaultVoices(): List<VoiceProfile> {
        return listOf(
            VoiceProfile("Microsoft David Desktop", "Microsoft David (English US)", "en-US", "Male"),
            VoiceProfile("Microsoft Zira Desktop", "Microsoft Zira (English US)", "en-US", "Female"),
            VoiceProfile("System Default", "System Default Voice", "en-US", "Neutral")
        )
    }

    fun stop() {
        currentUtteranceIndex = -1
        try {
            writer?.let {
                it.write("STOP\n")
                it.flush()
            }
        } catch (e: Exception) {}
    }

    fun speakSentence(
        index: Int,
        rawText: String,
        settings: VoiceSettings,
        rules: List<PronunciationRule>
    ) {
        stop()
        val processedText = applyPronunciationRules(rawText, rules)
        if (processedText.isBlank()) {
            onSentenceCompleted?.invoke(index)
            return
        }

        currentUtteranceIndex = index
        onSentenceStarted?.invoke(index)

        try {
            val psRate = ((settings.rate - 1.0f) * 8).toInt().coerceIn(-10, 10)
            val psVolume = (settings.volume * 100).toInt().coerceIn(0, 100)

            writer?.let { w ->
                if (settings.voiceName.isNotBlank() && settings.voiceName != "System Default") {
                    w.write("VOICE:${settings.voiceName}\n")
                }
                w.write("RATE:$psRate\n")
                w.write("VOLUME:$psVolume\n")

                // Base64 encode text so multi-line and special characters are preserved without escaping bugs
                val b64 = java.util.Base64.getEncoder().encodeToString(processedText.toByteArray(StandardCharsets.UTF_8))
                w.write("SPEAK:$b64\n")
                w.flush()
            }
        } catch (e: Exception) {
            onSpeechError?.invoke(e.localizedMessage ?: "Speech error")
        }
    }

    fun shutdown() {
        try {
            writer?.write("QUIT\n")
            writer?.flush()
            process?.destroyForcibly()
        } catch (e: Exception) {}
    }

    private fun applyPronunciationRules(text: String, rules: List<PronunciationRule>): String {
        var result = text
        for (rule in rules.filter { it.enabled }) {
            val findRegex = if (rule.matchCase) {
                Regex(Regex.escape(rule.find))
            } else {
                Regex(Regex.escape(rule.find), RegexOption.IGNORE_CASE)
            }
            result = result.replace(findRegex, rule.replaceWith)
        }
        return result
    }
}

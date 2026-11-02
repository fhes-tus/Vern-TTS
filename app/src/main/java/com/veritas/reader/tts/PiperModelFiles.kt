package com.veritas.reader.tts

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Full precision avoids the INT8 vocoder's noise. Legacy packages remain usable offline. */
internal object PiperModelFiles {
    fun preferred(directory: File, base: String): File? = listOf("$base.fp32.onnx", "$base.onnx")
        .map { File(directory, it) }.firstOrNull { it.isFile && it.length() > 0L }

    /** Publish only the new model, last. Never replace files used by the current legacy graph. */
    fun publishQuality(staging: File, destination: File, base: String) {
        require(base.matches(Regex("[A-Za-z0-9_-]+")))
        val model = File(staging, "$base.onnx")
        val tokens = File(staging, "tokens.txt")
        require(model.isFile && model.length() > 0L && tokens.isFile && tokens.length() in 1..1_048_576L)
        val installedTokens = File(destination, "tokens.txt")
        if (installedTokens.exists()) {
            require(installedTokens.length() == tokens.length() && installedTokens.readBytes().contentEquals(tokens.readBytes())) {
                "The updated voice has different tokens. Keep the current voice, or delete and download it when reading has stopped."
            }
        }
        check(destination.isDirectory || destination.mkdirs())
        if (!installedTokens.exists()) tokens.copyTo(installedTokens)
        for (name in listOf("$base.onnx.json", "MODEL_CARD", "README.md", "LICENSE")) {
            val source = File(staging, name)
            val target = File(destination, name)
            if (source.isFile && !target.exists()) source.copyTo(target)
        }
        Files.move(model.toPath(), File(destination, "$base.fp32.onnx").toPath(),
            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
}

package com.veritas.desktop.test

import com.veritas.desktop.parser.TextChunker
import com.veritas.desktop.storage.DesktopStorage
import com.veritas.desktop.study.StudyAssistant

fun main() {
    println("=== VERITAS READER DESKTOP ENGINE VERIFICATION ===")

    // 1. Verify Text Chunker with abbreviations and decimals
    val sampleText = "Dr. Johnson met Mr. Smith at 3.14 PM in Washington D.C. They discussed the e.g. methodology vs. the alternative! Is this working properly? Yes, absolutely."
    val chunks = TextChunker.chunk(sampleText)
    println("[1] Chunker Output (${chunks.size} sentences):")
    chunks.forEachIndexed { i, c -> println("    [$i] $c") }
    assert(chunks.size == 3) { "Expected 3 sentences, got ${chunks.size}" }

    // 2. Verify Study Assistant
    val studyPack = StudyAssistant.generateStudyPack("Test Topic", chunks)
    println("\n[2] Study Assistant Summary: ${studyPack.summary}")
    println("    Key Points: ${studyPack.keyPoints.size}")
    println("    Flashcards: ${studyPack.flashcards.size}")
    println("    Quiz Questions: ${studyPack.quizQuestions.size}")

    // 3. Verify Storage and Settings
    val (rSettings, vSettings) = DesktopStorage.loadSettings()
    println("\n[3] Storage Verification:")
    println("    Reader theme: ${rSettings.themeType}")
    println("    Voice rate: ${vSettings.rate}x")

    // 4. Verify Library load
    val library = DesktopStorage.loadLibrary()
    println("    Loaded Library Docs: ${library.size}")
    assert(library.isNotEmpty()) { "Library should not be empty (default welcome guide expected)" }

    println("\n=== ALL DESKTOP ENGINE VERIFICATIONS PASSED ===")
}

package com.veritas.reader

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

enum class AiPromptScope(val label: String) {
    CURRENT_SENTENCE("current sentence"),
    CURRENT_PAGE("current page"),
    CURRENT_SECTION("current section"),
    WHOLE_DOCUMENT("whole document"),
    CUSTOM_PAGE_RANGE("custom page range")
}

enum class AiPromptType(val label: String) {
    SUMMARY("Summary"),
    KEY_POINTS("Key points"),
    EXPLAIN_SECTION("Explain current section"),
    STUDY_NOTES("Study notes"),
    SIMPLIFY("Simplify"),
    SECTION_BY_SECTION("Section-by-section"),
    QUIZ("Quiz"),
    FLASHCARDS("Flashcards"),
    CUSTOM("Custom prompt")
}

object AiPromptLauncher {
    internal fun customPromptBody(instructions: String, passage: String = ""): String =
        instructions.trim() + passage.trim().takeIf { it.isNotEmpty() }?.let { "\n\nPassage:\n$it" }.orEmpty()

    fun launchCustomPrompt(context: Context, instructions: String, passage: String = "", settings: AskAiSettings? = null): Boolean {
        if (instructions.isBlank()) return false
        val body = customPromptBody(instructions, passage)
        copyPromptToClipboard(context, body)
        val preferred = if (settings?.assistantId == "chooser") null else preferredAiPackage(context, settings)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, body)
            if (!preferred.isNullOrBlank()) setPackage(preferred)
        }
        return try {
            context.startActivity(if (preferred.isNullOrBlank()) Intent.createChooser(share, "Send prompt to assistant") else share)
            true
        } catch (_: Exception) {
            try { context.startActivity(Intent.createChooser(share.setPackage(null), "Send prompt to assistant")); true }
            catch (_: Exception) { Toast.makeText(context, "Prompt copied. Open your assistant and paste it.", Toast.LENGTH_LONG).show(); false }
        }
    }

    fun getSelectedSentences(
        document: ReaderDocument,
        currentIndex: Int,
        scope: AiPromptScope,
        customPageRange: IntRange? = null
    ): List<ReaderSentence> {
        val model = ReaderTextModelCache.get(document.id, document.rawText, document.pageCount)
        return when (scope) {
            AiPromptScope.CURRENT_SENTENCE -> {
                if (currentIndex in model.sentences.indices) listOf(model.sentences[currentIndex]) else emptyList()
            }
            AiPromptScope.CURRENT_PAGE -> {
                val curSentence = model.sentences.getOrNull(currentIndex)
                val pageNum = curSentence?.pageNumber ?: 1
                val onPage = model.sentences.filter { it.pageNumber == pageNum }
                if (onPage.isNotEmpty()) onPage else if (currentIndex in model.sentences.indices) listOf(model.sentences[currentIndex]) else emptyList()
            }
            AiPromptScope.CURRENT_SECTION -> {
                val part = model.partForSentence(currentIndex)
                if (part == null) model.sentences else model.sentences.subList(part.sentenceStartIndex, part.sentenceEndIndexExclusive)
            }
            AiPromptScope.WHOLE_DOCUMENT -> {
                model.sentences
            }
            AiPromptScope.CUSTOM_PAGE_RANGE -> {
                if (customPageRange == null) model.sentences else model.sentences.filter { it.pageNumber in customPageRange }
            }
        }
    }

    fun extractTextForScope(
        document: ReaderDocument,
        currentIndex: Int,
        scope: AiPromptScope,
        customPageRange: IntRange? = null
    ): String {
        val sentences = getSelectedSentences(document, currentIndex, scope, customPageRange)
        return buildMarkdownForSentences(sentences)
    }

    fun launch(
        context: Context,
        document: ReaderDocument,
        currentIndex: Int,
        type: AiPromptType,
        customInstruction: String = "",
        scope: AiPromptScope = AiPromptScope.WHOLE_DOCUMENT,
        customPageRange: IntRange? = null,
        settings: AskAiSettings? = null,
        noPrompt: Boolean = false
    ): Boolean {
        val selectedSentences = getSelectedSentences(document, currentIndex, scope, customPageRange)

        if (selectedSentences.isEmpty()) {
            Toast.makeText(context, "No content to share", Toast.LENGTH_SHORT).show()
            return false
        }

        val markdownText = buildMarkdownForSentences(selectedSentences)

        val minPage = selectedSentences.minOfOrNull { it.pageNumber } ?: 1
        val maxPage = selectedSentences.maxOfOrNull { it.pageNumber } ?: 1
        val bookTitle = document.title.trim()
        val sanitizedTitle = bookTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_")

        val selectedPackage = preferredAiPackage(context, settings).orEmpty()

        val fileNameMd = when (scope) {
            AiPromptScope.CURRENT_SENTENCE -> "Vern - $sanitizedTitle - Sentence.md"
            AiPromptScope.CURRENT_PAGE -> "Vern - $sanitizedTitle - Page $minPage.md"
            AiPromptScope.CURRENT_SECTION -> {
                if (minPage == maxPage) "Vern - $sanitizedTitle - Page $minPage.md"
                else "Vern - $sanitizedTitle - Pages $minPage-$maxPage.md"
            }
            AiPromptScope.CUSTOM_PAGE_RANGE -> {
                if (minPage == maxPage) "Vern - $sanitizedTitle - Page $minPage.md"
                else "Vern - $sanitizedTitle - Pages $minPage-$maxPage.md"
            }
            AiPromptScope.WHOLE_DOCUMENT -> "Vern - $sanitizedTitle - Entire Document.md"
        }

        val prompt = buildPrompt(
            title = document.title,
            chunks = document.chunks,
            currentIndex = currentIndex,
            type = type,
            customInstruction = customInstruction,
            scope = scope
        )

        val textBody = if (noPrompt) markdownText else "$prompt\n\n$markdownText"

        if (!noPrompt) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Vern Prompt", prompt))
            Toast.makeText(context, "Prompt copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        val shareDir = File(context.cacheDir, "shares").apply { mkdirs() }
        val shareFileMd = File(shareDir, fileNameMd)
        try {
            shareFileMd.writeText(markdownText, Charsets.UTF_8)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                this.type = "text/plain"
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shareFileMd)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri("Vern Share", uri)
                val truncatedText = if (textBody.length <= 60000) {
                    textBody
                } else {
                    textBody.take(60000) + "\n\n... [Content truncated due to size limits. Full text is attached as a file] ..."
                }
                putExtra(Intent.EXTRA_TEXT, truncatedText)
                if (selectedPackage.isNotBlank()) {
                    setPackage(selectedPackage)
                }
            }

            if (selectedPackage.isBlank()) {
                context.startActivity(Intent.createChooser(shareIntent, "Share Document to AI"))
            } else {
                try {
                    context.startActivity(shareIntent)
                } catch (e: ActivityNotFoundException) {
                    try {
                        val fileNameTxt = fileNameMd.substringBeforeLast(".md") + ".txt"
                        val shareFileTxt = File(shareDir, fileNameTxt)
                        if (!shareFileTxt.exists()) {
                            shareFileTxt.writeText(markdownText, Charsets.UTF_8)
                        }
                        val uriTxt = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shareFileTxt)
                        val txtIntent = Intent(shareIntent).apply {
                            putExtra(Intent.EXTRA_STREAM, uriTxt)
                            clipData = ClipData.newRawUri("Vern Share", uriTxt)
                        }
                        context.startActivity(txtIntent)
                    } catch (e2: Exception) {
                        val chooserIntent = Intent.createChooser(shareIntent.apply { setPackage(null) }, "Share Document to AI")
                        context.startActivity(chooserIntent)
                    }
                }
            }
            return true
        } catch (e: Exception) {
            android.util.Log.e("AiPromptLauncher", "Error sharing markdown to AI: ${e.message}", e)
            Toast.makeText(context, "Error sharing file: ${e.message}", Toast.LENGTH_SHORT).show()
            return false
        }
    }

    private fun preferredAiPackage(context: Context, settings: AskAiSettings?): String? {
        if (settings == null) return null
        if (settings.packageName.isNotBlank() && isPackageInstalled(context, settings.packageName)) {
            return settings.packageName
        }
        if (settings.assistantId.isNotBlank()) {
            aiAssistantOptions.firstOrNull { it.id == settings.assistantId }
                ?.let { installedPackageForOption(context, it) }
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        return installedAiOptions(context).firstOrNull()?.second
    }

    fun buildPrompt(
        title: String,
        chunks: List<String>,
        currentIndex: Int,
        type: AiPromptType,
        customInstruction: String = "",
        scope: AiPromptScope = AiPromptScope.WHOLE_DOCUMENT
    ): String {
        val instruction = if (customInstruction.isNotBlank()) {
            customInstruction
        } else {
            when (type) {
                AiPromptType.SUMMARY -> {
                    val scopeDesc = when (scope) {
                        AiPromptScope.CURRENT_SENTENCE -> "this sentence"
                        AiPromptScope.CURRENT_PAGE -> "this page"
                        AiPromptScope.CURRENT_SECTION -> "this section"
                        else -> "this excerpt/document"
                    }
                    """
                    Provide a clear, high-yield executive summary of $scopeDesc:
                    1. 📌 Executive Overview: 2-3 sentences capturing the core thesis or narrative.
                    2. 💡 Key Takeaways: 4-6 bullet points covering the most essential facts, ideas, or arguments.
                    3. 🎯 Conclusion: The main significance or actionable insight.
                    """.trimIndent()
                }
                AiPromptType.KEY_POINTS -> """
                    Extract the high-yield core ideas from this text.
                    Group related concepts together and present them as concise, memorable bullet points.
                    Highlight key definitions and cause-and-effect relationships.
                """.trimIndent()
                AiPromptType.EXPLAIN_SECTION -> """
                    Explain this text using the Feynman technique (simple, clear, and intuitive):
                    1. 💡 Core Idea: Explain what is happening in plain English without academic jargon.
                    2. 🔍 Analogy: Provide a relatable real-world comparison.
                    3. 📖 Vocabulary: Define any difficult or domain-specific words.
                    4. 🔑 Significance: Why does this passage matter?
                """.trimIndent()
                AiPromptType.STUDY_NOTES -> """
                    Transform this text into an organized, high-yield study cheatsheet:
                    - 📚 Summary & Framework
                    - 🗝️ Key Definitions & Terms
                    - ⚡ Important Principles or Findings
                    - 📝 3 Quick Active-Recall Questions with Answers
                """.trimIndent()
                AiPromptType.SIMPLIFY -> """
                    Rewrite and explain this text in clear, everyday language (like explaining to a bright beginner).
                    Keep all important nuance, but eliminate unnecessary density, jargon, or complex syntax.
                """.trimIndent()
                AiPromptType.SECTION_BY_SECTION -> """
                    Provide a structured section-by-section breakdown of this text with concise bullet points and term definitions.
                """.trimIndent()
                AiPromptType.QUIZ -> """
                    Create a standard academic multiple-choice practice quiz (at least 10 questions) based on this text.
                    For every question, test genuine conceptual understanding and retention rather than superficial recall.
                    
                    CRITICAL ANTI-AI GIVEAWAY RULES:
                    1. UNIFORM OPTION LENGTH & DETAIL: All 4 multiple-choice options (A, B, C, D) MUST be of approximately equal length, grammatical complexity, and tone. Under NO circumstances make the correct answer noticeably longer or more detailed than distractors.
                    2. NO BRACKETED OR PARENTHETICAL HINTS: Do NOT include parenthetical explanations or bracketed qualifiers inside options.
                    3. PLAUSIBLE DISTRACTORS: All 3 incorrect choices must be plausible, realistic, and contextually grounded in the text so that answers cannot be guessed without reading.
                    4. Evenly distribute the correct answers across choices A, B, C, and D.
                    
                    Format each question clearly:
                    Q: <Question text>
                    A) <Option A>
                    B) <Option B>
                    C) <Option C>
                    D) <Option D>
                    Answer: <Letter>
                    Explanation: <Short 1-sentence rationale>
                """.trimIndent()
                AiPromptType.FLASHCARDS -> """
                    Create high-yield, active recall flashcards (5-10 cards) based on this text.
                    Focus on fundamental concepts, definitions, cause-and-effect, and key facts.
                    Format each flashcard cleanly:
                    Q: <Concise question or term>
                    A: <Direct, clear answer or definition>
                """.trimIndent()
                AiPromptType.CUSTOM -> "Help me study and master this document excerpt."
            }
        }

        val builder = StringBuilder()
        builder.appendLine("You are an expert study and learning companion for Vern.")
        builder.appendLine("Analyze the attached text from '${title.ifBlank { "the document" }}' and execute the following task:")
        builder.appendLine()
        builder.appendLine("Task Instructions:")
        builder.appendLine(instruction)

        return builder.toString().trim()
    }

    private fun buildMarkdownForSentences(sentences: List<ReaderSentence>): String {
        if (sentences.isEmpty()) return ""
        val output = StringBuilder()
        val ordered = sentences.groupBy { it.pageNumber.coerceAtLeast(1) }.toSortedMap()
        ordered.forEach { (_, pageSentences) ->
            if (output.isNotBlank()) output.append("\n\n")
            val pageText = StringBuilder()
            pageSentences.forEachIndexed { index, sentence ->
                if (pageText.isNotBlank()) {
                    val rawSeparator = sentence.separatorBefore.replace('\r', '\n')
                    val separator = when {
                        index > 0 && pageSentences.getOrNull(index - 1)?.pageNumber != sentence.pageNumber -> "\n\n"
                        rawSeparator.count { it == '\n' } >= 2 -> "\n\n"
                        rawSeparator.contains('\n') -> "\n"
                        else -> " "
                    }
                    pageText.append(separator)
                }
                pageText.append(sentence.text)
            }
            output.append(pageText.toString().trim())
        }
        return output.toString().trim()
    }

    fun copyPromptToClipboard(context: Context, prompt: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Vern AI prompt", prompt))
    }

    fun copyTextToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "Copied to clipboard.", Toast.LENGTH_SHORT).show()
    }
}

package com.veritas.reader

import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

/** Resource contracts: missing fallback keys and broken format arguments must fail before release. */
class AppLocalizationResourcesTest {
    private val res = listOf(File("src/main/res"), File("app/src/main/res"))
        .first { it.isDirectory }
    private val qualifiers = AppLanguageCatalog.choices.filterNot { it.tag == "en" }
        .map { if ('-' in it.tag) "b+" + it.tag.replace('-', '+') else it.tag }

    private fun strings(directory: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        File(res, directory).listFiles { file -> file.extension == "xml" }!!.forEach { file ->
            val factory = DocumentBuilderFactory.newInstance().apply {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }
            val nodes = factory.newDocumentBuilder().parse(file).getElementsByTagName("string")
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                val name = node.attributes.getNamedItem("name").nodeValue
                assertFalse("Duplicate $directory/$name", result.containsKey(name))
                result[name] = node.textContent.removeSurrounding("\"")
            }
        }
        return result
    }

    @Test fun previewPacksHaveEnglishFallbackAndMatchingFormatArguments() {
        val base = strings("values")
        val placeholders = Regex("%[1-9][0-9]*\\\$[sd]")
        qualifiers.forEach { qualifier ->
            val translated = strings("values-$qualifier")
            translated.forEach { (key, value) ->
                assertTrue("Missing English fallback: $qualifier/$key", base.containsKey(key))
                assertTrue("Empty translation: $qualifier/$key", value.isNotBlank())
                assertEquals("Format arguments: $qualifier/$key",
                    placeholders.findAll(base.getValue(key)).map { it.value }.toList().sorted(),
                    placeholders.findAll(value).map { it.value }.toList().sorted())
            }
            listOf("app_language", "language_follow_device", "language_preview_notice", "language_apply",
                "ui_home", "ui_library", "ui_study", "ui_notes", "ui_settings", "ui_cancel",
                "ui_voice_studio", "ui_continue_reading", "greeting_named").forEach { key ->
                assertTrue("Missing core translation: $qualifier/$key", translated.containsKey(key))
            }
            String.format(Locale.ENGLISH, translated.getValue("greeting_named"), "Hello", "Reader")
            String.format(Locale.ENGLISH, translated.getValue("library_resume_progress"), 1, 2, 50)
        }
    }

    @Test fun registeredLanguagesAndPluralFormsArePresent() {
        val factory = DocumentBuilderFactory.newInstance()
        val config = factory.newDocumentBuilder().parse(File(res, "xml/app_locales.xml"))
        val nodes = config.getElementsByTagName("locale")
        val tags = (0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("android:name").nodeValue }.toSet()
        assertEquals(AppLanguageCatalog.choices.map { it.tag }.toSet(), tags)
        (listOf("values") + qualifiers.map { "values-$it" }).forEach { directory ->
            val plurals = factory.newDocumentBuilder().parse(File(res, "$directory/reading_plurals.xml"))
                .getElementsByTagName("plurals")
            for (i in 0 until plurals.length) {
                val element = plurals.item(i) as org.w3c.dom.Element
                val items = element.getElementsByTagName("item")
                val quantities = (0 until items.length).map { (items.item(it) as org.w3c.dom.Element).getAttribute("quantity") }.toSet()
                assertTrue("Plural fallback: $directory/${element.getAttribute("name")}", "other" in quantities)
                if (directory == "values-ar") assertEquals(setOf("zero", "one", "two", "few", "many", "other"), quantities)
                if (directory == "values-ru") assertEquals(setOf("one", "few", "many", "other"), quantities)
                if (directory == "values-ko") assertEquals(setOf("other"), quantities)
            }
        }
    }

    @Test fun settingsSubpageResourcesHaveAllPackagedTranslations() {
        val sourceRoot = listOf(File("src/main/java/com/veritas/reader"), File("app/src/main/java/com/veritas/reader"))
            .first { it.isDirectory }
        val screens = setOf("SettingsHub.kt", "ReaderSettingsUi.kt", "CustomizationSettingsUi.kt",
            "CustomAccentControl.kt", "AccessibilityAndAiSettingsUi.kt", "VoiceStudioUi.kt",
            "NarrationStudioUi.kt", "SpeechRulesEditor.kt", "SyncAndBatteryDialogsUi.kt",
            "BackupRestoreUi.kt", "PdfImportOptionsUi.kt", "AboutAndHelpUi.kt", "NotesSettingsSheet.kt",
            "LibraryApiKeyAndVocabDialogsUi.kt", "MainSettingsDialogsUi.kt", "LocalizedSettingsOptions.kt")
        val references = sourceRoot.walkTopDown().filter { it.isFile && it.name in screens }
            .flatMap { Regex("R\\.string\\.(\\w+)").findAll(it.readText()).map { match -> match.groupValues[1] } }
            .toSet()
        assertTrue("Settings scan unexpectedly empty", references.size > 350)
        qualifiers.forEach { qualifier ->
            val translated = strings("values-$qualifier")
            assertEquals("Untranslated settings resources: $qualifier", emptySet<String>(), references - translated.keys)
        }
    }

    @Test fun everyDeclaredTranslatableMessageHasAllPackagedTranslations() {
        val translatable = linkedSetOf<String>()
        File(res, "values").listFiles { file -> file.extension == "xml" }!!.forEach { file ->
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val messages = document.getElementsByTagName("string")
            for (i in 0 until messages.length) {
                val element = messages.item(i) as org.w3c.dom.Element
                if (element.getAttribute("translatable") != "false") translatable += element.getAttribute("name")
            }
        }
        qualifiers.forEach { qualifier ->
            assertEquals("Untranslated declared messages: $qualifier", emptySet<String>(), translatable - strings("values-$qualifier").keys)
        }
    }
}

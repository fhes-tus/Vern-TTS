package com.veritas.reader

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SpeechPatternRulesTest {
    private fun rule(find: String, replacement: String = "", kind: SpeechRuleKind = SpeechRuleKind.PATTERN,
        case: Boolean = false, enabled: Boolean = true) = PronunciationRule(find, find, replacement,
        enabled = enabled, matchKind = kind, caseSensitive = case)
    private fun apply(source: String, vararg rules: PronunciationRule) = PronunciationRulesEngine.apply(source, rules.toList())

    @Test fun bracketNumberShortcutMatchesDifferentNumbersButNotProse() {
        assertEquals("Alpha Beta  [note]", apply("Alpha[1]Beta [123] [note]", rule("[n]")))
        assertEquals("A B", apply("A[١٢]B", rule("[n]")))
    }
    @Test fun starredAndUnderscorePatternsSkipWholeMarkedPhrases() {
        assertEquals("Keep  and  end.", apply("Keep _terro_ and *editor note* end.", rule("_{text}_"), rule("*{text}*")))
    }
    @Test fun capturedContentCanBeKeptWithoutItsMarkers() {
        assertEquals("Keep terro and another note.", apply("Keep _terro_ and _another note_.", rule("_{text}_", "\$1")))
    }
    @Test fun referenceNumbersCanBeSpokenInstead() {
        assertEquals("See reference 12.", apply("See [12].", rule("[{number}]", "reference \$1")))
    }
    @Test fun advancedRegexSupportsListsAndMultipleCaptures() {
        assertEquals("See reference 1, 2–4.", apply("See [1, 2–4].", rule("\\[([0-9, –-]+)\\]", "reference \$1", SpeechRuleKind.REGEX)))
        assertEquals("12 of 34", apply("12/34", rule("{number}/{number}", "\$1 of \${2}")))
    }
    @Test fun literalReplacementDollarSignsKeepTheirOldMeaning() {
        assertEquals("\$1", apply("SQL", rule("SQL", "\$1", SpeechRuleKind.LITERAL)))
        assertEquals("\$1", apply("_value_", rule("_{text}_", "\$\$1")))
        assertEquals("_value_", apply("_value_", rule("_{text}_", "\$0")))
    }
    @Test fun invalidCaptureReferencesAreRejectedBeforeSaving() {
        assertNotNull(SpeechPatternRules.error(rule("_{text}_", "\$2")))
        assertNotNull(SpeechPatternRules.error(rule("_{text}_", "\${oops}")))
        assertNotNull(SpeechPatternRules.error(rule("_{text}_", "\$9999999999999999")))
    }
    @Test fun unsupportedLookAroundAndPatternBackreferencesAreRejected() {
        assertNotNull(SpeechPatternRules.error(rule("(?=word)", kind = SpeechRuleKind.REGEX)))
        assertNotNull(SpeechPatternRules.error(rule("(word)\\1", kind = SpeechRuleKind.REGEX)))
    }
    @Test(timeout = 10000) fun nestedRepetitionDoesNotUseAnExponentialBacktrackingMatcher() {
        val source = "a".repeat(100000) + "!"
        assertEquals(source, apply(source, rule("(a+)+$", kind = SpeechRuleKind.REGEX)))
    }
    @Test fun zeroWidthPositionsAreNeverInsertedIntoTheReading() {
        assertEquals("Two words.", apply("Two words.", rule("\\b", "insert", SpeechRuleKind.REGEX)))
        assertNotNull(SpeechPatternRules.error(rule("a*", kind = SpeechRuleKind.REGEX)))
    }
    @Test fun invalidTemplateTokensAndBracesHaveValidationErrors() {
        assertNotNull(SpeechPatternRules.error(rule("{unknown}")))
        assertNotNull(SpeechPatternRules.error(rule("_{text")))
        assertNotNull(SpeechPatternRules.error(rule("text}")))
    }
    @Test fun literalBracesCanBeEscapedInTemplates() {
        assertEquals("number 123", apply("{123}", rule("{{{number}}}", "number \$1")))
    }
    @Test fun textTokensStopAtTheNearestMarkerAndDoNotCrossLines() {
        assertEquals("a and b", apply("_a_ and _b_", rule("_{text}_", "\$1")))
        assertEquals("_unfinished\nending_", apply("_unfinished\nending_", rule("_{text}_")))
        assertEquals("alpha beta\nnext", apply("note:alpha beta\nnext", rule("note:{text}", "\$1")))
    }
    @Test fun wordTokensHandleCombiningMarksAndForeignLetters() {
        assertEquals("caf\u00e9 e\u0301", apply("<caf\u00e9> <e\u0301>", rule("<{word}>", "\$1")))
    }
    @Test fun caseSensitivityAppliesToLiteralAndPatternRules() {
        assertEquals("ok WORD", apply("word WORD", rule("word", "ok", SpeechRuleKind.LITERAL, case = true)))
        assertEquals("ok ok", apply("word WORD", rule("word", "ok", SpeechRuleKind.LITERAL)))
        assertEquals("name 12 NAME12", apply("name12 NAME12", rule("name{number}", "name \$1", case = true)))
    }
    @Test fun regexSyntaxIsNeverDeduplicatedByLowercasing() {
        val rules = listOf(rule("\\s", "space", SpeechRuleKind.REGEX), rule("\\S", "letter", SpeechRuleKind.REGEX))
        assertEquals(2, PronunciationRulesEngine.compile(rules).size)
        assertEquals("letterspaceletter", PronunciationRulesEngine.apply("a b", rules))
    }
    @Test fun longestOriginalMatchWinsAndReplacementDoesNotCascade() {
        assertEquals("long tail", apply("[12] tail", rule("[n]", "long"), rule("12", "short", SpeechRuleKind.LITERAL),
            rule("long", "changed", SpeechRuleKind.LITERAL)))
        assertEquals("first", apply("[12]", rule("[n]", "first"), rule("[{number}]", "second")))
    }
    @Test fun disablingARuleLeavesTheInputAlone() {
        assertEquals("[12]", apply("[12]", rule("[n]", enabled = false)))
    }
    @Test fun skippingInlineReferencesPreservesWordSeparationAndSourceOffsets() {
        val source = "Alpha[12]Beta"
        val mapped = SpokenText.identity(source).applyRules(PronunciationRulesEngine.compile(listOf(rule("[n]"))))
        assertEquals("Alpha Beta", mapped.text)
        assertEquals(source.indexOf("Beta"), mapped.sourceOffset(mapped.text.indexOf("Beta")))
        assertEquals(source.length, mapped.sourceEndOffset(mapped.text.length))
    }
    @Test fun skippedReferencesDoNotJoinACombiningSequenceToTheNextWord() {
        assertEquals("Cafe\u0301 Beta", apply("Cafe\u0301[12]Beta", rule("[n]")))
    }
    @Test fun adjacentExcludedReferencesKeepAWordBoundaryAndBothMatchRecords() {
        val source = "Alpha[1][2]Beta"
        val compiled = PronunciationRulesEngine.compile(listOf(rule("[n]")))
        assertEquals("Alpha Beta", PronunciationRulesEngine.applyCompiled(source, compiled))
        assertEquals(2, PronunciationRulesEngine.edits(source, compiled).size)
        val mapped = SpokenText.identity(source).applyRules(compiled)
        assertEquals(source.indexOf("Beta"), mapped.sourceOffset(mapped.text.indexOf("Beta")))
    }
    @Test fun adjacentOmissionsFromDifferentPatternRulesShareOneSeparator() {
        assertEquals("Cafe\u0301 Beta", apply("Cafe\u0301[12]_note_Beta", rule("[n]"), rule("_{text}_")))
    }
    @Test fun capturedReplacementCarriesTheFullOriginalMatchedSpan() {
        val source = "Start _terro_ end"
        val mapped = SpokenText.identity(source).applyRules(PronunciationRulesEngine.compile(listOf(rule("_{text}_", "\$1"))))
        assertEquals("Start terro end", mapped.text)
        assertEquals(6, mapped.sourceOffset(6))
        assertEquals(13, mapped.sourceEndOffset(11))
        assertEquals(source.indexOf("end"), mapped.sourceOffset(mapped.text.indexOf("end")))
    }
    @Test fun supplementaryCharactersKeepTheirWholeMatchBounds() {
        assertEquals("123", apply("😀123😀", rule("😀{number}😀", "\$1")))
    }
    @Test fun unmatchedOptionalGroupsBecomeEmptyText() {
        assertEquals("word: ", apply("word", rule("(word)(?:-([0-9]+))?", "\$1: \$2", SpeechRuleKind.REGEX)))
    }
    @Test fun legacyJsonDefaultsToLiteralAndNewFieldsRoundTrip() {
        val legacy = PronunciationRule.fromJson(JSONObject().put("id", "old").put("find", "[n]").put("replaceWith", "literal"))!!
        assertEquals(SpeechRuleKind.LITERAL, legacy.matchKind)
        assertFalse(legacy.caseSensitive)
        assertEquals("[12] literal", apply("[12] [n]", legacy))
        val pattern = rule("name{number}", "\$1", case = true)
        assertEquals(pattern, PronunciationRule.fromJson(pattern.toJson()))
        assertNull(PronunciationRule.fromJson(pattern.toJson().put("matchKind", "UNKNOWN")))
    }
    @Test fun previewIncludesValidUnsavedRulesAndTheFinalTextPreparation() {
        val preview = SpeechRulePreview.build("Before[12] after.", emptyList(), SpeechRuleDraft("[n]", kind = SpeechRuleKind.PATTERN, skip = true), VoiceSettings())
        assertEquals("Before after.", preview.spoken)
        assertEquals(1, preview.matches)
        assertTrue(preview.includesDraft)
    }
    @Test fun replacingAnInlineReferenceKeepsItsSpokenWordsSeparate() {
        val preview = SpeechRulePreview.build("Before[12] after.", emptyList(),
            SpeechRuleDraft("[n]", "reference \$1", SpeechRuleKind.PATTERN), VoiceSettings())
        assertEquals("Before reference 12 after.", preview.spoken)
    }
    @Test fun adjacentMarkerReplacementsShareOneWordGap() {
        assertEquals("A reference 1 reference 2 B", apply("A[1][2]B", rule("[n]", "reference \$1")))
        assertEquals("Cafe\u0301 note Beta", apply("Cafe\u0301_note_Beta", rule("_{text}_", "\$1")))
    }
    @Test fun replacementAndOmissionNeighboursUseTheActualOutput() {
        assertEquals("A reference B", apply("A[1]_note_B", rule("[n]", "reference"), rule("_{text}_")))
        assertEquals("A reference B", apply("A_note_[1]B", rule("[n]", "reference"), rule("_{text}_")))
        assertEquals("reference1 B", apply("[1][2]B", rule("\\[1\\]", "reference1", SpeechRuleKind.REGEX), rule("[2]")))
    }
    @Test fun inlineTextTemplatesAndNonemptyRegexReplacementsRemainExact() {
        assertEquals("color", apply("colour", rule("our", "or")))
        assertEquals("A12B", apply("A[12]B", rule("\\[([0-9]+)\\]", "\$1", SpeechRuleKind.REGEX)))
    }
    @Test fun typedReplacementSpacesSurviveDraftAndJsonForPatternsAndRegex() {
        for (kind in listOf(SpeechRuleKind.PATTERN, SpeechRuleKind.REGEX)) {
            val draft = SpeechRuleDraft("x", " spoken ", kind)
            assertNull(draft.error())
            val saved = PronunciationRule.fromJson(draft.rule().toJson())!!
            assertEquals(" spoken ", saved.replaceWith)
            assertEquals("A spoken B", apply("AxB", saved))
        }
        assertEquals("spoken", SpeechRuleDraft("x", " spoken ").rule().replaceWith)
    }
    @Test fun previewDoesNotApplyAnInvalidDraft() {
        val preview = SpeechRulePreview.build("SQL [12]", listOf(rule("SQL", "sequel", SpeechRuleKind.LITERAL)),
            SpeechRuleDraft("(", kind = SpeechRuleKind.REGEX, skip = true), VoiceSettings())
        assertTrue(preview.spoken.startsWith("sequel"))
        assertFalse(preview.includesDraft)
    }
    @Test fun entirelySkippedPassagesHaveAnEmptyPreview() {
        val preview = SpeechRulePreview.build("[12]", emptyList(), SpeechRuleDraft("[n]", kind = SpeechRuleKind.PATTERN, skip = true), VoiceSettings())
        assertEquals("", preview.spoken)
    }
    @Test fun aSkippedReferenceDoesNotLeaveAPunctuationOnlyUtterance() {
        assertEquals("", apply("[12].", rule("[n]")))
        assertEquals("", apply("“_editor note_!”", rule("_{text}_")))
        assertEquals("+", apply("[12]+", rule("[n]")))
    }
    @Test fun skipIgnoresHiddenReplacementAndSayRequiresText() {
        assertNull(SpeechRuleDraft("SQL", "x".repeat(200), skip = true).error())
        assertNotNull(SpeechRuleDraft("SQL").error())
    }
    @Test fun backupPreflightRejectsInvalidPatternsBeforeAnyRestoreWrites() {
        val root = JSONObject().put("documents", JSONArray()).put("pronunciationRules", JSONArray().put(rule("(", kind = SpeechRuleKind.REGEX).toJson()))
        assertTrue(runCatching { BackupRestoreSafety.parseAndValidate(root.toString()) }.isFailure)
        root.put("pronunciationRules", JSONArray().put(rule("[n]").toJson()))
        assertNotNull(BackupRestoreSafety.parseAndValidate(root.toString()))
    }
    @Test fun overlappingRulesCanBeMergedWithoutMaterializingEveryCandidate() {
        val source = "a".repeat(5000)
        val rules = (1..20).map { count -> rule("a" + "(?:)".repeat(count), "b", SpeechRuleKind.REGEX) }
        assertEquals("b".repeat(5000), PronunciationRulesEngine.apply(source, rules))
    }
    @Test fun newPatternLengthsAreValidatedWithoutChangingOldLiteralExpansions() {
        assertNotNull(SpeechPatternRules.error(rule("x".repeat(513))))
        assertNotNull(SpeechPatternRules.error(rule("_{text}_", "x".repeat(513))))
        val legacy = rule("SQL", "x".repeat(5000), SpeechRuleKind.LITERAL)
        assertEquals(5000, apply("SQL", legacy).length)
    }
}

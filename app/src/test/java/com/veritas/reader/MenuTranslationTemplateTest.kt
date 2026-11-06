package com.veritas.reader

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class MenuTranslationTemplateTest {
    @Test fun reinsertsValuesLocallyAfterValidatingTranslatedPlaceholders() {
        val template = requireNotNull(MenuTranslationTemplate.from("Open %1\$s at page %2\$d"))
        assertEquals("Open [[V0]] at page [[V1]]", template.source)
        val values = arrayOf<Any>("A private book title", 12)
        assertEquals("Seite 12 in A private book title öffnen",
            template.format("Seite [[V1]] in [[V0]] öffnen", Locale.GERMAN, values))
        assertNull(template.format("Seite [[V1]] öffnen", Locale.GERMAN, values))
        assertNull(template.format("[[V0]] [[V1]] [[V1]]", Locale.GERMAN, values))
        assertNull(MenuTranslationTemplate.from("%1\$s (%2\$s)"))
    }
}

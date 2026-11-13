package com.veritas.reader

import org.junit.Assert.*
import org.junit.Test

class DictionaryLookupSourceTest {
    private val local = VocabularyDefinition("Local meaning.", examples = listOf("Local example."))
    private val remote = VocabularyDefinition("Online meaning.", examples = listOf("Online example."))
    @Test fun offlineNeverCallsOnlineEvenWhenAWordIsMissing() {
        assertEquals(local, DictionaryLookupSource.OFFLINE.resolve({ local }, { error("Unexpected network lookup") }))
        assertNull(DictionaryLookupSource.OFFLINE.resolve({ null }, { error("Unexpected network fallback") }))
    }
    @Test fun onlineBypassesAnInstalledOfflineDictionary() {
        assertEquals(remote, DictionaryLookupSource.ONLINE.resolve({ error("Unexpected offline lookup") }, { remote }))
    }
    @Test fun unavailableOnlineDoesNotSubstituteOffline() {
        assertNull(DictionaryLookupSource.ONLINE.resolve({ error("Unexpected offline substitution") }, { null }))
    }
    @Test fun existingAutomaticLookupsRetainTheirFallback() {
        assertEquals(local, DictionaryLookupSource.AUTOMATIC.resolve({ local }, { error("Unneeded online lookup") }))
        assertEquals(remote, DictionaryLookupSource.AUTOMATIC.resolve({ null }, { remote }))
    }
    @Test fun backgroundRequestsRetainTheirSourceAndOlderRequestsRemainAutomatic() {
        DictionaryLookupSource.entries.forEach { assertEquals(it, DictionaryLookupSource.fromId(it.name)) }
        assertEquals(DictionaryLookupSource.AUTOMATIC, DictionaryLookupSource.fromId(null))
        assertEquals(DictionaryLookupSource.AUTOMATIC, DictionaryLookupSource.fromId("unknown"))
    }
}

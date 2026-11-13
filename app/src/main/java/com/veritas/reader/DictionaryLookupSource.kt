package com.veritas.reader

internal enum class DictionaryLookupSource {
    AUTOMATIC, OFFLINE, ONLINE;

    fun resolve(offline: () -> VocabularyDefinition?, online: () -> VocabularyDefinition?): VocabularyDefinition? = when (this) {
        OFFLINE -> offline()
        ONLINE -> online()
        AUTOMATIC -> offline() ?: online()
    }

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.name == id } ?: AUTOMATIC
    }
}

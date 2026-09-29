package com.veritas.reader

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class VeritasReadingListSortMode {
    MANUAL,
    TITLE_ASCENDING,
    TITLE_DESCENDING,
    NEWEST_ADDED,
    OLDEST_ADDED
}

data class VeritasReadingListItem(
    val documentId: String,
    val addedAt: Long,
    val position: Int
) {
    init {
        require(documentId.isNotBlank()) { "Reading list item documentId cannot be blank." }
        require(position >= 0) { "Reading list item position cannot be negative." }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("documentId", documentId)
        .put("addedAt", addedAt)
        .put("position", position)

    companion object {
        fun fromJson(obj: JSONObject?): VeritasReadingListItem? {
            val documentId = obj?.optString("documentId")?.trim().orEmpty()
            if (documentId.isBlank()) return null
            val source = obj ?: return null
            return VeritasReadingListItem(
                documentId = documentId,
                addedAt = source.optLong("addedAt", System.currentTimeMillis()),
                position = source.optInt("position", Int.MAX_VALUE)
            )
        }
    }
}

data class VeritasReadingList(
    val id: String,
    val title: String,
    val description: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val archived: Boolean = false,
    val sortMode: VeritasReadingListSortMode = VeritasReadingListSortMode.MANUAL,
    val items: List<VeritasReadingListItem> = emptyList()
) {
    init {
        require(id.isNotBlank()) { "Reading list id cannot be blank." }
        require(title.isNotBlank()) { "Reading list title cannot be blank." }
        require(items.map { it.documentId }.toSet().size == items.size) {
            "$id contains duplicate document ids."
        }
        require(items.map { it.position } == items.indices.toList()) {
            "$id items must be normalized to zero-based positions."
        }
    }

    val documentIds: List<String>
        get() = items.map { it.documentId }

    fun contains(documentId: String): Boolean =
        items.any { it.documentId == documentId }

    fun orderedItems(documentTitles: Map<String, String> = emptyMap()): List<VeritasReadingListItem> {
        return when (sortMode) {
            VeritasReadingListSortMode.MANUAL -> items.sortedBy { it.position }
            VeritasReadingListSortMode.TITLE_ASCENDING -> items.sortedWith(
                compareBy<VeritasReadingListItem> { documentTitles[it.documentId].orEmpty().lowercase() }
                    .thenBy { it.position }
            )
            VeritasReadingListSortMode.TITLE_DESCENDING -> items.sortedWith(
                compareByDescending<VeritasReadingListItem> { documentTitles[it.documentId].orEmpty().lowercase() }
                    .thenBy { it.position }
            )
            VeritasReadingListSortMode.NEWEST_ADDED -> items.sortedWith(
                compareByDescending<VeritasReadingListItem> { it.addedAt }.thenBy { it.position }
            )
            VeritasReadingListSortMode.OLDEST_ADDED -> items.sortedWith(
                compareBy<VeritasReadingListItem> { it.addedAt }.thenBy { it.position }
            )
        }
    }

    fun toJson(): JSONObject {
        val itemArray = JSONArray()
        items.forEach { itemArray.put(it.toJson()) }
        return JSONObject()
            .put("id", id)
            .put("title", title)
            .put("description", description)
            .put("createdAt", createdAt)
            .put("updatedAt", updatedAt)
            .put("archived", archived)
            .put("sortMode", sortMode.name)
            .put("items", itemArray)
    }

    companion object {
        fun create(
            title: String,
            description: String = "",
            now: Long = System.currentTimeMillis(),
            id: String = UUID.randomUUID().toString()
        ): VeritasReadingList = VeritasReadingList(
            id = id.trim().ifBlank { UUID.randomUUID().toString() },
            title = cleanTitle(title),
            description = description.trim(),
            createdAt = now,
            updatedAt = now
        )

        fun fromJson(obj: JSONObject?): VeritasReadingList? {
            val id = obj?.optString("id")?.trim().orEmpty()
            if (id.isBlank()) return null
            val source = obj ?: return null
            val createdAt = source.optLong("createdAt", System.currentTimeMillis())
            val updatedAt = source.optLong("updatedAt", createdAt)
            val sortMode = runCatching {
                VeritasReadingListSortMode.valueOf(source.optString("sortMode"))
            }.getOrDefault(VeritasReadingListSortMode.MANUAL)
            return VeritasReadingList(
                id = id,
                title = cleanTitle(source.optString("title")),
                description = source.optString("description").trim(),
                createdAt = createdAt,
                updatedAt = updatedAt,
                archived = source.optBoolean("archived", false),
                sortMode = sortMode,
                items = normalizeItems(readItems(source.optJSONArray("items")))
            )
        }

        internal fun cleanTitle(value: String): String =
            value.replace(Regex("\\s+"), " ").trim().ifBlank { "Untitled list" }

        internal fun normalizeItems(items: List<VeritasReadingListItem>): List<VeritasReadingListItem> {
            return items
                .filter { it.documentId.isNotBlank() }
                .sortedWith(compareBy<VeritasReadingListItem> { it.position }.thenBy { it.addedAt }.thenBy { it.documentId })
                .distinctBy { it.documentId }
                .mapIndexed { index, item -> item.copy(position = index) }
        }

        private fun readItems(array: JSONArray?): List<VeritasReadingListItem> {
            if (array == null) return emptyList()
            return buildList {
                for (index in 0 until array.length()) {
                    VeritasReadingListItem.fromJson(array.optJSONObject(index))?.let(::add)
                }
            }
        }
    }
}

data class VeritasReadingListCatalog(
    val lists: List<VeritasReadingList> = emptyList()
) {
    init {
        require(lists.map { it.id }.toSet().size == lists.size) {
            "Reading list catalog contains duplicate ids."
        }
    }

    val activeLists: List<VeritasReadingList>
        get() = lists.filterNot { it.archived }.sortedWith(compareBy<VeritasReadingList> { it.title.lowercase() }.thenBy { it.createdAt })

    fun list(id: String): VeritasReadingList? =
        lists.firstOrNull { it.id == id }

    fun listsContaining(documentId: String): List<VeritasReadingList> =
        lists.filter { it.contains(documentId) }

    fun createList(
        title: String,
        description: String = "",
        now: Long = System.currentTimeMillis(),
        id: String = UUID.randomUUID().toString()
    ): VeritasReadingListCatalog {
        val list = VeritasReadingList.create(title = title, description = description, now = now, id = id)
        if (lists.any { it.id == list.id }) return this
        return copy(lists = lists + list)
    }

    fun renameList(
        listId: String,
        title: String,
        description: String? = null,
        now: Long = System.currentTimeMillis()
    ): VeritasReadingListCatalog = updateList(listId) { list ->
        list.copy(
            title = VeritasReadingList.cleanTitle(title),
            description = description?.trim() ?: list.description,
            updatedAt = now
        )
    }

    fun archiveList(
        listId: String,
        archived: Boolean = true,
        now: Long = System.currentTimeMillis()
    ): VeritasReadingListCatalog = updateList(listId) { list ->
        if (list.archived == archived) list else list.copy(archived = archived, updatedAt = now)
    }

    fun deleteList(listId: String): VeritasReadingListCatalog =
        copy(lists = lists.filterNot { it.id == listId })

    fun addDocument(
        listId: String,
        documentId: String,
        now: Long = System.currentTimeMillis()
    ): VeritasReadingListCatalog = addDocuments(listId, listOf(documentId), now)

    fun addDocuments(
        listId: String,
        documentIds: List<String>,
        now: Long = System.currentTimeMillis()
    ): VeritasReadingListCatalog = updateList(listId) { list ->
        val newIds = documentIds.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .filterNot { candidate -> list.items.any { it.documentId == candidate } }
        if (newIds.isEmpty()) return@updateList list

        val nextItems = list.items + newIds.mapIndexed { offset, documentId ->
            VeritasReadingListItem(
                documentId = documentId,
                addedAt = now,
                position = list.items.size + offset
            )
        }
        list.copy(
            updatedAt = now,
            sortMode = VeritasReadingListSortMode.MANUAL,
            items = VeritasReadingList.normalizeItems(nextItems)
        )
    }

    fun removeDocument(
        listId: String,
        documentId: String,
        now: Long = System.currentTimeMillis()
    ): VeritasReadingListCatalog = updateList(listId) { list ->
        if (!list.contains(documentId)) return@updateList list
        list.copy(
            updatedAt = now,
            items = VeritasReadingList.normalizeItems(list.items.filterNot { it.documentId == documentId })
        )
    }

    fun removeDocumentEverywhere(
        documentId: String,
        now: Long = System.currentTimeMillis()
    ): VeritasReadingListCatalog =
        copy(lists = lists.map { list ->
            if (!list.contains(documentId)) list else list.copy(
                updatedAt = now,
                items = VeritasReadingList.normalizeItems(list.items.filterNot { it.documentId == documentId })
            )
        })

    fun moveDocument(
        listId: String,
        documentId: String,
        offset: Int,
        now: Long = System.currentTimeMillis()
    ): VeritasReadingListCatalog = updateList(listId) { list ->
        val current = list.items.sortedBy { it.position }.toMutableList()
        val oldIndex = current.indexOfFirst { it.documentId == documentId }
        if (oldIndex == -1 || offset == 0) return@updateList list
        val newIndex = (oldIndex + offset).coerceIn(0, current.lastIndex)
        if (oldIndex == newIndex) return@updateList list

        val item = current.removeAt(oldIndex)
        current.add(newIndex, item)
        list.copy(
            updatedAt = now,
            sortMode = VeritasReadingListSortMode.MANUAL,
            items = current.mapIndexed { index, listItem -> listItem.copy(position = index) }
        )
    }

    fun replaceDocuments(
        listId: String,
        documentIds: List<String>,
        now: Long = System.currentTimeMillis()
    ): VeritasReadingListCatalog = updateList(listId) { list ->
        val existingAddedAt = list.items.associate { it.documentId to it.addedAt }
        val nextItems = documentIds.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .mapIndexed { index, documentId ->
                VeritasReadingListItem(
                    documentId = documentId,
                    addedAt = existingAddedAt[documentId] ?: now,
                    position = index
                )
            }
        list.copy(
            updatedAt = now,
            sortMode = VeritasReadingListSortMode.MANUAL,
            items = nextItems
        )
    }

    fun setSortMode(
        listId: String,
        sortMode: VeritasReadingListSortMode,
        now: Long = System.currentTimeMillis()
    ): VeritasReadingListCatalog = updateList(listId) { list ->
        if (list.sortMode == sortMode) list else list.copy(sortMode = sortMode, updatedAt = now)
    }

    fun resolveDocuments(
        listId: String,
        documents: Collection<SavedDocument>
    ): List<SavedDocument> {
        val list = list(listId) ?: return emptyList()
        val byId = documents.associateBy { it.id }
        val titles = documents.associate { it.id to it.title }
        return list.orderedItems(titles).mapNotNull { byId[it.documentId] }
    }

    fun toJsonArray(): JSONArray {
        val array = JSONArray()
        lists.sortedWith(compareBy<VeritasReadingList> { it.createdAt }.thenBy { it.id })
            .forEach { array.put(it.toJson()) }
        return array
    }

    private fun updateList(
        listId: String,
        mutation: (VeritasReadingList) -> VeritasReadingList
    ): VeritasReadingListCatalog {
        var changed = false
        val updated = lists.map { list ->
            if (list.id != listId) {
                list
            } else {
                changed = true
                mutation(list)
            }
        }
        return if (changed) copy(lists = updated) else this
    }

    companion object {
        fun fromJsonArray(array: JSONArray?): VeritasReadingListCatalog {
            if (array == null) return VeritasReadingListCatalog()
            val lists = buildList {
                for (index in 0 until array.length()) {
                    VeritasReadingList.fromJson(array.optJSONObject(index))?.let(::add)
                }
            }
            return VeritasReadingListCatalog(
                lists = lists
                    .sortedWith(compareByDescending<VeritasReadingList> { it.updatedAt }.thenBy { it.id })
                    .distinctBy { it.id }
                    .sortedWith(compareBy<VeritasReadingList> { it.createdAt }.thenBy { it.id })
            )
        }
    }
}

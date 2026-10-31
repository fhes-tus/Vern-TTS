package com.veritas.reader.ui.screens

/** Authored levels may skip numbers; depth follows actual parents, not missing levels. */
internal data class OutlineTreeRow(val entryIndex: Int, val parentIndex: Int?, val depth: Int, val hasChildren: Boolean)

internal fun buildOutlineHierarchy(levels: List<Int>): List<OutlineTreeRow> {
    val parents = mutableListOf<Int>()
    val rows = mutableListOf<OutlineTreeRow>()
    levels.forEachIndexed { index, level ->
        while (parents.isNotEmpty() && levels[parents.last()] >= level) parents.removeAt(parents.lastIndex)
        rows += OutlineTreeRow(index, parents.lastOrNull(), parents.size, false)
        parents += index
    }
    val branches = rows.mapNotNull { it.parentIndex }.toSet()
    return rows.map { it.copy(hasChildren = it.entryIndex in branches) }
}

/** Searching temporarily opens matching paths without changing the reader's collapsed branches. */
internal fun visibleOutlineRows(
    rows: List<OutlineTreeRow>,
    collapsed: Set<Int>,
    matches: Set<Int>? = null
): List<OutlineTreeRow> {
    if (matches != null) {
        val included = matches.toMutableSet()
        matches.forEach { index ->
            var parent = rows.getOrNull(index)?.parentIndex
            while (parent != null) {
                included += parent
                parent = rows[parent].parentIndex
            }
        }
        return rows.filter { it.entryIndex in included }
    }
    val hidden = mutableSetOf<Int>()
    return rows.filter { row ->
        val hide = row.parentIndex?.let { it in collapsed || it in hidden } == true
        if (hide) hidden += row.entryIndex
        !hide
    }
}

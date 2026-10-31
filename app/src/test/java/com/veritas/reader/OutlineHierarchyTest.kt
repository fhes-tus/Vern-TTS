package com.veritas.reader

import com.veritas.reader.ui.screens.buildOutlineHierarchy
import com.veritas.reader.ui.screens.visibleOutlineRows
import org.junit.Assert.*
import org.junit.Test

class OutlineHierarchyTest {
    @Test fun chaptersTopicsAndSubtopicsHaveIndependentParents() {
        val rows = buildOutlineHierarchy(listOf(0, 1, 2, 2, 1, 0, 1))
        assertEquals(listOf(null, 0, 1, 1, 0, null, 5), rows.map { it.parentIndex })
        assertEquals(listOf(0, 1, 2, 2, 1, 0, 1), rows.map { it.depth })
        assertEquals(listOf(0, 1, 5), rows.filter { it.hasChildren }.map { it.entryIndex })
        assertEquals(listOf(0, 1, 4, 5, 6), visibleOutlineRows(rows, setOf(1)).map { it.entryIndex })
        assertEquals(listOf(0, 5, 6), visibleOutlineRows(rows, setOf(0, 1)).map { it.entryIndex })
        // Reopening a chapter retains the independently collapsed topic.
        assertEquals(listOf(0, 1, 4, 5, 6), visibleOutlineRows(rows, setOf(1)).map { it.entryIndex })
    }

    @Test fun missingHeadingLevelsDoNotCreatePhantomParents() {
        val rows = buildOutlineHierarchy(listOf(2, 5, 7, 5, 2, 9))
        assertEquals(listOf(null, 0, 1, 0, null, 4), rows.map { it.parentIndex })
        assertEquals(listOf(0, 1, 2, 1, 0, 1), rows.map { it.depth })
        assertEquals(listOf(0, 4), visibleOutlineRows(rows, setOf(0, 4)).map { it.entryIndex })
    }

    @Test fun searchShowsTheCompleteMatchingPathInsideCollapsedBranches() {
        val rows = buildOutlineHierarchy(listOf(0, 1, 2, 1, 0, 1))
        val collapsed = setOf(0, 1, 4)
        assertEquals(listOf(0, 1, 2), visibleOutlineRows(rows, collapsed, setOf(2)).map { it.entryIndex })
        assertEquals(listOf(0, 4, 5), visibleOutlineRows(rows, collapsed, setOf(0, 5)).map { it.entryIndex })
        assertTrue(visibleOutlineRows(rows, collapsed, emptySet()).isEmpty())
        assertEquals(listOf(0, 4), visibleOutlineRows(rows, collapsed).map { it.entryIndex })
        assertTrue(buildOutlineHierarchy(emptyList()).isEmpty())
    }
}

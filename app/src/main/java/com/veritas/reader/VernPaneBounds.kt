package com.veritas.reader

internal data class VernPaneBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = (right - left).coerceAtLeast(0)
    val height get() = (bottom - top).coerceAtLeast(0)
    val area get() = width.toLong() * height
}

internal data class VernFoldBounds(val bounds: VernPaneBounds, val vertical: Boolean)

/** Choose one usable pane without changing the screen's navigation or remembered content. */
internal fun vernUsablePane(
    viewport: VernPaneBounds,
    folds: List<VernFoldBounds>,
    preferRight: Boolean = false
): VernPaneBounds {
    var panes = listOf(viewport)
    folds.forEach { fold ->
        panes = panes.flatMap { pane ->
            val hinge = fold.bounds
            when {
                fold.vertical && hinge.top <= pane.top && hinge.bottom >= pane.bottom &&
                    hinge.left >= pane.left && hinge.right <= pane.right -> listOf(
                        pane.copy(right = hinge.left), pane.copy(left = hinge.right)
                    ).filter { it.area > 0 }
                !fold.vertical && hinge.left <= pane.left && hinge.right >= pane.right &&
                    hinge.top >= pane.top && hinge.bottom <= pane.bottom -> listOf(
                        pane.copy(bottom = hinge.top), pane.copy(top = hinge.bottom)
                    ).filter { it.area > 0 }
                else -> listOf(pane)
            }.ifEmpty { listOf(pane) }
        }
    }
    return panes.sortedWith(compareByDescending<VernPaneBounds> { it.area }
        .thenBy { it.top }.thenBy { if (preferRight) -it.left else it.left }).firstOrNull() ?: viewport
}

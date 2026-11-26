package com.veritas.reader

import android.graphics.Rect as AndroidRect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Reads Compose's selected range directly through its copy callback, without changing
 * the system clipboard unless the user actually chooses Copy. */
internal class SelectionClipboard(private val delegate: Clipboard) : Clipboard by delegate {
    var capture: ((String) -> Unit)? = null
    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        val handler = capture
        if (handler == null) delegate.setClipEntry(clipEntry)
        else {
            capture = null
            val data = clipEntry?.clipData
            val text = if (data != null && data.itemCount > 0) data.getItemAt(0).text?.toString() else null
            if (!text.isNullOrBlank()) handler(text)
        }
    }
}

internal class DocumentSelectionToolbar(private val view: View, private val clipboard: SelectionClipboard,
    private val onAction: (ReaderSelectionAction, String) -> Unit) : TextToolbar {
    private var mode: ActionMode? = null
    private var bounds = Rect.Zero
    private var copySelection: (() -> Unit)? = null
    private var selectAll: (() -> Unit)? = null
    var onDismiss: (() -> Unit)? = null
    override val status get() = if (mode == null) TextToolbarStatus.Hidden else TextToolbarStatus.Shown

    override fun showMenu(rect: Rect, onCopyRequested: (() -> Unit)?, onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?) {
        bounds = rect
        copySelection = onCopyRequested
        selectAll = onSelectAllRequested
        if (mode != null) { mode?.invalidate(); mode?.invalidateContentRect(); return }
        mode = view.startActionMode(object : ActionMode.Callback2() {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.addReaderSelectionActions(view.context, true) { action ->
                    val copy = copySelection ?: return@addReaderSelectionActions
                    clipboard.capture = { text ->
                        hide()
                        onAction(action, text)
                    }
                    try { copy() } finally { clipboard.capture = null }
                }
                menu.add(0, android.R.id.selectAll, 3, view.context.getString(android.R.string.selectAll))
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                return true
            }
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.findItem(android.R.id.selectAll)?.isVisible = selectAll != null
                menu.removeItem(android.R.id.textAssist)
                return true
            }
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                if (item.itemId != android.R.id.selectAll) return false
                selectAll?.invoke()
                return true
            }
            override fun onGetContentRect(mode: ActionMode, view: View, outRect: AndroidRect) {
                outRect.set(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt())
            }
            override fun onDestroyActionMode(mode: ActionMode) {
                this@DocumentSelectionToolbar.mode = null
                onDismiss?.invoke()
            }
        }, ActionMode.TYPE_FLOATING)
    }

    override fun hide() { mode?.finish(); mode = null }
}

/** Compose 1.11 uses this provider instead of LocalTextToolbar by default. Keep its
 * live Copy/Select all callbacks and bounds, and present our shared native action menu. */
internal class DocumentSelectionContextMenuProvider(private val toolbar: DocumentSelectionToolbar,
    private val coordinates: () -> LayoutCoordinates?) : TextContextMenuProvider {
    private val sessions = MutatorMutex()

    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) = sessions.mutate {
        coroutineScope {
            val closed = CompletableDeferred<Unit>()
            val session = object : TextContextMenuSession {
                override fun close() { closed.complete(Unit) }
            }
            toolbar.onDismiss = { closed.complete(Unit) }
            val updater = launch(start = CoroutineStart.UNDISPATCHED) {
                snapshotFlow {
                    val destination = coordinates()?.takeIf { it.isAttached }
                    val bounds = destination?.let { dataProvider.contentBounds(it).translate(it.positionInRoot()) } ?: Rect.Zero
                    bounds to dataProvider.data().components.filterIsInstance<TextContextMenuItem>()
                }.collect { (bounds, items) ->
                    val copy = items.firstOrNull { it.key == TextContextMenuKeys.CopyKey }
                    val selectAll = items.firstOrNull { it.key == TextContextMenuKeys.SelectAllKey }
                    if (copy == null) { session.close(); return@collect }
                    toolbar.showMenu(bounds, onCopyRequested = { copy.onClick(session) },
                        onPasteRequested = null, onCutRequested = null,
                        onSelectAllRequested = selectAll?.let { item -> { item.onClick(session) } })
                }
            }
            try { closed.await() } finally {
                updater.cancel()
                toolbar.onDismiss = null
                toolbar.hide()
            }
        }
    }
}

@Composable
internal fun rememberDocumentSelectionToolbar(coordinates: () -> LayoutCoordinates?,
    onAction: (ReaderSelectionAction, String) -> Unit): Triple<Clipboard, TextToolbar, TextContextMenuProvider> {
    val view = LocalView.current
    val systemClipboard = LocalClipboard.current
    val clipboard = remember(systemClipboard) { SelectionClipboard(systemClipboard) }
    val currentAction = rememberUpdatedState(onAction)
    val currentCoordinates = rememberUpdatedState(coordinates)
    val toolbar = remember(view, clipboard) { DocumentSelectionToolbar(view, clipboard) { action, text -> currentAction.value(action, text) } }
    val provider = remember(toolbar) { DocumentSelectionContextMenuProvider(toolbar) { currentCoordinates.value() } }
    DisposableEffect(toolbar) { onDispose { toolbar.hide() } }
    return Triple(clipboard, toolbar, provider)
}

package com.veritas.reader

import android.content.Context
import android.view.Menu
import android.view.MenuItem

/** One action list for extracted text, original documents and the native PDF viewer. */
internal enum class ReaderSelectionAction(val menuId: Int, val label: String, val primary: Boolean = false,
    val feature: VeritasFeatureId? = null) {
    BOOKMARK(6104, "Bookmark", true, VeritasFeatureId.BOOKMARKS_AND_NOTES),
    NOTE(6103, "Note", true, VeritasFeatureId.BOOKMARKS_AND_NOTES),
    DEFINE(6109, "Search definitions", true),
    READ_FROM_HERE(6101, "Read from here"),
    COPY(6105, "Copy"),
    SEARCH(6106, "Search in book"),
    GOOGLE(6114, "Google Search"),
    TRANSLATE(6107, "Translate", feature = VeritasFeatureId.TRANSLATION_HANDOFF),
    SHARE(6108, "Share"),
    EDIT_TEXT(6110, "Edit selected text", feature = VeritasFeatureId.EXTRACTED_TEXT_EDITOR),
    EDIT_SPEECH(6111, "Fix pronunciation", feature = VeritasFeatureId.PRONUNCIATION_RULES),
    READ_ALOUD(6112, "Read aloud"),
    ASK_AI(6113, "Ask AI", feature = VeritasFeatureId.AI_APP_HANDOFF);

    fun title(context: Context): String = when (this) {
        BOOKMARK -> context.getString(R.string.ui_bookmark)
        NOTE -> context.getString(R.string.ui_note)
        DEFINE -> context.getString(R.string.ui_search_definitions)
        GOOGLE -> context.getString(R.string.reader_google_search)
        else -> label
    }
}

internal fun Menu.addReaderSelectionActions(context: Context, hasDocument: Boolean,
    onAction: ((ReaderSelectionAction) -> Unit)? = null) {
    val features = VeritasFeatureRegistry.resolve(VeritasFeatureSurface.SELECTION_OVERFLOW,
        VeritasFeatureContext(hasActiveDocument = hasDocument, hasTextSelection = true, hasSavedDocument = hasDocument))
        .associateBy { it.definition.id }
    ReaderSelectionAction.entries.forEachIndexed { index, action ->
        val item = findItem(action.menuId) ?: add(0, action.menuId, index, action.title(context))
        item.isEnabled = action.feature?.let { features[it]?.enabled } ?: true
        item.setShowAsAction(if (action.primary) MenuItem.SHOW_AS_ACTION_ALWAYS else MenuItem.SHOW_AS_ACTION_NEVER)
        if (onAction != null) item.setOnMenuItemClickListener { onAction(action); true }
    }
}

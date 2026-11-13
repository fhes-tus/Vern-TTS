package com.veritas.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.veritas.reader.ui.screens.OfflineDictionarySheet

internal fun showDictionaryLookup(context: Context, query: String, anchor: DictionaryLookupAnchor = DictionaryLookupAnchor()) {
    var owner = context
    repeat(16) {
        if (owner is Activity) return@repeat
        owner = (owner as? ContextWrapper)?.baseContext ?: return
    }
    val activity = owner as? Activity ?: return
    val parent = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
    val host = ComposeView(activity).apply { setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed) }
    fun dismiss() { parent.removeView(host); host.disposeComposition() }
    parent.addView(host, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    host.setContent {
        VeritasTheme {
            OfflineDictionarySheet(query, anchor, onSaved = { notes ->
                (activity as? MainActivity)?.viewModel?.updateState { it.copy(generalNotes = notes) }
            }, onDismiss = ::dismiss)
        }
    }
}

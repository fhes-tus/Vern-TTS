package com.veritas.reader.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.veritas.reader.R

@Composable
internal fun localizedBookCoverDescription(title: String): String = menuString(R.string.a11y_book_cover,title)

@Composable
internal fun Modifier.localizedBookCover(title: String): Modifier {
    val description = localizedBookCoverDescription(title)
    return semantics { contentDescription = description }
}

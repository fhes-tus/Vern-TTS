package com.veritas.reader.ui.screens

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Velocity

@Composable
internal fun rememberReaderEdgeStretch(): OverscrollEffect? {
    val platformEffect = rememberOverscrollEffect() ?: return null
    return remember(platformEffect) {
        object : OverscrollEffect by platformEffect {
            override suspend fun applyToFling(
                velocity: Velocity,
                performFling: suspend (Velocity) -> Velocity
            ) {
                val alreadyStretched = platformEffect.isInProgress
                platformEffect.applyToFling(velocity) { available ->
                    val consumed = performFling(available)
                    // An edge pulled during the drag should release without a second impact.
                    if (alreadyStretched) available else consumed
                }
            }
        }
    }
}

package com.veritas.reader.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/** The same gentle float and tilt used by Reading Insights, within fixed card bounds. */
@Composable
internal fun Modifier.bookCoverMotion(key: String): Modifier {
    val phase = rememberInsightsPhase("cover-$key")
    return graphicsLayer {
        val wave = sin(phase.value * 2f * PI.toFloat())
        translationY = wave * -1.5.dp.toPx()
        rotationZ = wave * .65f
    }
}

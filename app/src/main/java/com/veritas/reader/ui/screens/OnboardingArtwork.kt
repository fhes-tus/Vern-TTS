package com.veritas.reader.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.veritas.reader.VeritasPackStyle

@Composable
internal fun OnboardingArtwork(
    @DrawableRes image: Int,
    aspectRatio: Float,
    modifier: Modifier = Modifier,
    shape: Shape = VeritasPackStyle.cardShape(),
    paper: Color? = null
) {
    Image(
        painter = painterResource(image),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(shape)
            .drawWithContent {
                drawContent()
                if (paper != null) {
                    val clearPaper = paper.copy(alpha = 0f)
                    // Blend the photographed paper at the edges, leaving the drawing's colors intact.
                    drawRect(Brush.verticalGradient(0f to paper, .08f to clearPaper, .92f to clearPaper, 1f to paper))
                    drawRect(Brush.horizontalGradient(0f to paper, .04f to clearPaper, .96f to clearPaper, 1f to paper))
                }
            }
    )
}

package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A subtle, state-aware fade that hints at scrollable content beyond the viewport edge. */
fun Modifier.verticalEdgeFade(
    showTop: Boolean,
    showBottom: Boolean,
    edgeHeight: Dp = 14.dp,
): Modifier {
    if (!showTop && !showBottom) return this
    return graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            if (size.height <= 0f) return@drawWithContent
            val fraction = (edgeHeight.toPx() / size.height).coerceIn(0f, 0.22f)
            drawRect(
                brush =
                    Brush.verticalGradient(
                        colorStops =
                            arrayOf(
                                0f to if (showTop) Color.Transparent else Color.Black,
                                fraction to Color.Black,
                                (1f - fraction) to Color.Black,
                                1f to if (showBottom) Color.Transparent else Color.Black,
                            ),
                    ),
                blendMode = BlendMode.DstIn,
            )
        }
}

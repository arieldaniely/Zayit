package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme

/**
 * The frame of a docked pane (and of the text): a rounded card, 3 dp from a neighbour above or
 * below (the vertical split's gap) and 6 dp from the window edge.
 */
@Composable
fun PaneCard(
    top: Dp = 6.dp,
    bottom: Dp = 6.dp,
    content: @Composable () -> Unit,
) {
    val modifier =
        Modifier
            .fillMaxSize()
            .padding(top = top, bottom = bottom, start = 4.dp, end = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(JewelTheme.globalColors.panelBackground)
    Box(modifier) { content() }
}

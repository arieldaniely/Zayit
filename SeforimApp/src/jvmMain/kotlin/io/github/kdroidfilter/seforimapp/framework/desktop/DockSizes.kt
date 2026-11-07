package io.github.kdroidfilter.seforimapp.framework.desktop

import androidx.compose.ui.unit.dp

internal val DefaultCommentaryPaneExtent = 220.dp

/** The measured window docks in px, as in the original reader's pane-size synchronization. */
internal data class DockSizes(
    val outerWidthPx: Int,
    val innerWidthPx: Int,
    val innerHeightPx: Int,
    val density: Float,
)

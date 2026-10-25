package io.github.kdroidfilter.seforimapp.framework.desktop

/** The measured window docks in px, as in the original reader's pane-size synchronization. */
internal data class DockSizes(
    val outerWidthPx: Int,
    val innerWidthPx: Int,
    val innerHeightPx: Int,
    val density: Float,
)

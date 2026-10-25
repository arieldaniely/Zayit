@file:OptIn(ExperimentalNucleusApi::class)

package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.nucleusframework.application.Satellite
import dev.nucleusframework.window.ControlButtonsDirection
import dev.nucleusframework.window.ExperimentalNucleusApi
import dev.nucleusframework.window.tao.*
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabSelected
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import org.jetbrains.jewel.foundation.theme.JewelTheme

internal enum class PaneSide { Start, End, Bottom }

/** Integration data only: Nucleus's original SatelliteWorkspace owns all dragging, layout and sizing. */
@Stable
internal class PanePlacement(
    val home: PaneSide,
    val extent: Dp = 220.dp,
    val weight: Float = 1f,
    val order: Int = 0,
) {
    val initial =
        SatellitePlacement.Docked(
            when (home) {
                PaneSide.Start -> DockSide.Right
                PaneSide.End -> DockSide.Left
                PaneSide.Bottom -> DockSide.Bottom
            },
            extent = extent,
            weight = weight,
            order = order,
        )
    internal var lastPlacement: SatellitePlacement = initial
}

internal class MovablePane(
    val id: String,
    val title: String,
    val placement: PanePlacement,
    val onClose: () -> Unit,
    val content: @Composable () -> Unit,
)

internal val LocalPaneHome = staticCompositionLocalOf<SatellitePlacement.Docked?> { null }
internal val LocalPaneReset = staticCompositionLocalOf<(() -> Unit)?> { null }

/** Satellite declarations and DockLayout from upstream ReaderPanes/WindowBody, adapted to the existing tab owner. */
@Composable
internal fun MovablePaneLayout(
    panes: List<MovablePane>,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val workspace = remember { SatelliteWorkspace() }
    workspace.visible = LocalTabSelected.current
    for (pane in panes) {
        key(pane.id) {
            val latest by rememberUpdatedState(pane)
            Satellite(
                workspace = workspace,
                id = pane.id,
                title = pane.title,
                initialPlacement = pane.placement.lastPlacement,
                initiallyOpen = true,
                dockSides = setOf(DockSide.Left, DockSide.Right, DockSide.Bottom),
                floatable = true,
                reorderable = true,
                hideWhileOwnerFullscreenOrMaximized = false,
                header = {},
                floatingBarMovesWindow = true,
                controlButtonsDirection = ControlButtonsDirection.SystemNative,
            ) {
                CompositionLocalProvider(
                    LocalPaneSatellite provides this,
                    LocalPaneHome provides latest.placement.initial,
                    LocalPaneReset provides {
                        workspace.dock(pane.id, latest.placement.initial.side, latest.placement.initial.order)
                    },
                ) { latest.content() }
            }
            LaunchedEffect(workspace, pane.id) {
                snapshotFlow { workspace.satellite(pane.id)?.placement }.filterNotNull().collect {
                    latest.placement.lastPlacement = it
                }
            }
            LaunchedEffect(workspace, pane.id) {
                snapshotFlow { workspace.satellite(pane.id)?.isOpen }.filterNotNull().distinctUntilChanged().collect {
                    if (!it) latest.onClose()
                }
            }
        }
    }
    val bottomOpen =
        workspace.satellites.any {
            it.isOpen && (it.placement as? SatellitePlacement.Docked)?.side == DockSide.Bottom
        }
    Box(modifier.fillMaxSize()) {
        DockLayout(
            workspace = workspace,
            sideOrder = listOf(DockSide.Bottom, DockSide.Left, DockSide.Right, DockSide.Top),
            splitter = { ReaderSplitter() },
            panel = { panel -> DockedPaneCard(bottomOpen) { panel() } },
        ) { Box(Modifier.fillMaxSize(), content = content) }
        // Requested visual override only; hit testing and the drop itself remain Nucleus's.
        workspace.dockPreview?.let { target ->
            val accent = JewelTheme.globalColors.outlines.focused
            Canvas(Modifier.fillMaxSize()) {
                val rect =
                    when (target.side) {
                        DockSide.Left -> Rect(0f, 0f, size.width * 0.3f, size.height)
                        DockSide.Right -> Rect(size.width * 0.7f, 0f, size.width, size.height)
                        DockSide.Bottom -> Rect(0f, size.height * 0.65f, size.width, size.height)
                        DockSide.Top -> Rect(0f, 0f, size.width, size.height * 0.35f)
                    }
                listOf(18f to 0.05f, 10f to 0.10f, 5f to 0.22f, 1.5f to 0.9f).forEach { (width, alpha) ->
                    drawRect(accent.copy(alpha = alpha), rect.topLeft, rect.size, style = Stroke(width.dp.toPx()))
                }
            }
        }
    }
}

/**
 * The reader's splitter: no visible line (the cards' gaps are the dividers), just a 5 dp grip.
 */
@Composable
fun DockSplitterScope.ReaderSplitter() {
    val horizontal = orientation == Orientation.Horizontal
    val line = if (horizontal) Modifier.fillMaxHeight().width(0.dp) else Modifier.fillMaxWidth().height(0.dp)
    Box(line, contentAlignment = Alignment.Center) {
        val grip =
            if (horizontal) {
                Modifier
                    .requiredWidth(
                        GRIP_DP.dp,
                    ).fillMaxHeight()
            } else {
                Modifier.requiredHeight(GRIP_DP.dp).fillMaxWidth()
            }
        Box(grip.dockSplitterHandle())
    }
}

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

/** A docked pane's frame by its side of the text: [bottomOpen] is whether a pane is docked under it. */
@Composable
fun SatelliteScope.DockedPaneCard(
    bottomOpen: Boolean,
    content: @Composable () -> Unit,
) {
    val side = (satellite.placement as? SatellitePlacement.Docked)?.side
    when {
        side == DockSide.Bottom -> PaneCard(top = 3.dp, content = content)
        side == DockSide.Left || side == DockSide.Right -> PaneCard(bottom = if (bottomOpen) 3.dp else 6.dp, content = content)
        else -> PaneCard(content = content)
    }
}

private const val GRIP_DP = 5

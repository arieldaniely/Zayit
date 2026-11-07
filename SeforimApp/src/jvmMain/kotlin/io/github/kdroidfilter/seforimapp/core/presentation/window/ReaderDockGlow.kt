@file:OptIn(dev.nucleusframework.window.ExperimentalNucleusApi::class)
@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package io.github.kdroidfilter.seforimapp.core.presentation.window

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.nucleusframework.window.tao.DockLayoutState
import dev.nucleusframework.window.tao.DockSide
import dev.nucleusframework.window.tao.DragPreviewDefaults
import dev.nucleusframework.window.tao.SatellitePlacement
import dev.nucleusframework.window.tao.SatelliteWorkspace
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.RRect
import kotlin.math.roundToInt

internal fun Modifier.readerDockGlow(
    workspace: SatelliteWorkspace,
    color: Color,
    layeredSides: Set<DockSide> = emptySet(),
): Modifier =
    drawWithCache {
        val radius = DragPreviewDefaults.CornerRadius.toPx()
        val glow =
            Paint().apply {
                isAntiAlias = true
                this.color = color.toArgb()
                mode = PaintMode.STROKE
                strokeWidth = 4.dp.toPx()
                maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, 3.5.dp.toPx())
            }
        onDrawWithContent {
            drawContent()
            val layout = IntSize(size.width.roundToInt(), size.height.roundToInt())
            val rect = workspace.previewBounds(layeredSides, layout, this)
            if (rect != null && !rect.isEmpty) {
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawRRect(RRect.makeXYWH(rect.left, rect.top, rect.width, rect.height, radius), glow)
                }
            }
        }
    }

// Nucleus owns the drop and its preview. The pinned framework version has no preview slot, so
// reuse its sizing and slot math with the live host and panel bounds to paint the halo on the same edge.
private fun SatelliteWorkspace.previewBounds(
    layered: Set<DockSide>,
    layout: IntSize,
    density: Density,
): Rect? {
    val target = dockPreview ?: return null
    val dragged = draggedSatellite ?: return null
    if (target.host !== owner) return null
    val host = dockHostGeometry(target.host) ?: return null
    val bounds = host.layoutBoundsInWindowPx
    if (bounds.isEmpty) return null

    val state = DockLayoutState(this)
    state.docked = satellites.filter { it.isOpen && it.dockHost === target.host && it.isDocked }
    state.layeredSides = layered
    state.direction = host.layoutDirection
    state.containerSize = host.containerSizePx
    state.layoutSize = layout
    state.layoutBoundsInWindowPx = bounds
    for ((side, panels) in state.docked.groupBy { (it.placement as SatellitePlacement.Docked).side }) {
        val rects = panels.mapNotNull { it.dockedBoundsInWindowPx }
        if (rects.isEmpty()) continue
        val band =
            Rect(
                rects.minOf { it.left },
                rects.minOf { it.top },
                rects.maxOf { it.right },
                rects.maxOf { it.bottom },
            )
        state.bandBoundsInWindowPx[side] = band
        state.stackLengthsPx[side] = (if (side.isVertical) band.height else band.width).roundToInt()
    }

    val side = target.side
    val extent = if (side in layered) dockSeedExtent(dragged, side) else plannedDockExtent(dragged, side)
    val thickness = with(density) { extent.toPx() }
    val order = target.order.takeIf { host.zoneBoundsInWindowPx[side]?.slots?.isNotEmpty() == true }
    return state.dropRectPx(side, dragged, order, thickness * state.fitAfterDrop(side, dragged, thickness, density))
}

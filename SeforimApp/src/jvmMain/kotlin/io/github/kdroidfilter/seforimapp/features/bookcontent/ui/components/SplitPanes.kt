package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import io.github.kdroidfilter.seforimapp.core.presentation.utils.cursorForHorizontalResize
import io.github.kdroidfilter.seforimapp.core.presentation.utils.cursorForVerticalResize
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import org.jetbrains.compose.splitpane.SplitPaneState
import org.jetbrains.compose.splitpane.VerticalSplitPane
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider

private enum class ResizeAxis {
    Horizontal,
    Vertical,
}

@Composable
private fun ResizeGlow(
    axis: ResizeAxis,
    enabled: Boolean,
    highlighted: Boolean,
    dragging: Boolean,
    pointerPosition: Float,
    modifier: Modifier = Modifier,
) {
    val accent = JewelTheme.globalColors.outlines.focused
    val intensity by
        animateFloatAsState(
            targetValue =
                when {
                    !enabled -> 0f
                    dragging -> 1f
                    highlighted -> 0.72f
                    else -> 0f
                },
            animationSpec = tween(durationMillis = 140),
        )

    Canvas(modifier) {
        if (!enabled || intensity <= 0.01f) return@Canvas

        val horizontalMovement = axis == ResizeAxis.Horizontal
        val primarySize = if (horizontalMovement) size.height else size.width
        val rawCenter = if (pointerPosition.isFinite()) pointerPosition else primarySize / 2f
        val centerAlongLine = rawCenter.coerceIn(0f, primarySize)
        val center =
            if (horizontalMovement) {
                Offset(size.width / 2f, centerAlongLine)
            } else {
                Offset(centerAlongLine, size.height / 2f)
            }

        val glowRadius = 150.dp.toPx()
        val glowColors =
            listOf(
                accent.copy(alpha = 0f),
                accent.copy(alpha = 0.45f * intensity),
                accent.copy(alpha = intensity),
                accent.copy(alpha = 0.45f * intensity),
                accent.copy(alpha = 0f),
            )
        val glowBrush =
            if (horizontalMovement) {
                Brush.verticalGradient(
                    colors = glowColors,
                    startY = center.y - glowRadius,
                    endY = center.y + glowRadius,
                )
            } else {
                Brush.horizontalGradient(
                    colors = glowColors,
                    startX = center.x - glowRadius,
                    endX = center.x + glowRadius,
                )
            }

        val lineStart = if (horizontalMovement) Offset(center.x, 0f) else Offset(0f, center.y)
        val lineEnd = if (horizontalMovement) Offset(center.x, size.height) else Offset(size.width, center.y)
        drawLine(
            color = accent.copy(alpha = 0.18f * intensity),
            start = lineStart,
            end = lineEnd,
            strokeWidth = 1.dp.toPx(),
        )
        drawLine(
            brush = glowBrush,
            start = lineStart,
            end = lineEnd,
            strokeWidth = 14.dp.toPx(),
            cap = StrokeCap.Round,
            alpha = 0.12f,
        )
        drawLine(
            brush = glowBrush,
            start = lineStart,
            end = lineEnd,
            strokeWidth = 7.dp.toPx(),
            cap = StrokeCap.Round,
            alpha = 0.28f,
        )
        drawLine(
            brush = glowBrush,
            start = lineStart,
            end = lineEnd,
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Stable
@JvmInline
value class StableSplitPaneState
    @OptIn(ExperimentalSplitPaneApi::class)
    constructor(
        val value: SplitPaneState,
    )

@OptIn(ExperimentalSplitPaneApi::class)
fun SplitPaneState.asStable(): StableSplitPaneState = StableSplitPaneState(this)

@OptIn(ExperimentalSplitPaneApi::class, ExperimentalComposeUiApi::class)
@Composable
fun EnhancedHorizontalSplitPane(
    splitPaneState: StableSplitPaneState,
    firstContent: @Composable BoxScope.() -> Unit,
    secondContent: (@Composable BoxScope.() -> Unit)?,
    modifier: Modifier = Modifier,
    firstMinSize: Float = 200f,
    secondMinSize: Float = 200f,
    showSplitter: Boolean = true,
    dividerVisibleInIslands: Boolean = false,
) {
    val isIslands = ThemeUtils.isIslandsStyle() && !dividerVisibleInIslands
    val state = splitPaneState.value
    val effectiveSecondMin = if (secondContent == null) 0f else secondMinSize
    val splitterVisible = showSplitter && secondContent != null
    var splitterHovered by remember { mutableStateOf(false) }
    var splitterDragging by remember { mutableStateOf(false) }
    var pointerPosition by remember { mutableStateOf(Float.NaN) }
    val splitterHighlighted = splitterHovered || splitterDragging

    // When the second pane is hidden, expand the first to 100% to avoid blank space
    LaunchedEffect(secondContent == null) {
        if (secondContent == null) {
            state.positionPercentage = 1f
        }
    }

    // When the first pane is hidden (minSize = 0), collapse it to 0% to avoid blank space
    LaunchedEffect(firstMinSize == 0f) {
        if (firstMinSize == 0f) {
            state.positionPercentage = 0f
        }
    }

    // Disable drag when splitter is hidden so the library's default handle
    // (8dp invisible drag zone) is never placed.
    LaunchedEffect(splitterVisible) {
        state.moveEnabled = splitterVisible
    }

    HorizontalSplitPane(
        splitPaneState = state,
        modifier = modifier,
    ) {
        first(firstMinSize.dp) {
            Box(
                modifier = Modifier.fillMaxSize(),
                content = firstContent,
            )
        }
        second(effectiveSecondMin.dp) {
            if (secondContent != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    content = secondContent,
                )
            } else {
                // Keep the pane structure stable even when hidden
                Box(modifier = Modifier.fillMaxSize())
            }
        }
        if (splitterVisible) {
            splitter {
                visiblePart {
                    if (!isIslands) {
                        Divider(
                            Orientation.Vertical,
                            Modifier.fillMaxHeight().width(1.dp),
                            color = JewelTheme.globalColors.borders.disabled,
                        )
                    }
                }
                handle {
                    ResizeGlow(
                        axis = ResizeAxis.Horizontal,
                        enabled = isIslands,
                        highlighted = splitterHighlighted,
                        dragging = splitterDragging,
                        pointerPosition = pointerPosition,
                        modifier =
                            Modifier
                                .width(7.dp)
                                .fillMaxHeight()
                                .onPointerEvent(PointerEventType.Enter) { event ->
                                    splitterHovered = true
                                    pointerPosition = event.changes.firstOrNull()?.position?.y ?: pointerPosition
                                }.onPointerEvent(PointerEventType.Move) { event ->
                                    pointerPosition = event.changes.firstOrNull()?.position?.y ?: pointerPosition
                                }.onPointerEvent(PointerEventType.Exit) { splitterHovered = false }
                                .onPointerEvent(PointerEventType.Press) { event ->
                                    splitterDragging = true
                                    pointerPosition = event.changes.firstOrNull()?.position?.y ?: pointerPosition
                                }.onPointerEvent(PointerEventType.Release) { splitterDragging = false }
                                .markAsHandle()
                                .cursorForHorizontalResize(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSplitPaneApi::class, ExperimentalComposeUiApi::class)
@Composable
fun EnhancedVerticalSplitPane(
    splitPaneState: StableSplitPaneState,
    firstContent: @Composable BoxScope.() -> Unit,
    secondContent: (@Composable BoxScope.() -> Unit)?,
    modifier: Modifier = Modifier,
    firstMinSize: Float = 200f,
    secondMinSize: Float = 200f,
    showSplitter: Boolean = true,
    dividerVisibleInIslands: Boolean = false,
) {
    val isIslands = ThemeUtils.isIslandsStyle() && !dividerVisibleInIslands
    val state = splitPaneState.value
    val effectiveSecondMin = if (secondContent == null) 0f else secondMinSize
    val splitterVisible = showSplitter && secondContent != null
    var splitterHovered by remember { mutableStateOf(false) }
    var splitterDragging by remember { mutableStateOf(false) }
    var pointerPosition by remember { mutableStateOf(Float.NaN) }
    val splitterHighlighted = splitterHovered || splitterDragging

    // When the second pane is hidden, expand the first to 100% to avoid blank space
    LaunchedEffect(secondContent == null) {
        if (secondContent == null) {
            state.positionPercentage = 1f
        }
    }

    // When the first pane is hidden (minSize = 0), collapse it to 0% to avoid blank space
    LaunchedEffect(firstMinSize == 0f) {
        if (firstMinSize == 0f) {
            state.positionPercentage = 0f
        }
    }

    // Disable drag when splitter is hidden so the library's default handle
    // (8dp invisible drag zone) is never placed.
    LaunchedEffect(splitterVisible) {
        state.moveEnabled = splitterVisible
    }

    VerticalSplitPane(
        splitPaneState = state,
        modifier = modifier,
    ) {
        first(firstMinSize.dp) {
            Box(
                modifier = Modifier.fillMaxSize(),
                content = firstContent,
            )
        }
        second(effectiveSecondMin.dp) {
            if (secondContent != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    content = secondContent,
                )
            } else {
                // Keep the pane structure stable even when hidden
                Box(modifier = Modifier.fillMaxSize())
            }
        }
        if (splitterVisible) {
            splitter {
                visiblePart {
                    if (!isIslands) {
                        Divider(
                            Orientation.Horizontal,
                            Modifier.fillMaxWidth().height(1.dp),
                            color = JewelTheme.globalColors.borders.disabled,
                        )
                    }
                }
                handle {
                    ResizeGlow(
                        axis = ResizeAxis.Vertical,
                        enabled = isIslands,
                        highlighted = splitterHighlighted,
                        dragging = splitterDragging,
                        pointerPosition = pointerPosition,
                        modifier =
                            Modifier
                                .height(7.dp)
                                .fillMaxWidth()
                                .onPointerEvent(PointerEventType.Enter) { event ->
                                    splitterHovered = true
                                    pointerPosition = event.changes.firstOrNull()?.position?.x ?: pointerPosition
                                }.onPointerEvent(PointerEventType.Move) { event ->
                                    pointerPosition = event.changes.firstOrNull()?.position?.x ?: pointerPosition
                                }.onPointerEvent(PointerEventType.Exit) { splitterHovered = false }
                                .onPointerEvent(PointerEventType.Press) { event ->
                                    splitterDragging = true
                                    pointerPosition = event.changes.firstOrNull()?.position?.x ?: pointerPosition
                                }.onPointerEvent(PointerEventType.Release) { splitterDragging = false }
                                .markAsHandle()
                                .cursorForVerticalResize(),
                    )
                }
            }
        }
    }
}

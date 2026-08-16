package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import dev.nucleusframework.core.runtime.Platform
import dev.nucleusframework.window.ControlButtonsDirection
import dev.nucleusframework.window.DecoratedWindowScope
import dev.nucleusframework.window.jewel.JewelTitleBar
import dev.nucleusframework.window.macOSLargeCornerRadius
import dev.nucleusframework.window.newFullscreenControls
import dev.nucleusframework.window.styling.LocalTitleBarStyle
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabsView
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo
import io.github.kdroidfilter.seforimapp.framework.session.ScreenshotAutomationBridge
import io.github.kdroidfilter.seforimapp.framework.update.showTitleBarIcon

@Composable
fun DecoratedWindowScope.MainTitleBar(
    windowState: WindowState,
    onCloseRequest: () -> Unit,
) {
    val screenshotMode = ScreenshotAutomationBridge.isEnabled
    val baseTitleBarStyle = LocalTitleBarStyle.current
    val titleBarStyle =
        if (screenshotMode) {
            // The replay owns its one deterministic Windows 11 pane. A zero button size clips
            // Nucleus' backend-selected controls, so Linux/legacy/native variants cannot leak in.
            baseTitleBarStyle.copy(metrics = baseTitleBarStyle.metrics.copy(titlePaneButtonSize = DpSize.Zero))
        } else {
            baseTitleBarStyle
        }
    JewelTitleBar(
        modifier = Modifier.newFullscreenControls().macOSLargeCornerRadius(),
        gradientStartColor = if (ThemeUtils.isIslandsStyle()) ThemeUtils.titleBarGradientColor() else Color.Unspecified,
        style = titleBarStyle,
        controlButtonsDirection =
            if (screenshotMode) ControlButtonsDirection.Ltr else ControlButtonsDirection.SystemNative,
    ) {
        // Window control buttons (close/maximize/minimize) are Compose-based on Linux and
        // Windows-fallback. Their total width must be subtracted from the available width so
        // that the BoxWithConstraints content doesn't push them outside the window boundary.
        val effectivePlatform = if (screenshotMode) Platform.Windows else PlatformInfo.currentOS
        val windowControlButtonWidth = baseTitleBarStyle.metrics.titlePaneButtonSize.width
        val windowControlCount =
            when (effectivePlatform) {
                Platform.MacOS -> 0 // native traffic lights, not in Compose layout
                else -> 3 // close + maximize/restore + minimize
            }
        // The update badge is an extra action button shown only for PROMPT updates; it must be
        // counted so the reserved icons-area width (and thus tabsAreaWidth) stays correct.
        val updateIconVisible =
            LocalAppGraph.current.appUpdateService.state
                .collectAsState()
                .value.showTitleBarIcon
        BoxWithConstraints(modifier = Modifier.align(Alignment.Start)) {
            val windowWidth = maxWidth
            // Non-macOS gets the extra Favorites button (macOS uses the native menus).
            // Tab Search lives at the leading edge of the tabs area and is accounted for there.
            val actionButtonCount = (if (effectivePlatform == Platform.MacOS) 2 else 5) + if (updateIconVisible) 1 else 0
            val iconWidth: Dp = 40.dp
            val desktopSwitcherWidth: Dp = DESKTOP_SWITCHER_WIDTH
            val actionButtonsWidth = iconWidth * actionButtonCount + desktopSwitcherWidth
            val iconsAreaWidth: Dp =
                when (effectivePlatform) {
                    Platform.MacOS -> actionButtonsWidth + iconWidth * 2 // traffic lights space
                    Platform.Windows -> actionButtonsWidth + WINDOWS_CAPTION_BUTTON_WIDTH * 3
                    else -> actionButtonsWidth + windowControlButtonWidth * windowControlCount
                }
            val tabsAreaWidth: Dp = (windowWidth - iconsAreaWidth).coerceAtLeast(0.dp)
            Row {
                Row(
                    modifier =
                        Modifier
                            .align(Alignment.Start)
                            .width(tabsAreaWidth),
                ) {
                    if (effectivePlatform != Platform.MacOS) {
                        TabSearchButton()
                    }
                    TabsView()
                }
                Row(
                    modifier =
                        Modifier
                            .align(Alignment.End)
                            .fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DesktopSwitcher()
                    TitleBarActionsButtonsView()
                }
            }
            if (screenshotMode) {
                ScreenshotWindows11ControlButtons(
                    isMaximized = windowState.placement == WindowPlacement.Maximized,
                    iconColor = baseTitleBarStyle.colors.controlButtonIconColor,
                    onMinimize = { windowState.isMinimized = true },
                    onToggleMaximize = {
                        windowState.placement =
                            if (windowState.placement == WindowPlacement.Maximized) {
                                WindowPlacement.Floating
                            } else {
                                WindowPlacement.Maximized
                            }
                    },
                    onClose = onCloseRequest,
                    modifier = Modifier.align(AbsoluteAlignment.TopRight),
                )
            }
        }
    }
}

@Composable
private fun ScreenshotWindows11ControlButtons(
    isMaximized: Boolean,
    iconColor: Color,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val buttons =
        listOf(
            WindowsCaptionButton(WindowsCaptionGlyph.Close, onClose),
            WindowsCaptionButton(
                if (isMaximized) WindowsCaptionGlyph.Restore else WindowsCaptionGlyph.Maximize,
                onToggleMaximize,
            ),
            WindowsCaptionButton(WindowsCaptionGlyph.Minimize, onMinimize),
        ).let { if (isRtl) it else it.reversed() }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier.width(WINDOWS_CAPTION_BUTTON_WIDTH * buttons.size).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            buttons.forEach { button ->
                Box(
                    modifier =
                        Modifier
                            .width(WINDOWS_CAPTION_BUTTON_WIDTH)
                            .fillMaxHeight()
                            .clickable(onClick = button.onClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Windows11CaptionGlyph(button.glyph, iconColor)
                }
            }
        }
    }
}

@Composable
private fun Windows11CaptionGlyph(
    glyph: WindowsCaptionGlyph,
    color: Color,
) {
    Canvas(Modifier.size(WINDOWS_CAPTION_GLYPH_SIZE)) {
        val onePixel = 1.dp.toPx()
        when (glyph) {
            WindowsCaptionGlyph.Minimize ->
                drawLine(
                    color = color,
                    start = Offset(onePixel, size.height - 3.5f * onePixel),
                    end = Offset(size.width - onePixel, size.height - 3.5f * onePixel),
                    strokeWidth = onePixel,
                )

            WindowsCaptionGlyph.Maximize ->
                drawRect(
                    color = color,
                    topLeft = Offset(1.5f * onePixel, 1.5f * onePixel),
                    size = Size(size.width - 3f * onePixel, size.height - 3f * onePixel),
                    style = Stroke(width = onePixel),
                )

            WindowsCaptionGlyph.Restore -> {
                val squareSize = Size(size.width - 4f * onePixel, size.height - 4f * onePixel)
                drawRect(
                    color = color,
                    topLeft = Offset(2.5f * onePixel, 0.5f * onePixel),
                    size = squareSize,
                    style = Stroke(width = onePixel),
                )
                drawRect(
                    color = color,
                    topLeft = Offset(0.5f * onePixel, 2.5f * onePixel),
                    size = squareSize,
                    style = Stroke(width = onePixel),
                )
            }

            WindowsCaptionGlyph.Close -> {
                drawLine(
                    color = color,
                    start = Offset(1.5f * onePixel, 1.5f * onePixel),
                    end = Offset(size.width - 1.5f * onePixel, size.height - 1.5f * onePixel),
                    strokeWidth = onePixel,
                )
                drawLine(
                    color = color,
                    start = Offset(size.width - 1.5f * onePixel, 1.5f * onePixel),
                    end = Offset(1.5f * onePixel, size.height - 1.5f * onePixel),
                    strokeWidth = onePixel,
                )
            }
        }
    }
}

private data class WindowsCaptionButton(
    val glyph: WindowsCaptionGlyph,
    val onClick: () -> Unit,
)

private enum class WindowsCaptionGlyph {
    Minimize,
    Maximize,
    Restore,
    Close,
}

private val WINDOWS_CAPTION_BUTTON_WIDTH = 46.dp
private val WINDOWS_CAPTION_GLYPH_SIZE = 12.dp

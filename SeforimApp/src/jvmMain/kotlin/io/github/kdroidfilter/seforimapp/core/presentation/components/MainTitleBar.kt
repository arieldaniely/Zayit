package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.nucleusframework.core.runtime.Platform
import dev.nucleusframework.window.ControlButtonsDirection
import dev.nucleusframework.window.DecoratedWindowScope
import dev.nucleusframework.window.icons.windows.Close
import dev.nucleusframework.window.icons.windows.Maximize
import dev.nucleusframework.window.icons.windows.Minimize
import dev.nucleusframework.window.icons.windows.WindowsControlButtonIcons
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
import org.jetbrains.jewel.ui.component.Icon

@Composable
fun DecoratedWindowScope.MainTitleBar() {
    val screenshotMode = ScreenshotAutomationBridge.isEnabled
    val baseTitleBarStyle = LocalTitleBarStyle.current
    val titleBarStyle =
        if (screenshotMode) {
            baseTitleBarStyle.copy(metrics = baseTitleBarStyle.metrics.copy(titlePaneButtonSize = DpSize.Zero))
        } else {
            baseTitleBarStyle
        }
    JewelTitleBar(
        modifier = Modifier.newFullscreenControls().macOSLargeCornerRadius(),
        gradientStartColor = if (ThemeUtils.isIslandsStyle()) ThemeUtils.titleBarGradientColor() else Color.Unspecified,
        style = titleBarStyle,
        controlButtonsDirection = if (screenshotMode) ControlButtonsDirection.Ltr else ControlButtonsDirection.SystemNative,
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
                    Platform.Windows -> actionButtonsWidth + iconWidth * 3.5f // window controls
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
                ScreenshotWindowsControlButtons(baseTitleBarStyle, Modifier.align(Alignment.TopStart))
            }
        }
    }
}

@Composable
private fun ScreenshotWindowsControlButtons(
    style: dev.nucleusframework.window.styling.TitleBarStyle,
    modifier: Modifier = Modifier,
) {
    val buttonWidth = 46.dp
    val iconColor = style.colors.controlButtonIconColor
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier.width(buttonWidth * 3).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                WindowsControlButtonIcons.Minimize,
                WindowsControlButtonIcons.Maximize,
                WindowsControlButtonIcons.Close,
            ).forEach { imageVector ->
                Box(
                    modifier = Modifier.width(buttonWidth).fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = imageVector,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

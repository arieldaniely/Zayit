package io.github.kdroidfilter.seforimapp.core.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.dp
import dev.nucleusframework.window.ControlButtonsDirection
import dev.nucleusframework.window.DecoratedWindowScope
import dev.nucleusframework.window.TitleBarLayoutPolicy
import dev.nucleusframework.window.jewel.JewelTitleBar
import dev.nucleusframework.window.macOSLargeCornerRadius
import dev.nucleusframework.window.newFullscreenControls
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.TabsView
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.tabStripMinWidth
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeUtils
import io.github.kdroidfilter.seforimapp.framework.desktop.LocalOpenWindow
import io.github.kdroidfilter.seforimapp.framework.platform.PlatformInfo

private enum class TitleBarSlot { Tabs, Switcher, Buttons }

@Composable
fun DecoratedWindowScope.MainTitleBar() {
    JewelTitleBar(
        modifier = Modifier.newFullscreenControls().macOSLargeCornerRadius(),
        gradientStartColor = if (ThemeUtils.isIslandsStyle()) ThemeUtils.titleBarGradientColor() else Color.Unspecified,
        controlButtonsDirection = ControlButtonsDirection.SystemNative,
        layoutPolicy = TitleBarLayoutPolicy.FillCenter,
    ) {
        val tabCount =
            LocalOpenWindow.current.tabsViewModel.state
                .collectAsState()
                .value.tabs.size
        // The title bar supplies the room left by the native window controls. Measure actions first;
        // future buttons automatically give the tab strip less room, without another manual count.
        SubcomposeLayout(Modifier.align(Alignment.CenterHorizontally)) { constraints ->
            val loose = constraints.copy(minWidth = 0)
            val buttons =
                subcompose(TitleBarSlot.Buttons) {
                    Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                        TitleBarActionsButtonsView()
                    }
                }.map { it.measure(loose) }
            val buttonsWidth = buttons.sumOf { it.width }
            val searchWidth = if (PlatformInfo.isMacOS) 0.dp else 40.dp
            val compact =
                constraints.maxWidth - buttonsWidth - DESKTOP_SWITCHER_WIDTH.roundToPx() <
                    (tabStripMinWidth(tabCount) + searchWidth).roundToPx()
            val switcher =
                subcompose(TitleBarSlot.Switcher) {
                    DesktopSwitcher(compact = compact)
                }.map { it.measure(loose) }
            val tabsWidth = (constraints.maxWidth - buttonsWidth - switcher.sumOf { it.width }).coerceAtLeast(0)
            val tabs =
                subcompose(TitleBarSlot.Tabs) {
                    Row {
                        if (!PlatformInfo.isMacOS) TabSearchButton()
                        Box(Modifier.weight(1f)) { TabsView() }
                    }
                }.map { it.measure(constraints.copy(minWidth = tabsWidth, maxWidth = tabsWidth)) }
            val height = (tabs + switcher + buttons).maxOfOrNull { it.height } ?: constraints.minHeight
            layout(constraints.maxWidth, height) {
                var x = 0
                for (child in tabs + switcher + buttons) {
                    child.placeRelative(x, (height - child.height) / 2)
                    x += child.width
                }
            }
        }
    }
}

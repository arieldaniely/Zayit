package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.ui.window.rememberWindowState
import dev.nucleusframework.application.NucleusApplicationScope
import dev.nucleusframework.window.ControlButtonsDirection
import dev.nucleusframework.window.jewel.JewelDecoratedDialog
import dev.nucleusframework.window.jewel.JewelDecoratedWindow
import dev.nucleusframework.window.jewel.JewelDialogTitleBar
import dev.nucleusframework.window.jewel.JewelTitleBar
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.kdroidfilter.seforimapp.features.home.widgets.calendar.CalendarWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.library.DictionaryWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.measures.MeasuresWidget
import io.github.kdroidfilter.seforimapp.framework.di.AppScope
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.*

internal val availableTools: List<HomeWidget> = listOf(CalendarWidget, DictionaryWidget, MeasuresWidget)

/** One app-wide window per tool; requesting an open tool brings it forward. */
@Inject
@SingleIn(AppScope::class)
class ToolWindows {
    internal val open = mutableStateListOf<HomeWidget>()
    internal val revisions = mutableStateMapOf<String, Int>()

    fun open(id: String) {
        val tool = availableTools.firstOrNull { it.id == id } ?: return
        if (tool !in open) open += tool else revisions[id] = (revisions[id] ?: 0) + 1
    }
}

@Composable
fun NucleusApplicationScope.ToolWindowsHost() {
    val graph = LocalAppGraph.current
    val windows = graph.toolWindows
    if (windows.open.isEmpty()) return
    val state = remember(graph.widgetPreferences) { HomeWidgetsState(graph.widgetPreferences) }
    SideEffect {
        state.openTab = {
            graph.desktopManager
                .focusedWindow()
                ?.tabsViewModel
                ?.openTab(it)
        }
    }
    for (tool in windows.open.toList()) {
        key(tool.id) {
            val revision = windows.revisions[tool.id] ?: 0
            val title = stringResource(tool.title)
            val windowState = rememberWindowState(size = tool.toolWindowSize!!)
            JewelDecoratedWindow(
                onCloseRequest = {
                    windows.open -= tool
                    windows.revisions -= tool.id
                },
                title = title,
                state = windowState,
            ) {
                val window = nucleusWindow
                LaunchedEffect(revision) {
                    if (revision > 0) {
                        window.setMinimized(false)
                        window.toFront()
                        window.requestFocus()
                    }
                }
                JewelTitleBar(controlButtonsDirection = ControlButtonsDirection.SystemNative) { Text(title) }
                tool.Content(state, Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground).padding(12.dp))
            }
        }
    }
    WidgetOptionsHost(state)
}

@Composable
internal fun NucleusApplicationScope.WidgetOptionsHost(state: HomeWidgetsState) {
    val widget = state.optionsOpen ?: return
    val title = stringResource(Res.string.home_widgets_options)
    JewelDecoratedDialog(
        onCloseRequest = { state.optionsOpen = null },
        title = title,
        state = rememberDialogState(width = 460.dp, height = 500.dp),
        resizable = true,
    ) {
        JewelDialogTitleBar(controlButtonsDirection = ControlButtonsDirection.SystemNative) { Text(title) }
        Column(Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground).padding(16.dp)) {
            Box(Modifier.weight(1f)) { widget.Options(state) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    widget.settings?.let(state.preferences::reset)
                    widget.applySettings(state.preferences)
                }) { Text(stringResource(Res.string.home_widgets_reset)) }
                Spacer(Modifier.weight(1f))
                DefaultButton(onClick = { state.optionsOpen = null }) { Text(stringResource(Res.string.home_widgets_done)) }
            }
        }
    }
}

@Composable
fun ToolsMenuItems(onOpen: (String) -> Unit) {
    // Also used by the native menu; the title-bar launcher exposes these on every OS.
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (tool in availableTools) {
            OutlinedButton(onClick = { onOpen(tool.id) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(tool.title)) }
        }
    }
}

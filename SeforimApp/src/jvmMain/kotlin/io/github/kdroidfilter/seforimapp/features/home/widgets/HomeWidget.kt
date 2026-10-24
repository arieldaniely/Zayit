package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.settings.Setting
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.jewel.ui.icon.IconKey
import java.time.LocalDate

@Immutable
data class CellSpan(
    val w: Int,
    val h: Int,
)

/** Shared content and preferences for standalone tools and Home cards. */
interface HomeWidget {
    val id: String
    val title: StringResource
    val defaultSpan: CellSpan
    val minSpan: CellSpan get() = defaultSpan
    val maxSpan: CellSpan get() = defaultSpan
    val settings: WidgetSettings? get() = null
    val toolWindowSize: DpSize? get() = DpSize(460.dp, 560.dp)
    val toolWindowMinSize: DpSize? get() = toolWindowSize
    val toolSymbol: String? get() = null

    fun heightAt(width: Dp): Dp? = null

    fun applySettings(appSettings: WidgetPreferences) {}

    @Composable
    fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    )

    @Composable
    fun Options(state: HomeWidgetsState) {}

    @Composable
    fun menuItems(state: HomeWidgetsState): List<WidgetMenuItem> = emptyList()
}

@Immutable
data class WidgetMenuItem(
    val label: String,
    val icon: IconKey? = null,
    val checked: Boolean? = null,
    val children: List<WidgetMenuItem> = emptyList(),
    val onClick: () -> Unit = {},
)

@Stable
class HomeWidgetsState(
    val preferences: WidgetPreferences,
) {
    var optionsOpen by mutableStateOf<HomeWidget?>(null)
    var selectedDate by mutableStateOf(LocalDate.now())
    var openTab: (TabsDestination) -> Unit = {}

    @Composable
    operator fun <T> get(setting: Setting<T>): T = preferences.flow(setting).collectAsState().value

    operator fun <T> set(
        setting: Setting<T>,
        value: T,
    ) {
        preferences[setting] = value
    }
}

package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.LogoImage
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.UnifiedSearchBar
import io.github.kdroidfilter.seforimapp.features.search.SearchModePicker
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.jewel.ui.component.Text
import seforimapp.seforimapp.generated.resources.*

internal val homeControlWidgets: List<WidgetPlacement> =
    listOf(
        WidgetPlacement(HomeLogoWidget, CellRect(6, 0, 8, 2)),
        WidgetPlacement(HomeSearchWidget, CellRect(4, 2, 10, 1)),
        WidgetPlacement(HomeSearchModeWidget, CellRect(14, 2, 3, 1)),
    )

object HomeLogoWidget : HomeWidget {
    override val id = "home_logo"
    override val title = Res.string.app_name
    override val defaultSpan = CellSpan(8, 2)
    override val minSpan = CellSpan(3, 1)
    override val maxSpan = CellSpan(20, 5)
    override val participatesInAutoArrange = false

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        LogoImage(modifier.fillMaxSize())
    }
}

object HomeSearchWidget : HomeWidget {
    override val id = "home_search"
    override val title = Res.string.home_widget_search
    override val defaultSpan = CellSpan(10, 1)
    override val minSpan = CellSpan(6, 1)
    override val maxSpan = CellSpan(20, 3)
    override val removable = false
    override val participatesInAutoArrange = false

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            state.searchCallbacks?.let { callbacks ->
                UnifiedSearchBar(
                    searchUi = state.searchUi,
                    searchCallbacks = callbacks,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    showModePicker = false,
                )
            } ?: Text(stringResource(title))
        }
    }

    @Composable
    override fun Preview(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(title)) }
    }
}

object HomeSearchModeWidget : HomeWidget {
    override val id = "home_search_mode"
    override val title = Res.string.home_widget_search_mode
    override val defaultSpan = CellSpan(3, 1)
    override val minSpan = CellSpan(3, 1)
    override val maxSpan = CellSpan(8, 3)
    override val participatesInAutoArrange = false

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            SearchModePicker(state.searchUi.mode, { state.searchCallbacks?.onModeChange?.invoke(it) })
        }
    }
}

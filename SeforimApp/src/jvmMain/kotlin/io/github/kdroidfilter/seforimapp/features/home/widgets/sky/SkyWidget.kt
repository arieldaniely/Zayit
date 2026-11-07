package io.github.kdroidfilter.seforimapp.features.home.widgets.sky

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabSelected
import io.github.kdroidfilter.seforimapp.earthwidget.SkyWidgetView
import io.github.kdroidfilter.seforimapp.earthwidget.isMoonAboveHorizon
import io.github.kdroidfilter.seforimapp.features.home.widgets.CellSpan
import io.github.kdroidfilter.seforimapp.features.home.widgets.HOME_GRID_COLUMNS
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.features.home.widgets.SkyPreview
import io.github.kdroidfilter.seforimapp.features.home.widgets.WidgetCard
import io.github.kdroidfilter.seforimapp.features.home.widgets.playMillisHere
import io.github.kdroidfilter.seforimapp.features.home.widgets.skyTimeMillisHere
import kotlinx.coroutines.delay
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.home_widget_name_sky

/** The sky seen from the selected location at the selected moment. */
internal object SkyWidget : HomeWidget {
    override val id = "sky"
    override val title = Res.string.home_widget_name_sky
    override val defaultSpan = CellSpan(5, 3)
    override val minSpan = CellSpan(5, 3)
    override val maxSpan = CellSpan(HOME_GRID_COLUMNS, 8)

    @Composable
    override fun Preview(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) = SkyPreview(modifier)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        WidgetCard(modifier) {
            if (LocalTabSelected.current) {
                val selectedMoment = state.skyTimeMillisHere()
                val moment = skyMoment(selectedMoment)
                val location = state.location
                val automaticMode =
                    remember(location, moment) {
                        if (isMoonAboveHorizon(location.latitude, location.longitude, moment)) SkyMode.Sky else SkyMode.Moon
                    }
                SkyWidgetView(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    timeZone = location.timeZone,
                    timeMillis = selectedMoment,
                    glide = state.playMillisHere() == null,
                    moonOnly = automaticMode == SkyMode.Moon,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private enum class SkyMode { Moon, Sky }

@Composable
private fun skyMoment(selectedMoment: Long?): Long {
    if (selectedMoment != null) return selectedMoment
    val live by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(20_000L)
        }
    }
    return live
}

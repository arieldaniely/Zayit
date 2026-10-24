package io.github.kdroidfilter.seforimapp.features.home.widgets.calendar

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.features.home.widgets.CellSpan
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.features.home.widgets.PanelCard
import io.github.kdroidfilter.seforimapp.hebrewcalendar.HebrewCalendarPicker
import seforimapp.seforimapp.generated.resources.Res
import seforimapp.seforimapp.generated.resources.home_widget_name_calendar

/** Uses the existing Hebrew/civil calendar without loading a rendering engine. */
internal object CalendarWidget : HomeWidget {
    override val id = "calendar"
    override val title = Res.string.home_widget_name_calendar
    override val defaultSpan = CellSpan(7, 5)
    override val toolWindowSize = DpSize(440.dp, 500.dp)

    @Composable
    override fun Content(
        state: HomeWidgetsState,
        modifier: Modifier,
    ) {
        PanelCard(modifier) {
            HebrewCalendarPicker(
                initialDate = state.selectedDate,
                onDateSelect = { state.selectedDate = it },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )
        }
    }
}

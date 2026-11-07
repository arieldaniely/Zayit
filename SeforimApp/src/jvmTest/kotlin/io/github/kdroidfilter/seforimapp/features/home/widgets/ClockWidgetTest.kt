package io.github.kdroidfilter.seforimapp.features.home.widgets

import androidx.compose.ui.geometry.Offset
import io.github.kdroidfilter.seforimapp.features.home.widgets.clock.clockHandDegrees
import io.github.kdroidfilter.seforimapp.features.home.widgets.clock.timeFromDialOffset
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class ClockWidgetTest {
    @Test
    fun `dial positions select a time in the current half day`() {
        assertEquals(LocalTime.of(15, 0), timeFromDialOffset(Offset(1f, 0f), LocalTime.NOON))
        assertEquals(LocalTime.of(6, 0), timeFromDialOffset(Offset(0f, 1f), LocalTime.MIDNIGHT))
        assertEquals(LocalTime.of(10, 30), timeFromDialOffset(Offset(-1f, -1f), LocalTime.MIDNIGHT))
        assertEquals(LocalTime.NOON, timeFromDialOffset(Offset(0f, -1f), LocalTime.of(23, 30)))
    }

    @Test
    fun hourHandProgressesBetweenHoursAndWrapsAtNoon() {
        assertEquals(105f to 180f, clockHandDegrees(LocalTime.of(3, 30)))
        assertEquals(0f to 0f, clockHandDegrees(LocalTime.NOON))
        assertEquals(0f to 0f, clockHandDegrees(LocalTime.MIDNIGHT))
    }
}

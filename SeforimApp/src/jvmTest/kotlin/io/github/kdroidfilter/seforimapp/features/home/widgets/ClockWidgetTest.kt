package io.github.kdroidfilter.seforimapp.features.home.widgets

import io.github.kdroidfilter.seforimapp.features.home.widgets.clock.clockHandDegrees
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class ClockWidgetTest {
    @Test
    fun hourHandProgressesBetweenHoursAndWrapsAtNoon() {
        assertEquals(105f to 180f, clockHandDegrees(LocalTime.of(3, 30)))
        assertEquals(0f to 0f, clockHandDegrees(LocalTime.NOON))
        assertEquals(0f to 0f, clockHandDegrees(LocalTime.MIDNIGHT))
    }
}

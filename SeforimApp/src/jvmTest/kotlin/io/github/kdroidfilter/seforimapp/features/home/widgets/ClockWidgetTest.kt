package io.github.kdroidfilter.seforimapp.features.home.widgets

import io.github.kdroidfilter.seforimapp.features.home.widgets.clock.clockHandDegrees
import io.github.kdroidfilter.seforimapp.features.home.widgets.clock.parseClockTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClockWidgetTest {
    @Test
    fun acceptsTwentyFourHourTimesWithoutRollingInvalidInputIntoAnotherDay() {
        assertEquals(LocalTime.of(14, 30), parseClockTime("14:30"))
        assertEquals(LocalTime.of(9, 5), parseClockTime("9:05"))
        assertNull(parseClockTime("24:00"))
        assertNull(parseClockTime("14:65"))
    }

    @Test
    fun hourHandProgressesBetweenHoursAndWrapsAtNoon() {
        assertEquals(105f to 180f, clockHandDegrees(LocalTime.of(3, 30)))
        assertEquals(0f to 0f, clockHandDegrees(LocalTime.NOON))
        assertEquals(0f to 0f, clockHandDegrees(LocalTime.MIDNIGHT))
    }
}

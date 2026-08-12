package io.github.kdroidfilter.seforimapp.hebrewcalendar

import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FlexibleDateParserTest {
    private val referenceDate = LocalDate.of(2026, 7, 29)

    @Test
    fun `parses flexible Gregorian formats`() {
        val expected = LocalDate.of(2028, 6, 12)
        listOf("12/06/28", "12/6/2028", "12 6 28", "2028-06-12").forEach {
            assertEquals(expected, parseFlexibleDate(it, CalendarMode.GREGORIAN, referenceDate), it)
        }
    }

    @Test
    fun `Gregorian date without year uses current year`() {
        assertEquals(LocalDate.of(2026, 3, 7), parseFlexibleDate("7.3", CalendarMode.GREGORIAN, referenceDate))
    }

    @Test
    fun `rejects invalid Gregorian date`() {
        assertNull(parseFlexibleDate("31/2/2026", CalendarMode.GREGORIAN, referenceDate))
    }

    @Test
    fun `parses Hebrew spelling and punctuation variants`() {
        assertHebrewDate("\u05D0 \u05D0\u05D3\u05E8 \u05D0 \u05EA\u05E9\u05E4\u05D5", 5786, 12, 1)
        assertHebrewDate("\u05D9\u05F4\u05D1 \u05E1\u05D9\u05D5\u05DF \u05EA\u05E9\u05E4\u05F4\u05D7", 5788, 3, 12)
        assertHebrewDate("\u05D8\u05D6 \u05E1\u05D9\u05D5\u05D5\u05DF \u05D4\u05EA\u05E9\u05D2", 5703, 3, 16)
        assertHebrewDate("\u05D0 \u05D0\u05D3\u05E8 \u05D0 \u05D2\u05F3\u05EA\u05EA\u05E8\u05F4\u05D3", 4004, 12, 1)
        assertHebrewDate("\u05DB\u05F4\u05D4 \u05DE\u05E8\u05D7\u05E9\u05D5\u05D5\u05DF \u05EA\u05E9\u05E4\u05F4\u05D6", 5787, 8, 25)
    }

    @Test
    fun `Hebrew date without year uses current Hebrew year`() {
        val currentHebrewYear = JewishDate(referenceDate).jewishYear
        val parsed = parseFlexibleDate("\u05D9 \u05D1 \u05E1\u05D9\u05D5\u05DF", CalendarMode.HEBREW, referenceDate)
        val jewishDate = JewishDate(parsed)
        assertEquals(currentHebrewYear, jewishDate.jewishYear)
        assertEquals(3, jewishDate.jewishMonth)
        assertEquals(12, jewishDate.jewishDayOfMonth)
    }

    @Test
    fun `rejects invalid Hebrew date`() {
        assertNull(parseFlexibleDate("\u05DC \u05D0\u05D9\u05D9\u05E8 \u05EA\u05E9\u05E4\u05D5", CalendarMode.HEBREW, referenceDate))
        assertNull(parseFlexibleDate("\u05D0 \u05D0\u05D3\u05E8 \u05D1 \u05EA\u05E9\u05E4\u05D4", CalendarMode.HEBREW, referenceDate))
    }

    private fun assertHebrewDate(
        input: String,
        year: Int,
        month: Int,
        day: Int,
    ) {
        val parsed = parseFlexibleDate(input, CalendarMode.HEBREW, referenceDate)
        val jewishDate = JewishDate(parsed)
        assertEquals(year, jewishDate.jewishYear, input)
        assertEquals(month, jewishDate.jewishMonth, input)
        assertEquals(day, jewishDate.jewishDayOfMonth, input)
    }
}

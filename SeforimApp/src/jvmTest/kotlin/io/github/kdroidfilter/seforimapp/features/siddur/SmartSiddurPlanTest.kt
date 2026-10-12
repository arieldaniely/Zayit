package io.github.kdroidfilter.seforimapp.features.siddur

import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.HebrewMonth
import io.github.kdroidfilter.kosherkotlin.hebrewcalendar.JewishCalendar
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import kotlinx.datetime.toJavaLocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SmartSiddurPlanTest {
    private fun day(month: HebrewMonth, date: Int, israel: Boolean = true) = siddurDay(
        JewishCalendar(5787, month, date).gregorianLocalDate.toJavaLocalDate(),
        SiddurPrayer.SHACHARIT,
        israel,
    )

    @Test
    fun `rosh chodesh includes yaaleh and half hallel without tachanun`() {
        val day = day(HebrewMonth.CHESHVAN, 1)
        assertTrue(day.roshChodesh)
        assertTrue(day.yaalehVeyavo)
        assertFalse(day.tachanun)
        assertEquals(Hallel.HALF, day.hallel)
        assertTrue(sectionIsApplicable(listOf("שחרית", "חצי הלל"), day))
        assertFalse(sectionIsApplicable(listOf("שחרית", "הלל שלם"), day))
    }

    @Test
    fun `diaspora second festival day differs from Israel`() {
        assertFalse(day(HebrewMonth.NISSAN, 16).festival)
        assertTrue(day(HebrewMonth.NISSAN, 16, israel = false).festival)
    }

    @Test
    fun `summer winter and chanuka select the proper headings`() {
        val summer = day(HebrewMonth.TAMMUZ, 20)
        val winter = day(HebrewMonth.TEVES, 20)
        assertFalse(summer.talUmatar)
        assertTrue(winter.talUmatar)
        assertFalse(sectionIsApplicable(listOf("ותן טל ומטר"), summer))
        assertFalse(sectionIsApplicable(listOf("ותן ברכה"), winter))
        assertTrue(day(HebrewMonth.KISLEV, 27).alHanissim)
        assertEquals(3, day(HebrewMonth.KISLEV, 27).chanuka)
    }

    @Test
    fun `no minyan excludes congregational sections and keeps source order`() {
        val day = day(HebrewMonth.TISHREI, 24).copy(minyan = false)
        val entries = listOf(
            TocEntry(id = 1, bookId = 5, text = "שַׁחֲרִית", level = 0),
            TocEntry(id = 2, bookId = 5, parentId = 1, text = "ברכות השחר", level = 1),
            TocEntry(id = 3, bookId = 5, parentId = 1, text = "קדיש יתום", level = 1),
            TocEntry(id = 4, bookId = 5, parentId = 1, text = "עמידה", level = 1),
            TocEntry(id = 5, bookId = 5, text = "מנחה", level = 0),
        )
        assertEquals(listOf(2L, 4L), selectSiddurSections(entries, day).map { it.entry.id })
    }

    @Test
    fun `missing prayer does not substitute the entire book and cyclic outlines fail`() {
        val day = day(HebrewMonth.TISHREI, 24)
        val entries = listOf(TocEntry(id = 1, bookId = 5, text = "מנחה", level = 0))
        assertTrue(selectSiddurSections(entries, day).isEmpty())
        assertFailsWith<IllegalStateException> {
            selectSiddurSections(listOf(entries.first().copy(parentId = 1)), day)
        }
    }
}

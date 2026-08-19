package io.github.kdroidfilter.seforimapp.features.search

import io.github.kdroidfilter.seforimapp.features.search.domain.TorahReferenceSearchHelper
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TorahReferenceSearchHelperTest {

    @Test
    fun `gematria conversion works accurately`() {
        assertEquals(1, TorahReferenceSearchHelper.gematriaToNumber("א"))
        assertEquals(2, TorahReferenceSearchHelper.gematriaToNumber("ב"))
        assertEquals(10, TorahReferenceSearchHelper.gematriaToNumber("י"))
        assertEquals(15, TorahReferenceSearchHelper.gematriaToNumber("טו"))
        assertEquals(16, TorahReferenceSearchHelper.gematriaToNumber("טז"))
        assertEquals(27, TorahReferenceSearchHelper.gematriaToNumber("כז"))
        assertEquals(263, TorahReferenceSearchHelper.gematriaToNumber("רסג"))
        assertEquals(263, TorahReferenceSearchHelper.gematriaToNumber("רס\"ג"))
        assertEquals(119, TorahReferenceSearchHelper.gematriaToNumber("קי\"ט"))

        assertEquals("א", TorahReferenceSearchHelper.numberToGematria(1))
        assertEquals("ב", TorahReferenceSearchHelper.numberToGematria(2))
        assertEquals("טו", TorahReferenceSearchHelper.numberToGematria(15))
        assertEquals("טז", TorahReferenceSearchHelper.numberToGematria(16))
        assertEquals("כז", TorahReferenceSearchHelper.numberToGematria(27))
        assertEquals("רסג", TorahReferenceSearchHelper.numberToGematria(263))
        assertEquals("קיט", TorahReferenceSearchHelper.numberToGematria(119))
    }

    @Test
    fun `splitReferenceQuery splits delimiter based references`() {
        val splits1 = TorahReferenceSearchHelper.splitReferenceQuery("ברכות, ב.")
        assertTrue(splits1.any { it.first == "ברכות" && it.second == "ב." })

        val splits2 = TorahReferenceSearchHelper.splitReferenceQuery("שו\"ע, רסג")
        assertTrue(splits2.any { it.first == "שו\"ע" && it.second == "רסג" })

        val splits3 = TorahReferenceSearchHelper.splitReferenceQuery("בראשית - יח א")
        assertTrue(splits3.any { it.first == "בראשית" && it.second == "יח א" })
    }

    @Test
    fun `splitReferenceQuery splits token based references`() {
        val splits1 = TorahReferenceSearchHelper.splitReferenceQuery("ברכות ב:")
        assertTrue(splits1.any { it.first == "ברכות" && it.second == "ב:" })

        val splits2 = TorahReferenceSearchHelper.splitReferenceQuery("שולחן ערוך אורח חיים רסג")
        assertTrue(splits2.any { it.first == "שולחן ערוך אורח חיים" && it.second == "רסג" })
        assertTrue(splits2.any { it.first == "שולחן ערוך" && it.second == "אורח חיים רסג" })

        val splits3 = TorahReferenceSearchHelper.splitReferenceQuery("משנה ברורה רסג")
        assertTrue(splits3.any { it.first == "משנה ברורה" && it.second == "רסג" })

        val splits4 = TorahReferenceSearchHelper.splitReferenceQuery("רמב\"ם הלכות שבת א ב")
        assertTrue(splits4.any { it.first == "רמב\"ם הלכות שבת" && it.second == "א ב" })
    }

    @Test
    fun `matchesTocLocation matches Talmud Bavli daf expressions`() {
        val tocEntryAmudA = TocEntry(id = 1L, bookId = 1L, text = "דף ב עמוד א", level = 2)
        val tocDtoAmudA = TocSuggestionDto(tocEntryAmudA, listOf("ברכות", "פרק ראשון", "דף ב עמוד א"))

        val tocEntryAmudB = TocEntry(id = 2L, bookId = 1L, text = "דף ב עמוד ב", level = 2)
        val tocDtoAmudB = TocSuggestionDto(tocEntryAmudB, listOf("ברכות", "פרק ראשון", "דף ב עמוד ב"))

        // Test Amud B notations
        assertTrue(TorahReferenceSearchHelper.matchesTocLocation(tocDtoAmudB, "ב:"))
        assertTrue(TorahReferenceSearchHelper.matchesTocLocation(tocDtoAmudB, "ב ע\"ב"))
        assertTrue(TorahReferenceSearchHelper.matchesTocLocation(tocDtoAmudB, "דף ב עמוד ב"))
        assertTrue(TorahReferenceSearchHelper.matchesTocLocation(tocDtoAmudB, "ב/ב"))
        assertFalse(TorahReferenceSearchHelper.matchesTocLocation(tocDtoAmudA, "ב:"))

        // Test Amud A notations
        assertTrue(TorahReferenceSearchHelper.matchesTocLocation(tocDtoAmudA, "ב."))
        assertTrue(TorahReferenceSearchHelper.matchesTocLocation(tocDtoAmudA, "ב ע\"א"))
        assertTrue(TorahReferenceSearchHelper.matchesTocLocation(tocDtoAmudA, "דף ב עמוד א"))
        assertTrue(TorahReferenceSearchHelper.matchesTocLocation(tocDtoAmudA, "ב/א"))
        assertFalse(TorahReferenceSearchHelper.matchesTocLocation(tocDtoAmudB, "ב."))

        // Test daf only (matches both amudim)
        assertTrue(TorahReferenceSearchHelper.matchesTocLocation(tocDtoAmudA, "דף ב"))
        assertTrue(TorahReferenceSearchHelper.matchesTocLocation(tocDtoAmudB, "דף ב"))

        // Test numeric daf
        assertTrue(TorahReferenceSearchHelper.matchesTocLocation(tocDtoAmudB, "2:"))
        assertTrue(TorahReferenceSearchHelper.matchesTocLocation(tocDtoAmudA, "2."))
    }

    @Test
    fun `matchesTocLocation matches Shulchan Aruch and Rambam locations`() {
        val tocSa = TocEntry(id = 10L, bookId = 2L, text = "סימן רסג - דיני הדלקת נרות", level = 2)
        val tocDtoSa = TocSuggestionDto(tocSa, listOf("שולחן ערוך אורח חיים", "הלכות שבת", "סימן רסג - דיני הדלקת נרות"))

        assertTrue(TorahReferenceSearchHelper.matchesTocLocation(tocDtoSa, "רסג"))
        assertTrue(TorahReferenceSearchHelper.matchesTocLocation(tocDtoSa, "סימן רסג"))
        assertTrue(TorahReferenceSearchHelper.matchesTocLocation(tocDtoSa, "או\"ח רסג"))
        assertTrue(TorahReferenceSearchHelper.matchesTocLocation(tocDtoSa, "שבת רסג"))

        val tocRambam = TocEntry(id = 20L, bookId = 3L, text = "הלכה ב", level = 4)
        val tocDtoRambam = TocSuggestionDto(tocRambam, listOf("משנה תורה", "ספר זמנים", "הלכות שבת", "פרק א", "הלכה ב"))

        assertTrue(TorahReferenceSearchHelper.matchesTocLocation(tocDtoRambam, "פרק א הלכה ב"))
        assertTrue(TorahReferenceSearchHelper.matchesTocLocation(tocDtoRambam, "א ב"))
        assertTrue(TorahReferenceSearchHelper.matchesTocLocation(tocDtoRambam, "שבת א ב"))
        assertTrue(TorahReferenceSearchHelper.matchesTocLocation(tocDtoRambam, "הל' שבת א ב"))
    }
}

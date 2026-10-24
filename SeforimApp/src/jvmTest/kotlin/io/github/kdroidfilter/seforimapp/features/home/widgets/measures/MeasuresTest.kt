package io.github.kdroidfilter.seforimapp.features.home.widgets.measures

import com.russhwolf.settings.PropertiesSettings
import io.github.kdroidfilter.seforimapp.features.home.widgets.WidgetPreferences
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals

class MeasuresTest {
    @Test
    fun convertsLengthsAndWalkingTimesWithoutRoundingIntermediateValues() {
        assertEquals(6.0, LengthUnit.AMMA.etzbaos / LengthUnit.TEFACH.etzbaos)
        assertEquals(8000.0, LengthUnit.PARSAH.etzbaos / LengthUnit.AMMA.etzbaos)
        assertEquals(0.25, LengthUnit.MIL.etzbaos / LengthUnit.PARSAH.etzbaos)
        assertEquals(listOf(58.0, 53.975), AMMA_OPINIONS.map { it.cm })
        assertEquals(listOf(18.0, 22.5, 24.0), MIL_OPINIONS.map { it.minutes })
    }

    @Test
    fun `a conversion cites each unit it goes through`() {
        assertEquals(listOf("רמב״ם שבת יז, לו"), conversionSources(LengthUnit.AMMA, LengthUnit.TEFACH).map { it.ref })
        assertEquals(
            listOf("רמב״ם שבת יז, לו", "רמב״ם תפילה ד, ב", "משנה ברורה קי, לא"),
            conversionSources(LengthUnit.PARSAH, LengthUnit.ETZBA).map { it.ref },
        )
        assertEquals(emptyList(), conversionSources(LengthUnit.MIL, LengthUnit.MIL))
    }

    @Test
    fun `every opinion is shown until the user picks`() {
        assertEquals(
            setOf("chazon_ish", "igros_moshe", "mil_18", "mil_22_5", "mil_24"),
            WidgetPreferences(PropertiesSettings(Properties()))[MeasuresSettings.shown],
        )
    }
}

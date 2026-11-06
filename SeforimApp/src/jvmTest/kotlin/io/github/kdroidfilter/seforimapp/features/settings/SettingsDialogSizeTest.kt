package io.github.kdroidfilter.seforimapp.features.settings

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsDialogSizeTest {
    @Test
    fun respectsSmallWorkAreasAndKeepsThePreferredSizeOnLargeScreens() {
        assertEquals(DpSize(960.dp, 720.dp), settingsDialogSize(1920, 1080))
        val compact = settingsDialogSize(800, 600)
        assertTrue(compact.width < 800.dp)
        assertTrue(compact.height < 600.dp)
        assertEquals(DpSize(752.dp, 552.dp), compact)
    }
}

package io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeThemeTransitionTest {
    @Test
    fun frameMappingCoversBothEndpoints() {
        assertEquals(0, homeThemeTransitionFrame(0f))
        assertEquals(HOME_THEME_TRANSITION_FRAME_COUNT - 1, homeThemeTransitionFrame(1f))
    }

    @Test
    fun frameMappingIsClamped() {
        assertEquals(0, homeThemeTransitionFrame(-1f))
        assertEquals(HOME_THEME_TRANSITION_FRAME_COUNT - 1, homeThemeTransitionFrame(2f))
    }

    @Test
    fun overlayBlendsIntoTheSelectedEndpoint() {
        assertEquals(0f, homeThemeTransitionAlpha(position = 1f, targetIsDark = true))
        assertEquals(0f, homeThemeTransitionAlpha(position = 0f, targetIsDark = false))
        assertTrue(homeThemeTransitionAlpha(position = 0.5f, targetIsDark = true) > 0.99f)
        assertTrue(homeThemeTransitionAlpha(position = 0.5f, targetIsDark = false) > 0.99f)
    }
}

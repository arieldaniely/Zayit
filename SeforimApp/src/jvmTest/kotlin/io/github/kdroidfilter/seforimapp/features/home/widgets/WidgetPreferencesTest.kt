package io.github.kdroidfilter.seforimapp.features.home.widgets

import com.russhwolf.settings.PropertiesSettings
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WidgetPreferencesTest {
    private class Options : WidgetSettings("test") {
        val enabled by boolean(true)
        val shown by stringSet(setOf("first", "second"))
    }

    @Test
    fun changesPersistAndResetOnlyTheChosenGroup() {
        val properties = Properties()
        val store = PropertiesSettings(properties)
        store.putString("unrelated", "keep")
        val preferences = WidgetPreferences(store)
        val options = Options()
        val flow = preferences.flow(options.enabled)
        preferences[options.enabled] = false
        preferences[options.shown] = setOf("second")
        assertFalse(flow.value)
        val restarted = WidgetPreferences(PropertiesSettings(properties))
        assertFalse(restarted[options.enabled])
        assertEquals(setOf("second"), restarted[options.shown])
        preferences.reset(options)
        assertTrue(flow.value)
        assertEquals(options.shown.default, preferences[options.shown])
        assertEquals("keep", store.getString("unrelated", ""))
    }

    @Test
    fun requestingAnOpenToolReusesItsWindow() {
        val windows = ToolWindows()
        windows.open("calendar")
        windows.open("calendar")
        windows.open("unknown")
        assertEquals(listOf("calendar"), windows.open.map { it.id })
        assertEquals(1, windows.reopened["calendar"])
    }
}

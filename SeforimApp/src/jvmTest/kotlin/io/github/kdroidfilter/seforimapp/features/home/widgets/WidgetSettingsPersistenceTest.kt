package io.github.kdroidfilter.seforimapp.features.home.widgets

import com.russhwolf.settings.PropertiesSettings
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WidgetSettingsPersistenceTest {
    private class Options : WidgetSettings("test") {
        val enabled by boolean(true)
        val shown by stringSet(setOf("first", "second"))
    }

    @Test
    fun changesPersistAndResetOnlyTheChosenGroup() {
        val properties = Properties()
        val store = PropertiesSettings(properties)
        store.putString("unrelated", "keep")
        val preferences = AppSettings.also { it.initialize(store) }
        val options = Options()
        val flow = preferences.flow(options.enabled)
        preferences[options.enabled] = false
        preferences[options.shown] = setOf("second")
        assertFalse(flow.value)
        AppSettings.initialize(PropertiesSettings(Properties()))
        assertTrue(flow.value)
        val restarted = AppSettings.also { it.initialize(PropertiesSettings(properties)) }
        assertFalse(restarted[options.enabled])
        assertEquals(setOf("second"), restarted[options.shown])
        preferences.reset(options)
        assertTrue(flow.value)
        assertEquals(options.shown.default, preferences[options.shown])
        assertEquals("keep", store.getString("unrelated", ""))
    }

    @Test
    fun legacyLayoutIsReadAndMovesToTheOriginalKeyOnlyWhenEdited() {
        val store = PropertiesSettings(Properties())
        store.putString("home_widgets.saved", "calendar@0,0,4,4")
        AppSettings.initialize(store)
        assertEquals("calendar@0,0,4,4", AppSettings.homeWidgetsLayoutFlow.value)
        assertTrue(store.hasKey("home_widgets.saved"))
        AppSettings.setHomeWidgetsLayout("zmanim@0,0,5,3")
        assertFalse(store.hasKey("home_widgets.saved"))
        assertEquals("zmanim@0,0,5,3", store.getString("home_widgets_layout", ""))
        AppSettings.initialize(PropertiesSettings(Properties()))
        AppSettings.initialize(store)
        assertEquals("zmanim@0,0,5,3", AppSettings.homeWidgetsLayoutFlow.value)
        AppSettings.setHomeWidgetsLayout(null)
        assertFalse(store.hasKey("home_widgets_layout"))
        assertEquals(null, AppSettings.homeWidgetsLayoutFlow.value)
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

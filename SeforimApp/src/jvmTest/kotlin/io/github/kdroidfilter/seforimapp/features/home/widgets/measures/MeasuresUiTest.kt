package io.github.kdroidfilter.seforimapp.features.home.widgets.measures

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.russhwolf.settings.PropertiesSettings
import io.github.kdroidfilter.seforimapp.core.MainAppState
import io.github.kdroidfilter.seforimapp.core.presentation.theme.AccentColor
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.features.home.widgets.WidgetPreferences
import io.github.kdroidfilter.seforimapp.framework.di.AppGraph
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import java.util.Properties
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class MeasuresUiTest {
    @Test
    fun showsHebrewUnitsAndReflectsOpinionChangesImmediately() =
        runComposeUiTest {
            val preferences = WidgetPreferences(PropertiesSettings(Properties()))
            val state = HomeWidgetsState(preferences)
            val graph = mockk<AppGraph>()
            val mainState = mockk<MainAppState>()
            every { graph.mainAppState } returns mainState
            every { mainState.accentColor } returns MutableStateFlow(AccentColor.Gold)
            setContent {
                CompositionLocalProvider(LocalAppGraph provides graph) {
                    IntUiTheme(isDark = false) {
                        MeasuresWidget.Content(state, Modifier.size(460.dp, 500.dp))
                    }
                }
            }
            onNodeWithText("= 6 טפחים").assertExists()
            onNodeWithText("58 ס״מ").assertExists()
            onNodeWithText("אגרות משה").assertExists()
            runOnIdle { preferences[MeasuresSettings.shown] = setOf("chazon_ish") }
            onNodeWithText("אגרות משה").assertDoesNotExist()
            onNodeWithText("58 ס״מ").assertExists()
        }
}

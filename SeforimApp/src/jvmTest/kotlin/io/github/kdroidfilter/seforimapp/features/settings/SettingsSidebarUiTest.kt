package io.github.kdroidfilter.seforimapp.features.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.features.settings.navigation.SettingsDestination
import io.github.kdroidfilter.seforimapp.features.settings.ui.SettingsSidebarView
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.Text
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SettingsSidebarUiTest {
    @Test
    fun searchFindsSemanticSettingsAndNavigationRemainsUsableAfterClearing() =
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    IntUiTheme(isDark = false) {
                        var destination: SettingsDestination by remember { mutableStateOf(SettingsDestination.General) }
                        Row {
                            SettingsSidebarView(destination::class.simpleName.orEmpty(), { destination = it }, Modifier.width(220.dp))
                            Text(destination::class.simpleName.orEmpty())
                        }
                    }
                }
            }
            onNodeWithText("חיפוש בהגדרות…").performTextInput("סמנטי")
            onNodeWithText("גופנים").assertDoesNotExist()
            onNodeWithText("General").assertExists()
            onNodeWithText("ספרים אישיים").performClick()
            onNodeWithText("PersonalLibrary").assertExists()
            onNodeWithText("ניקוי החיפוש").performClick()
            onNodeWithText("גופנים").assertExists()
            onNodeWithText("חיפוש בהגדרות…").performTextInput("איןכזונושא")
            onNodeWithText("לא נמצאו הגדרות מתאימות. נסו מילה אחרת.").assertExists()
            onNodeWithText("PersonalLibrary").assertExists()
        }
}

package io.github.kdroidfilter.seforimapp.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.russhwolf.settings.PropertiesSettings
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.settings.ui.SettingsSearchResults
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import java.io.File
import java.util.Properties
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SettingsSearchUiTest {
    @Test
    fun `search previews individual antialiasing controls and descriptions`() {
        for (dark in listOf(false, true)) {
            runComposeUiTest {
                AppSettings.initialize(PropertiesSettings(Properties()))
                setContent {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        IntUiTheme(isDark = dark) {
                            Box(Modifier.width(640.dp).height(400.dp).background(JewelTheme.globalColors.toolwindowBackground)) {
                                SettingsSearchResults("החלקת קצוות") {}
                            }
                        }
                    }
                }
                onNodeWithText("החלקת קצוות בתלת־ממד (אנטי־אליאסינג)").assertExists()
                onNodeWithText("כבוי (ברירת מחדל)").assertExists()
                onNodeWithText("גופן לטקסט הספר").assertDoesNotExist()
                val output = File("artifacts/ui-review/settings-search-${if (dark) "dark" else "light"}.png")
                output.parentFile.mkdirs()
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", output)
            }
        }
    }

    @Test
    fun `a preview checkbox updates the setting without navigating away`() =
        runComposeUiTest {
            AppSettings.initialize(PropertiesSettings(Properties()))
            var navigated = false
            setContent {
                IntUiTheme(isDark = false) {
                    SettingsSearchResults("מנע כיבוי מסך") { navigated = true }
                }
            }
            assertTrue(AppSettings.isKeepScreenAwakeOnBookEnabled())
            onNode(isToggleable()).performClick()
            assertFalse(AppSettings.isKeepScreenAwakeOnBookEnabled())
            assertFalse(navigated)
            AppSettings.initialize(PropertiesSettings(Properties()))
        }
}

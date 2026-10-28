package io.github.kdroidfilter.seforimapp.features.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.russhwolf.settings.PropertiesSettings
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.search.SemanticSearchSettings
import io.github.kdroidfilter.seforimapp.features.settings.ui.SemanticSearchSettingsCard
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import java.io.File
import java.util.Properties
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SemanticSearchSettingsUiTest {
    @Test
    fun thresholdControlsAndCachePreferenceWorkInHebrew() =
        runComposeUiTest {
            AppSettings.initialize(PropertiesSettings(Properties()))
            try {
                setContent {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        IntUiTheme(isDark = false) {
                            Box(Modifier.width(760.dp).height(760.dp)) { SemanticSearchSettingsCard() }
                        }
                    }
                }
                onNodeWithText("מספר מועמדים קבוע").performClick()
                onNodeWithText("כל התוצאות שעוברות סף דמיון").performClick()
                onNodeWithText("סף דמיון מינימלי (0–1)").assertExists()
                onNodeWithText("תקרה אופציונלית במצב סף (0 = ללא תקרה)").assertExists()
                onNodeWithText("שמירת קידודי שאילתות בזיכרון לחיפוש חוזר מהיר").performClick()
                waitForIdle()
                assertTrue(AppSettings[SemanticSearchSettings.preferences].thresholdEnabled)
                assertTrue(AppSettings[SemanticSearchSettings.preferences].cacheQueries)
                val output = File("artifacts/ui-review/semantic-search-options.png")
                output.parentFile.mkdirs()
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", output)
            } finally {
                AppSettings.initialize(PropertiesSettings(Properties()))
            }
        }
}

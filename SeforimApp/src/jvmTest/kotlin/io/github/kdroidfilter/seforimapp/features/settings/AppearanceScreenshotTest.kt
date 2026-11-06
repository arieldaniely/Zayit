package io.github.kdroidfilter.seforimapp.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.core.presentation.tabs.LocalTabSelected
import io.github.kdroidfilter.seforimapp.core.presentation.theme.AccentColor
import io.github.kdroidfilter.seforimapp.core.presentation.theme.IntUiThemes
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeStyle
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeUserLocation
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsLayout
import io.github.kdroidfilter.seforimapp.features.home.widgets.HomeWidgetsState
import io.github.kdroidfilter.seforimapp.features.home.widgets.calendar.CalendarWidget
import io.github.kdroidfilter.seforimapp.features.home.widgets.clock.ClockWidget
import io.github.kdroidfilter.seforimapp.features.onboarding.userprofile.Community
import io.github.kdroidfilter.seforimapp.features.settings.ui.AppearancePreview
import io.github.kdroidfilter.seforimapp.features.settings.ui.HomeAppearanceCard
import io.github.kdroidfilter.seforimapp.features.settings.ui.ThemeModeChoices
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.testAppGraph
import io.github.kdroidfilter.seforimapp.testAppSettings
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AppearanceScreenshotTest {
    @Test
    fun renderAppearanceControlsInBothThemes() {
        for (dark in listOf(false, true)) {
            testAppSettings()
            val graph = testAppGraph()
            var preview: ImageBitmap? = null
            runComposeUiTest {
                val state =
                    HomeWidgetsState(HomeUserLocation.preview, Community.SEPHARADE, HomeWidgetsLayout(testAppSettings()))
                setContent {
                    IntUiTheme(isDark = dark) {
                        CompositionLocalProvider(
                            LocalAppGraph provides graph,
                            LocalTabSelected provides false,
                            LocalLayoutDirection provides LayoutDirection.Rtl,
                        ) {
                            Row(
                                Modifier
                                    .width(800.dp)
                                    .height(450.dp)
                                    .background(JewelTheme.globalColors.panelBackground)
                                    .padding(16.dp),
                            ) {
                                CalendarWidget.Content(state, Modifier.weight(1f).height(400.dp))
                                ClockWidget.Content(state, Modifier.weight(1f).height(400.dp))
                            }
                        }
                    }
                }
                mainClock.advanceTimeBy(1_000)
                waitForIdle()
                preview = onRoot().captureToImage()
            }
            runComposeUiTest {
                setContent {
                    IntUiTheme(isDark = dark) {
                        CompositionLocalProvider(
                            LocalAppGraph provides graph,
                            LocalLayoutDirection provides LayoutDirection.Rtl,
                            LocalSettingsPreview provides preview,
                        ) {
                            Column(
                                Modifier
                                    .width(900.dp)
                                    .height(850.dp)
                                    .background(JewelTheme.globalColors.panelBackground)
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                AppearancePreview(ThemeStyle.Islands, AccentColor.Default)
                                ThemeModeChoices(IntUiThemes.System, {})
                                HomeAppearanceCard()
                            }
                        }
                    }
                }
                waitForIdle()
                onNodeWithContentDescription("תצוגה מקדימה חיה").assertExists()
                val output = File("build/screenshots/appearance-${if (dark) "dark" else "light"}.png")
                output.parentFile.mkdirs()
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", output)
            }
        }
    }
}

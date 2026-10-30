package io.github.kdroidfilter.seforimapp.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.core.presentation.theme.AccentColor
import io.github.kdroidfilter.seforimapp.core.presentation.theme.IntUiThemes
import io.github.kdroidfilter.seforimapp.core.presentation.theme.ThemeStyle
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
            runComposeUiTest {
                testAppSettings()
                val graph = testAppGraph()
                setContent {
                    IntUiTheme(isDark = dark) {
                        CompositionLocalProvider(LocalAppGraph provides graph, LocalLayoutDirection provides LayoutDirection.Rtl) {
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
                val output = File("build/screenshots/appearance-${if (dark) "dark" else "light"}.png")
                output.parentFile.mkdirs()
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", output)
            }
        }
    }
}

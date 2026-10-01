package io.github.kdroidfilter.seforimapp.features.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.russhwolf.settings.PropertiesSettings
import dev.zacsweers.metro.createGraph
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import io.github.kdroidfilter.seforimapp.core.presentation.utils.LocalWindowViewModelStoreOwner
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.settings.ui.DisplaySettingsScreen
import io.github.kdroidfilter.seforimapp.features.settings.ui.FontsSettingsScreen
import io.github.kdroidfilter.seforimapp.features.settings.ui.ProfileSettingsScreen
import io.github.kdroidfilter.seforimapp.framework.di.AppGraph
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.InlineInformationBanner
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SettingsScreensUiTest {
    @Test
    fun `display settings renders with Jewel combo boxes`() = assertScreenRenders("סגנון ערכת נושא") { DisplaySettingsScreen() }

    @Test
    fun `profile settings renders with Jewel combo boxes`() = assertScreenRenders("שם פרטי") { ProfileSettingsScreen() }

    @Test
    fun `fonts settings renders with Jewel combo boxes`() = assertScreenRenders("גופן לטקסט הספר") { FontsSettingsScreen() }

    @Test
    fun `update banner uses the available card width`() {
        runComposeUiTest {
            setContent {
                IntUiTheme(isDark = false) {
                    Column(modifier = Modifier.width(400.dp).testTag("card")) {
                        InlineInformationBanner(text = "בודק עדכונים", modifier = Modifier.testTag("banner"))
                    }
                }
            }
            val card = onNodeWithTag("card").getUnclippedBoundsInRoot()
            val banner = onNodeWithTag("banner").getUnclippedBoundsInRoot()
            val cardWidth = card.right - card.left
            val bannerWidth = banner.right - banner.left
            assertTrue(bannerWidth >= cardWidth * 0.95f, "Update banner occupied $bannerWidth of $cardWidth")
        }
    }

    private fun assertScreenRenders(
        expectedText: String,
        content: @Composable () -> Unit,
    ) = runComposeUiTest {
        AppSettings.initialize(PropertiesSettings(Properties()))
        val graph = createGraph<AppGraph>()
        val owner =
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }

        try {
            setContent {
                CompositionLocalProvider(
                    LocalAppGraph provides graph,
                    LocalMetroViewModelFactory provides graph.metroViewModelFactory,
                    LocalWindowViewModelStoreOwner provides owner,
                ) {
                    IntUiTheme(isDark = false) {
                        content()
                    }
                }
            }
            onNodeWithText(expectedText).assertExists()
        } finally {
            owner.viewModelStore.clear()
            AppSettings.initialize(PropertiesSettings(Properties()))
        }
    }
}

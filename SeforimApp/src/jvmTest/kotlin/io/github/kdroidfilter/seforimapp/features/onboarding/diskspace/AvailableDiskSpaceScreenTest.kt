package io.github.kdroidfilter.seforimapp.features.onboarding.diskspace

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AvailableDiskSpaceScreenTest {
    @Test
    fun `low free space does not prevent reaching cleanup before installation`() =
        runComposeUiTest {
            var continued = false
            setContent {
                IntUiTheme(isDark = false) {
                    AvailableDiskSpaceView(
                        state =
                            AvailableDiskSpaceState(
                                isLoading = false,
                                hasEnoughSpace = false,
                                totalDiskSpace = 100L * 1024 * 1024 * 1024,
                                availableDiskSpace = 5L * 1024 * 1024 * 1024,
                            ),
                        onEvent = {},
                        onNext = { continued = true },
                    )
                }
            }
            onNodeWithText("המשך").assertIsEnabled().performClick()
            assertTrue(continued)
        }

    @Test
    fun `disk preview waits for capacity information before continuing`() =
        runComposeUiTest {
            setContent {
                IntUiTheme(isDark = false) {
                    AvailableDiskSpaceView(
                        state = AvailableDiskSpaceState(isLoading = true),
                        onEvent = {},
                    )
                }
            }
            onNodeWithText("המשך").assertIsNotEnabled()
        }
}

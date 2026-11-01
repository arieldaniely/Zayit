package io.github.kdroidfilter.seforimapp.features.onboarding.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class OnBoardingScaffoldTest {
    @Test
    fun `file action scrolls into view above a tall location footer`() =
        runComposeUiTest {
            var filePickerOpened = false
            var locationPickerOpened = false
            setContent {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    IntUiTheme(isDark = false) {
                        Box(Modifier.size(700.dp, 360.dp)) {
                            OnBoardingScaffold(
                                title = "בחירת קבצים",
                                bottomAction = {
                                    Column(Modifier.testTag("footer").padding(top = 48.dp)) {
                                        ChooseInstallationLocationButton(onClick = { locationPickerOpened = true })
                                    }
                                },
                            ) {
                                Spacer(Modifier.height(500.dp))
                                DefaultButton(
                                    onClick = { filePickerOpened = true },
                                    modifier = Modifier.testTag("file-picker"),
                                ) {
                                    Text("בחירת קבצים")
                                }
                            }
                        }
                    }
                }
            }

            val filePicker = onNodeWithTag("file-picker")
            filePicker.performScrollTo().assertIsDisplayed()
            val fileBounds = filePicker.getUnclippedBoundsInRoot()
            val footerBounds = onNodeWithTag("footer").getUnclippedBoundsInRoot()
            assertTrue(fileBounds.bottom <= footerBounds.top, "The footer must not cover the file picker")
            filePicker.performMouseInput { click(center) }
            assertTrue(filePickerOpened)
            onNodeWithText("לבחירת מקום התקנה אחר").performMouseInput { click(center) }
            assertTrue(locationPickerOpened)
        }

    @Test
    fun `content with its own scrolling list keeps bounded height`() =
        runComposeUiTest {
            setContent {
                IntUiTheme(isDark = false) {
                    Box(Modifier.size(700.dp, 360.dp)) {
                        OnBoardingScaffold(
                            title = "תנאי שימוש",
                            bottomAction = { DefaultButton(onClick = {}) { Text("הבא") } },
                            scrollContent = false,
                        ) {
                            LazyColumn(Modifier.testTag("license")) {
                                items(30) { index -> Text("סעיף $index", Modifier.height(40.dp)) }
                            }
                        }
                    }
                }
            }

            onNodeWithTag("license").performScrollToIndex(29)
            onNodeWithText("סעיף 29").assertIsDisplayed()
            onNodeWithText("הבא").assertIsDisplayed()
        }
}

package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult
import kotlinx.collections.immutable.persistentMapOf
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SearchFeedbackUiTest {
    @Test
    fun `feedback appears on hover and clicking it does not open the result`() =
        runComposeUiTest {
            val feedbackEnabled = mutableStateOf(true)
            var entries = 0
            val feedback = mutableListOf<SearchFeedbackType>()
            setContent {
                IntUiTheme(isDark = false) {
                    Box(Modifier.width(700.dp).testTag("result")) {
                        ResultView(
                            item = ResultItem(SearchResult(1, "ספר לבדיקה", 2, 0, "פסקה עם <b>תורה</b> ומצוות", 0.9), 0),
                            selected = false,
                            findQuery = null,
                            onClick = { entries++ },
                            onOpen = { entries++ },
                            onMoreInBook = {},
                            breadcrumbs = persistentMapOf(),
                            onRequestBreadcrumb = {},
                            bookFontCode = "notoserifhebrew",
                            feedbackEnabled = feedbackEnabled.value,
                            onFeedback = { feedback += it },
                        )
                    }
                }
            }
            onNodeWithContentDescription("התוצאה מועילה").assertDoesNotExist()
            onNodeWithTag("result").performMouseInput { enter(center) }
            onNodeWithContentDescription("התוצאה מועילה").assertExists()
            onNodeWithContentDescription("התוצאה מועילה").performMouseInput { click() }
            assertEquals(listOf(SearchFeedbackType.LIKE), feedback)
            assertEquals(0, entries)
            onNodeWithTag("result").performMouseInput {
                moveTo(Offset(-10f, -10f))
            }
            onNodeWithContentDescription("התוצאה מועילה").assertDoesNotExist()
            runOnIdle { feedbackEnabled.value = false }
            onNodeWithTag("result").performMouseInput { enter(center) }
            onNodeWithContentDescription("התוצאה מועילה").assertDoesNotExist()
        }
}

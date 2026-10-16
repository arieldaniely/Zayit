package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.russhwolf.settings.PropertiesSettings
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult
import kotlinx.collections.immutable.persistentMapOf
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SearchFeedbackUiTest {
    @Test
    fun `grouped feedback targets the hovered hit without opening the book`() =
        runComposeUiTest {
            AppSettings.initialize(PropertiesSettings(Properties()))
            val primary = SearchResult(1, "ספר לבדיקה", 2, 0, "תוצאה ראשית", 0.9)
            val secondary = SearchResult(1, "ספר לבדיקה", 3, 1, "תוצאה משנית", 0.8)
            var opens = 0
            val votes = mutableListOf<Pair<Long, SearchFeedbackType>>()
            setContent {
                IntUiTheme(isDark = false) {
                    Box(Modifier.width(700.dp)) {
                        BookResultCard(
                            group = BookGroup(1, primary.bookTitle, primary, listOf(secondary), 2),
                            textSize = 16f,
                            lineHeight = 1.5f,
                            fontFamily = FontFamily.Default,
                            findQuery = null,
                            bookFontCode = "notoserifhebrew",
                            breadcrumbs = persistentMapOf(),
                            onRequestBreadcrumb = {},
                            onOpenResult = { opens++ },
                            onOpenPdf = null,
                            isExpanded = false,
                            expandedHits = null,
                            onToggleExpand = {},
                            onFeedback = { hit, type -> votes += hit.lineId to type },
                            feedbackKey = "search-1",
                        )
                    }
                }
            }
            onNodeWithContentDescription("התוצאה מועילה").assertDoesNotExist()
            onNodeWithText("תוצאה ראשית").performMouseInput { enter(center) }
            onNodeWithContentDescription("התוצאה מועילה").performMouseInput { click() }
            onNodeWithContentDescription("התוצאה מועילה").performMouseInput { click() }
            onNodeWithText("תוצאה משנית").performMouseInput { moveTo(center) }
            onNodeWithContentDescription("התוצאה אינה מועילה").performMouseInput { click() }
            assertEquals(listOf(2L to SearchFeedbackType.LIKE, 3L to SearchFeedbackType.DISLIKE), votes)
            assertEquals(0, opens)
            AppSettings.setSearchFeedbackEnabled(false)
            onNodeWithContentDescription("התוצאה מועילה").assertDoesNotExist()
            AppSettings.initialize(PropertiesSettings(Properties()))
        }

    @Test
    fun `feedback appears on hover and clicking it does not open the result`() =
        runComposeUiTest {
            AppSettings.initialize(PropertiesSettings(Properties()))
            var entries = 0
            val feedback = mutableListOf<SearchFeedbackType>()
            setContent {
                IntUiTheme(isDark = false) {
                    Box(Modifier.width(700.dp).testTag("result")) {
                        LegacySearchResultItem(
                            result = SearchResult(1, "ספר לבדיקה", 2, 0, "פסקה עם <b>תורה</b> ומצוות", 0.9),
                            textSize = 16f,
                            lineHeight = 1.5f,
                            fontFamily = FontFamily.Default,
                            findQuery = null,
                            onClick = { entries++ },
                            breadcrumbs = persistentMapOf(),
                            onRequestBreadcrumb = {},
                            bookFontCode = "notoserifhebrew",
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
            AppSettings.setSearchFeedbackEnabled(false)
            onNodeWithTag("result").performMouseInput { enter(center) }
            onNodeWithContentDescription("התוצאה מועילה").assertDoesNotExist()
            AppSettings.initialize(PropertiesSettings(Properties()))
        }
}

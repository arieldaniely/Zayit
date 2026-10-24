package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import io.github.kdroidfilter.seforimapp.framework.di.AppGraph
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Line
import io.github.kdroidfilter.seforimlibrary.core.models.SearchResult
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SearchPreviewUiTest {
    @Test
    fun previewShowsContextAndOpensOnlyOnExplicitAction() =
        runComposeUiTest {
            val repository = mockk<SeforimRepository>()
            val engine = mockk<SearchEngine>()
            val graph = mockk<AppGraph>()
            every { graph.repository } returns repository
            every { graph.searchEngine } returns engine
            every { engine.highlightRanges(any(), any()) } returns emptyList()
            coEvery { repository.getLine(10) } returns Line(10, 7, 4, "התאמה לחיפוש")
            coEvery { repository.getLines(7, 0, 8) } returns
                listOf(
                    Line(9, 7, 3, "הקשר לפני התוצאה"),
                    Line(10, 7, 4, "התאמה לחיפוש"),
                    Line(11, 7, 5, "הקשר אחרי התוצאה"),
                )
            var opened = 0
            setContent {
                CompositionLocalProvider(LocalAppGraph provides graph, LocalLayoutDirection provides LayoutDirection.Rtl) {
                    IntUiTheme(isDark = false) {
                        var visible by remember { mutableStateOf(false) }
                        DefaultButton(onClick = { visible = true }) { Text("preview") }
                        if (visible) {
                            SearchResultPreview(
                                SearchResult(7, "ספר לבדיקה", 10, 4, "התאמה", 1.0),
                                "חיפוש",
                                "ספר לבדיקה · פרק א",
                                18f,
                                FontFamily.Default,
                                {},
                                { opened++ },
                            )
                        }
                    }
                }
            }
            onNodeWithText("preview").performClick()
            waitUntil(timeoutMillis = 30_000) {
                onAllNodesWithText("הקשר לפני התוצאה", substring = true, useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            onNodeWithText("הקשר אחרי התוצאה", substring = true, useUnmergedTree = true).assertExists()
            assertEquals(0, opened)
            onNodeWithText("פתח בספר").performClick()
            assertEquals(1, opened)
        }
}

package io.github.kdroidfilter.seforimapp.features.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.HomeSearchCallbacks
import io.github.kdroidfilter.seforimapp.features.bookcontent.ui.panels.bookcontent.views.UnifiedSearchBar
import io.github.kdroidfilter.seforimapp.framework.di.AppGraph
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.dao.repository.AuthorLookupEntry
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class UnifiedHomeSearchUiTest {
    @Test
    fun oneFieldOffersAuthorsBooksAndExplicitTextSearch() =
        runComposeUiTest {
            val repository = mockk<SeforimRepository>()
            coEvery { repository.getAuthorsForLookup() } returns listOf(AuthorLookupEntry(2, "הרמב״ם", emptyList(), 12))
            val graph = mockk<AppGraph>()
            every { graph.repository } returns repository
            var submitted = ""
            var picked = 0L
            var opened = false
            val actions =
                HomeSearchCallbacks(
                    onReferenceQueryChanged = {},
                    onTocQueryChanged = {},
                    onFilterChange = {},
                    onGlobalExtendedChange = {},
                    onModeChange = {},
                    onSubmitTextSearch = { submitted = it },
                    onOpenReference = { opened = true },
                    onPickCategory = {},
                    onPickBook = { book, _ -> picked = book.id },
                    onOpenBook = { book, _ ->
                        picked = book.id
                        opened = true
                    },
                    onPickToc = {},
                )
            setContent {
                CompositionLocalProvider(LocalAppGraph provides graph, LocalLayoutDirection provides LayoutDirection.Rtl) {
                    IntUiTheme(isDark = false) {
                        Box(Modifier.width(900.dp).height(560.dp)) {
                            UnifiedSearchBar(
                                SearchHomeUiState(
                                    suggestionsVisible = true,
                                    authorSuggestions =
                                        listOf(
                                            io.github.kdroidfilter.seforimapp.framework.search.LuceneLookupSearchService.AuthorHit(
                                                2,
                                                "הרמב״ם",
                                                12,
                                            ),
                                        ),
                                    bookSuggestions =
                                        listOf(
                                            BookSuggestionDto(Book(7, 1, 1, "משנה תורה לרמב״ם"), listOf("משנה תורה לרמב״ם")),
                                        ),
                                ),
                                actions,
                            )
                        }
                    }
                }
            }
            onNode(hasSetTextAction()).performTextInput("רמב")
            waitUntil(timeoutMillis = 10_000) { onAllNodesWithText("הרמב״ם").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("משנה תורה לרמב״ם").assertExists()
            onNode(hasSetTextAction()).performKeyInput {
                withKeyDown(Key.CtrlLeft) { pressKey(Key.Enter) }
            }
            waitForIdle()
            assertEquals("רמב", submitted)
            onNode(hasSetTextAction()).performClick()
            onNodeWithText("משנה תורה לרמב״ם").performClick()
            waitForIdle()
            assertEquals(7L, picked)
            assertEquals(true, opened)
        }
}

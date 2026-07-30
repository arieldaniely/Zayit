package io.github.kdroidfilter.seforimapp.framework.session

import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.core.settings.AppSettings
import io.github.kdroidfilter.seforimapp.features.search.SearchFilter
import io.github.kdroidfilter.seforimapp.framework.di.AppGraph
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/** Applies transient UI state that is intentionally absent from a persisted desktop snapshot. */
object ScreenshotScenarioReplay {
    private const val EXPECTED_TEXT_SIZE = 22f
    private const val EXPECTED_COMMENTATORS_PER_PAGE = 2

    suspend fun prepare(
        appGraph: AppGraph,
        scenario: String,
    ) {
        AppSettings.setTextSize(EXPECTED_TEXT_SIZE)
        AppSettings.setMaxCommentatorsPerPage(EXPECTED_COMMENTATORS_PER_PAGE)
        AppSettings.setCloseBookTreeOnNewBookSelected(true)
        require(AppSettings.getTextSize() == EXPECTED_TEXT_SIZE)
        require(AppSettings.getMaxCommentatorsPerPage() == EXPECTED_COMMENTATORS_PER_PAGE)

        val window = requireNotNull(appGraph.desktopManager.focusedWindow()) { "No focused screenshot window" }
        val tabsState = window.tabsViewModel.state.value
        val selectedDestination =
            requireNotNull(tabsState.tabs.getOrNull(tabsState.selectedTabIndex)?.destination) {
                "The screenshot fixture has no selected tab"
            }
        tabsState.tabs.forEach { tab ->
            AppSettings.closeFindBar(tab.destination.tabId)
            AppSettings.setFindQuery(tab.destination.tabId, "")
            AppSettings.setFindSmartMode(tab.destination.tabId, false)
        }

        val searchHome = window.searchHomeViewModel
        searchHome.resetForScreenshotReplay()
        ScreenshotAutomationState.showHomeSearch()

        when (scenario.uppercase()) {
            "BOOK-SEARCH" -> {
                val query = "\u05E9\u05D5\u05E2 \u05D9\u05D5\"\u05D3"
                searchHome.onFilterChange(SearchFilter.REFERENCE)
                ScreenshotAutomationState.showHomeSearch(referenceQuery = query)
                searchHome.onReferenceQueryChanged(query)
                withTimeout(120_000) {
                    searchHome.uiState.first { state ->
                        state.selectedFilter == SearchFilter.REFERENCE &&
                            state.suggestionsVisible &&
                            !state.isReferenceLoading &&
                            state.bookSuggestions.isNotEmpty()
                    }
                }
            }

            "TOC-BOOK-SEARCH" -> {
                val books = appGraph.repository.findBooksByTitleLikeCore("%\u05E9\u05D5\u05DC\u05D7\u05DF \u05E2\u05E8\u05D5\u05DA%", limit = 120)
                val book =
                    books.firstOrNull { candidate -> candidate.id == 383L }
                        ?: books.firstOrNull { candidate -> candidate.title == "\u05E9\u05D5\u05DC\u05D7\u05DF \u05E2\u05E8\u05D5\u05DA, \u05D9\u05D5\u05E8\u05D4 \u05D3\u05E2\u05D4" }
                        ?: error("Could not find the recorded Yoreh Deah book")
                val query = ""
                searchHome.onFilterChange(SearchFilter.REFERENCE)
                searchHome.onPickBook(book)
                searchHome.uiState.first { state -> state.selectedScopeBook?.id == book.id && !state.isTocLoading }
                ScreenshotAutomationState.showHomeSearch(tocQuery = query)
                searchHome.onTocQueryChanged(query)
                withTimeout(120_000) {
                    searchHome.uiState.first { state ->
                        state.tocSuggestionsVisible && !state.isTocLoading && state.tocSuggestions.isNotEmpty()
                    }
                }
            }

            "INBOOK-SEARCH" -> {
                require(selectedDestination is TabsDestination.BookContent)
                AppSettings.setFindQuery(selectedDestination.tabId, "\u05E9\u05DE\u05E2")
                AppSettings.openFindBar(selectedDestination.tabId)
            }
        }
        delay(750)
    }
}

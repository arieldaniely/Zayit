package io.github.kdroidfilter.seforimapp.features.search

import androidx.lifecycle.viewModelScope
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SearchHomeSelectionTest {
    private lateinit var viewModel: SearchHomeViewModel
    private lateinit var store: TabPersistedStateStore
    private val book = Book(id = 1L, categoryId = 1L, sourceId = 1L, title = "ברכות", order = 1f)
    private val toc = TocEntry(id = 2L, bookId = 1L, text = "דף ב עמוד ב", level = 1, lineId = 42L)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        store = TabPersistedStateStore()
        viewModel = SearchHomeViewModel(store, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
    }

    @AfterTest
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `combined scope selection produces only a search event and preserves toc filter`() =
        runTest {
            viewModel.onPickBook(book)
            viewModel.onPickToc(toc)
            assertNull(store.get("tab"))
            viewModel.submitSearch("תפילה", "tab")
            assertEquals(SearchHomeNavigationEvent.NavigateToSearch("תפילה", "tab"), viewModel.navigationEvents.first())
            assertEquals("toc", store.get("tab")?.search?.datasetScope)
            assertEquals(toc.id, store.get("tab")?.search?.fetchTocId)
            assertEquals(book.id, store.get("tab")?.search?.fetchBookId)
        }

    @Test
    fun `explicit opening uses selected combined anchor for text and PDF`() =
        runTest {
            for (pdf in listOf(false, true)) {
                viewModel.onPickBook(book, pdf)
                viewModel.onPickToc(toc)
                viewModel.openSelectedReferenceInCurrentTab("tab")
                val expected =
                    if (pdf) {
                        SearchHomeNavigationEvent.NavigateToPdfContent(book.id, "tab", toc.lineId)
                    } else {
                        SearchHomeNavigationEvent.NavigateToBookContent(book.id, "tab", toc.lineId)
                    }
                assertEquals(expected, viewModel.navigationEvents.first())
            }
        }

    @Test
    fun `editing location clears old target and rejects toc from another book`() {
        viewModel.onPickBook(book)
        viewModel.onPickToc(toc)
        viewModel.onTocQueryChanged("ג")
        assertNull(viewModel.uiState.value.selectedScopeToc)
        viewModel.onPickToc(toc.copy(bookId = 99L))
        assertNull(viewModel.uiState.value.selectedScopeToc)
    }
}

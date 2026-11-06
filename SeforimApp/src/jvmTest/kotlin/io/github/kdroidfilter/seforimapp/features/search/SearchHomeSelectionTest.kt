package io.github.kdroidfilter.seforimapp.features.search

import androidx.lifecycle.viewModelScope
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.github.kdroidfilter.seforimlibrary.core.models.Category
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
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
    @Test
    fun `favorite folder selection persists an explicit search scope`() =
        runTest {
            viewModel.onPickFavorites(FavoritesSuggestionDto(7L, "My folder"))
            viewModel.submitSearch("תפילה", "tab")
            assertEquals("favorites", store.get("tab")?.search?.datasetScope)
            assertEquals(7L, store.get("tab")?.search?.scopeFavoriteFolderId)
            assertEquals(true, store.get("tab")?.search?.globalExtended)
        }

    private lateinit var viewModel: SearchHomeViewModel
    private lateinit var store: TabPersistedStateStore
    private lateinit var repository: SeforimRepository
    private val book = Book(id = 1L, categoryId = 1L, sourceId = 1L, title = "ברכות", order = 1f)
    private val toc = TocEntry(id = 2L, bookId = 1L, text = "דף ב עמוד ב", level = 1, lineId = 42L)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        mockkObject(SemanticAssetsManager)
        every { SemanticAssetsManager.validatedReady() } returns false
        store = TabPersistedStateStore()
        repository = mockk(relaxed = true)
        viewModel = SearchHomeViewModel(store, repository, mockk(relaxed = true), mockk(relaxed = true))
    }

    @AfterTest
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        unmockkObject(SemanticAssetsManager)
        Dispatchers.resetMain()
    }

    @Test
    fun `category suggestions stay inside the selected tree while typing`() =
        runTest {
            val second = book.copy(id = 2L, title = "שבת", categoryId = 2L)
            coEvery { repository.getCategory(any()) } returns null
            coEvery { repository.getBooksUnderCategoryTree(9L) } returns listOf(book, second)
            viewModel.onPickCategory(Category(id = 9L, title = "תלמוד"))
            val initial = viewModel.uiState.first { it.bookSuggestions.size == 2 }
            assertEquals(setOf(1L, 2L), initial.bookSuggestions.map { it.book.id }.toSet())
            viewModel.onReferenceQueryChanged("בר")
            val filtered = viewModel.uiState.first { it.bookSuggestions.size == 1 }
            assertEquals(
                book.id,
                filtered.bookSuggestions
                    .single()
                    .book.id,
            )
            assertEquals(emptyList(), filtered.authorSuggestions)
            assertEquals(emptyList(), filtered.categorySuggestions)
            assertEquals(emptyList(), filtered.jumpSuggestions)
            coVerify(exactly = 0) { repository.findBooksByTitleLikeCore(any(), any()) }
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

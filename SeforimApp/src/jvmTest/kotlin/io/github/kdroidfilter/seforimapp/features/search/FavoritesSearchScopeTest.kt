package io.github.kdroidfilter.seforimapp.features.search

import io.github.kdroidfilter.seforim.tabs.SearchScope
import io.github.kdroidfilter.seforimapp.core.favorites.FavoriteEntry
import io.github.kdroidfilter.seforimapp.core.favorites.FavoritesStore
import io.github.kdroidfilter.seforimapp.framework.session.SearchPersistedState
import io.github.kdroidfilter.seforimlibrary.core.models.LineTocMapping
import io.github.kdroidfilter.seforimlibrary.core.models.TocEntry
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FavoritesSearchScopeTest {
    @Test
    fun includesBookLinesAndDescendantSectionsWithoutDuplicates() =
        runTest {
            val store = mockk<FavoritesStore>()
            val repository = mockk<SeforimRepository>()
            coEvery { store.query() } returns
                listOf(
                    FavoriteEntry("a", 10L, "Book", 1L, 0L),
                    FavoriteEntry("b", 20L, "Chapter", 1L, 0L, tocEntryId = 2L),
                    FavoriteEntry("c", 30L, "Other folder", 2L, 0L),
                )
            coEvery { repository.getLineIdsByBookId(10L) } returns mapOf(0 to 11L, 1 to 12L)
            coEvery { repository.getBookToc(20L) } returns
                listOf(
                    TocEntry(id = 2L, bookId = 20L, text = "Parent", level = 1),
                    TocEntry(id = 3L, bookId = 20L, parentId = 2L, text = "Child", level = 2),
                )
            coEvery { repository.getLineTocMappingsForBook(20L) } returns
                listOf(LineTocMapping(21L, 2L), LineTocMapping(22L, 3L))
            assertEquals(setOf(11L, 12L, 21L, 22L), favoriteScopeLines(store, repository, 1L))
            assertEquals(emptySet(), favoriteScopeLines(store, repository, 99L))
        }

    @Test
    fun emptyScopesNeverSearchGloballyAndFurtherFiltersIntersect() {
        val delegate = mockk<SearchEngine>(relaxed = true)
        var scope = emptySet<Long>()
        val engine = LineScopeSearchEngine(delegate) { scope }
        assertNull(engine.openSession("query"))
        assertNull(engine.computeFacets("query"))
        verify(exactly = 0) { delegate.openSession(any(), any(), any(), any(), any(), any(), any(), any()) }
        scope = setOf(1L, 2L)
        engine.openSession("query", lineIds = setOf(2L, 3L), baseBookOnly = true)
        verify { delegate.openSession("query", 5, null, null, null, setOf(2L), false, any()) }
    }

    @Test
    fun favoritesScopeSurvivesRestoredBookFilters() {
        val state = SearchPersistedState().withScope(SearchScope.Favorites(7L)).copy(filterBookId = 42L)
        assertEquals(SearchScope.Favorites(7L), state.scope)
    }
}

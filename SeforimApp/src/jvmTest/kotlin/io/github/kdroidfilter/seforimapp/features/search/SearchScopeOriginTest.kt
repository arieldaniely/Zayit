package io.github.kdroidfilter.seforimapp.features.search

import io.github.kdroidfilter.seforim.tabs.SearchScope
import io.github.kdroidfilter.seforimapp.framework.session.SearchPersistedState
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchScopeOriginTest {
    @Test
    fun authorAndFavoriteOriginsRemainVisibleWhenFilteredByBook() {
        val author = SearchPersistedState().withScope(SearchScope.Author(7L)).copy(filterBookId = 42L)
        assertEquals(SearchScope.Author(7L), author.originScope)
        val favorites = SearchPersistedState().withScope(SearchScope.Favorites(8L)).copy(filterBookId = 42L)
        assertEquals(SearchScope.Favorites(8L), favorites.originScope)
    }

    @Test
    fun ordinaryBookScopesKeepTheirCatalogBreadcrumb() {
        val book = SearchPersistedState().withScope(SearchScope.Book(42L))
        assertEquals(SearchScope.Book(42L), book.originScope)
    }
}

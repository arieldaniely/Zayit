package io.github.kdroidfilter.seforimapp.features.search

import io.github.kdroidfilter.seforimapp.core.catalog.LibraryVisibility
import io.github.kdroidfilter.seforimapp.core.catalog.LibraryVisibilitySnapshot
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchFacets
import io.github.kdroidfilter.seforimlibrary.search.SearchMode
import io.github.kdroidfilter.seforimlibrary.search.SearchSession

/** Applies visibility before ranking, pagination and facets, for both the bundled and personal indexes. */
internal class VisibilitySearchEngine(
    private val delegate: SearchEngine,
    private val visibility: LibraryVisibility,
) : SearchEngine by delegate {
    private fun LibraryVisibilitySnapshot.visibleIds(
        bookIds: Collection<Long>?,
        bookFilter: Long?,
    ): Collection<Long>? {
        val allowed = allowedBookIds ?: return bookIds?.filter(::isBookVisible)
        val requested = bookIds ?: bookFilter?.let(::listOf)
        return requested?.filter(allowed::contains) ?: allowed
    }

    override fun openSession(
        query: String,
        near: Int,
        bookFilter: Long?,
        categoryFilter: Long?,
        bookIds: Collection<Long>?,
        lineIds: Collection<Long>?,
        baseBookOnly: Boolean,
        mode: SearchMode,
    ): SearchSession? {
        val snapshot = visibility.snapshot()
        if (bookFilter != null && !snapshot.isBookVisible(bookFilter)) return null
        if (categoryFilter != null && !snapshot.isCategoryVisible(categoryFilter)) return null
        val ids = snapshot.visibleIds(bookIds, bookFilter)
        if (ids?.isEmpty() == true) return null
        return delegate.openSession(query, near, bookFilter, categoryFilter, ids, lineIds, baseBookOnly, mode)
    }

    override fun computeFacets(
        query: String,
        near: Int,
        bookFilter: Long?,
        categoryFilter: Long?,
        bookIds: Collection<Long>?,
        lineIds: Collection<Long>?,
        baseBookOnly: Boolean,
        mode: SearchMode,
    ): SearchFacets? {
        val snapshot = visibility.snapshot()
        if (bookFilter != null && !snapshot.isBookVisible(bookFilter)) return null
        if (categoryFilter != null && !snapshot.isCategoryVisible(categoryFilter)) return null
        val ids = snapshot.visibleIds(bookIds, bookFilter)
        if (ids?.isEmpty() == true) return null
        return delegate.computeFacets(query, near, bookFilter, categoryFilter, ids, lineIds, baseBookOnly, mode)
    }

    override fun searchBooksByTitlePrefix(
        query: String,
        limit: Int,
    ): List<Long> {
        val snapshot = visibility.snapshot()
        return delegate.searchBooksByTitlePrefix(query, limit).filter(snapshot::isBookVisible)
    }
}

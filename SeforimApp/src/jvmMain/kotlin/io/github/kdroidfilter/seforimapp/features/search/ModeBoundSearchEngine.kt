package io.github.kdroidfilter.seforimapp.features.search

import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchFacets
import io.github.kdroidfilter.seforimlibrary.search.SearchMode
import io.github.kdroidfilter.seforimlibrary.search.SearchSession

/** Keeps search mode scoped to a search tab, including filtered searches and facet queries. */
internal class ModeBoundSearchEngine(
    private val delegate: SearchEngine,
    private val selectedMode: () -> SearchMode,
) : SearchEngine by delegate {
    override fun openSession(
        query: String,
        near: Int,
        bookFilter: Long?,
        categoryFilter: Long?,
        bookIds: Collection<Long>?,
        lineIds: Collection<Long>?,
        baseBookOnly: Boolean,
        mode: SearchMode,
    ): SearchSession? = delegate.openSession(
        query, near, bookFilter, categoryFilter, bookIds, lineIds, baseBookOnly, selectedMode(),
    )

    override fun computeFacets(
        query: String,
        near: Int,
        bookFilter: Long?,
        categoryFilter: Long?,
        bookIds: Collection<Long>?,
        lineIds: Collection<Long>?,
        baseBookOnly: Boolean,
        mode: SearchMode,
    ): SearchFacets? = delegate.computeFacets(
        query, near, bookFilter, categoryFilter, bookIds, lineIds, baseBookOnly, selectedMode(),
    )
}

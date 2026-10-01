package io.github.kdroidfilter.seforimapp.features.search

import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchFacets
import io.github.kdroidfilter.seforimlibrary.search.SearchMode
import io.github.kdroidfilter.seforimlibrary.search.SearchPage
import io.github.kdroidfilter.seforimlibrary.search.SearchSession

/** Keeps search mode scoped to a search tab, including filtered searches and facet queries. */
internal class ModeBoundSearchEngine(
    private val delegate: SearchEngine,
    private val selectedMode: () -> SearchMode,
    private val onFallback: () -> Unit = {},
    private val semanticReady: () -> Boolean = SemanticAssetsManager::validatedReady,
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
    ): SearchSession? {
        val requested = selectedMode()
        val effective =
            if (requested == SearchMode.SMART &&
                (!semanticReady() || categoryFilter != null || lineIds != null)
            ) {
                onFallback()
                SearchMode.FLEXIBLE
            } else {
                requested
            }
        val session =
            delegate.openSession(
                query,
                near,
                bookFilter,
                categoryFilter,
                bookIds,
                lineIds,
                baseBookOnly,
                effective,
            ) ?: return null
        return object : SearchSession by session {
            override suspend fun nextPage(limit: Int): SearchPage? {
                val page = session.nextPage(limit)
                if (effective == SearchMode.SMART &&
                    selectedMode() == SearchMode.SMART &&
                    session.effectiveMode == SearchMode.FLEXIBLE
                ) {
                    onFallback()
                }
                return page
            }
        }
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
    ): SearchFacets? =
        delegate.computeFacets(
            query,
            near,
            bookFilter,
            categoryFilter,
            bookIds,
            lineIds,
            baseBookOnly,
            selectedMode(),
        )
}

package io.github.kdroidfilter.seforimapp.features.search

import io.github.kdroidfilter.seforimapp.core.favorites.FavoritesStore
import io.github.kdroidfilter.seforimapp.features.search.domain.SearchTocUseCase
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchFacets
import io.github.kdroidfilter.seforimlibrary.search.SearchMode
import io.github.kdroidfilter.seforimlibrary.search.SearchSession
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Includes whole bookmarked books and the subtrees of bookmarked sections, once per line. */
internal suspend fun favoriteScopeLines(
    store: FavoritesStore,
    repository: SeforimRepository,
    folderId: Long?,
): Set<Long> {
    val entries = store.query().filter { folderId == null || folderId in it.folderIds }
    val toc = SearchTocUseCase(repository)
    return buildSet {
        entries.groupBy { it.bookId }.forEach { (bookId, positions) ->
            currentCoroutineContext().ensureActive()
            if (positions.any { it.tocEntryId == null }) {
                addAll(repository.getLineIdsByBookId(bookId).values)
            } else {
                positions.mapNotNull { it.tocEntryId }.distinct().forEach { id ->
                    addAll(toc.collectLineIdsForTocSubtree(id, bookId))
                }
            }
        }
    }
}

/** Applies the same explicit line scope to every page and facet query, including further view filters. */
internal class LineScopeSearchEngine(
    private val delegate: SearchEngine,
    private val allowedLines: () -> Set<Long>?,
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
        val scope = allowedLines()
        val lines = scope?.let { if (lineIds == null) it else it.intersect(lineIds.toSet()) } ?: lineIds
        if (lines?.isEmpty() == true) return null
        return delegate.openSession(
            query,
            near,
            bookFilter,
            categoryFilter,
            bookIds,
            lines,
            baseBookOnly && scope == null,
            mode,
        )
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
        val scope = allowedLines()
        val lines = scope?.let { if (lineIds == null) it else it.intersect(lineIds.toSet()) } ?: lineIds
        if (lines?.isEmpty() == true) return null
        return delegate.computeFacets(
            query,
            near,
            bookFilter,
            categoryFilter,
            bookIds,
            lines,
            baseBookOnly && scope == null,
            mode,
        )
    }
}

package io.github.kdroidfilter.seforimapp.features.search

import io.github.kdroidfilter.seforimapp.framework.session.SearchPersistedState

internal val SearchPersistedState.hasExplicitScope: Boolean
    get() =
        datasetScope != "global" ||
            scopeAuthorId != 0L ||
            filterCategoryId != 0L ||
            filterBookId != 0L ||
            filterTocId != 0L ||
            fetchCategoryId != 0L ||
            fetchBookId != 0L ||
            fetchTocId != 0L ||
            selectedCategoryIds.isNotEmpty() ||
            selectedBookIds.isNotEmpty() ||
            selectedTocIds.isNotEmpty()

/** Explicit scopes include all their books, even when the previous global search used only base books. */
internal fun SearchPersistedState.withScopeCoverage(): SearchPersistedState =
    if (hasExplicitScope && !globalExtended) copy(globalExtended = true, snapshot = null) else this

internal fun scopedBaseBooksOnly(
    requested: Boolean,
    bookFilter: Long?,
    categoryFilter: Long?,
    bookIds: Collection<Long>?,
    lineIds: Collection<Long>?,
): Boolean = requested && bookFilter == null && categoryFilter == null && bookIds == null && lineIds == null

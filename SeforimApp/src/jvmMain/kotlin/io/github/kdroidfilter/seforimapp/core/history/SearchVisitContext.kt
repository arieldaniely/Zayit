package io.github.kdroidfilter.seforimapp.core.history

import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.framework.session.SearchPersistedState
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import kotlinx.serialization.Serializable

/** Search settings only: result snapshots and scroll positions do not belong in visit history. */
@Serializable
data class SearchVisitContext(
    val mode: String,
    val globalExtended: Boolean,
    val categoryId: Long = 0,
    val bookId: Long = 0,
    val tocId: Long = 0,
    val scopeTitle: String = "",
) {
    fun persistedState(query: String) =
        SearchPersistedState(
            query = query,
            mode = mode,
            globalExtended = globalExtended,
            datasetScope =
                when {
                    tocId != 0L -> "toc"
                    bookId != 0L -> "book"
                    categoryId != 0L -> "category"
                    else -> "global"
                },
            filterCategoryId = categoryId,
            filterBookId = bookId,
            filterTocId = tocId,
            fetchCategoryId = categoryId,
            fetchBookId = bookId,
            fetchTocId = tocId,
        )
}

/** Seed the new tab before navigation so both history entry points restore the same settings. */
fun VisitEntry.searchDestination(
    tabId: String,
    store: TabPersistedStateStore,
): TabsDestination.Search? {
    val query = searchQuery ?: return null
    searchContext?.let { context ->
        store.update(tabId) { it.copy(search = context.persistedState(query)) }
    }
    return TabsDestination.Search(searchQuery = query, tabId = tabId)
}

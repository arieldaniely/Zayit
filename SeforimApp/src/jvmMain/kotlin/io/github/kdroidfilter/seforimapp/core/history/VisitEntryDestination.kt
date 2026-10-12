package io.github.kdroidfilter.seforimapp.core.history

import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import java.util.UUID

/** Where a history entry reopens, in a new tab: the book at its line, or the search in the scope it ran in. */
fun VisitEntry.destination(store: TabPersistedStateStore? = null): TabsDestination? {
    val tabId = UUID.randomUUID().toString()
    if (kind == VisitKind.SEARCH && store != null) return searchDestination(tabId, store)
    return when (kind) {
        VisitKind.BOOK -> bookId?.let { TabsDestination.BookContent(bookId = it, tabId = tabId, lineId = lineId) }
        VisitKind.SEARCH ->
            searchQuery?.let {
                TabsDestination.Search(
                    searchQuery = it,
                    tabId = tabId,
                    scope = searchScope,
                    globalExtended = searchGlobalExtended,
                    mode = searchMode.takeIf { mode -> mode.isNotBlank() },
                )
            }
    }
}

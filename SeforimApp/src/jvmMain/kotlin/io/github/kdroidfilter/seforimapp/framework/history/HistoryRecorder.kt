package io.github.kdroidfilter.seforimapp.framework.history

import io.github.kdroidfilter.seforim.tabs.TabItem
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.framework.di.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun recordTabToHistory(
    tab: TabItem,
    appGraph: AppGraph,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {
    val dest = tab.destination
    if (dest is TabsDestination.Home) return

    scope.launch {
        val desktopManager = appGraph.desktopManager
        val historyManager = appGraph.historyManager
        val tabStore = appGraph.tabPersistedStateStore
        val repository = appGraph.repository

        val activeDesktopName =
            desktopManager.desktops.value
                .find { it.id == desktopManager.activeDesktopId.value }
                ?.name ?: "\u05DE\u05E8\u05D7\u05D1 \u05D0׳"

        val tabState = tabStore.get(dest.tabId)

        when (dest) {
            is TabsDestination.Search -> {
                val query = dest.searchQuery.ifBlank { tabState?.search?.query.orEmpty() }
                if (query.isNotBlank()) {
                    val scopeText = tabState?.search?.datasetScope ?: "global"
                    historyManager.addEntry(
                        HistoryEntry(
                            desktopName = activeDesktopName,
                            type = HistoryType.SEARCH,
                            searchQuery = query,
                            searchScope = scopeText,
                        ),
                    )
                }
            }

            is TabsDestination.BookContent -> {
                val bookId =
                    dest.bookId.takeIf { it != 0L && it != -1L }
                        ?: tabState?.bookContent?.selectedBookId?.takeIf { it != 0L && it != -1L }
                if (bookId != null) {
                    val book = repository.getBookCore(bookId)
                    val bookTitle = book?.title ?: tab.title.ifBlank { "\u05E1\u05E4\u05E8 $bookId" }
                    val lineId = dest.lineId ?: tabState?.bookContent?.primarySelectedLineId?.takeIf { it != -1L }
                    val lineLabel =
                        if (lineId != null && lineId != -1L) {
                            repository.getLineByIdCore(lineId)?.let { line ->
                                "\u05E9\u05D5\u05E8\u05D4 ${line.lineIndex}"
                            }
                        } else {
                            null
                        }

                    historyManager.addEntry(
                        HistoryEntry(
                            desktopName = activeDesktopName,
                            type = HistoryType.BOOK,
                            bookId = bookId,
                            bookTitle = bookTitle,
                            lineId = lineId,
                            lineDisplayLabel = lineLabel,
                        ),
                    )
                }
            }

            is TabsDestination.PdfContent -> {
                val bookId = dest.bookId.takeIf { it != 0L && it != -1L }
                if (bookId != null) {
                    val book = repository.getBookCore(bookId)
                    val bookTitle = book?.title ?: tab.title.ifBlank { "PDF $bookId" }
                    val lineId = dest.lineId

                    historyManager.addEntry(
                        HistoryEntry(
                            desktopName = activeDesktopName,
                            type = HistoryType.BOOK,
                            bookId = bookId,
                            bookTitle = "$bookTitle (PDF)",
                            lineId = lineId,
                        ),
                    )
                }
            }

            else -> {}
        }
    }
}

package io.github.kdroidfilter.seforimapp.framework.history

import io.github.kdroidfilter.seforim.tabs.TabItem
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforimapp.framework.di.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import io.github.santimattius.structured.annotations.StructuredScope

fun recordTabToHistory(
    tab: TabItem,
    appGraph: AppGraph,
    @StructuredScope scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
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
                            val tocId = runCatching { repository.getTocEntryIdForLine(lineId) }.getOrNull()
                            val tocPath =
                                if (tocId != null) {
                                    val path = mutableListOf<io.github.kdroidfilter.seforimlibrary.core.models.TocEntry>()
                                    var current: Long? = tocId
                                    var guard = 0
                                    while (current != null && guard++ < 200) {
                                        val entry = repository.getTocEntry(current)
                                        if (entry != null) {
                                            path.add(0, entry)
                                            current = entry.parentId
                                        } else {
                                            break
                                        }
                                    }
                                    if (path.firstOrNull()?.text == bookTitle) path.drop(1) else path
                                } else {
                                    emptyList()
                                }
                            val line = repository.getLine(lineId)
                            val heRef = line?.heRef?.takeIf { it.isNotBlank() }
                            val tocText = tocPath.joinToString(" > ") { it.text }.takeIf { it.isNotBlank() }

                            when {
                                tocText != null && heRef != null && !tocText.endsWith(heRef) -> "$tocText · $heRef"
                                tocText != null -> tocText
                                heRef != null -> heRef
                                line != null -> "שורה ${line.lineIndex}"
                                else -> null
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

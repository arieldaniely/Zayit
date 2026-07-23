package io.github.kdroidfilter.seforimapp.framework.history

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class HistoryType {
    BOOK,
    SEARCH,
}

@Serializable
data class HistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val desktopName: String = "",
    val type: HistoryType,
    val bookId: Long? = null,
    val bookTitle: String? = null,
    val lineId: Long? = null,
    val lineDisplayLabel: String? = null,
    val searchQuery: String? = null,
    val searchScope: String? = null,
)

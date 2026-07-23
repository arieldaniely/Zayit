@file:OptIn(ExperimentalSerializationApi::class)

package io.github.kdroidfilter.seforimapp.framework.history

import io.github.kdroidfilter.seforimapp.framework.portable.PortablePaths
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.databasesDir
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.File

class HistoryManager {
    private val proto = ProtoBuf

    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    init {
        loadHistory()
    }

    private fun historyDir(): File = File(portableDatabasesDirPath(), "history").apply { mkdirs() }

    private fun historyFile(): File = File(historyDir(), "history_v1.pb")

    private fun loadHistory() {
        val file = historyFile()
        if (!file.exists()) return
        runCatching {
            val bytes = file.readBytes()
            val loaded = proto.decodeFromByteArray(HistoryListWrapper.serializer(), bytes).entries
            _entries.value = loaded.sortedByDescending { it.timestamp }
        }
    }

    private fun saveHistory() {
        runCatching {
            val wrapper = HistoryListWrapper(_entries.value.take(MAX_HISTORY_ENTRIES))
            val bytes = proto.encodeToByteArray(HistoryListWrapper.serializer(), wrapper)
            historyFile().writeBytes(bytes)
        }
    }

    fun addEntry(entry: HistoryEntry) {
        val current = _entries.value
        val first = current.firstOrNull()
        if (first != null && isDuplicate(first, entry)) {
            val updated = current.toMutableList()
            updated[0] = first.copy(timestamp = System.currentTimeMillis())
            _entries.value = updated
            saveHistory()
            return
        }

        val newEntries = (listOf(entry) + current).take(MAX_HISTORY_ENTRIES)
        _entries.value = newEntries
        saveHistory()
    }

    private fun isDuplicate(
        a: HistoryEntry,
        b: HistoryEntry,
    ): Boolean {
        if (a.type != b.type) return false
        return when (a.type) {
            HistoryType.BOOK -> a.bookId == b.bookId && a.lineId == b.lineId
            HistoryType.SEARCH -> a.searchQuery.orEmpty().trim().equals(b.searchQuery.orEmpty().trim(), ignoreCase = true)
        }
    }

    fun deleteEntry(id: String) {
        _entries.value = _entries.value.filter { it.id != id }
        saveHistory()
    }

    fun clearAll() {
        _entries.value = emptyList()
        saveHistory()
    }

    companion object {
        const val MAX_HISTORY_ENTRIES = 500
    }
}

@Serializable
private data class HistoryListWrapper(
    val entries: List<HistoryEntry> = emptyList(),
)

private fun portableDatabasesDirPath(): String =
    if (PortablePaths.isPortable) PortablePaths.databasesDir.absolutePath else FileKit.databasesDir.path

package io.github.kdroidfilter.seforimapp.framework.history

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryManagerTest {

    @Test
    fun testAddAndClearHistory() {
        val manager = HistoryManager()
        manager.clearAll()
        assertEquals(0, manager.entries.value.size)

        val entry1 = HistoryEntry(
            desktopName = "מרחב א'",
            type = HistoryType.BOOK,
            bookId = 100L,
            bookTitle = "ברכות",
            lineId = 5L,
            lineDisplayLabel = "שורה 5"
        )
        manager.addEntry(entry1)

        assertEquals(1, manager.entries.value.size)
        assertEquals("ברכות", manager.entries.value.first().bookTitle)

        val entry2 = HistoryEntry(
            desktopName = "מרחב א'",
            type = HistoryType.SEARCH,
            searchQuery = "תורה אור",
            searchScope = "global"
        )
        manager.addEntry(entry2)

        assertEquals(2, manager.entries.value.size)
        assertEquals("תורה אור", manager.entries.value.first().searchQuery)

        // Test delete single entry
        manager.deleteEntry(entry2.id)
        assertEquals(1, manager.entries.value.size)
        assertEquals("ברכות", manager.entries.value.first().bookTitle)

        // Cleanup
        manager.clearAll()
        assertEquals(0, manager.entries.value.size)
    }

    @Test
    fun testDuplicateEntryPruning() {
        val manager = HistoryManager()
        manager.clearAll()

        val entry1 = HistoryEntry(
            desktopName = "מרחב א'",
            type = HistoryType.SEARCH,
            searchQuery = "שבת",
            searchScope = "global"
        )
        manager.addEntry(entry1)
        assertEquals(1, manager.entries.value.size)

        // Adding exact duplicate search query should not increase size, just update timestamp
        val entry2 = HistoryEntry(
            desktopName = "מרחב א'",
            type = HistoryType.SEARCH,
            searchQuery = "שבת",
            searchScope = "global"
        )
        manager.addEntry(entry2)
        assertEquals(1, manager.entries.value.size)

        manager.clearAll()
    }
}

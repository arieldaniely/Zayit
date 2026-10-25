@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.kdroidfilter.seforimapp.features.notes

import io.github.kdroidfilter.seforim.tabs.SearchScope
import io.github.kdroidfilter.seforim.tabs.TabTitleUpdateManager
import io.github.kdroidfilter.seforim.tabs.TabType
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsEvents
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.core.annotations.BookNote
import io.github.kdroidfilter.seforimapp.core.annotations.UserNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class NotesNavigationTest {
    private lateinit var tabs: TabsViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        tabs = TabsViewModel(TabTitleUpdateManager(), TabsDestination.Home("home"))
    }

    @AfterTest
    fun cleanup() {
        tabs.dispose()
        Dispatchers.resetMain()
    }

    private val selected get() = tabs.state.value.tabs[tabs.state.value.selectedTabIndex]

    @Test
    fun openingANoteKeepsItsBookLineAndNotesPaneFlag() {
        tabs.openTab(noteDestination(BookNote(-8, UserNote(1, 42, 0, 4, "הערה"))))
        val destination = assertIs<TabsDestination.BookContent>(selected.destination)
        assertEquals(-8L, destination.bookId)
        assertEquals(42L, destination.lineId)
        assertTrue(destination.openNotes)
    }

    @Test
    fun replacingATabPreservesTheSearchFolderAndExtendedScope() {
        tabs.replaceCurrentTabDestination(TabsDestination.Search("לימוד", "requested", SearchScope.Category(7), true))
        val destination = assertIs<TabsDestination.Search>(selected.destination)
        assertEquals("home", destination.tabId)
        assertEquals(SearchScope.Category(7), destination.scope)
        assertTrue(destination.globalExtended)
    }

    @Test
    fun freshTabNavigationKeepsAllReadingOptions() {
        val requested = TabsDestination.BookContent(8, "requested", 42, openNotes = true, endLineId = 50, shnayimMikra = true)
        tabs.replaceCurrentTabWithNewTabId(requested)
        val destination = assertIs<TabsDestination.BookContent>(selected.destination)
        assertNotEquals("home", destination.tabId)
        assertEquals(requested.copy(tabId = destination.tabId), destination)
    }

    @Test
    fun notesPageIsReusedAndItsDestinationCanBeRestored() {
        openNotesTab(tabs)
        val destination = assertIs<TabsDestination.Notes>(selected.destination)
        assertEquals(TabType.NOTES, selected.tabType)
        tabs.onEvent(TabsEvents.OnAdd)
        openNotesTab(tabs)
        assertEquals(destination, selected.destination)
        assertEquals(
            1,
            tabs.state.value.tabs
                .count { it.destination is TabsDestination.Notes },
        )
        val encoded = Json.encodeToString<TabsDestination>(destination)
        assertEquals(destination, Json.decodeFromString<TabsDestination>(encoded))
    }

    @Test
    fun notesFilterMatchesNoteQuoteAndBookTitle() {
        val first = BookNote(1, UserNote(1, 10, 0, 3, "הלכה", quote = "ציטוט"))
        val second = BookNote(2, UserNote(2, 20, 0, 3, "Study", quote = "קטע"))
        val notes = UserNotes(listOf(first, second), mapOf(1L to "ברכות", 2L to "שבת"))
        assertEquals(listOf(first), notes.matching(" ברכות "))
        assertEquals(listOf(first), notes.matching("ציטוט"))
        assertEquals(listOf(second), notes.matching("study"))
        assertEquals(listOf(first, second), notes.matching("  "))
        assertTrue(notes.matching("לא קיים").isEmpty())
    }
}

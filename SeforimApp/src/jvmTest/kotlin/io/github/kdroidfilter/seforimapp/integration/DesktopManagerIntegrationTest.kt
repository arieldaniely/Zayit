package io.github.kdroidfilter.seforimapp.integration

import io.github.kdroidfilter.seforim.tabs.TabTitleUpdateManager
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsEvents
import io.github.kdroidfilter.seforimapp.features.search.SearchHomeViewModel
import io.github.kdroidfilter.seforimapp.framework.desktop.DesktopManager
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Integration coverage for the window-scoped desktop and tab lifecycle. */
@OptIn(ExperimentalCoroutinesApi::class)
class DesktopManagerIntegrationTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var persistedStore: TabPersistedStateStore
    private lateinit var desktopManager: DesktopManager

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        persistedStore = TabPersistedStateStore()
        desktopManager = createManager(persistedStore)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts with one focused window on the default desktop`() {
        val desktop = desktopManager.desktops.value.single()
        val window = desktopManager.windows.value.single()

        assertEquals(desktop.id, desktopManager.activeDesktopId.value)
        assertEquals(desktop.id, window.desktopId.value)
        assertEquals(window.id, desktopManager.focusedWindowId.value)
        val initialDestination =
            window.tabsViewModel.state.value.tabs
                .single()
                .destination as TabsDestination.BookContent
        assertEquals(-1L, initialDestination.bookId)
    }

    @Test
    fun `creating a desktop in a new window preserves the original window`() {
        val originalWindow = desktopManager.windows.value.single()
        val newDesktopId = desktopManager.createDesktopInNewWindow("Research")
        val newWindow = desktopManager.focusedWindow()

        assertEquals(2, desktopManager.desktops.value.size)
        assertEquals(2, desktopManager.windows.value.size)
        assertEquals(newDesktopId, newWindow?.desktopId?.value)
        assertNotEquals(originalWindow.id, newWindow?.id)
        assertEquals(
            originalWindow.desktopId.value,
            desktopManager.windows.value
                .first()
                .desktopId.value,
        )
    }

    @Test
    fun `detaching a tab creates another window on the same desktop`() {
        val source = desktopManager.windows.value.single()
        val tabId = UUID.randomUUID().toString()
        source.tabsViewModel.openTab(TabsDestination.BookContent(bookId = 42, tabId = tabId))

        val detached = desktopManager.detachTabToNewWindow(tabId, source.id)

        assertNotNull(detached)
        assertEquals(source.desktopId.value, detached.desktopId.value)
        assertEquals(2, desktopManager.windows.value.size)
        assertFalse(
            source.tabsViewModel.state.value.tabs
                .any { it.destination.tabId == tabId },
        )
        assertTrue(
            detached.tabsViewModel.state.value.tabs
                .any { it.destination.tabId == tabId },
        )
        assertEquals(detached.tabsViewModel, desktopManager.tabsViewModelFor(tabId))
    }

    @Test
    fun `moving a tab transfers ownership between desktop windows`() {
        val source = desktopManager.windows.value.single()
        val tabId = UUID.randomUUID().toString()
        source.tabsViewModel.openTab(TabsDestination.BookContent(bookId = 84, tabId = tabId))
        desktopManager.createDesktopInNewWindow("Study")
        val target = desktopManager.focusedWindow()!!

        desktopManager.moveTabToWindow(tabId, source.id, target.id)

        assertFalse(
            source.tabsViewModel.state.value.tabs
                .any { it.destination.tabId == tabId },
        )
        assertTrue(
            target.tabsViewModel.state.value.tabs
                .any { it.destination.tabId == tabId },
        )
        assertEquals(target.tabsViewModel, desktopManager.tabsViewModelFor(tabId))
        assertEquals(target.id, desktopManager.focusedWindowId.value)
    }

    @Test
    fun `session state restores open desktops and their windows`() {
        desktopManager.createDesktopInNewWindow("Study")
        val state = desktopManager.buildDesktopsState()
        val restored = createManager(TabPersistedStateStore())

        restored.restoreFromDesktopsState(state)

        assertEquals(state.desktops.map { it.name }, restored.desktops.value.map { it.name })
        assertEquals(state.openDesktopIds.toSet(), restored.openDesktopIds().toSet())
        assertEquals(state.openDesktopIds.size, restored.windows.value.size)
        assertEquals(state.focusedDesktopId, restored.activeDesktopId.value)
    }

    @Test
    fun `pinned tabs are restored only in their desktop`() {
        val originalWindow = desktopManager.windows.value.single()
        val originalDesktopId = originalWindow.desktopId.value
        val pinnedTabId =
            originalWindow.tabsViewModel.state.value.tabs
                .single()
                .destination.tabId
        originalWindow.tabsViewModel.onEvent(TabsEvents.OnTogglePin(0))

        desktopManager.createDesktop("Desktop 2")
        assertTrue(
            desktopManager
                .focusedWindow()!!
                .tabsViewModel.state.value.tabs
                .none { it.isPinned },
        )

        desktopManager.focusedWindow()!!.clearSwitching()
        desktopManager.switchTo(originalDesktopId)
        val restoredTabs =
            desktopManager
                .focusedWindow()!!
                .tabsViewModel.state.value.tabs
        assertEquals(
            setOf(pinnedTabId),
            restoredTabs.filter { it.isPinned }.map { it.destination.tabId }.toSet(),
        )
    }

    @Test
    fun `closing the last tab replaces it with a fresh home tab`() {
        val window = desktopManager.windows.value.single()
        val closedTabId =
            window.tabsViewModel.state.value.tabs
                .single()
                .destination.tabId

        window.tabsViewModel.onEvent(TabsEvents.OnClose(0))

        val replacement =
            window.tabsViewModel.state.value.tabs
                .single()
                .destination
        assertTrue(replacement is TabsDestination.BookContent)
        assertEquals(-1L, replacement.bookId)
        assertNotEquals(closedTabId, replacement.tabId)
        assertEquals(1, desktopManager.windows.value.size)
    }

    private fun createManager(store: TabPersistedStateStore): DesktopManager =
        DesktopManager(
            tabPersistedStateStore = store,
            titleUpdateManager = TabTitleUpdateManager(),
            searchHomeViewModelFactory = { mockk<SearchHomeViewModel>(relaxed = true) },
            defaultDesktopName = "Desktop 1",
        )
}

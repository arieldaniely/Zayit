@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.kdroidfilter.seforimapp.features.notes

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.window.WindowState
import io.github.kdroidfilter.seforim.tabs.TabTitleUpdateManager
import io.github.kdroidfilter.seforim.tabs.TabsDestination
import io.github.kdroidfilter.seforim.tabs.TabsViewModel
import io.github.kdroidfilter.seforimapp.framework.desktop.LocalOpenWindow
import io.github.kdroidfilter.seforimapp.framework.desktop.OpenWindow
import io.github.kdroidfilter.seforimapp.framework.di.LocalAppGraph
import io.github.kdroidfilter.seforimapp.testAppGraph
import io.github.kdroidfilter.seforimlibrary.core.models.Book
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import kotlin.test.Test

class NotesScreenUiTest {
    @Test
    fun openingALibraryNoteTargetsItsLineWithTheNotesPaneOpen() =
        runComposeUiTest {
            val graph = testAppGraph()
            val tabs = mockk<TabsViewModel>(relaxed = true)
            every { graph.tabTitleUpdateManager } returns TabTitleUpdateManager()
            coEvery { graph.repository.getBookCore(11) } returns Book(11, 1, 1, "ספר לבדיקה")
            runBlocking { graph.noteStore.addNote(11, 42, 0, 4, "הערה לבדיקה", timestamp = 3, quote = "קטע מסומן") }
            val window = OpenWindow("notes-window", "desktop", tabs, mockk(relaxed = true), WindowState())
            setContent {
                CompositionLocalProvider(LocalAppGraph provides graph, LocalOpenWindow provides window) {
                    IntUiTheme(isDark = false) { NotesTabContent("notes") }
                }
            }
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("הערה לבדיקה").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("הערה לבדיקה").performClick()
            verify {
                tabs.replaceCurrentTabWithNewTabId(
                    match { it is TabsDestination.BookContent && it.bookId == 11L && it.lineId == 42L && it.openNotes },
                    newTabId = any(),
                )
            }
        }
}

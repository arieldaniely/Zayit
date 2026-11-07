package io.github.kdroidfilter.seforimapp.core.catalog

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import io.github.kdroidfilter.seforimapp.features.search.VisibilitySearchEngine
import io.github.kdroidfilter.seforimapp.features.settings.ui.LibraryVisibilitySettingsView
import io.github.kdroidfilter.seforimlibrary.core.models.CatalogBook
import io.github.kdroidfilter.seforimlibrary.core.models.CatalogCategory
import io.github.kdroidfilter.seforimlibrary.core.models.PrecomputedCatalog
import io.github.kdroidfilter.seforimlibrary.search.SearchEngine
import io.github.kdroidfilter.seforimlibrary.search.SearchMode
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryVisibilityTest {
    private val catalog =
        PrecomputedCatalog(
            rootCategories =
                listOf(
                    CatalogCategory(
                        id = 10,
                        title = "הלכה",
                        level = 0,
                        books = listOf(CatalogBook(100, "ספר א", 10, authors = listOf("משה בן מימון"))),
                        subcategories =
                            listOf(
                                CatalogCategory(
                                    id = 11,
                                    title = "שבת",
                                    level = 1,
                                    parentId = 10,
                                    books = listOf(CatalogBook(101, "מִשְׁנֵה תורה", 11, authors = listOf("משֶׁה בן מימון"))),
                                    subcategories = emptyList(),
                                ),
                            ),
                    ),
                    CatalogCategory(
                        id = 20,
                        title = "מחשבה",
                        level = 0,
                        books =
                            listOf(
                                CatalogBook(200, "ספר ב", 20, authors = listOf("ישראל")),
                                CatalogBook(201, "מורה נבוכים", 20, authors = listOf("משה בן מימון")),
                            ),
                        subcategories = emptyList(),
                    ),
                ),
        )

    @Test
    fun `hidden categories include their descendants and independent book choices survive restoring a category`() =
        runTest {
            withDatabase { database ->
                val visibility = LibraryVisibility(database) { catalog }
                visibility.setHidden(LibraryEntryKind.CATEGORY, listOf(10), true)
                visibility.setHidden(LibraryEntryKind.BOOK, listOf(200), true)
                val reopened = LibraryVisibility(database) { catalog }
                assertEquals(setOf(10L, 11L), reopened.snapshot().hiddenCategoryIds)
                assertEquals(setOf(100L, 101L, 200L), reopened.snapshot().hiddenBookIds)
                assertEquals(setOf(201L), reopened.snapshot().allowedBookIds)
                val menus = CatalogAccess(reopened) { catalog }
                assertNull(menus.categoryTitle(10))
                assertTrue(menus.booksFor(11).isEmpty())

                reopened.setHidden(LibraryEntryKind.CATEGORY, listOf(10), false)
                assertTrue(reopened.snapshot().isCategoryVisible(11))
                assertTrue(reopened.snapshot().isBookVisible(101))
                assertFalse(reopened.snapshot().isBookVisible(200))
                assertEquals(listOf(101L), menus.booksFor(11).map { it.id })

                reopened.restoreAll()
                assertNull(LibraryVisibility(database) { catalog }.snapshot().allowedBookIds)
            }
        }

    @Test
    fun `title and author filters work together without requiring Hebrew diacritics`() {
        withDatabase { database ->
            val entries = LibraryVisibility(database) { catalog }.entries()
            assertEquals(listOf(101L), filterLibraryEntries(entries, LibraryEntryKind.BOOK, "משנה", "משה מימון").map { it.id })
            assertTrue(filterLibraryEntries(entries, LibraryEntryKind.BOOK, "משנה", "ישראל").isEmpty())
            assertEquals(listOf(11L), filterLibraryEntries(entries, LibraryEntryKind.CATEGORY, "שבת", "").map { it.id })
        }
    }

    @Test
    fun `hidden books are excluded before searching or counting facets and an empty scope stays empty`() =
        runTest {
            withDatabase { database ->
                val visibility = LibraryVisibility(database) { catalog }
                visibility.setHidden(LibraryEntryKind.CATEGORY, listOf(10), true)
                val delegate = mockk<SearchEngine>(relaxed = true)
                val engine = VisibilitySearchEngine(delegate, visibility)
                engine.openSession("תורה", mode = SearchMode.SMART)
                verify(exactly = 1) { delegate.openSession("תורה", 5, null, null, setOf(200L, 201L), null, false, SearchMode.SMART) }

                engine.computeFacets("תורה", bookIds = listOf(100, 201))
                verify(exactly = 1) {
                    delegate.computeFacets("תורה", 5, null, null, listOf(201L), null, false, SearchMode.FLEXIBLE)
                }
                assertNull(engine.openSession("תורה", bookIds = listOf(100, 101)))
                assertNull(engine.openSession("תורה", categoryFilter = 11))
                visibility.setHidden(LibraryEntryKind.CATEGORY, listOf(20), true)
                assertNull(engine.openSession("תורה"))
                verify(exactly = 1) { delegate.openSession(any(), any(), any(), any(), any(), any(), any(), any()) }
            }
        }

    @Test
    fun `hiding an author removes their books and prunes empty branches`() =
        runTest {
            withDatabase { database ->
                val authors =
                    listOf(
                        LibraryAuthor(1, "Author one", null, setOf(100, 101, 201)),
                        LibraryAuthor(2, "Author two", null, setOf(101)),
                    )
                val visibility = LibraryVisibility(database, { authors }) { catalog }
                visibility.setHidden(LibraryEntryKind.AUTHOR, listOf(1), true)

                val snapshot = visibility.snapshot()
                assertEquals(setOf(100L, 101L, 201L), snapshot.hiddenBookIds)
                assertEquals(setOf(1L, 2L), snapshot.hiddenAuthorIds)
                assertEquals(setOf(10L, 11L), snapshot.hiddenCategoryIds)
                assertEquals(setOf(200L), snapshot.allowedBookIds)

                visibility.setHidden(LibraryEntryKind.AUTHOR, listOf(1), false)
                assertTrue(visibility.snapshot().isCategoryVisible(11))
                assertTrue(visibility.snapshot().isAuthorVisible(2))
            }
        }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `settings can hide and restore books filtered by author`() {
        withDatabase { database ->
            val visibility = LibraryVisibility(database) { catalog }
            runComposeUiTest {
                setContent {
                    IntUiTheme(isDark = false) {
                        LibraryVisibilitySettingsView(visibility)
                    }
                }
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("ספר ב").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithContentDescription("סינון לפי מחבר").performTextInput("משה")
                onNodeWithText("מורה נבוכים").assertIsDisplayed()
                onNodeWithText("ספר ב").assertDoesNotExist()

                onNodeWithText("הסתרת תוצאות").performClick()
                waitUntil(timeoutMillis = 5_000) { visibility.selection.value.bookIds == setOf(100L, 101L, 201L) }
                onNodeWithText("הצגת תוצאות").performClick()
                waitUntil(timeoutMillis = 5_000) { visibility.selection.value.isEmpty }
            }
        }
    }

    private inline fun withDatabase(block: (UserSettingsDb) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            UserSettingsDb.Schema.create(driver)
            block(UserSettingsDb(driver))
        } finally {
            driver.close()
        }
    }
}

package io.github.kdroidfilter.seforimapp.core.favorites

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FavoritesStoreTest {
    @Test
    fun preservesLegacyFoldersAndSupportsIndependentMemberships() =
        runTest {
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
                UserSettingsDb.Schema.create(driver)
                val database = UserSettingsDb(driver)
                val queries = database.favoritesQueries
                queries.insertFolder("First", 1L)
                val first = queries.lastInsertedFolderId().executeAsOne()
                queries.upsertFavorite("book:10", 10L, "Book", first, 1L, null, null)
                val store = FavoritesStore(database)
                val second = store.createFolder("Second", 2L)
                assertEquals(setOf(first), store.get("book:10")?.folderIds)
                store.toggleFolder("book:10", second)
                assertEquals(setOf(first, second), store.get("book:10")?.folderIds)
                assertEquals(1, store.recent(10, first).size)
                assertEquals(1, store.recent(10, second).size)
                store.toggleFolder("book:10", first)
                assertEquals(setOf(second), FavoritesStore(database).get("book:10")?.folderIds)
                store.deleteFolder(second)
                assertTrue(store.get("book:10")!!.folderIds.isEmpty())
                store.remove("book:10")
                assertTrue(store.query().isEmpty())
            }
        }
}

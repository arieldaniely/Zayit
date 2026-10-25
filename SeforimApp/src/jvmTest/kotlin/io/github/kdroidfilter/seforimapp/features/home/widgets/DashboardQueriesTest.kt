package io.github.kdroidfilter.seforimapp.features.home.widgets

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.kdroidfilter.seforimapp.core.annotations.NoteStore
import io.github.kdroidfilter.seforimapp.core.favorites.FavoritesStore
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DashboardQueriesTest {
    @Test
    fun notesAndFavoritesAreBoundedAndFoldersAreFilteredBeforeTheLimit() =
        runTest {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            try {
                UserSettingsDb.Schema.create(driver)
                val database = UserSettingsDb(driver)
                val notes = NoteStore(database)
                val favorites = FavoritesStore(database)
                val folder = favorites.createFolder("folder", 1)
                for (id in 1L..60L) {
                    notes.addNote(1, id, 0, 1, "note $id", id)
                    favorites.add(id, "book $id", id, folderId = if (id <= 10) folder else null)
                }
                assertEquals((60L downTo 54L).toList(), notes.recent(7).map { it.note.lineId })
                assertEquals(60, notes.all().size)
                assertEquals((60L downTo 54L).toList(), favorites.recent(7).map { it.bookId })
                assertEquals((10L downTo 4L).toList(), favorites.recent(7, folder).map { it.bookId })
            } finally {
                driver.close()
            }
        }
}

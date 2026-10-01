package io.github.kdroidfilter.seforimapp.core.history

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.kdroidfilter.seforimapp.db.UserSettingsDb
import io.github.kdroidfilter.seforimapp.framework.session.TabPersistedStateStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HistoryStoreTest {
    @Test
    fun searchSettingsRoundTripAndDistinctTargetsRemainSeparate() =
        runTest {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            try {
                UserSettingsDb.Schema.create(driver)
                val db = UserSettingsDb(driver)
                val store = HistoryStore(db)
                val contexts =
                    listOf(
                        SearchVisitContext("EXACT", true),
                        SearchVisitContext("FLEXIBLE", true),
                        SearchVisitContext("SMART", true),
                        SearchVisitContext("EXACT", false),
                        SearchVisitContext("EXACT", true, categoryId = 10, scopeTitle = "תנ״ך"),
                        SearchVisitContext("EXACT", true, bookId = 20, scopeTitle = "בראשית"),
                        SearchVisitContext("EXACT", true, bookId = 20, tocId = 30, scopeTitle = "בראשית › פרק א"),
                    )
                contexts.forEachIndexed { index, context -> store.recordSearchVisit("  שלום  ", index.toLong(), context) }
                // A changed display title must not create a different target.
                store.recordSearchVisit("שלום", 10, contexts.last().copy(scopeTitle = "בראשית › א"))
                val entries = HistoryStore(db).query("", 100)
                assertEquals(contexts.size, entries.size)
                val latest = entries.first()
                assertEquals(2L, latest.visitCount)
                assertEquals("בראשית › א", latest.searchContext?.scopeTitle)
                val tabs = TabPersistedStateStore()
                assertEquals("שלום", latest.searchDestination("restored", tabs)?.searchQuery)
                assertEquals(latest.searchContext?.persistedState("שלום"), tabs.get("restored")?.search)
                assertEquals(contexts.dropLast(1).toSet(), entries.drop(1).map { it.searchContext }.toSet())
            } finally {
                driver.close()
            }
        }

    @Test
    fun existingHistorySurvivesAnIdempotentSchemaUpgrade() =
        runTest {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            try {
                driver.execute(
                    null,
                    """
                    CREATE TABLE visit_history (
                        key TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, bookId INTEGER,
                        searchQuery TEXT, title TEXT NOT NULL, visitedAt INTEGER NOT NULL,
                        visitCount INTEGER NOT NULL DEFAULT 1, tocEntryId INTEGER, lineId INTEGER
                    )
                    """.trimIndent(),
                    0,
                )
                driver.execute(
                    null,
                    """
                    INSERT INTO visit_history(key, kind, searchQuery, title, visitedAt)
                    VALUES ('search:old', 'search', 'old', 'old', 1)
                    """.trimIndent(),
                    0,
                )
                UserSettingsDb.Schema.create(driver)
                upgradeHistorySchema(driver)
                upgradeHistorySchema(driver)
                val store = HistoryStore(UserSettingsDb(driver))
                val old = store.query("", 100).single()
                assertNull(old.searchContext)
                assertEquals("old", old.searchQuery)
                store.recordSearchVisit("new", 2, SearchVisitContext("SMART", false))
                store.recordBookVisit(5, "ספר", 3, tocEntryId = 6, lineId = 7)
                assertEquals(3, store.query("", 100).size)
                assertEquals(7L, store.query("ספר", 100).single().lineId)
            } finally {
                driver.close()
            }
        }
}

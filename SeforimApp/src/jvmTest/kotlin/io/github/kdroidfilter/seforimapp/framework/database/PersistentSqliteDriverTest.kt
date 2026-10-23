package io.github.kdroidfilter.seforimapp.framework.database

import app.cash.sqldelight.db.QueryResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersistentSqliteDriverTest {
    @Test
    fun nestedIdenticalQueriesDoNotResetTheOuterCursor() {
        PersistentSqliteDriver("jdbc:sqlite::memory:").use { driver ->
            val sql = "SELECT 1 UNION ALL SELECT 2"
            val result =
                driver.executeQuery(
                    identifier = 1,
                    sql = sql,
                    mapper = { outer ->
                        assertTrue(outer.next().value)
                        assertEquals(1L, outer.getLong(0))
                        val nested =
                            driver.executeQuery(
                                identifier = 1,
                                sql = sql,
                                mapper = { inner ->
                                    assertTrue(inner.next().value)
                                    QueryResult.Value(inner.getLong(0))
                                },
                                parameters = 0,
                                binders = null,
                            )
                        assertEquals(1L, nested.value)
                        assertTrue(outer.next().value)
                        QueryResult.Value(outer.getLong(0))
                    },
                    parameters = 0,
                    binders = null,
                )
            assertEquals(2L, result.value)
            driver.clearStatementCache()
            driver.execute(null, "CREATE TABLE smoke (id INTEGER)", 0, null)
            driver.execute(null, "INSERT INTO smoke VALUES (1)", 0, null)
        }
    }
}

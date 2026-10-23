package io.github.kdroidfilter.seforimapp.framework.database

import java.sql.DriverManager
import java.sql.PreparedStatement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SqliteStatementCacheTest {
    @Test
    fun variableLengthQueriesRetainAtMost128NativeStatements() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            val statements = mutableListOf<PreparedStatement>()
            val cache = SqliteStatementCache(connection::prepareStatement)
            try {
                for (arity in 1..300) {
                    val placeholders = List(arity) { "?" }.joinToString(",")
                    cache.withStatement("SELECT 1 WHERE 1 IN ($placeholders)") { statement ->
                        statements += statement
                        for (index in 1..arity) statement.setInt(index, 1)
                        statement.executeQuery().use { rows ->
                            assertTrue(rows.next())
                            assertEquals(1, rows.getInt(1))
                        }
                    }
                    assertTrue(statements.count { !it.isClosed } <= 128)
                }
                assertEquals(128, statements.count { !it.isClosed })
            } finally {
                cache.clear()
            }
            assertTrue(statements.all { it.isClosed })
        }
    }

    @Test
    fun frequentlyUsedStatementsSurviveEviction() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            val cache = SqliteStatementCache(connection::prepareStatement, capacity = 2)
            try {
                val frequent = cache.withStatement("SELECT 1") { it }
                val stale = cache.withStatement("SELECT 2") { it }
                assertSame(frequent, cache.withStatement("SELECT 1") { it })
                cache.withStatement("SELECT 3") { }
                assertTrue(stale.isClosed)
                assertFalse(frequent.isClosed)
                assertSame(frequent, cache.withStatement("SELECT 1") { it })
                assertNotSame(stale, cache.withStatement("SELECT 2") { it })
            } finally {
                cache.clear()
            }
        }
    }

    @Test
    fun activeCursorSurvivesNestedReuseAndEviction() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            val cache = SqliteStatementCache(connection::prepareStatement, capacity = 2)
            val sql = "SELECT 1 UNION ALL SELECT 2"
            try {
                cache.withStatement(sql) { outer ->
                    outer.executeQuery().use { rows ->
                        assertTrue(rows.next())
                        assertEquals(1, rows.getInt(1))
                        cache.withStatement(sql) { nested ->
                            assertNotSame(outer, nested)
                            nested.executeQuery().use { assertTrue(it.next()) }
                        }
                        repeat(5) { index -> cache.withStatement("SELECT $index") { } }
                        assertFalse(outer.isClosed)
                        assertTrue(rows.next())
                        assertEquals(2, rows.getInt(1))
                    }
                }
            } finally {
                cache.clear()
            }
        }
    }

    @Test
    fun clearingDuringAQueryClosesItsStatementAfterTheCursorFinishes() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            val cache = SqliteStatementCache(connection::prepareStatement)
            val sql = "SELECT 1 UNION ALL SELECT 2"
            val active =
                cache.withStatement(sql) { statement ->
                    statement.executeQuery().use { rows ->
                        assertTrue(rows.next())
                        cache.clear()
                        assertFalse(statement.isClosed)
                        assertTrue(rows.next())
                        assertEquals(2, rows.getInt(1))
                    }
                    statement
                }
            assertTrue(active.isClosed)
            try {
                assertNotSame(active, cache.withStatement(sql) { it })
            } finally {
                cache.clear()
            }
        }
    }
}

package io.github.kdroidfilter.seforimapp.framework.database

import java.sql.PreparedStatement

/** Bounded LRU of idle statements. The caller must hold the connection lock. */
internal class SqliteStatementCache(
    private val prepare: (String) -> PreparedStatement,
    private val capacity: Int = 128,
) {
    private val idle = LinkedHashMap<String, PreparedStatement>()
    private var generation = 0L

    init {
        require(capacity > 0)
    }

    fun <T> withStatement(
        sql: String,
        block: (PreparedStatement) -> T,
    ): T {
        // A mapper can issue nested queries. Its active statement must neither be reused
        // (which resets its cursor/bindings) nor evicted while that mapper is running.
        val statement = idle.remove(sql)?.takeUnless { it.isClosed } ?: prepare(sql)
        val acquiredGeneration = generation
        var queryFailure: Throwable? = null
        try {
            return block(statement)
        } catch (failure: Throwable) {
            queryFailure = failure
            throw failure
        } finally {
            try {
                if (acquiredGeneration != generation) {
                    statement.close()
                } else if (!statement.isClosed) {
                    idle.remove(sql)?.close()
                    if (idle.size >= capacity) {
                        val oldest = idle.entries.iterator()
                        val evicted = oldest.next().value
                        oldest.remove()
                        evicted.close()
                    }
                    idle[sql] = statement
                }
            } catch (failure: Throwable) {
                runCatching { statement.close() }.onFailure(failure::addSuppressed)
                if (queryFailure == null) throw failure
                queryFailure.addSuppressed(failure)
            }
        }
    }

    fun clear() {
        generation++
        val statements = idle.values.toList()
        idle.clear()
        var failure: Throwable? = null
        statements.forEach { statement ->
            try {
                statement.close()
            } catch (closeFailure: Throwable) {
                val previousFailure = failure
                if (previousFailure == null) failure = closeFailure else previousFailure.addSuppressed(closeFailure)
            }
        }
        failure?.let { throw it }
    }
}

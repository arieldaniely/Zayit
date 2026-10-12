package io.github.kdroidfilter.seforimapp.core.history

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver

/** Schema.create uses IF NOT EXISTS, so existing tables need explicit column upgrades. */
fun upgradeHistorySchema(driver: SqlDriver) {
    val columns =
        driver
            .executeQuery(
                null,
                "PRAGMA table_info(visit_history)",
                { cursor ->
                    val names = mutableSetOf<String>()
                    while (cursor.next().value) cursor.getString(1)?.let(names::add)
                    QueryResult.Value(names)
                },
                0,
            ).value
    mapOf(
        "tocEntryId" to "INTEGER",
        "lineId" to "INTEGER",
        "searchContext" to "TEXT",
        "searchCategoryId" to "INTEGER",
        "searchBookId" to "INTEGER",
        "searchTocId" to "INTEGER",
        "searchExtended" to "INTEGER NOT NULL DEFAULT 0",
        "searchMode" to "TEXT NOT NULL DEFAULT ''",
    ).forEach { (name, type) ->
        if (name !in columns) driver.execute(null, "ALTER TABLE visit_history ADD COLUMN $name $type", 0)
    }
    if ("searchContext" in columns && "searchMode" !in columns) {
        val contexts =
            driver
                .executeQuery(
                    null,
                    "SELECT key, searchContext FROM visit_history WHERE searchContext IS NOT NULL",
                    { cursor ->
                        val rows = mutableListOf<Pair<String, String>>()
                        while (cursor.next().value) {
                            val key = cursor.getString(0)
                            val context = cursor.getString(1)
                            if (key != null && context != null) rows += key to context
                        }
                        QueryResult.Value(rows)
                    },
                    0,
                ).value
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        contexts.forEach { (key, saved) ->
            val context = runCatching { json.decodeFromString<SearchVisitContext>(saved) }.getOrNull() ?: return@forEach
            driver.execute(
                null,
                "UPDATE visit_history SET searchCategoryId=?, searchBookId=?, searchTocId=?, searchExtended=?, searchMode=? WHERE key=?",
                6,
            ) {
                bindLong(0, context.categoryId.takeIf { it != 0L })
                bindLong(1, context.bookId.takeIf { it != 0L })
                bindLong(2, context.tocId.takeIf { it != 0L })
                bindLong(3, if (context.globalExtended) 1L else 0L)
                bindString(4, context.mode)
                bindString(5, key)
            }
        }
    }
}

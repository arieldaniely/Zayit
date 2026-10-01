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
    mapOf("tocEntryId" to "INTEGER", "lineId" to "INTEGER", "searchContext" to "TEXT").forEach { (name, type) ->
        if (name !in columns) driver.execute(null, "ALTER TABLE visit_history ADD COLUMN $name $type", 0)
    }
}

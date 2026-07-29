package io.github.kdroidfilter.seforimapp.features.personallibrary

import io.github.kdroidfilter.seforimapp.framework.database.PersistentSqliteDriver
import java.nio.file.Path

/** Installs same-name TEMP views, so existing SQLDelight queries read a zero-copy union. */
class PersonalLibraryOverlay(private val driver: PersistentSqliteDriver) {
    @Synchronized
    fun attach(database: Path?) {
        val connection = driver.getConnection()
        TABLES.asReversed().forEach { table ->
            connection.createStatement().use { it.execute("DROP VIEW IF EXISTS temp.\"$table\"") }
        }
        runCatching { connection.createStatement().use { it.execute("DETACH DATABASE personal") } }
        if (database == null) return
        val escaped = database.toAbsolutePath().toString().replace("'", "''")
        connection.createStatement().use { it.execute("ATTACH DATABASE '$escaped' AS personal") }
        TABLES.forEach { table ->
            connection.createStatement().use {
                it.execute(
                    "CREATE TEMP VIEW \"$table\" AS " +
                        "SELECT * FROM main.\"$table\" UNION ALL SELECT * FROM personal.\"$table\"",
                )
            }
        }
    }

    companion object {
        private val TABLES = listOf(
            "category", "category_closure", "author", "topic", "pub_place", "pub_date", "source",
            "book", "book_pub_place", "book_pub_date", "book_topic", "book_author", "line", "tocText",
            "tocEntry", "connection_type", "link", "book_has_links", "line_toc", "alt_toc_structure",
            "alt_toc_entry", "line_alt_toc", "book_acronym", "default_commentator", "default_targum",
        )
    }
}

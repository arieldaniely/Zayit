package io.github.kdroidfilter.seforimapp.features.personallibrary

import io.github.kdroidfilter.seforimapp.framework.database.PersistentSqliteDriver
import java.nio.file.Path

/** Installs same-name TEMP views, so existing SQLDelight queries read a zero-copy union. */
class PersonalLibraryOverlay(private val driver: PersistentSqliteDriver) {
    @Synchronized
    fun attach(database: Path?) {
        val connection = driver.getConnection()
        driver.setPersonalOverlayAttached(false)
        TABLES.asReversed().forEach { table ->
            connection.createStatement().use { it.execute("DROP VIEW IF EXISTS temp.\"$table\"") }
        }
        runCatching { connection.createStatement().use { it.execute("DETACH DATABASE personal") } }
        if (database == null) return
        val escaped = database.toAbsolutePath().toString().replace("'", "''")
        connection.createStatement().use { it.execute("ATTACH DATABASE '$escaped' AS personal") }
        connection.createStatement().use { statement ->
            // ATTACH starts with SQLite's tiny default cache and mmap disabled for the new schema.
            // The personal database is much smaller than the multi-gigabyte corpus. Giving both
            // schemas a 256 MiB page cache made the app retain hundreds of unnecessary megabytes
            // after a UNION scan. A 64 MiB cache plus 128 MiB mmap keeps indexed reads hot without
            // duplicating the main database's memory budget.
            statement.execute("PRAGMA personal.cache_size=-65536")
            statement.execute("PRAGMA personal.mmap_size=134217728")
        }
        TABLES.forEach { table ->
            connection.createStatement().use {
                it.execute(
                    "CREATE TEMP VIEW \"$table\" AS " +
                        "SELECT * FROM main.\"$table\" UNION ALL SELECT * FROM personal.\"$table\"",
                )
            }
        }
        driver.setPersonalOverlayAttached(true)
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

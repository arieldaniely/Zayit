package io.github.kdroidfilter.seforimapp.framework.database

import java.sql.Connection

/** Project older author schemas into the current reader model without changing the installed corpus. */
internal fun installAuthorMetadataCompatibility(
    connection: Connection,
    personalAttached: Boolean = false,
) {
    fun projection(schema: String): String {
        val columns =
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA $schema.table_info(author)").use { rows ->
                    buildSet { while (rows.next()) add(rows.getString("name")) }
                }
            }
        val fields = listOf("id", "name", "birthYear", "birthYearApprox", "deathYear", "deathYearApprox", "era")
        return "SELECT " +
            fields.joinToString(",") { field ->
                if (field in columns) "\"$field\"" else "NULL AS \"$field\""
            } + " FROM $schema.author"
    }
    connection.createStatement().use { statement ->
        statement.execute("DROP VIEW IF EXISTS temp.author")
        val union = projection("main") + if (personalAttached) " UNION ALL " + projection("personal") else ""
        statement.execute("CREATE TEMP VIEW author AS $union")
        // Old bundles may predate these optional tables altogether. Empty, typed views keep
        // names and linked books available; an absent biography must not hide an author.
        val optional =
            mapOf(
                "author_alias" to "NULL AS authorId, NULL AS alias",
                "author_bio" to "NULL AS authorId, NULL AS summary, NULL AS bodyMd, NULL AS confidence, NULL AS licenseId",
                "edition" to "NULL AS id, NULL AS title, NULL AS heTitle, NULL AS sourceUrl",
                "license" to "NULL AS id, NULL AS code",
                "book_edition" to "NULL AS bookId, NULL AS editionId, NULL AS licenseId, NULL AS licenseInferred",
            )
        optional.forEach { (table, fields) ->
            val exists =
                connection.createStatement().use { query ->
                    query.executeQuery("SELECT 1 FROM main.sqlite_master WHERE name='$table'").use { it.next() }
                }
            if (!exists) statement.execute("CREATE TEMP VIEW IF NOT EXISTS $table AS SELECT $fields WHERE 0")
        }
    }
}

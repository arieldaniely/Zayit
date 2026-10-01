package io.github.kdroidfilter.seforimapp.features.personallibrary

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.kdroidfilter.seforimlibrary.db.SeforimDb
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersonalLibraryImporterTest {
    @Test
    fun skipsDamagedBooksAndImportsRemainingTxt() {
        val temp = Files.createTempDirectory("personal-library-mixed")
        try {
            val baseDatabase = temp.resolve("base.db")
            JdbcSqliteDriver("jdbc:sqlite:$baseDatabase").use(SeforimDb.Schema::create)
            val books = Files.createDirectory(temp.resolve("books"))
            books.resolve("01-valid.txt").writeText("טקסט ראשון")
            Files.write(books.resolve("02-broken.txt"), byteArrayOf(0xC3.toByte(), 0x28))
            books.resolve("03-valid.txt").writeText("<h1>פרק א</h1>\nטקסט שני")
            val importer = PersonalLibraryImporter(baseDatabase, temp.resolve("generations"))
            val folder = PersonalBookFolder("mixed", books.toString(), "Mixed")
            val progress = mutableListOf<Float>()
            val (artifacts, summaries) = importer.build(listOf(folder), "mixed", progress::add)
            val summary = summaries.getValue(folder.id)
            assertEquals(2, summary.books)
            assertEquals(listOf("02-broken.txt"), summary.failedFiles)
            assertEquals(1f, progress.last())
            assertEquals(progress.sorted(), progress)
            DriverManager.getConnection("jdbc:sqlite:${artifacts.databasePath}").use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT title,totalLines FROM book ORDER BY title").use { rows ->
                        assertTrue(rows.next())
                        assertEquals("01-valid", rows.getString(1))
                        assertTrue(rows.next())
                        assertEquals("03-valid", rows.getString(1))
                        assertEquals(2, rows.getInt(2))
                        assertTrue(!rows.next())
                    }
                    statement.executeQuery("SELECT text FROM tocText").use { rows ->
                        assertTrue(rows.next())
                        assertEquals("פרק א", rows.getString(1))
                    }
                }
            }
        } finally {
            temp.toFile().deleteRecursively()
        }
    }

    @Test
    fun importsPlainFolderWithoutOtzariaSubdirectory() {
        val temp = Files.createTempDirectory("personal-library-import")
        try {
            val baseDatabase = temp.resolve("base.db")
            JdbcSqliteDriver("jdbc:sqlite:$baseDatabase").use(SeforimDb.Schema::create)

            val books = Files.createDirectory(temp.resolve("books"))
            books.resolve("ספר בדיקה.txt").writeText("שורה ראשונה\nשורה שנייה")

            val importer = PersonalLibraryImporter(baseDatabase, temp.resolve("generations"))
            val folder =
                PersonalBookFolder(
                    id = "plain-folder",
                    path = books.toString(),
                    displayName = "הספרים שלי",
                    placement = PersonalFolderPlacement.PERSONAL_BOOKS,
                )

            val (artifacts, summaries) = importer.build(listOf(folder), "test-generation")

            assertTrue(Files.isRegularFile(artifacts.databasePath))
            assertEquals(1, summaries.getValue(folder.id).books)
            DriverManager.getConnection("jdbc:sqlite:${artifacts.databasePath}").use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT title, totalLines FROM book").use { rows ->
                        assertTrue(rows.next())
                        assertEquals("ספר בדיקה", rows.getString("title"))
                        assertEquals(2, rows.getInt("totalLines"))
                    }
                    statement
                        .executeQuery(
                            "SELECT value FROM schema_meta WHERE key='personal_target_book_hints_v2'",
                        ).use { rows ->
                            assertTrue(rows.next())
                            assertEquals("1", rows.getString(1))
                        }
                    statement
                        .executeQuery(
                            "SELECT COUNT(*) FROM personal_link_target_book",
                        ).use { rows ->
                            assertTrue(rows.next())
                            assertEquals(0, rows.getInt(1))
                        }
                }
            }
        } finally {
            temp.toFile().deleteRecursively()
        }
    }

    @Test
    fun reportsProgressUpdatesDuringBuild() {
        val temp = Files.createTempDirectory("personal-library-progress")
        try {
            val baseDatabase = temp.resolve("base.db")
            JdbcSqliteDriver("jdbc:sqlite:$baseDatabase").use(SeforimDb.Schema::create)

            val books = Files.createDirectory(temp.resolve("books"))
            books.resolve("ספר בדיקה.txt").writeText("שורה ראשונה")

            val importer = PersonalLibraryImporter(baseDatabase, temp.resolve("generations"))
            val folder =
                PersonalBookFolder(
                    id = "progress-folder",
                    path = books.toString(),
                    displayName = "הספרים שלי",
                    placement = PersonalFolderPlacement.PERSONAL_BOOKS,
                )
            val progressValues = mutableListOf<Float>()

            importer.build(listOf(folder), "test-progress") { progress ->
                progressValues += progress
            }

            assertTrue(progressValues.isNotEmpty())
            assertTrue(progressValues.last() >= 1f)
            assertTrue(progressValues.size > 4, "Progress should be reported throughout import and indexing")
            assertEquals(progressValues.sorted(), progressValues, "Progress must never move backwards")
            assertTrue(progressValues.any { it in 0.03f..0.62f }, "Import should report intermediate progress")
            assertTrue(progressValues.any { it in 0.62f..0.98f }, "Indexing should report intermediate progress")
        } finally {
            temp.toFile().deleteRecursively()
        }
    }

    @Test
    fun preservesHeadingOrderWhenImporting() {
        val temp = Files.createTempDirectory("personal-library-heading-order")
        try {
            val baseDatabase = temp.resolve("base.db")
            JdbcSqliteDriver("jdbc:sqlite:$baseDatabase").use(SeforimDb.Schema::create)

            val books = Files.createDirectory(temp.resolve("books"))
            val content =
                """
                <h1>כותרת א</h1>
                טקסט פרק א
                <h1>כותרת ב</h1>
                טקסט פרק ב
                <h2>תת כותרת ב1</h2>
                טקסט סעיף
                <h1>כותרת ג</h1>
                טקסט פרק ג
                """.trimIndent()
            books.resolve("ספר מרובה כותרות.txt").writeText(content)

            val importer = PersonalLibraryImporter(baseDatabase, temp.resolve("generations"))
            val folder =
                PersonalBookFolder(
                    id = "headings-folder",
                    path = books.toString(),
                    displayName = "הספרים שלי",
                    placement = PersonalFolderPlacement.PERSONAL_BOOKS,
                )

            val (artifacts, _) = importer.build(listOf(folder), "test-headings")

            DriverManager.getConnection("jdbc:sqlite:${artifacts.databasePath}").use { connection ->
                connection.createStatement().use { statement ->
                    val query =
                        """
                        SELECT tt.text, t.id
                        FROM tocEntry t
                        JOIN tocText tt ON t.textId = tt.id
                        ORDER BY t.id ASC
                        """.trimIndent()
                    statement.executeQuery(query).use { rows ->
                        val titles = mutableListOf<String>()
                        while (rows.next()) {
                            titles.add(rows.getString("text"))
                        }
                        assertEquals(
                            listOf("כותרת א", "כותרת ב", "תת כותרת ב1", "כותרת ג"),
                            titles,
                        )
                    }
                }
            }
        } finally {
            temp.toFile().deleteRecursively()
        }
    }
}

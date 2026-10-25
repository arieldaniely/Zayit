package io.github.kdroidfilter.seforimapp.framework.database

import io.github.kdroidfilter.seforimapp.features.search.domain.BookDetailsLoader
import io.github.kdroidfilter.seforimlibrary.dao.repository.SeforimRepository
import io.github.kdroidfilter.seforimlibrary.db.SeforimDb
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AuthorMetadataCompatibilityTest {
    @Test
    fun installedMetadataLoadsThroughTheActualRepository() =
        runBlocking {
            val installed = System.getenv("ZAYITA_TEST_DATABASE") ?: return@runBlocking
            PersistentSqliteDriver("jdbc:sqlite::memory:").use { driver ->
                SeforimDb.Schema.create(driver)
                val connection = driver.getConnection()
                connection.prepareStatement("ATTACH DATABASE ? AS installed").use { statement ->
                    statement.setString(1, Path.of(installed).toUri().toASCIIString() + "?mode=ro")
                    statement.execute()
                }
                val bookId =
                    connection.createStatement().use { statement ->
                        statement.executeQuery("SELECT bookId FROM installed.book_author ORDER BY bookId LIMIT 1").use { rows ->
                            check(rows.next()) { "Installed library has no linked authors" }
                            rows.getLong(1)
                        }
                    }
                connection.createStatement().use { statement ->
                    // Copy only a book and its real author rows into memory, preserving the installed schema.
                    statement.execute("DROP TABLE main.author")
                    statement.execute("CREATE TABLE main.author AS SELECT * FROM installed.author")
                    statement.execute("INSERT INTO main.book SELECT * FROM installed.book WHERE id=$bookId")
                    statement.execute("INSERT INTO main.book_author SELECT * FROM installed.book_author WHERE bookId=$bookId")
                }
                val repository = SeforimRepository("", driver)
                installAuthorMetadataCompatibility(connection)
                val book = assertNotNull(repository.getBook(bookId))
                val author = assertNotNull(repository.getAuthorDetails(book.authors.first().id))
                assertEquals(book.title, author.books.single().title)
                assertEquals(author.name, book.authors.first().name)
                val details = assertNotNull(BookDetailsLoader(repository).load(bookId))
                assertEquals(book.title, details.book.title)
                assertEquals(author.id, details.authors.single().id)
                println(
                    "Installed metadata verified: book=${book.id}, author=${author.id}, description=${!book.heShortDesc.isNullOrBlank()}",
                )
            }
        }

    @Test
    fun oldAuthorSchemaStillLoadsBookAuthorsAndAuthorBooks() =
        runBlocking {
            PersistentSqliteDriver("jdbc:sqlite::memory:").use { driver ->
                SeforimDb.Schema.create(driver)
                driver.getConnection().createStatement().use { statement ->
                    statement.execute("DROP TABLE author")
                    statement.execute("CREATE TABLE author (id INTEGER PRIMARY KEY NOT NULL, name TEXT NOT NULL)")
                    statement.execute("INSERT INTO author VALUES (7, 'מחבר לבדיקה')")
                    statement.execute("INSERT INTO book (id,categoryId,sourceId,title) VALUES (9,1,1,'ספר לבדיקה')")
                    statement.execute("INSERT INTO book_author VALUES (9,7)")
                }
                val repository = SeforimRepository("", driver)
                installAuthorMetadataCompatibility(driver.getConnection())
                assertEquals(
                    "מחבר לבדיקה",
                    repository
                        .getBook(9)
                        ?.authors
                        ?.single()
                        ?.name,
                )
                val author = assertNotNull(repository.getAuthorDetails(7))
                assertEquals("מחבר לבדיקה", author.name)
                assertNull(author.birthYear)
                assertEquals("ספר לבדיקה", author.books.single().title)
                // The compatibility projection is temporary; it never upgrades the corpus on disk.
                driver.getConnection().createStatement().use { statement ->
                    statement.executeQuery("PRAGMA main.table_info(author)").use { rows ->
                        var count = 0
                        while (rows.next()) count++
                        assertEquals(2, count)
                    }
                }
            }
        }
}
